package com.example.musicplayer

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.core.data.prefs.PlayerPreferencesRepository
import com.example.musicplayer.ui.app.MusicPlayerApp
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var preferencesRepository: PlayerPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_MUSIC

        setContent {
            // Read here rather than inside the theme so that the whole tree
            // recomposes against one scheme when the preference flips.
            val dynamicColor by preferencesRepository.dynamicColor
                .collectAsStateWithLifecycle(initialValue = false)

            MusicPlayerTheme(dynamicColor = dynamicColor) {
                MusicPlayerApp()
            }
        }
    }
}
