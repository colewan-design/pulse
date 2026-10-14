package com.example.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicplayer.MainActivity
import com.example.musicplayer.core.data.prefs.PlayerPreferencesRepository
import com.example.musicplayer.core.data.prefs.SavedPlaybackState
import com.example.musicplayer.core.data.repository.SongRepository
import com.example.musicplayer.core.model.SongSource
import com.example.musicplayer.core.model.parseMediaId
import com.example.musicplayer.core.model.youtubeSongId
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject
    lateinit var songRepository: SongRepository

    @Inject
    lateinit var preferencesRepository: PlayerPreferencesRepository

    @Inject
    lateinit var streamDataSourceFactory: YouTubeStreamDataSourceFactory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    /** Suppresses state saves while the restored queue is being installed. */
    private var restoring = true

    /** Consecutive playback failures, reset once anything reaches STATE_READY. */
    private var consecutiveFailures = 0

    /** Item awaiting a play-count write, held until playback actually reaches STATE_READY. */
    private var pendingPlayCountMediaId: String? = null

    private val playerListener = object : Player.Listener {
        /**
         * Records only which item is now current. The play count is deliberately NOT written
         * here: this fires the moment an item becomes current, which is before a single byte has
         * been decoded. A track that then fails to resolve and gets skipped would still be
         * counted, and a queue churning through failures filled "Recently Played" and "Most
         * Played" with tracks that never produced any audio. The write happens once playback
         * actually reaches STATE_READY instead.
         */
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            pendingPlayCountMediaId = mediaItem?.mediaId
        }

        override fun onEvents(player: Player, events: Player.Events) {
            // Queue shape, running order and transport mode all change rarely, so
            // writing on the event is cheap. Position is handled by the ticker
            // below instead — it changes continuously.
            if (
                events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                )
            ) {
                savePlaybackState()
            }
        }

        /**
         * Skips past a track that will not play — but only while the failure looks isolated.
         *
         * The previous version skipped unconditionally, which had two faults. Calling prepare()
         * immediately clears the error before it reaches the MediaSession's PlaybackState, so no
         * MediaController ever observed one: a queue could burn through every track in silence
         * with the session reporting BUFFERING and error=null throughout. And on a systemic
         * failure — stream resolution refused for every track, not one bad file — skipping turns
         * a single refusal into one failed request per queue item as fast as the loop runs.
         *
         * So consecutive failures are counted. Below the threshold the skip still happens, which
         * is right for one corrupt file. At the threshold the player is left in its error state,
         * where the error survives for controllers to render and the retry storm stops.
         */
        override fun onPlayerError(error: PlaybackException) {
            consecutiveFailures++

            val hasNextItem = player.mediaItemCount > 1 &&
                player.currentMediaItemIndex < player.mediaItemCount - 1

            if (consecutiveFailures >= MAX_CONSECUTIVE_SKIPS || !hasNextItem) {
                // Leave the error standing. Controllers surface it; the user decides what next.
                return
            }

            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_READY) return

            // Anything actually reaching READY means the chain works, so the run of failures is
            // over. Resetting here rather than on item transition avoids counting a successful
            // track as part of a later streak.
            consecutiveFailures = 0

            // READY means the source opened and buffered — for a streamed track that means
            // resolution succeeded and bytes are flowing. Consumed so a pause/resume cycle on the
            // same track does not count it twice.
            pendingPlayCountMediaId?.let { mediaId ->
                pendingPlayCountMediaId = null
                songIdFromMediaId(mediaId)?.let { songId ->
                    serviceScope.launch { songRepository.markSongPlayed(songId) }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Without a wake lock the CPU can sleep mid-track once the screen is
            // off, which is exactly the 30-minutes-screen-off case this app has
            // to survive. WAKE_MODE_NETWORK rather than _LOCAL now that queues can
            // contain streamed tracks: _LOCAL does not hold a WifiLock, so the
            // radio can idle mid-track and starve the buffer. It degrades to the
            // same behaviour as _LOCAL for local files.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Routes ytmusic:// placeholders through stream resolution, and leaves
            // content:// library files on the default path.
            .setMediaSourceFactory(DefaultMediaSourceFactory(streamDataSourceFactory.create()))
            .build()
            .apply {
                addListener(playerListener)
            }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildSessionActivityIntent())
            .build()

        restorePlaybackState()
        startPositionTicker()
    }

    /**
     * Without this the media notification and the lock screen card have no
     * content intent at all, so tapping either does nothing.
     */
    private fun buildSessionActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Rebuilds the queue the user left behind, paused at the saved position.
     * Deliberately does not call play(): resuming audio on its own after a
     * reboot or a process kill is not something the user asked for.
     */
    private fun restorePlaybackState() {
        serviceScope.launch {
            val saved = runCatching { preferencesRepository.readPlaybackState() }.getOrNull()
            val songs = saved?.let { songRepository.getSongsByIds(it.songIds) }.orEmpty()

            withContext(Dispatchers.Main) {
                if (saved != null && songs.isNotEmpty()) {
                    // Songs deleted since the last session drop out, so the saved
                    // index is re-anchored on the song that was actually playing.
                    val playingSongId = saved.songIds.getOrNull(saved.index)
                    val restoredIndex = songs
                        .indexOfFirst { it.id == playingSongId }
                        .takeIf { it >= 0 }
                        ?: 0

                    player.setMediaItems(
                        songs.map { it.toMediaItem() },
                        restoredIndex,
                        saved.positionMs,
                    )
                    player.shuffleModeEnabled = saved.shuffleEnabled
                    player.repeatMode = saved.repeatMode
                    player.prepare()
                }
                restoring = false
            }
        }
    }

    /** Position moves continuously, so it is sampled rather than event-driven. */
    private fun startPositionTicker() {
        serviceScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                withContext(Dispatchers.Main) {
                    if (player.isPlaying) savePlaybackState()
                }
            }
        }
    }

    /** Must be called on the main thread — it reads player state. */
    private fun savePlaybackState() {
        if (restoring) return
        if (player.mediaItemCount == 0) {
            serviceScope.launch { preferencesRepository.clearPlaybackState() }
            return
        }

        val snapshot = SavedPlaybackState(
            songIds = List(player.mediaItemCount) { index ->
                songIdFromMediaId(player.getMediaItemAt(index).mediaId)
            }.filterNotNull(),
            index = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        serviceScope.launch { preferencesRepository.savePlaybackState(snapshot) }
    }

    /**
     * Maps a Media3 media id back to the `songs.id` primary key.
     *
     * Streamed ids carry a videoId rather than a number, so the row key is recomputed with the
     * same hash the repository used when saving. Returning null for an unrecognised id keeps a
     * malformed queue entry from being written back as a bogus key.
     */
    private fun songIdFromMediaId(mediaId: String): Long? {
        val (source, value) = parseMediaId(mediaId) ?: return null
        return when (source) {
            SongSource.LOCAL -> value.toLongOrNull()
            SongSource.YOUTUBE -> value.takeIf { it.isNotBlank() }?.let(::youtubeSongId)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlaybackState()
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        savePlaybackState()
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        // SimpleCache holds an exclusive lock on its index; leaving it open means the next
        // service start throws when it tries to claim the same directory.
        streamDataSourceFactory.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val POSITION_SAVE_INTERVAL_MS = 5_000L

        /**
         * How many tracks may fail back-to-back before the player stops skipping and surfaces the
         * error. Two is enough to step over an isolated unplayable file without letting a
         * service-wide refusal run the queue to its end in silence.
         */
        const val MAX_CONSECUTIVE_SKIPS = 2
    }
}
