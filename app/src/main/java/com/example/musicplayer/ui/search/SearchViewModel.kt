package com.example.musicplayer.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.core.data.repository.YouTubeMusicRepository
import com.example.musicplayer.core.model.RemoteAlbum
import com.example.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchTab { Songs, Albums }

data class SearchUiState(
    val query: String = "",
    val tab: SearchTab = SearchTab.Songs,
    val suggestions: List<String> = emptyList(),
    val songs: List<Song> = emptyList(),
    val albums: List<RemoteAlbum> = emptyList(),
    val openAlbum: RemoteAlbum? = null,
    val albumTracks: List<Song> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingAlbum: Boolean = false,
    /** Non-null when the last network call failed. Streaming is the only networked path. */
    val errorMessage: String? = null,
    val hasSearched: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: YouTubeMusicRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }

        suggestJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(suggestions = emptyList(), songs = emptyList(), albums = emptyList(), hasSearched = false)
            }
            return
        }
        // Suggestions are typed-into, so they are debounced separately from the
        // search itself and failures are swallowed — an unavailable suggestion
        // endpoint should never surface an error over a query that still works.
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            val results = runCatching { repository.suggestions(query) }.getOrDefault(emptyList())
            _uiState.update { it.copy(suggestions = results) }
        }
    }

    fun setTab(tab: SearchTab) {
        _uiState.update { it.copy(tab = tab) }
        if (_uiState.value.hasSearched) search()
    }

    fun search(query: String = _uiState.value.query) {
        if (query.isBlank()) return
        searchJob?.cancel()
        _uiState.update {
            it.copy(query = query, isSearching = true, errorMessage = null, suggestions = emptyList())
        }

        searchJob = viewModelScope.launch {
            when (_uiState.value.tab) {
                SearchTab.Songs -> runCatching { repository.searchSongs(query) }
                    .onSuccess { results ->
                        _uiState.update {
                            it.copy(songs = results, isSearching = false, hasSearched = true)
                        }
                    }
                    .onFailure { error -> failSearch(error) }

                SearchTab.Albums -> runCatching { repository.searchAlbums(query) }
                    .onSuccess { results ->
                        _uiState.update {
                            it.copy(albums = results, isSearching = false, hasSearched = true)
                        }
                    }
                    .onFailure { error -> failSearch(error) }
            }
        }
    }

    fun openAlbum(album: RemoteAlbum) {
        _uiState.update { it.copy(openAlbum = album, albumTracks = emptyList(), isLoadingAlbum = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.albumTracks(album.browseId) }
                .onSuccess { tracks ->
                    _uiState.update { it.copy(albumTracks = tracks, isLoadingAlbum = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoadingAlbum = false, errorMessage = error.toUserMessage())
                    }
                }
        }
    }

    fun closeAlbum() {
        _uiState.update { it.copy(openAlbum = null, albumTracks = emptyList()) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Streamed results only exist in memory until they are persisted, and a
     * track needs a row before the service can record play counts against it —
     * so the queue is saved before it is handed to the player.
     *
     * [onReady] receives the persisted songs, which carry any favourite/play
     * state already attached to tracks the user has met before.
     */
    fun playSearchResults(songs: List<Song>, startIndex: Int, onReady: (List<Song>, Int) -> Unit) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.saveAll(songs) }
                .onSuccess { saved -> onReady(saved, startIndex.coerceIn(0, saved.lastIndex)) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.toUserMessage()) } }
        }
    }

    private fun failSearch(error: Throwable) {
        _uiState.update {
            it.copy(isSearching = false, hasSearched = true, errorMessage = error.toUserMessage())
        }
    }

    private fun Throwable.toUserMessage(): String =
        message?.takeIf { it.isNotBlank() }?.let { "Search failed: $it" }
            ?: "Search failed. Check your connection and try again."

    private companion object {
        const val SUGGEST_DEBOUNCE_MS = 250L
    }
}
