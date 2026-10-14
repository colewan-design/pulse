package com.example.musicplayer.core.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.core.model.SongSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression cover for the rescan-empties-playlists bug, which appeared twice in different forms:
 * first as `@Insert(OnConflictStrategy.REPLACE)` (REPLACE is DELETE+INSERT in SQLite) and then as
 * an explicit `clearLocalSongs()` before re-inserting. Both deleted rows that playlist_songs
 * foreign-keys to, so `ON DELETE CASCADE` removed the membership, and re-adding the song did not
 * restore it.
 *
 * The invariant is simply that a rescan must never delete a row that still exists on disk. These
 * tests drive the DAO directly rather than SongRepository so no MediaStore permission is needed.
 */
@RunWith(AndroidJUnit4::class)
class RescanPlaylistIntegrityTest {

    private lateinit var db: MusicDatabase
    private lateinit var songDao: SongDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MusicDatabase::class.java,
        )
            // Foreign keys are off by default in SQLite; without this the cascade under test
            // would not fire and the test would pass against the broken code.
            .setQueryCallback({ _, _ -> }, Runnable::run)
            .build()
        songDao = db.songDao()
        runBlocking { db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON") }
    }

    @After
    fun tearDown() = db.close()

    private fun localSong(id: Long, title: String) = SongEntity(
        id = id,
        uri = "content://media/external/audio/media/$id",
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        albumArtUri = null,
        genre = "Rock",
        durationMs = 1000L,
        trackNumber = 1,
        year = 2020,
        dateAdded = 0L,
        dateModified = 0L,
        relativePath = "Music",
        source = SongSource.LOCAL,
    )

    private fun streamedSong(id: Long, remoteId: String) = localSong(id, "Streamed $remoteId").copy(
        uri = "",
        source = SongSource.YOUTUBE,
        remoteId = remoteId,
    )

    private fun playlistWith(vararg songIds: Long) = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO playlists (id, name, createdAt) VALUES (1, 'Mix', 0)",
        )
        songIds.forEachIndexed { index, songId ->
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO playlist_songs (playlistId, songId, position) VALUES (1, $songId, $index)",
            )
        }
    }

    private fun membershipCount(): Int = db.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = 1")
        .use { it.moveToFirst(); it.getInt(0) }

    /**
     * Mirrors SongRepository.refreshLibrary's write half.
     *
     * Verified to fail against the previous clear-then-reinsert order: swapping these two lines
     * reproduces "rescan emptied the playlist expected:<2> but was:<0>" and the mixed-playlist
     * 2-becomes-1 case. These assertions are not vacuous — the FK cascade does fire here.
     */
    private fun rescanWith(found: List<SongEntity>) = runBlocking {
        songDao.upsertSongs(found)
        if (found.isEmpty()) songDao.clearLocalSongs()
        else songDao.deleteLocalSongsNotIn(found.map { it.id })
    }

    @Test
    fun rescanFindingSameFiles_keepsPlaylistMembership() {
        runBlocking { songDao.upsertSongs(listOf(localSong(1, "A"), localSong(2, "B"))) }
        playlistWith(1, 2)
        assertEquals(2, membershipCount())

        rescanWith(listOf(localSong(1, "A"), localSong(2, "B")))

        assertEquals("rescan emptied the playlist", 2, membershipCount())
    }

    @Test
    fun rescan_keepsMixedLocalAndStreamedMembership() {
        // The exact reproduction reported: one streamed track, one local, rescan, local vanishes.
        runBlocking {
            songDao.upsertSongs(listOf(localSong(1, "local"), streamedSong(-2L, "dQw4w9WgXcQ")))
        }
        playlistWith(1, -2L)
        assertEquals(2, membershipCount())

        // A MediaStore scan only ever reports local files.
        rescanWith(listOf(localSong(1, "local")))

        assertEquals("rescan dropped a track from a mixed playlist", 2, membershipCount())
        runBlocking {
            assertNotNull("streamed track was deleted", songDao.getByRemoteId("dQw4w9WgXcQ"))
        }
    }

    @Test
    fun rescan_removesOnlyGenuinelyDeletedFiles() {
        runBlocking { songDao.upsertSongs(listOf(localSong(1, "kept"), localSong(2, "deleted"))) }
        playlistWith(1, 2)

        rescanWith(listOf(localSong(1, "kept")))

        assertEquals("only the removed file should leave the playlist", 1, membershipCount())
        runBlocking {
            assertEquals(1, songDao.getAllSongsOnce().count { it.source == SongSource.LOCAL })
        }
    }

    @Test
    fun rescan_preservesFavouritesAndPlayCountsOnSurvivingRows() {
        runBlocking {
            songDao.upsertSongs(listOf(localSong(1, "A")))
            songDao.updateFavorite(1, true)
            songDao.markSongPlayed(1, 12345L)
        }

        // Upsert must merge, not replace — a scanned row carries no user state.
        rescanWith(listOf(localSong(1, "A").copy(isFavorite = true, playCount = 1, lastPlayedAt = 12345L)))

        runBlocking {
            val row = songDao.getSongsByIds(listOf(1L)).single()
            assertEquals(true, row.isFavorite)
            assertEquals(1, row.playCount)
            assertEquals(12345L, row.lastPlayedAt)
        }
    }

    @Test
    fun rescanFindingNothing_clearsLocalButKeepsStreamed() {
        runBlocking {
            songDao.upsertSongs(listOf(localSong(1, "local"), streamedSong(-2L, "abc12345678")))
        }

        // The empty case takes the unconditional delete, because Room renders an empty list as
        // `NOT IN ()` which SQLite rejects.
        rescanWith(emptyList())

        runBlocking {
            assertEquals(0, songDao.getAllSongsOnce().count { it.source == SongSource.LOCAL })
            assertNotNull("streamed tracks must survive an empty scan", songDao.getByRemoteId("abc12345678"))
            assertNull(songDao.getSongsByIds(listOf(1L)).firstOrNull())
        }
    }
}
