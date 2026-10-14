package com.example.musicplayer.core.data.innertube

/**
 * A YouTube "InnerTube" client identity.
 *
 * Every InnerTube request carries a `context.client` block naming which official client is
 * supposedly making the call. Google gates response shape, available fields and — critically —
 * how stream URLs are signed on that identity. It also tightens individual clients over time,
 * so the only durable strategy is to hold several profiles and fall through them in order.
 *
 * [requiresJsChallenge] marks profiles whose `player` response returns `signatureCipher`
 * rather than a plain `url`, which forces a JS descramble step before the stream is usable.
 * [requiresPoToken] marks profiles Google has moved behind BotGuard attestation; without a
 * token these return `playabilityStatus.status = LOGIN_REQUIRED`.
 */
data class InnerTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: Int,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val androidSdkVersion: Int? = null,
    val deviceModel: String? = null,
    val referer: String? = null,
    val requiresJsChallenge: Boolean = false,
    val requiresPoToken: Boolean = false,
) {
    companion object {
        /**
         * The Oculus/Quest YouTube VR app, and as of 2026-10-14 the only profile here that
         * actually resolves a stream.
         *
         * Verified against the live `player` endpoint on that date: `status=OK`, four audio
         * formats (itags 139/140/249/251), every URL pre-signed with no `signatureCipher` and no
         * `n` throttle parameter, and a range request returning HTTP 206 with valid WebM. It is
         * unattested — no PoToken, no JS challenge, no sign-in — which is exactly why it is first.
         *
         * Note it is served from [InnerTubeEndpoints.VIDEO_BASE], not the music host.
         */
        val ANDROID_VR = InnerTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.60.19",
            clientId = 28,
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; GB) gzip",
            osName = "Android",
            osVersion = "12",
            androidSdkVersion = 32,
            deviceModel = "Quest 3",
        )

        /**
         * The YouTube Music Android app. Kept as a fallback, but **currently non-functional**:
         * verified 2026-10-14 returning `LOGIN_REQUIRED` / "Please sign in", with or without
         * `visitorData`. Google has moved this client behind attestation. Retained because these
         * gates have been lifted and re-applied before, and a dead profile costs one request.
         */
        val ANDROID_MUSIC = InnerTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.27.52",
            clientId = 21,
            userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14; US) gzip",
            osName = "Android",
            osVersion = "14",
            androidSdkVersion = 34,
        )

        /**
         * The iOS YouTube Music app. Also **currently non-functional** — verified 2026-10-14
         * returning `LOGIN_REQUIRED`, same as [ANDROID_MUSIC]. Kept for the same reason.
         */
        val IOS_MUSIC = InnerTubeClient(
            clientName = "IOS_MUSIC",
            clientVersion = "7.27.0",
            clientId = 26,
            userAgent = "com.google.ios.youtubemusic/7.27.0 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X; US)",
            osName = "iOS",
            osVersion = "18.2.1.22C161",
            deviceModel = "iPhone16,2",
        )

        /**
         * music.youtube.com itself.
         *
         * **Use this for search and browse** — verified 2026-10-14 returning 20 fully-populated
         * song rows with videoIds, needing no attestation at all. Its `player` endpoint is a
         * different story: `UNPLAYABLE` / "Video unavailable" on the same date, since stream
         * resolution here wants both a PoToken and signature deciphering. Hence search and
         * playback deliberately use different profiles.
         */
        val WEB_REMIX = InnerTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20250310.01.00",
            clientId = 67,
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            osName = "Windows",
            osVersion = "10.0",
            referer = "https://music.youtube.com/",
            requiresJsChallenge = true,
            requiresPoToken = true,
        )

        /**
         * Embedded-TV client, historically the last resort for age-restricted tracks.
         * **Currently rejected outright** — verified 2026-10-14 returning `ERROR` / "YouTube is
         * no longer supported in this application or device."
         */
        val TV_EMBEDDED = InnerTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientId = 85,
            userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/16.0 Safari/605.1.15",
            referer = "https://www.youtube.com/",
            requiresJsChallenge = true,
        )

        /**
         * Order matters: cheapest and least fragile first. [InnerTubeStreamResolver] walks this
         * list and keeps the first profile that yields a playable format.
         *
         * As measured 2026-10-14, only [ANDROID_VR] resolves; the rest are gated. That is the
         * normal state of affairs for this integration rather than a broken configuration — which
         * of these works rotates as Google adjusts attestation, and the chain exists so that
         * rotation costs a few wasted requests instead of a dead feature. When playback stops
         * working across the board, re-probe each profile's `player` endpoint and reorder here;
         * the per-profile reasons are already surfaced through
         * [StreamResolutionException.attempts].
         */
        val STREAM_PROFILE_ORDER = listOf(ANDROID_VR, ANDROID_MUSIC, IOS_MUSIC, WEB_REMIX, TV_EMBEDDED)
    }
}

internal object InnerTubeEndpoints {
    const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1"
    const val VIDEO_BASE = "https://www.youtube.com/youtubei/v1"

    /**
     * Long-lived public web key, kept for reference. Not sent: InnerTube serves these endpoints
     * unauthenticated, which is the configuration verified against both hosts.
     */
    const val WEB_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    const val SEARCH = "search"
    const val PLAYER = "player"
    const val BROWSE = "browse"
    const val NEXT = "next"
    const val SEARCH_SUGGESTIONS = "music/get_search_suggestions"
}

/**
 * `params` values that scope a music search to one result type.
 *
 * These are base64 protobuf blobs travelling in the JSON request body. Kept in canonical
 * un-percent-encoded form: a `%3D%3D` tail is also currently accepted, but the `==` spelling is
 * what the body format actually calls for, and relying on the server to tolerate the encoded
 * variant is a dependency worth not having.
 *
 * Omitting `params` entirely returns zero `musicShelfRenderer` sections rather than unfiltered
 * results, so a filter is effectively required for song search.
 */
object SearchFilter {
    const val SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
    const val VIDEOS = "EgWKAQIQAWoKEAkQChAFEAMQBA=="
    const val ALBUMS = "EgWKAQIYAWoKEAkQChAFEAMQBA=="
    const val ARTISTS = "EgWKAQIgAWoKEAkQChAFEAMQBA=="
    const val PLAYLISTS = "EgeKAQQoAEABagoQAxAEEAkQChAF"
}
