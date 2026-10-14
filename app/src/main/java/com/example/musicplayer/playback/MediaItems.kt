package com.example.musicplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicplayer.core.model.Song
import com.example.musicplayer.core.model.SongSource

/** Scheme for tracks whose real URL is resolved at play time by [YouTubeStreamDataSourceFactory]. */
const val YOUTUBE_URI_SCHEME = "ytmusic"

fun youtubePlaceholderUri(videoId: String): Uri = Uri.parse("$YOUTUBE_URI_SCHEME://$videoId")

/**
 * Shared by [PlaybackConnection] (user-initiated playback) and [PlaybackService]
 * (restoring a saved queue). Both paths must produce identical items, or a
 * restored queue would render differently from the one the user left behind.
 *
 * The media id is [Song.mediaId] — a source-prefixed string. [PlaybackService] reads it back to
 * record play counts, and the UI resolves queue rows to library songs with it.
 *
 * Streamed tracks carry a `ytmusic://<videoId>` placeholder rather than a real URL. Signed
 * YouTube URLs expire in hours and are IP-bound, so baking one into a queue item would break any
 * queue that outlives it — notably a restored one. The real URL is fetched during the player's
 * open() call instead.
 */
fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(
        when (source) {
            SongSource.LOCAL -> Uri.parse(uri)
            SongSource.YOUTUBE -> youtubePlaceholderUri(remoteId.orEmpty())
        },
    )
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri?.let(Uri::parse))
            .setDurationMs(durationMs.takeIf { it > 0 })
            .build(),
    )
    .build()
