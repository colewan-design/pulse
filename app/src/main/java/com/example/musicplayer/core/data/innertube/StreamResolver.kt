package com.example.musicplayer.core.data.innertube

/** A playable audio stream URL plus the metadata needed to configure the player for it. */
data class ResolvedStream(
    val videoId: String,
    val url: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val contentLengthBytes: Long?,
    /**
     * Per-track loudness YouTube reports so clients can normalise. Feed this into a
     * `loudnessEnhancer` or gain stage, otherwise tracks vary wildly in volume.
     */
    val loudnessDb: Double?,
    val durationMs: Long?,
    /** Wall-clock expiry. These URLs are signed and short-lived — never persist them. */
    val expiresAtMillis: Long,
    /** Which client profile produced this, for diagnostics when playback starts failing. */
    val resolvedBy: String,
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis >= expiresAtMillis - EXPIRY_SAFETY_MARGIN_MS

    private companion object {
        /** Resolve again a little early so a long buffer read can't outlive the URL. */
        const val EXPIRY_SAFETY_MARGIN_MS = 5 * 60 * 1000L
    }
}

fun interface StreamResolver {
    /**
     * Resolves a videoId to a playable audio stream.
     *
     * @throws StreamResolutionException when no client profile yields a playable format.
     */
    suspend fun resolve(videoId: String): ResolvedStream
}

class StreamResolutionException(
    message: String,
    val attempts: List<ProfileAttempt> = emptyList(),
    cause: Throwable? = null,
) : Exception(message, cause)

/** Why one client profile failed, kept so a user-visible error can name the real cause. */
data class ProfileAttempt(
    val clientName: String,
    val status: String?,
    val reason: String?,
)

/**
 * Audio format preference.
 *
 * itag 251 (Opus ~160kbps) is the best quality YouTube Music serves to free clients, but Opus in
 * a WebM container is only reliably seekable in ExoPlayer from the itag's own index. itag 140
 * (AAC 128kbps, MP4) is the safer default on older devices. Preference is expressed as an ordered
 * list rather than a single choice because availability varies per track.
 */
enum class AudioQualityPreference(val itagPriority: List<Int>) {
    /** Opus first, falling back through AAC. */
    HIGHEST(listOf(251, 141, 140, 250, 139, 249)),

    /** AAC first — widest decoder support, gapless-friendly. */
    COMPATIBLE(listOf(140, 141, 251, 250, 139, 249)),

    /** Smallest streams, for metered connections. */
    DATA_SAVER(listOf(249, 250, 139, 140, 251, 141)),
}
