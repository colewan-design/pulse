package com.example.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicplayer.playback.PlaybackUiState
import com.example.musicplayer.ui.theme.GlassFillStrong
import com.example.musicplayer.ui.theme.GlassStroke
import com.example.musicplayer.ui.theme.PulseMagenta
import com.example.musicplayer.ui.theme.PulseText
import com.example.musicplayer.ui.theme.PulseTextMuted
import com.example.musicplayer.ui.theme.PulseViolet

/**
 * Docked player that sits directly above the nav pill. Progress is a hairline
 * along the bottom edge rather than a full bar — at this size a thick bar
 * competes with the nav pill right below it.
 */
@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    onOpen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentItem = state.currentItem ?: return
    val progress = if (state.durationMs > 0) {
        (state.positionMs / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shape = RoundedCornerShape(26.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFillStrong)
            .border(1.dp, GlassStroke, shape)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkImage(
                artworkUri = currentItem.artworkUri,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(46.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentItem.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PulseText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentItem.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = PulseTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = PulseText,
                    modifier = Modifier.size(26.dp),
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next track",
                    tint = PulseText,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .background(Brush.horizontalGradient(listOf(PulseViolet, PulseMagenta))),
        )
    }
}
