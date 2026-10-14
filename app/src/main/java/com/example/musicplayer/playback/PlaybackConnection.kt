package com.example.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.core.data.innertube.StreamResolutionException
import com.example.musicplayer.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueItemUiModel(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String?,
)

data class PlaybackUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentItem: QueueItemUiModel? = null,
    val queue: List<QueueItemUiModel> = emptyList(),
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1f,
    val errorMessage: String? = null,
)

@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerReady = CompletableDeferred<MediaController>()
    private var controller: MediaController? = null
    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState(player)
        }

        /**
         * The service recovers from a failed item by skipping to the next one.
         * That is the right playback behaviour but it is silent, and a streamed
         * queue can fail for an external reason — an expired or unresolvable
         * stream — so the failure is surfaced here rather than swallowed.
         */
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _uiState.value = _uiState.value.copy(errorMessage = error.toUserMessage())
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    init {
        connect()
        scope.launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL_MS)
                controller?.let { publishState(it) }
            }
        }
    }

    fun setQueueAndPlay(songs: List<Song>, startIndex: Int) {
        scope.launch {
            val mediaItems = songs.map { it.toMediaItem() }
            val safeIndex = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
            withController { mediaController ->
                mediaController.setMediaItems(mediaItems, safeIndex, 0L)
                mediaController.prepare()
                mediaController.play()
            }
        }
    }

    fun playNext(song: Song) {
        scope.launch {
            withController { mediaController ->
                val nextIndex = (mediaController.currentMediaItemIndex + 1).coerceAtLeast(0)
                mediaController.addMediaItem(nextIndex, song.toMediaItem())
            }
        }
    }

    fun togglePlayPause() {
        scope.launch {
            withController { mediaController ->
                if (mediaController.isPlaying) {
                    mediaController.pause()
                } else {
                    mediaController.play()
                }
            }
        }
    }

    fun pause() {
        scope.launch {
            withController { it.pause() }
        }
    }

    fun skipNext() {
        scope.launch {
            withController { mediaController ->
                mediaController.seekToNextMediaItem()
                mediaController.play()
            }
        }
    }

    fun skipPrevious() {
        scope.launch {
            withController { mediaController ->
                mediaController.seekToPreviousMediaItem()
                mediaController.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            withController { it.seekTo(positionMs.coerceAtLeast(0L)) }
        }
    }

    fun playQueueItem(index: Int) {
        scope.launch {
            withController { mediaController ->
                mediaController.seekToDefaultPosition(index)
                mediaController.play()
            }
        }
    }

    fun removeQueueItem(index: Int) {
        scope.launch {
            withController { mediaController ->
                if (index in 0 until mediaController.mediaItemCount) {
                    mediaController.removeMediaItem(index)
                }
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        scope.launch {
            withController { mediaController ->
                if (fromIndex in 0 until mediaController.mediaItemCount &&
                    toIndex in 0 until mediaController.mediaItemCount
                ) {
                    mediaController.moveMediaItem(fromIndex, toIndex)
                }
            }
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        scope.launch {
            withController { it.shuffleModeEnabled = enabled }
        }
    }

    fun cycleRepeatMode() {
        scope.launch {
            withController { mediaController ->
                mediaController.repeatMode = when (mediaController.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        scope.launch {
            withController { mediaController ->
                mediaController.setPlaybackParameters(PlaybackParameters(speed))
            }
        }
    }

    private fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        if (!controllerReady.isCompleted) {
                            controllerReady.complete(mediaController)
                        }
                        mediaController.addListener(playerListener)
                        publishState(mediaController)
                    }
                    .onFailure { throwable ->
                        _uiState.value = _uiState.value.copy(
                            errorMessage = throwable.message ?: "Unable to connect to playback service.",
                        )
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private suspend fun withController(block: (MediaController) -> Unit) {
        block(controller ?: controllerReady.await())
        controller?.let { publishState(it) }
    }

    private fun publishState(player: Player) {
        val currentItem = player.currentMediaItem?.toQueueItem(player.currentMediaItemIndex)
        _uiState.value = PlaybackUiState(
            connected = true,
            isPlaying = player.isPlaying,
            currentItem = currentItem,
            queue = List(player.mediaItemCount) { index ->
                player.getMediaItemAt(index).toQueueItem(index)
            },
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            playbackSpeed = player.playbackParameters.speed,
            errorMessage = _uiState.value.errorMessage,
        )
    }

    /**
     * Deliberately stable, track-independent strings.
     *
     * The service recovers from a failed item by skipping, so a systemic failure
     * produces one error per queue item in quick succession. A message naming the
     * track would differ every time, and the collector restarts on every change —
     * cancelling each notice before it could be read. Stable strings show once
     * and stay put.
     *
     * Three cases are worth telling apart, because the right user response
     * differs. The per-profile reasons stay in logcat; they are a developer
     * diagnostic, not something to put in front of a listener.
     */
    private fun androidx.media3.common.PlaybackException.toUserMessage(): String {
        val resolution = generateSequence(cause) { it.cause }
            .filterIsInstance<StreamResolutionException>()
            .firstOrNull()
            ?: return "Couldn't play that track."

        return when {
            // The breaker throws before it collects attempts, so an empty list
            // means "we are not even asking right now", not "every profile failed".
            resolution.attempts.isEmpty() ->
                "Streaming is paused after repeated failures. Local tracks still play."

            // Every profile refused on sign-in/availability grounds — that is a
            // platform gate on the app, not a property of this one track.
            resolution.attempts.all { it.status == "LOGIN_REQUIRED" || it.status == "UNPLAYABLE" } ->
                "Streaming is unavailable right now. Local tracks still play."

            else -> "Couldn't play that track — it may be unavailable."
        }
    }

    private fun MediaItem.toQueueItem(index: Int): QueueItemUiModel = QueueItemUiModel(
        index = index,
        mediaId = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty(),
        artworkUri = mediaMetadata.artworkUri?.toString(),
    )

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
}

