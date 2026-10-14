package com.example.musicplayer.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the sleep timer countdown.
 *
 * This deliberately does not live in a ViewModel. The service keeps playing
 * after the activity goes away — `onTaskRemoved` only stops it when nothing is
 * playing — so a timer in `viewModelScope` is cancelled the moment the user
 * swipes the app away, leaving the music running forever. That is precisely the
 * nightstand case the feature exists for.
 *
 * Being a @Singleton ties the countdown to the process instead, which is the
 * same lifetime as the playback it is meant to stop.
 */
@Singleton
class SleepTimerController @Inject constructor(
    private val playbackConnection: PlaybackConnection,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    /** [minutes] of null cancels a running timer. */
    fun setTimer(minutes: Int?) {
        timerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _remainingMs.value = null
            return
        }

        val durationMs = minutes * 60_000L
        // Anchored to wall-clock rather than accumulated delays so that a
        // process doze or a slow tick cannot stretch the countdown.
        val endAt = System.currentTimeMillis() + durationMs
        _remainingMs.value = durationMs

        timerJob = scope.launch {
            while (isActive) {
                val remaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
                _remainingMs.value = remaining
                if (remaining == 0L) {
                    playbackConnection.pause()
                    break
                }
                delay(TICK_INTERVAL_MS)
            }
            _remainingMs.value = null
        }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 1_000L
    }
}
