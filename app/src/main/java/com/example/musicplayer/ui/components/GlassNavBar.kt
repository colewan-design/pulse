package com.example.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.musicplayer.ui.theme.GlassFillStrong
import com.example.musicplayer.ui.theme.GlassStroke
import com.example.musicplayer.ui.theme.GlassStrokeBright
import com.example.musicplayer.ui.theme.PulseMagenta
import com.example.musicplayer.ui.theme.PulseText
import com.example.musicplayer.ui.theme.PulseTextFaint
import com.example.musicplayer.ui.theme.PulseViolet

data class PulseNavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * The floating glass pill that replaces the stock navigation bar.
 *
 * The centre slot is the current album art rather than a fourth destination —
 * it is the shortcut into now-playing, and it is what gives the bar its
 * asymmetric focal point. It is only rendered when something is loaded.
 */
@Composable
fun GlassNavBar(
    leading: List<PulseNavItem>,
    trailing: List<PulseNavItem>,
    centerArtworkUri: String?,
    hasCurrentTrack: Boolean,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(34.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(GlassFillStrong)
            .border(1.dp, GlassStroke, shape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            leading.forEach { NavIcon(it) }
            if (hasCurrentTrack) {
                CenterArtButton(artworkUri = centerArtworkUri, onClick = onCenterClick)
            }
            trailing.forEach { NavIcon(it) }
        }
    }
}

@Composable
private fun RowScope.NavIcon(item: PulseNavItem) {
    val tint by animateColorAsState(
        targetValue = if (item.selected) PulseText else PulseTextFaint,
        label = "navTint",
    )
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .then(
                if (item.selected) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                PulseViolet.copy(alpha = 0.30f),
                                PulseViolet.copy(alpha = 0.08f),
                            ),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = item.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun CenterArtButton(
    artworkUri: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Soft magenta halo so the centre slot reads as raised out of the pill.
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(PulseMagenta.copy(alpha = 0.55f), Color.Transparent),
                    ),
                ),
        )
        ArtworkImage(
            artworkUri = artworkUri,
            shape = CircleShape,
            modifier = Modifier
                .size(42.dp)
                .border(1.dp, GlassStrokeBright, CircleShape),
        )
    }
}
