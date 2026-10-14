package com.example.musicplayer.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicplayer.core.model.RemoteAlbum
import com.example.musicplayer.core.model.Song
import com.example.musicplayer.ui.components.ArtworkImage
import com.example.musicplayer.ui.components.GlassSurface
import com.example.musicplayer.ui.components.SongRow
import com.example.musicplayer.ui.theme.GlassFill
import com.example.musicplayer.ui.theme.GlassStroke
import com.example.musicplayer.ui.theme.PulseMagenta
import com.example.musicplayer.ui.theme.PulseText
import com.example.musicplayer.ui.theme.PulseTextFaint
import com.example.musicplayer.ui.theme.PulseTextMuted
import com.example.musicplayer.ui.theme.PulseViolet

/**
 * The only user-reachable entry point into the streaming layer.
 *
 * Everything here is networked, which makes it the one screen in the app that
 * can fail for reasons outside the device — so loading and error states are
 * first-class rather than an afterthought.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSelectTab: (SearchTab) -> Unit,
    onSelectSuggestion: (String) -> Unit,
    onPlaySong: (Int) -> Unit,
    onOpenAlbum: (RemoteAlbum) -> Unit,
    onCloseAlbum: () -> Unit,
    onPlayAlbumTrack: (Int) -> Unit,
    onPlayAlbum: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    if (state.openAlbum != null) {
        AlbumDetail(
            album = state.openAlbum,
            tracks = state.albumTracks,
            isLoading = state.isLoadingAlbum,
            errorMessage = state.errorMessage,
            onBack = onCloseAlbum,
            onPlayTrack = onPlayAlbumTrack,
            onPlayAll = onPlayAlbum,
            onAddToPlaylist = onAddToPlaylist,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            placeholder = { Text("Search YouTube Music", color = PulseTextFaint) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = PulseTextFaint,
                    modifier = Modifier.size(19.dp),
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = PulseTextFaint)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GlassFill,
                unfocusedContainerColor = GlassFill,
                focusedBorderColor = PulseViolet.copy(alpha = 0.6f),
                unfocusedBorderColor = GlassStroke,
                focusedTextColor = PulseText,
                unfocusedTextColor = PulseText,
                cursorColor = PulseMagenta,
            ),
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchTab.entries.forEach { tab ->
                SearchTabPill(
                    label = tab.name,
                    selected = state.tab == tab,
                    onClick = { onSelectTab(tab) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isSearching -> CenteredBox { CircularProgressIndicator(color = PulseViolet) }

            state.errorMessage != null -> MessageCard(state.errorMessage)

            state.suggestions.isNotEmpty() && !state.hasSearched -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.suggestions, key = { it }) { suggestion ->
                    SuggestionRow(suggestion = suggestion, onClick = { onSelectSuggestion(suggestion) })
                }
            }

            !state.hasSearched -> MessageCard(
                "Search YouTube Music for tracks and albums. Results play straight from the stream and can be saved, favourited and added to playlists.",
            )

            state.tab == SearchTab.Songs && state.songs.isEmpty() ->
                MessageCard("No tracks found for \"${state.query}\".")

            state.tab == SearchTab.Albums && state.albums.isEmpty() ->
                MessageCard("No albums found for \"${state.query}\".")

            state.tab == SearchTab.Songs -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.songs, key = { _, song -> song.mediaId }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(index) },
                        onToggleFavorite = null,
                        onPlayNext = null,
                        onAddToPlaylist = { onAddToPlaylist(song) },
                    )
                }
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.albums, key = { it.browseId }) { album ->
                    AlbumRow(album = album, onClick = { onOpenAlbum(album) })
                }
            }
        }
    }
}

@Composable
private fun AlbumDetail(
    album: RemoteAlbum,
    tracks: List<Song>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            ArtworkImage(artworkUri = album.artworkUri, modifier = Modifier.size(56.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        album.artist.takeIf { it.isNotBlank() },
                        album.year.takeIf { it > 0 }?.toString(),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PulseTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(onClick = onPlayAll, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> CenteredBox { CircularProgressIndicator(color = PulseViolet) }
            errorMessage != null -> MessageCard(errorMessage)
            tracks.isEmpty() -> MessageCard("No tracks in this release.")
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(tracks, key = { _, song -> song.mediaId }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlayTrack(index) },
                        onToggleFavorite = null,
                        onPlayNext = null,
                        onAddToPlaylist = { onAddToPlaylist(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: RemoteAlbum, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkImage(artworkUri = album.artworkUri, modifier = Modifier.size(52.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PulseText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        album.artist.takeIf { it.isNotBlank() },
                        album.year.takeIf { it > 0 }?.toString(),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = PulseTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.Album, contentDescription = null, tint = PulseTextFaint)
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = PulseTextFaint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodyLarge,
            color = PulseText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchTabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        fill = if (selected) PulseViolet.copy(alpha = 0.85f) else GlassFill,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) PulseText else PulseTextMuted,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun MessageCard(message: String) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = PulseTextFaint)
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = PulseTextMuted)
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
