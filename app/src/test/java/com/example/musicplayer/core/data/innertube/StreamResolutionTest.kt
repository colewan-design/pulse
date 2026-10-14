package com.example.musicplayer.core.data.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedStreamTest {

    private fun stream(expiresAtMillis: Long) = ResolvedStream(
        videoId = "dQw4w9WgXcQ",
        url = "https://rr1---sn-x.googlevideo.com/videoplayback?itag=251",
        itag = 251,
        mimeType = "audio/webm",
        bitrate = 160_000,
        contentLengthBytes = 4_000_000L,
        loudnessDb = 1.5,
        durationMs = 212_000L,
        expiresAtMillis = expiresAtMillis,
        resolvedBy = "ANDROID_MUSIC",
    )

    @Test
    fun `a stream well inside its window is live`() {
        val now = 1_000_000_000L
        assertFalse(stream(now + 60 * 60 * 1000).isExpired(now))
    }

    @Test
    fun `a stream past its expiry is expired`() {
        val now = 1_000_000_000L
        assertTrue(stream(now - 1).isExpired(now))
    }

    @Test
    fun `a stream inside the safety margin is treated as expired`() {
        // Re-resolving early is deliberate: a long buffered read must not outlive the signature.
        val now = 1_000_000_000L
        assertTrue(stream(now + 60 * 1000).isExpired(now))
    }
}

class AudioQualityPreferenceTest {

    @Test
    fun `highest prefers opus 251`() {
        assertEquals(251, AudioQualityPreference.HIGHEST.itagPriority.first())
    }

    @Test
    fun `compatible prefers aac 140 for widest decoder support`() {
        assertEquals(140, AudioQualityPreference.COMPATIBLE.itagPriority.first())
    }

    @Test
    fun `data saver prefers the lowest-bitrate opus tier`() {
        assertEquals(249, AudioQualityPreference.DATA_SAVER.itagPriority.first())
    }

    @Test
    fun `every preference can fall back to the same full itag set`() {
        // A track missing the preferred itag must still resolve, so each ordering has to be a
        // permutation of the same candidates rather than a truncated list.
        val expected = AudioQualityPreference.HIGHEST.itagPriority.toSet()
        AudioQualityPreference.entries.forEach { preference ->
            assertEquals(
                "${preference.name} covers a different itag set",
                expected,
                preference.itagPriority.toSet(),
            )
            assertEquals(
                "${preference.name} repeats an itag",
                preference.itagPriority.size,
                preference.itagPriority.distinct().size,
            )
        }
    }
}

class InnerTubeClientTest {

    @Test
    fun `stream profile order tries unattested clients first`() {
        // The whole fallback design rests on this: the profiles needing a JS challenge or a
        // PoToken are the expensive, fragile ones and must come last.
        val order = InnerTubeClient.STREAM_PROFILE_ORDER
        val firstChallenged = order.indexOfFirst { it.requiresJsChallenge || it.requiresPoToken }
        val lastClean = order.indexOfLast { !it.requiresJsChallenge && !it.requiresPoToken }
        assertTrue("a cheap profile must exist", lastClean >= 0)
        assertTrue("cheap profiles must precede challenged ones", lastClean < firstChallenged)
    }

    @Test
    fun `the profile verified to resolve streams is tried first`() {
        // ANDROID_VR was the only profile returning status=OK when last probed. If a future
        // change demotes it, playback starts with a wasted round trip against a gated client.
        assertEquals(InnerTubeClient.ANDROID_VR, InnerTubeClient.STREAM_PROFILE_ORDER.first())
    }

    @Test
    fun `video-host clients are not pointed at the music host`() {
        // ANDROID_VR is only served from www.youtube.com. The host is chosen by name suffix, so
        // a profile named *_MUSIC would be routed to the music host — assert the discriminator
        // still classifies the primary profile as a video client.
        val name = InnerTubeClient.ANDROID_VR.clientName
        assertFalse(name.endsWith("MUSIC"))
        assertFalse(name == "WEB_REMIX")
    }

    @Test
    fun `search filters use canonical unencoded base64`() {
        // Both spellings are currently accepted by the server; `==` is the correct body form and
        // not depending on that tolerance is the point.
        listOf(
            SearchFilter.SONGS,
            SearchFilter.VIDEOS,
            SearchFilter.ALBUMS,
            SearchFilter.ARTISTS,
            SearchFilter.PLAYLISTS,
        ).forEach { filter ->
            assertFalse("$filter is percent-encoded", filter.contains('%'))
        }
    }
}
