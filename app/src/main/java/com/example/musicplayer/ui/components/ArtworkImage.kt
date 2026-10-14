package com.example.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.musicplayer.ui.theme.PulsePlum
import com.example.musicplayer.ui.theme.PulseSurfaceHigh
import com.example.musicplayer.ui.theme.PulseTextFaint

/**
 * Album art with a graceful empty state. Tracks scanned off a device very
 * often have no embedded artwork, so the placeholder is a first-class case
 * here rather than a grey box — it keeps lists from looking broken.
 */
@Composable
fun ArtworkImage(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(PulseSurfaceHigh, PulsePlum.copy(alpha = 0.45f))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = PulseTextFaint,
                modifier = Modifier.fillMaxSize(0.38f),
            )
        }
    }
}
