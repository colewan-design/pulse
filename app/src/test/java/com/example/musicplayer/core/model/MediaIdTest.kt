package com.example.musicplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The media id and remote-id-hash scheme is load-bearing in two places that are painful to debug
 * at runtime: queue persistence across a process kill, and the foreign key that lets streamed
 * tracks sit in `playlist_songs`. These are cheap to pin down here.
 */
class MediaIdTest {

    private fun song(
        id: Long = 42L,
        source: SongSource = SongSource.LOCAL,
        remoteId: String? = null,
    ) = Song(
        id = id,
        uri = "content://media/external/audio/media/$id",
        title = "Title",
        artist = "Artist",
        album = "Album",
        albumId = 7L,
        albumArtUri = null,
        genre = "",
        durationMs = 1000L,
        trackNumber = 1,
        year = 2024,
        dateAdded = 0L,
        dateModified = 0L,
        relativePath = "Music",
        isFavorite = false,
        playCount = 0,
        lastPlayedAt = null,
        source = source,
        remoteId = remoteId,
    )

    @Test
    fun `local media id round-trips`() {
        val mediaId = song(id = 42L).mediaId
        assertEquals("local:42", mediaId)
        assertEquals(SongSource.LOCAL to "42", parseMediaId(mediaId))
    }

    @Test
    fun `youtube media id round-trips`() {
        val mediaId = song(source = SongSource.YOUTUBE, remoteId = "dQw4w9WgXcQ").mediaId
        assertEquals("yt:dQw4w9WgXcQ", mediaId)
        assertEquals(SongSource.YOUTUBE to "dQw4w9WgXcQ", parseMediaId(mediaId))
    }

    @Test
    fun `bare numeric media id is read as local for backward compatibility`() {
        // Queues saved before the prefix existed hold a raw MediaStore _ID.
        assertEquals(SongSource.LOCAL to "123", parseMediaId("123"))
    }

    @Test
    fun `unrecognised media id does not parse`() {
        assertNull(parseMediaId("spotify:track:abc"))
        assertNull(parseMediaId(""))
    }

    @Test
    fun `youtube song id is stable across calls`() {
        assertEquals(youtubeSongId("dQw4w9WgXcQ"), youtubeSongId("dQw4w9WgXcQ"))
    }

    @Test
    fun `youtube song id differs per video`() {
        assertNotEquals(youtubeSongId("dQw4w9WgXcQ"), youtubeSongId("oHg5SJYRHA0"))
    }

    @Test
    fun `youtube song id is always negative so it cannot collide with a MediaStore id`() {
        // MediaStore _IDs are small positive integers. Forcing the high bit keeps the two
        // keyspaces disjoint, which is what makes a shared songs table safe.
        listOf("dQw4w9WgXcQ", "oHg5SJYRHA0", "a", "", "zzzzzzzzzzz").forEach { videoId ->
            assertTrue("$videoId hashed non-negative", youtubeSongId(videoId) < 0L)
        }
    }

    @Test
    fun `single-character video ids do not collide`() {
        val ids = ('a'..'z').map { youtubeSongId(it.toString()) }
        assertEquals(ids.size, ids.distinct().size)
    }
}
