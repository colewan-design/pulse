package com.example.musicplayer.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongCrossRef::class],
    version = 3,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        /**
         * v1 → v3 directly: adds the genre column, then makes the table source-aware.
         *
         * Migrating rather than falling back to a destructive rebuild matters here: the songs
         * table carries favourites and play counts, which are app-owned and not recoverable from
         * a MediaStore rescan.
         *
         * There is deliberately no 1 → 2 migration. Version 2 was never released, and
         * `app/schemas` holds 1.json and 3.json but no 2.json — so a 1 → 2 → 3 chain could not be
         * covered by `MigrationTestHelper`, which needs an exported schema for each starting
         * version. Collapsing the hop makes the only path a real database can take the same path
         * the test exercises. Room's resolver prefers the largest available jump, so a v1 file
         * takes this migration rather than stepping through v2.
         */
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN genre TEXT NOT NULL DEFAULT 'Unknown genre'")
                addSourceColumns(db)
            }
        }

        /**
         * v3 makes the songs table source-aware so streamed tracks can live beside local ones.
         *
         * Every pre-existing row is by definition LOCAL, which is why the column can take a NOT
         * NULL default without a backfill pass. Room stores the enum by name, so the literal
         * 'LOCAL' here must match [com.example.musicplayer.core.model.SongSource] exactly — the
         * `clearLocalSongs` query compares against the same string.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = addSourceColumns(db)
        }

        /**
         * Shared by both paths into v3 so the two can never drift — a column added to one and
         * not the other produces a database whose shape depends on which version it started at.
         */
        private fun addSourceColumns(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL'")
            db.execSQL("ALTER TABLE songs ADD COLUMN remoteId TEXT DEFAULT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_source ON songs(source)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_remoteId ON songs(remoteId)")
        }

        /**
         * [MIGRATION_2_3] is retained only for development installs already sitting at v2 (the
         * emulator used during this work is one). It is unreachable for any released build and is
         * the one migration without test coverage, for want of a 2.json.
         */
        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_3, MIGRATION_2_3)
    }
}
