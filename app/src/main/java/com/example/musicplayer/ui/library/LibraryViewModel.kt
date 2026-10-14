package com.example.musicplayer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.core.data.auth.GoogleAuthStore
import com.example.musicplayer.core.data.prefs.PlayerPreferencesRepository
import com.example.musicplayer.core.data.repository.PlaylistRepository
import com.example.musicplayer.core.data.repository.SongRepository
import com.example.musicplayer.core.model.Album
import com.example.musicplayer.core.model.Artist
import com.example.musicplayer.core.model.Folder
import com.example.musicplayer.core.model.Genre
import com.example.musicplayer.core.model.PlaylistSummary
import com.example.musicplayer.core.model.Song
import com.example.musicplayer.core.model.SongSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val preferencesRepository: PlayerPreferencesRepository,
    private val authStore: GoogleAuthStore,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectedPlaylistId = MutableStateFlow<Long?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    /** Unfiltered. Header counts must not follow the search box. */
    private val allSongs: StateFlow<List<Song>> = songRepository.observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songs: StateFlow<List<Song>> = combine(
        allSongs,
        searchQuery,
        ::filterSongs,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * (on-device, streamed). Kept separate because "tracks on this device" stops
     * being true once a saved streamed track is in the same table — it lives in
     * the library, but it is not on the device.
     */
    val libraryCounts: StateFlow<Pair<Int, Int>> = allSongs
        .map { all ->
            val local = all.count { it.source == SongSource.LOCAL }
            local to (all.size - local)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

    val albums: StateFlow<List<Album>> = songs
        .map(songRepository::buildAlbums)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<Artist>> = songs
        .map(songRepository::buildArtists)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<Folder>> = songs
        .map(songRepository::buildFolders)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val genres: StateFlow<List<Genre>> = songs
        .map(songRepository::buildGenres)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<Song>> = songRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyPlayed: StateFlow<List<Song>> = songRepository.observeRecentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mostPlayed: StateFlow<List<Song>> = songRepository.observeMostPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<PlaylistSummary>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentSearchQuery: StateFlow<String> = searchQuery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val currentPlaylistId: StateFlow<Long?> = selectedPlaylistId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedPlaylistSongs: StateFlow<List<Song>> = selectedPlaylistId
        .flatMapLatest(playlistRepository::observePlaylistSongs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastScanAt: StateFlow<Long?> = preferencesRepository.lastScanAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dynamicColor: StateFlow<Boolean> = preferencesRepository.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isRefreshing: StateFlow<Boolean> = _isRefreshing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val statusMessage: StateFlow<String?> = _statusMessage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectPlaylist(playlistId: Long?) {
        selectedPlaylistId.value = playlistId
    }

    fun refreshLibrary() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching {
                val trackCount = songRepository.refreshLibrary()
                _statusMessage.value = "Scanned $trackCount tracks."
            }.onFailure { throwable ->
                _statusMessage.value = throwable.message ?: "Unable to scan your device music."
            }
            _isRefreshing.value = false
        }
    }

    val isSignedIn: StateFlow<Boolean> = authStore.isSignedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun saveSignIn(cookieHeader: String) {
        viewModelScope.launch { authStore.save(cookieHeader) }
    }

    fun signOut() {
        viewModelScope.launch { authStore.clear() }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDynamicColor(enabled)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            songRepository.toggleFavorite(song.id, !song.isFavorite)
        }
    }

    /**
     * Create-and-add, for the "add to playlist" sheet when no playlist exists yet.
     *
     * Separate from [createPlaylist] because the two have different intents: this
     * one starts from a song the user is trying to place, so creating the
     * playlist without putting the song in it drops what they actually asked for.
     * Deliberately does not change the selected playlist — the user is adding
     * from the library, not navigating into the playlist.
     */
    fun createPlaylistWithSong(name: String, songId: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(name)
            playlistRepository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(name)
            selectedPlaylistId.value = playlistId
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            if (selectedPlaylistId.value == playlistId) {
                selectedPlaylistId.value = null
            }
        }
    }

    fun addSongToPlaylist(songId: Long, playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(songId: Long, playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun moveSongInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            playlistRepository.moveSong(playlistId, fromIndex, toIndex)
        }
    }

    private fun filterSongs(songs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return songs
        val needle = query.trim().lowercase()
        return songs.filter { song ->
            song.title.lowercase().contains(needle) ||
                song.artist.lowercase().contains(needle) ||
                song.album.lowercase().contains(needle) ||
                song.relativePath.lowercase().contains(needle) ||
                song.genre.lowercase().contains(needle)
        }
    }
}

