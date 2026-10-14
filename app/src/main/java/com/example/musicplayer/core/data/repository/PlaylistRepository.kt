package com.example.musicplayer.core.data.repository

import androidx.room.withTransaction
import com.example.musicplayer.core.data.db.MusicDatabase
import com.example.musicplayer.core.data.db.PlaylistDao
import com.example.musicplayer.core.data.db.PlaylistEntity
import com.example.musicplayer.core.data.db.PlaylistSongCrossRef
import com.example.musicplayer.core.data.db.asDomainModel
import com.example.musicplayer.core.model.PlaylistSummary
import com.example.musicplayer.core.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class PlaylistRepository @Inject constructor(
    private val database: MusicDatabase,
    private val playlistDao: PlaylistDao,
) {
    fun observePlaylists(): Flow<List<PlaylistSummary>> = playlistDao.observePlaylistSummaries().map { rows ->
        rows.map { it.asDomainModel() }
    }

    fun observePlaylistSongs(playlistId: Long?): Flow<List<Song>> {
        if (playlistId == null) {
            return flowOf(emptyList())
        }
        return playlistDao.observePlaylistSongs(playlistId).map { rows ->
            rows.map { it.asDomainModel() }
        }
    }

    suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        playlistDao.renamePlaylist(playlistId, name.trim())
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val nextPosition = playlistDao.nextPosition(playlistId)
        playlistDao.upsertPlaylistSong(
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                position = nextPosition,
            ),
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        database.withTransaction {
            playlistDao.removeSongFromPlaylist(playlistId, songId)
            normalizePlaylistPositions(playlistId)
        }
    }

    suspend fun moveSong(playlistId: Long, fromIndex: Int, toIndex: Int) {
        database.withTransaction {
            val songIds = playlistDao.getSongIdsForPlaylist(playlistId).toMutableList()
            if (fromIndex !in songIds.indices || toIndex !in songIds.indices) {
                return@withTransaction
            }
            val movedSongId = songIds.removeAt(fromIndex)
            songIds.add(toIndex, movedSongId)
            songIds.forEachIndexed { index, songId ->
                playlistDao.updateSongPosition(playlistId, songId, index)
            }
        }
    }

    private suspend fun normalizePlaylistPositions(playlistId: Long) {
        playlistDao.getSongIdsForPlaylist(playlistId).forEachIndexed { index, songId ->
            playlistDao.updateSongPosition(playlistId, songId, index)
        }
    }
}

