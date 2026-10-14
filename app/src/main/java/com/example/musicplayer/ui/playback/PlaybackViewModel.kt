package com.example.musicplayer.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.core.data.prefs.PlayerPreferencesRepository
import com.example.musicplayer.core.model.Song
import com.example.musicplayer.playback.PlaybackConnection
import com.example.musicplayer.playback.PlaybackUiState
import com.example.musicplayer.playback.SleepTimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val playbackConnection: PlaybackConnection,
    private val preferencesRepository: PlayerPreferencesRepository,
    private val sleepTimerController: SleepTimerController,
) : ViewModel() {
    // Read straight off the app-scoped controller: the countdown must outlive
    // this ViewModel, so it cannot be re-hosted in viewModelScope here.
    val sleepTimerRemainingMs: StateFlow<Long?> = sleepTimerController.remainingMs

    val uiState: StateFlow<PlaybackUiState> = playbackConnection.uiState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackUiState())

    init {
        viewModelScope.launch {
            preferencesRepository.playbackSpeed.collect { savedSpeed ->
                playbackConnection.setPlaybackSpeed(savedSpeed)
            }
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        playbackConnection.setQueueAndPlay(songs, startIndex)
    }

    fun playNext(song: Song) {
        playbackConnection.playNext(song)
    }

    fun togglePlayPause() = playbackConnection.togglePlayPause()

    fun pause() = playbackConnection.pause()

    fun skipNext() = playbackConnection.skipNext()

    fun skipPrevious() = playbackConnection.skipPrevious()

    fun seekTo(positionMs: Long) = playbackConnection.seekTo(positionMs)

    fun playQueueItem(index: Int) = playbackConnection.playQueueItem(index)

    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playbackConnection.moveQueueItem(fromIndex, toIndex)

    fun removeQueueItem(index: Int) = playbackConnection.removeQueueItem(index)

    fun toggleShuffle() = playbackConnection.setShuffleEnabled(!uiState.value.shuffleEnabled)

    fun cycleRepeatMode() = playbackConnection.cycleRepeatMode()

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesRepository.setPlaybackSpeed(speed)
            playbackConnection.setPlaybackSpeed(speed)
        }
    }

    fun setSleepTimer(minutes: Int?) = sleepTimerController.setTimer(minutes)

    fun clearPlaybackError() = playbackConnection.clearError()
}

