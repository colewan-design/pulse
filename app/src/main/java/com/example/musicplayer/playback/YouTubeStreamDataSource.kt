package com.example.musicplayer.playback

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import com.example.musicplayer.core.data.innertube.InnerTubeStreamResolver
import com.example.musicplayer.core.data.innertube.StreamResolutionException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Turns `ytmusic://<videoId>` placeholders into real, signed CDN URLs at the moment the player
 * opens the stream.
 *
 * Resolving here rather than when the queue is built is what makes streamed queues survive: a
 * signed URL lives a few hours, so a queue item built this morning would be dead by evening, and
 * a restored queue would fail on every track. By deferring to open() each playback attempt gets a
 * fresh URL, and a mid-queue expiry costs one extra round trip instead of a playback error.
 */
@Singleton
class YouTubeStreamDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: InnerTubeStreamResolver,
) {

    /** Shared so the cache index is opened exactly once per process — SimpleCache requires it. */
    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, STREAM_CACHE_DIR),
            LeastRecentlyUsedCacheEvictor(STREAM_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    /**
     * Builds the full chain: cache in front, resolver in the middle, HTTP (or local file) behind.
     *
     * [DefaultDataSource] is the terminal factory so the same player handles `content://` library
     * files and streamed tracks without swapping media sources per item.
     */
    fun create(): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(STREAM_USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            // googlevideo redirects between CDN hosts routinely.
            .setAllowCrossProtocolRedirects(true)

        val resolving = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context, httpFactory),
            YouTubeUriResolver(resolver),
        )

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolving)
            // A failed write to the cache should never take playback down with it.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun release() = cache.release()

    private companion object {
        const val STREAM_CACHE_DIR = "yt-stream-cache"
        const val STREAM_CACHE_BYTES = 512L * 1024 * 1024
        const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * Rewrites a placeholder DataSpec into one pointing at a live CDN URL.
 *
 * [ResolvingDataSource.Resolver.resolveDataSpec] is invoked on an ExoPlayer loading thread, never
 * the main thread, so blocking on the suspend resolver here is safe and is what the interface
 * expects.
 */
private class YouTubeUriResolver(
    private val resolver: InnerTubeStreamResolver,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.scheme != YOUTUBE_URI_SCHEME) return dataSpec

        val videoId = dataSpec.uri.host?.takeIf { it.isNotBlank() }
            ?: dataSpec.uri.schemeSpecificPart?.trimStart('/')?.takeIf { it.isNotBlank() }
            ?: throw IOException("Malformed stream placeholder: ${dataSpec.uri}")

        val resolved = try {
            runBlocking { resolver.resolve(videoId) }
        } catch (e: StreamResolutionException) {
            // Must be an IOException: ExoPlayer's loader only wraps IOExceptions into a
            // PlaybackException, so anything else escapes as an unexpected crash rather than
            // reaching Player.Listener.onPlayerError and the skip-to-next recovery.
            // The per-profile reasons are folded into the message — "no profile worked" on its
            // own is undiagnosable once this is in a user's hands.
            throw IOException(
                e.attempts.joinToString(prefix = "Could not resolve $videoId: ") { attempt ->
                    "${attempt.clientName}=${attempt.status ?: "?"}" +
                        (attempt.reason?.let { " ($it)" } ?: "")
                },
                e,
            )
        }

        // Keying the cache by videoId rather than the signed URL is essential: the URL contains
        // an expiry and a signature, so URL-keyed entries would never be reused.
        return dataSpec.buildUpon()
            .setUri(resolved.url)
            .setKey(videoId)
            .build()
    }
}
