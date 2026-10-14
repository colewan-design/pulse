package com.example.musicplayer.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the v1 → v3 migration with real data in the table.
 *
 * PLAN.md calls migration bugs unrecoverable, and the reason is here: `songs` holds favourites,
 * play counts and last-played timestamps that no MediaStore rescan can rebuild. A migration that
 * drops or resets those is silent data loss, so the assertions below check the user-owned columns
 * survive rather than only that the schema validates.
 */
@RunWith(AndroidJUnit4::class)
class MusicDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MusicDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To3_preservesUserOwnedColumns() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO songs (
                    id, uri, title, artist, album, albumId, albumArtUri, durationMs,
                    trackNumber, year, dateAdded, dateModified, relativePath,
                    isFavorite, playCount, lastPlayedAt
                ) VALUES (
                    7, 'content://media/external/audio/media/7', 'Test Title', 'Test Artist',
                    'Test Album', 3, NULL, 212000, 4, 2001, 1000, 2000, 'Music/Albums',
                    1, 12, 1699999999
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MusicDatabase.MIGRATION_1_3)

        db.query("SELECT * FROM songs WHERE id = 7").use { cursor ->
            assertTrue("migrated row is missing", cursor.moveToFirst())

            // App-owned state: losing any of these is unrecoverable.
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isFavorite")))
            assertEquals(12, cursor.getInt(cursor.getColumnIndexOrThrow("playCount")))
            assertEquals(1699999999L, cursor.getLong(cursor.getColumnIndexOrThrow("lastPlayedAt")))

            // Carried-over library metadata.
            assertEquals("Test Title", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(212000L, cursor.getLong(cursor.getColumnIndexOrThrow("durationMs")))

            // Columns the migration introduces. Every pre-existing row is local by definition,
            // and 'LOCAL' must match SongSource.LOCAL exactly or clearLocalSongs() silently
            // matches nothing and rescans stop pruning deleted files.
            assertEquals("LOCAL", cursor.getString(cursor.getColumnIndexOrThrow("source")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("remoteId")))
            assertEquals(
                "Unknown genre",
                cursor.getString(cursor.getColumnIndexOrThrow("genre")),
            )
        }
    }

    @Test
    fun migrate1To3_createsIndicesTheQueriesRelyOn() {
        helper.createDatabase(TEST_DB, 1).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MusicDatabase.MIGRATION_1_3)

        val indices = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'songs'")
            .use { cursor ->
                while (cursor.moveToNext()) indices += cursor.getString(0)
            }

        assertTrue("source index missing: $indices", "index_songs_source" in indices)
        assertTrue("remoteId index missing: $indices", "index_songs_remoteId" in indices)
    }

    @Test
    fun migrate1To3_leavesPlaylistMembershipIntact() {
        // The cascade from songs to playlist_songs is what made the old REPLACE-based upsert
        // destructive. A migration that rebuilt the table instead of ALTERing it would reintroduce
        // exactly that failure, so this pins the playlist row to the song surviving.
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO songs (
                    id, uri, title, artist, album, albumId, albumArtUri, durationMs,
                    trackNumber, year, dateAdded, dateModified, relativePath,
                    isFavorite, playCount, lastPlayedAt
                ) VALUES (
                    9, 'content://media/external/audio/media/9', 'T', 'A', 'Al', 1, NULL,
                    1000, 1, 2000, 0, 0, 'Music', 0, 0, NULL
                )
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO playlists (id, name, createdAt) VALUES (1, 'Mix', 500)")
            db.execSQL(
                "INSERT INTO playlist_songs (playlistId, songId, position) VALUES (1, 9, 0)",
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MusicDatabase.MIGRATION_1_3)

        db.query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = 1 AND songId = 9")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("playlist membership was dropped", 1, cursor.getInt(0))
            }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
