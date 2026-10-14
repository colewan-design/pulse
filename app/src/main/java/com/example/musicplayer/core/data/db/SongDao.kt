package com.example.musicplayer.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.musicplayer.core.model.SongSource
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun observeAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 50")
    fun observeRecentlyPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, title COLLATE NOCASE ASC LIMIT 50")
    fun observeMostPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    suspend fun getSongsByIds(songIds: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE source = :source ORDER BY title COLLATE NOCASE ASC")
    fun observeBySource(source: SongSource): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): SongEntity?

    /**
     * Must be `@Upsert`, not `@Insert(REPLACE)`. On SQLite, REPLACE resolves a conflict by
     * deleting the existing row and inserting a new one, which fires `ON DELETE CASCADE` on
     * `playlist_songs` — so a plain rescan would silently empty every playlist. `@Upsert` compiles
     * to `INSERT ... ON CONFLICT DO UPDATE`, which leaves child rows alone.
     */
    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    /**
     * Scoped to local rows on purpose. A MediaStore rescan knows nothing about streamed tracks,
     * so clearing the whole table would drop every saved YouTube track and cascade it out of the
     * user's playlists.
     *
     * Only correct when the scan genuinely found nothing. For the normal case use
     * [deleteLocalSongsNotIn] — see the note there.
     */
    @Query("DELETE FROM songs WHERE source = 'LOCAL'")
    suspend fun clearLocalSongs()

    /**
     * Removes local rows whose files are gone, leaving surviving rows in place.
     *
     * This exists because clear-then-reinsert is quietly destructive: deleting a row fires
     * `ON DELETE CASCADE` on `playlist_songs`, so the membership is gone before the re-insert
     * puts the song back. Re-inserting the song does not re-create the playlist entry. That is
     * the same failure the `@Insert(REPLACE)` → `@Upsert` change fixed at the SQLite level, and
     * clearing explicitly reintroduced it one layer up — the row has to never be deleted, not be
     * deleted and restored.
     */
    @Query("DELETE FROM songs WHERE source = 'LOCAL' AND id NOT IN (:keepIds)")
    suspend fun deleteLocalSongsNotIn(keepIds: List<Long>)

    @Query("DELETE FROM songs WHERE source = 'YOUTUBE' AND isFavorite = 0 AND playCount = 0 AND id NOT IN (SELECT songId FROM playlist_songs)")
    suspend fun pruneUnreferencedRemoteSongs()

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavorite(songId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :songId")
    suspend fun markSongPlayed(songId: Long, playedAt: Long)
}

