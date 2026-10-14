package com.example.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicplayer.core.model.Song
import com.example.musicplayer.ui.theme.GlassFill
import com.example.musicplayer.ui.theme.PulseMagenta
import com.example.musicplayer.ui.theme.PulseText
import com.example.musicplayer.ui.theme.PulseTextFaint
import com.example.musicplayer.ui.theme.PulseTextMuted
import com.example.musicplayer.util.formatDuration

/**
 * A library/queue row. Deliberately chrome-free — artwork, two lines of text
 * and a duration — so long lists stay quiet. Per-row actions live behind the
 * overflow menu, with a heart shown inline only when the track is a favourite.
 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onPlayNext: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (highlighted) Modifier.background(GlassFill) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        ArtworkImage(
            artworkUri = song.albumArtUri,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(52.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (highlighted) PulseMagenta else PulseText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = PulseTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingContent()
        if (song.isFavorite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Favourite",
                tint = PulseMagenta,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = PulseTextMuted,
        )
        if (onPlayNext != null || onAddToPlaylist != null || onToggleFavorite != null) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More actions",
                        tint = PulseTextFaint,
                        modifier = Modifier.size(19.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (onToggleFavorite != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (song.isFavorite) "Remove from favourites" else "Add to favourites",
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleFavorite()
                            },
                        )
                    }
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            },
                        )
                    }
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            },
                        )
                    }
                }
            }
        }
    }
}
