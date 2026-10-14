package com.example.musicplayer.core.data.repository

import androidx.room.withTransaction
import com.example.musicplayer.core.data.db.MusicDatabase
import com.example.musicplayer.core.data.db.SongDao
import com.example.musicplayer.core.data.db.asDomainModel
import com.example.musicplayer.core.data.mediastore.MediaStoreScanner
import com.example.musicplayer.core.data.prefs.PlayerPreferencesRepository
import com.example.musicplayer.core.model.Album
import com.example.musicplayer.core.model.Artist
import com.example.musicplayer.core.model.Folder
import com.example.musicplayer.core.model.Genre
import com.example.musicplayer.core.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class SongRepository @Inject constructor(
    private val database: MusicDatabase,
    private val songDao: SongDao,
    private val scanner: MediaStoreScanner,
    private val preferencesRepository: PlayerPreferencesRepository,
) {
    fun observeSongs(): Flow<List<Song>> = songDao.observeAllSongs().map { rows ->
        rows.map { it.asDomainModel() }
    }

    fun observeFavorites(): Flow<List<Song>> = songDao.observeFavorites().map { rows ->
        rows.map { it.asDomainModel() }
    }

    fun observeRecentlyPlayed(): Flow<List<Song>> = songDao.observeRecentlyPlayed().map { rows ->
        rows.map { it.asDomainModel() }
    }

    fun observeMostPlayed(): Flow<List<Song>> = songDao.observeMostPlayed().map { rows ->
        rows.map { it.asDomainModel() }
    }

    suspend fun refreshLibrary(): Int = withContext(Dispatchers.IO) {
        val existingSongs = songDao.getAllSongsOnce().associateBy { it.id }
        val scannedSongs = scanner.scanDeviceAudio().map { scannedSong ->
            val previous = existingSongs[scannedSong.id]
            scannedSong.copy(
                isFavorite = previous?.isFavorite ?: false,
                playCount = previous?.playCount ?: 0,
                lastPlayedAt = previous?.lastPlayedAt,
            )
        }

        database.withTransaction {
            // Upsert first, then delete only what the scan did not find.
            //
            // Clearing the table first and re-inserting looks equivalent but is not: the DELETE
            // cascades into playlist_songs, dropping every playlist entry for those songs, and
            // re-inserting the song does not bring the membership back. A plain rescan therefore
            // emptied playlists of local tracks. Updating rows in place keeps their primary keys
            // alive, so the foreign keys are never orphaned.
            songDao.upsertSongs(scannedSongs)
            if (scannedSongs.isEmpty()) {
                // Room renders an empty list as `NOT IN ()`, which SQLite rejects outright, so
                // the genuinely-empty library has to take the unconditional delete.
                songDao.clearLocalSongs()
            } else {
                songDao.deleteLocalSongsNotIn(scannedSongs.map { it.id })
            }
        }
        preferencesRepository.setLastScanAt(System.currentTimeMillis())
        scannedSongs.size
    }

    /** Resolves a saved queue back to songs, preserving the saved ordering. */
    suspend fun getSongsByIds(songIds: List<Long>): List<Song> = withContext(Dispatchers.IO) {
        val bySongId = songDao.getSongsByIds(songIds).associateBy { it.id }
        songIds.mapNotNull { bySongId[it]?.asDomainModel() }
    }

    suspend fun toggleFavorite(songId: Long, isFavorite: Boolean) {
        songDao.updateFavorite(songId, isFavorite)
    }

    suspend fun markSongPlayed(songId: Long) {
        songDao.markSongPlayed(songId, System.currentTimeMillis())
    }

    fun buildAlbums(songs: List<Song>): List<Album> = songs
        .groupBy { song -> song.albumId to song.album }
        .map { (key, groupedSongs) ->
            Album(
                id = key.first,
                title = key.second,
                artist = groupedSongs.firstOrNull()?.artist.orEmpty(),
                songCount = groupedSongs.size,
                songs = groupedSongs.sortedBy { it.trackNumber.takeIf { track -> track > 0 } ?: Int.MAX_VALUE },
                artworkUri = groupedSongs.firstNotNullOfOrNull { it.albumArtUri },
            )
        }
        .sortedBy { it.title.lowercase() }

    fun buildArtists(songs: List<Song>): List<Artist> = songs
        .groupBy { it.artist }
        .map { (artistName, groupedSongs) ->
            Artist(
                name = artistName,
                songCount = groupedSongs.size,
                albumCount = groupedSongs.map { it.albumId }.distinct().size,
                songs = groupedSongs.sortedBy { it.title.lowercase() },
                artworkUri = groupedSongs.firstNotNullOfOrNull { it.albumArtUri },
            )
        }
        .sortedBy { it.name.lowercase() }

    fun buildGenres(songs: List<Song>): List<Genre> = songs
        .groupBy { it.genre.ifBlank { "Unknown genre" } }
        .map { (genreName, groupedSongs) ->
            Genre(
                name = genreName,
                songCount = groupedSongs.size,
                songs = groupedSongs.sortedBy { it.title.lowercase() },
                artworkUri = groupedSongs.firstNotNullOfOrNull { it.albumArtUri },
            )
        }
        .sortedBy { it.name.lowercase() }

    fun buildFolders(songs: List<Song>): List<Folder> = songs
        .groupBy { it.relativePath.ifBlank { "Music" } }
        .map { (path, groupedSongs) ->
            Folder(
                path = path,
                displayName = path.substringAfterLast('/').ifBlank { path.substringAfterLast('\\') }.ifBlank { "Music" },
                songCount = groupedSongs.size,
                songs = groupedSongs.sortedBy { it.title.lowercase() },
                artworkUri = groupedSongs.firstNotNullOfOrNull { it.albumArtUri },
            )
        }
        .sortedBy { it.displayName.lowercase() }
}

