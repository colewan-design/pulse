package com.example.musicplayer.core.data.innertube

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves videoIds to audio streams by walking [InnerTubeClient.STREAM_PROFILE_ORDER] until one
 * profile returns a playable format.
 *
 * This fallback chain is the whole point of the class. Google rotates which clients require
 * attestation, which get ciphered URLs, and which get throttled, and it does so without notice —
 * so a single hard-coded client is a build that works until it suddenly doesn't. Walking a list
 * degrades instead: when the cheap mobile profiles stop working the expensive web profile still
 * serves, and the only cost is latency.
 *
 * Resolved URLs are cached in memory until shortly before they expire. They are deliberately
 * never written to Room: they are signed, IP-bound and short-lived, so a persisted URL is a
 * guaranteed playback failure after a restart.
 */
@Singleton
class InnerTubeStreamResolver @Inject constructor(
    private val service: InnerTubeService,
    private val jsChallengeSolver: JsChallengeSolver,
) : StreamResolver {

    private val cache = mutableMapOf<String, ResolvedStream>()
    private val cacheMutex = Mutex()

    /**
     * Circuit breaker for systemic failure.
     *
     * When every profile is being refused, each further attempt is five more requests against an
     * endpoint that is already refusing us — and YouTube's "Sign in to confirm you're not a bot"
     * gate is triggered by request volume and IP reputation, so retrying in a tight loop actively
     * deepens the block. A queue of 25 tracks skipping on error turns one refusal into 125
     * requests in seconds, which is how a transient throttle becomes a sticky one.
     */
    @Volatile
    private var breakerOpenUntilMillis = 0L

    @Volatile
    private var consecutiveTotalFailures = 0

    @Volatile
    var qualityPreference: AudioQualityPreference = AudioQualityPreference.HIGHEST

    override suspend fun resolve(videoId: String): ResolvedStream {
        cacheMutex.withLock {
            cache[videoId]?.let { cached ->
                if (!cached.isExpired()) return cached
                cache.remove(videoId)
            }
        }

        val now = System.currentTimeMillis()
        if (now < breakerOpenUntilMillis) {
            throw StreamResolutionException(
                message = "Stream resolution paused for " +
                    "${(breakerOpenUntilMillis - now) / 1000}s after $consecutiveTotalFailures " +
                    "consecutive failures across all client profiles. Retrying sooner makes the " +
                    "block worse, not better.",
            )
        }

        val attempts = mutableListOf<ProfileAttempt>()
        for (client in InnerTubeClient.STREAM_PROFILE_ORDER) {
            val resolved = try {
                attempt(videoId, client, attempts)
            } catch (e: Exception) {
                // A transport failure on one profile should not abort the chain.
                Log.w(TAG, "Profile ${client.clientName} threw for $videoId", e)
                attempts += ProfileAttempt(client.clientName, "EXCEPTION", e.message)
                null
            }

            if (resolved != null) {
                cacheMutex.withLock { cache[videoId] = resolved }
                consecutiveTotalFailures = 0
                breakerOpenUntilMillis = 0L
                return resolved
            }
        }

        consecutiveTotalFailures++
        if (consecutiveTotalFailures >= FAILURES_BEFORE_BREAKER) {
            // Exponential, capped. Deliberately long: the gate this backs off from is measured in
            // minutes, and a short cooldown just resumes hammering.
            val backoffMillis = (BASE_BACKOFF_MS shl
                (consecutiveTotalFailures - FAILURES_BEFORE_BREAKER).coerceAtMost(4))
                .coerceAtMost(MAX_BACKOFF_MS)
            breakerOpenUntilMillis = System.currentTimeMillis() + backoffMillis
            Log.w(
                TAG,
                "All profiles failed $consecutiveTotalFailures times; pausing resolution for " +
                    "${backoffMillis / 1000}s. Last attempts: $attempts",
            )
        }

        throw StreamResolutionException(
            message = "No client profile could resolve $videoId",
            attempts = attempts,
        )
    }

    private suspend fun attempt(
        videoId: String,
        client: InnerTubeClient,
        attempts: MutableList<ProfileAttempt>,
    ): ResolvedStream? {
        val response = service.player(videoId, client)
        val status = response.playabilityStatus?.status

        if (status != null && status != "OK") {
            attempts += ProfileAttempt(client.clientName, status, response.playabilityStatus?.reason)
            return null
        }

        val format = selectFormat(response.streamingData?.adaptiveFormats.orEmpty())
        if (format == null) {
            attempts += ProfileAttempt(client.clientName, "NO_AUDIO_FORMAT", null)
            return null
        }

        val url = materialiseUrl(format) ?: run {
            attempts += ProfileAttempt(client.clientName, "UNRESOLVED_CIPHER", null)
            return null
        }

        val expiresInSeconds = response.streamingData?.expiresInSeconds?.toLongOrNull()
            ?: DEFAULT_EXPIRY_SECONDS

        return ResolvedStream(
            videoId = videoId,
            url = url,
            itag = format.itag,
            mimeType = format.mimeType.substringBefore(';').trim(),
            bitrate = format.effectiveBitrate,
            contentLengthBytes = format.contentLength?.toLongOrNull(),
            loudnessDb = format.loudnessDb,
            durationMs = format.approxDurationMs?.toLongOrNull()
                ?: response.videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000),
            expiresAtMillis = System.currentTimeMillis() + expiresInSeconds * 1000,
            resolvedBy = client.clientName,
        )
    }

    /**
     * Picks the best audio-only format according to [qualityPreference], preferring an explicit
     * itag match and falling back to the highest-bitrate audio stream on offer.
     */
    private fun selectFormat(formats: List<PlayerResponse.Format>): PlayerResponse.Format? {
        val audioOnly = formats.filter { it.isAudioOnly }
        if (audioOnly.isEmpty()) return null

        for (itag in qualityPreference.itagPriority) {
            audioOnly.firstOrNull { it.itag == itag }?.let { return it }
        }
        return audioOnly.maxByOrNull { it.effectiveBitrate }
    }

    /**
     * Turns a format into a usable URL. Mobile profiles hand back [PlayerResponse.Format.url]
     * directly; web profiles return a `signatureCipher` query string whose `s` parameter has to be
     * descrambled by a function lifted out of YouTube's player JS.
     */
    private suspend fun materialiseUrl(format: PlayerResponse.Format): String? {
        format.url?.let { return jsChallengeSolver.applyThrottleFix(it) }

        val cipher = format.signatureCipher ?: format.cipher ?: return null
        return jsChallengeSolver.decipher(cipher)
    }

    /** Drops a cached resolution, forcing the next play to re-resolve. */
    suspend fun invalidate(videoId: String) {
        cacheMutex.withLock { cache.remove(videoId) }
    }

    suspend fun invalidateAll() {
        cacheMutex.withLock { cache.clear() }
    }

    /** Clears the breaker so a user-initiated retry is not refused locally. */
    fun resetFailureBackoff() {
        consecutiveTotalFailures = 0
        breakerOpenUntilMillis = 0L
    }

    /** Null when resolution is available; otherwise how long the local pause has left. */
    fun backoffRemainingMillis(): Long? =
        (breakerOpenUntilMillis - System.currentTimeMillis()).takeIf { it > 0 }

    private companion object {
        const val TAG = "InnerTubeResolver"
        const val DEFAULT_EXPIRY_SECONDS = 21_540L

        /** One or two failures are a bad track; three in a row is the service refusing us. */
        const val FAILURES_BEFORE_BREAKER = 3
        const val BASE_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 10 * 60 * 1000L
    }
}
