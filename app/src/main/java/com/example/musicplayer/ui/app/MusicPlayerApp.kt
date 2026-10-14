package com.example.musicplayer.ui.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.example.musicplayer.core.model.Album
import com.example.musicplayer.core.model.Artist
import com.example.musicplayer.core.model.Folder
import com.example.musicplayer.core.model.Genre
import com.example.musicplayer.core.model.PlaylistSummary
import com.example.musicplayer.core.model.Song
import com.example.musicplayer.playback.PlaybackUiState
import com.example.musicplayer.playback.QueueItemUiModel
import com.example.musicplayer.ui.components.ArtworkImage
import com.example.musicplayer.ui.components.CircularPlayer
import com.example.musicplayer.ui.components.GlassNavBar
import com.example.musicplayer.ui.components.GlassSurface
import com.example.musicplayer.ui.components.MiniPlayer
import com.example.musicplayer.ui.components.PeekArtwork
import com.example.musicplayer.ui.components.PermissionGate
import com.example.musicplayer.ui.components.PulseBackground
import com.example.musicplayer.ui.components.PulseNavItem
import com.example.musicplayer.ui.components.SongRow
import com.example.musicplayer.ui.theme.GlassFill
import com.example.musicplayer.ui.theme.GlassStroke
import com.example.musicplayer.ui.theme.PulseMagenta
import com.example.musicplayer.ui.theme.PulseText
import com.example.musicplayer.ui.theme.PulseTextFaint
import com.example.musicplayer.ui.theme.PulseTextMuted
import com.example.musicplayer.ui.theme.PulseViolet
import com.example.musicplayer.ui.library.LibraryViewModel
import com.example.musicplayer.ui.playback.PlaybackViewModel
import com.example.musicplayer.ui.search.SearchScreen
import com.example.musicplayer.ui.auth.SignInScreen
import com.example.musicplayer.ui.search.SearchViewModel
import com.example.musicplayer.util.formatDuration
import com.example.musicplayer.util.formatLastScan
import com.example.musicplayer.util.formatPlaybackSpeed
import com.example.musicplayer.util.formatSleepTimerRemaining
import com.example.musicplayer.util.requiredMusicPermissions
import kotlinx.coroutines.launch

private enum class HomeDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Library("Library", Icons.Rounded.LibraryMusic),
    Search("Search", Icons.Rounded.Explore),
    Playlists("Playlists", Icons.Rounded.PlaylistPlay),
    Settings("Settings", Icons.Rounded.Settings),
}

/**
 * The auto-generated collections on the Playlists tab. They are backed by Room
 * queries rather than playlist rows, so they are modelled separately from
 * [PlaylistSummary] rather than faked as playlists.
 */
private enum class QuickMix(val label: String) {
    Favorites("Favorites"),
    RecentlyPlayed("Recently Played"),
    MostPlayed("Most Played"),
}

private enum class LibraryTab(val label: String) {
    Songs("Songs"),
    Albums("Albums"),
    Artists("Artists"),
    Genres("Genres"),
    Folders("Folders"),
}

/**
 * Left-aligned header. The stock centre-aligned app bar fought the bloom
 * behind it; anchoring the title to the leading edge lets the gradient read.
 */
@Composable
private fun PulseHeader(
    title: String,
    subtitle: String,
    showRefresh: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 22.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = PulseText,
            )
            Text(
                text = if (isRefreshing) "Scanning device…" else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isRefreshing) PulseMagenta else PulseTextMuted,
            )
        }
        if (showRefresh) {
            GlassSurface(shape = CircleShape) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Rescan library",
                        tint = PulseText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.horizontalGradient(listOf(PulseViolet, PulseMagenta)),
                    )
                } else {
                    Modifier
                        .background(GlassFill)
                        .border(1.dp, GlassStroke, CircleShape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else PulseTextMuted,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val libraryCounts by libraryViewModel.libraryCounts.collectAsStateWithLifecycle()
    val isSignedIn by libraryViewModel.isSignedIn.collectAsStateWithLifecycle()
    val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
    val artists by libraryViewModel.artists.collectAsStateWithLifecycle()
    val folders by libraryViewModel.folders.collectAsStateWithLifecycle()
    val genres by libraryViewModel.genres.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    val recentlyPlayed by libraryViewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by libraryViewModel.mostPlayed.collectAsStateWithLifecycle()
    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylistId by libraryViewModel.currentPlaylistId.collectAsStateWithLifecycle()
    val selectedPlaylistSongs by libraryViewModel.selectedPlaylistSongs.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.currentSearchQuery.collectAsStateWithLifecycle()
    val lastScanAt by libraryViewModel.lastScanAt.collectAsStateWithLifecycle()
    val dynamicColorEnabled by libraryViewModel.dynamicColor.collectAsStateWithLifecycle()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by libraryViewModel.isRefreshing.collectAsStateWithLifecycle()
    val libraryStatusMessage by libraryViewModel.statusMessage.collectAsStateWithLifecycle()
    val playbackState by playbackViewModel.uiState.collectAsStateWithLifecycle()
    val sleepTimerRemaining by playbackViewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()

    var currentDestination by rememberSaveable { mutableStateOf(HomeDestination.Library) }
    var selectedLibraryTab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var songPendingPlaylistAdd by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistBeingEdited by remember { mutableStateOf<PlaylistSummary?>(null) }
    var playlistBeingDeleted by remember { mutableStateOf<PlaylistSummary?>(null) }
    var selectedQuickMix by rememberSaveable { mutableStateOf<QuickMix?>(null) }
    var showSignIn by rememberSaveable { mutableStateOf(false) }
    var permissionRefreshToken by rememberSaveable { mutableIntStateOf(0) }
    var initialRefreshTriggered by rememberSaveable { mutableStateOf(false) }

    val requiredPermissions = remember { requiredMusicPermissions() }
    val hasPermissions = remember(permissionRefreshToken) {
        requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            permissionRefreshToken++
        },
    )

    LaunchedEffect(hasPermissions, initialRefreshTriggered) {
        if (hasPermissions && !initialRefreshTriggered) {
            initialRefreshTriggered = true
            libraryViewModel.refreshLibrary()
        }
    }

    // A streamed track can fail to resolve for reasons outside the device, and
    // the service recovers by skipping — without this the queue would advance
    // silently and look like nothing happened.
    LaunchedEffect(playbackState.errorMessage) {
        val message = playbackState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        playbackViewModel.clearPlaybackError()
    }

    LaunchedEffect(libraryStatusMessage) {
        val message = libraryStatusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        libraryViewModel.clearStatusMessage()
    }

    if (!hasPermissions) {
        PermissionGate(
            onGrantAccess = {
                permissionLauncher.launch(requiredPermissions.toTypedArray())
            },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )
        return
    }

    if (showSignIn) {
        SignInScreen(
            onCancel = { showSignIn = false },
            onSignedIn = { cookies ->
                libraryViewModel.saveSignIn(cookies)
                showSignIn = false
                scope.launch { snackbarHostState.showSnackbar("Signed in to YouTube Music.") }
            },
        )
        return
    }

    PulseBackground {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            PulseHeader(
                title = when (currentDestination) {
                    HomeDestination.Library -> "Pulse"
                    HomeDestination.Search -> "Search"
                    HomeDestination.Playlists -> "Playlists"
                    HomeDestination.Settings -> "Settings"
                },
                subtitle = when (currentDestination) {
                    HomeDestination.Library -> {
                        val (onDevice, streamed) = libraryCounts
                        if (streamed > 0) {
                            "$onDevice on this device · $streamed streamed"
                        } else {
                            "$onDevice tracks on this device"
                        }
                    }
                    HomeDestination.Search -> "YouTube Music"
                    HomeDestination.Playlists -> "${playlists.size} playlists"
                    HomeDestination.Settings -> "Playback and library"
                },
                showRefresh = currentDestination == HomeDestination.Library,
                isRefreshing = isRefreshing,
                onRefresh = libraryViewModel::refreshLibrary,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (playbackState.currentItem != null) {
                    MiniPlayer(
                        state = playbackState,
                        onOpen = { showNowPlaying = true },
                        onTogglePlayPause = playbackViewModel::togglePlayPause,
                        onSkipNext = playbackViewModel::skipNext,
                    )
                }
                GlassNavBar(
                    leading = listOf(HomeDestination.Library, HomeDestination.Search).map {
                        PulseNavItem(
                            label = it.label,
                            icon = it.icon,
                            selected = currentDestination == it,
                            onClick = { currentDestination = it },
                        )
                    },
                    trailing = listOf(HomeDestination.Playlists, HomeDestination.Settings).map {
                        PulseNavItem(
                            label = it.label,
                            icon = it.icon,
                            selected = currentDestination == it,
                            onClick = { currentDestination = it },
                        )
                    },
                    centerArtworkUri = playbackState.currentItem?.artworkUri,
                    hasCurrentTrack = playbackState.currentItem != null,
                    onCenterClick = { showNowPlaying = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (currentDestination) {
                HomeDestination.Library -> LibraryScreen(
                    songs = songs,
                    albums = albums,
                    artists = artists,
                    folders = folders,
                    genres = genres,
                    playlists = playlists,
                    selectedTab = selectedLibraryTab,
                    searchQuery = searchQuery,
                    isRefreshing = isRefreshing,
                    onSelectTab = { selectedLibraryTab = it },
                    onSearchChange = libraryViewModel::setSearchQuery,
                    onPlaySong = { song ->
                        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                        playbackViewModel.playSongs(songs, index)
                    },
                    onPlaySongs = playbackViewModel::playSongs,
                    onToggleFavorite = libraryViewModel::toggleFavorite,
                    onPlayNext = playbackViewModel::playNext,
                    onAddToPlaylist = { songPendingPlaylistAdd = it },
                )
                HomeDestination.Search -> SearchScreen(
                    state = searchState,
                    onQueryChange = searchViewModel::setQuery,
                    onSubmit = { searchViewModel.search() },
                    onSelectTab = searchViewModel::setTab,
                    onSelectSuggestion = { suggestion -> searchViewModel.search(suggestion) },
                    onPlaySong = { index ->
                        searchViewModel.playSearchResults(searchState.songs, index) { saved, startIndex ->
                            playbackViewModel.playSongs(saved, startIndex)
                        }
                    },
                    onOpenAlbum = searchViewModel::openAlbum,
                    onCloseAlbum = searchViewModel::closeAlbum,
                    onPlayAlbumTrack = { index ->
                        searchViewModel.playSearchResults(searchState.albumTracks, index) { saved, startIndex ->
                            playbackViewModel.playSongs(saved, startIndex)
                        }
                    },
                    onPlayAlbum = {
                        searchViewModel.playSearchResults(searchState.albumTracks, 0) { saved, startIndex ->
                            playbackViewModel.playSongs(saved, startIndex)
                        }
                    },
                    // A streamed track has no Room row until it is saved, so the
                    // playlist FK would have nothing to point at without this.
                    onAddToPlaylist = { song ->
                        searchViewModel.playSearchResults(listOf(song), 0) { saved, _ ->
                            saved.firstOrNull()?.let { songPendingPlaylistAdd = it }
                        }
                    },
                )
                HomeDestination.Playlists -> PlaylistsScreen(
                    playlists = playlists,
                    favorites = favorites,
                    recentlyPlayed = recentlyPlayed,
                    mostPlayed = mostPlayed,
                    selectedPlaylistId = selectedPlaylistId,
                    selectedPlaylistSongs = selectedPlaylistSongs,
                    selectedQuickMix = selectedQuickMix,
                    onSelectQuickMix = { selectedQuickMix = it },
                    onAddToPlaylist = { songPendingPlaylistAdd = it },
                    onSelectPlaylist = libraryViewModel::selectPlaylist,
                    onCreatePlaylist = { showCreatePlaylistDialog = true },
                    onRenamePlaylist = { playlistBeingEdited = it },
                    onDeletePlaylist = { playlistBeingDeleted = it },
                    onPlaySongs = playbackViewModel::playSongs,
                    onToggleFavorite = libraryViewModel::toggleFavorite,
                    onPlayNext = playbackViewModel::playNext,
                    onRemoveSong = libraryViewModel::removeSongFromPlaylist,
                    onMoveSong = libraryViewModel::moveSongInPlaylist,
                )
                HomeDestination.Settings -> SettingsScreen(
                    lastScanAt = lastScanAt,
                    playbackState = playbackState,
                    sleepTimerRemainingMs = sleepTimerRemaining,
                    dynamicColorEnabled = dynamicColorEnabled,
                    onSetDynamicColor = libraryViewModel::setDynamicColor,
                    isSignedIn = isSignedIn,
                    onSignIn = { showSignIn = true },
                    onSignOut = libraryViewModel::signOut,
                    onRefreshLibrary = libraryViewModel::refreshLibrary,
                    onSetPlaybackSpeed = playbackViewModel::setPlaybackSpeed,
                    onSetSleepTimer = playbackViewModel::setSleepTimer,
                    onOpenBatterySettings = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    },
                )
            }
        }
    }
    }

    if (showNowPlaying && playbackState.currentItem != null) {
        NowPlayingSheet(
            playbackState = playbackState,
            // The queue model carries no favourite flag, so resolve the row back
            // to the library song (media ids are the song id as a string).
            favoriteSong = songs.firstOrNull {
                it.id.toString() == playbackState.currentItem?.mediaId
            },
            onToggleFavorite = libraryViewModel::toggleFavorite,
            onDismiss = { showNowPlaying = false },
            onTogglePlayPause = playbackViewModel::togglePlayPause,
            onSkipNext = playbackViewModel::skipNext,
            onSkipPrevious = playbackViewModel::skipPrevious,
            onSeekTo = playbackViewModel::seekTo,
            onToggleShuffle = playbackViewModel::toggleShuffle,
            onCycleRepeat = playbackViewModel::cycleRepeatMode,
            onPlayQueueItem = playbackViewModel::playQueueItem,
            onMoveQueueItem = playbackViewModel::moveQueueItem,
            onRemoveQueueItem = playbackViewModel::removeQueueItem,
        )
    }

    songPendingPlaylistAdd?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onDismiss = { songPendingPlaylistAdd = null },
            onAddToPlaylist = { playlistId ->
                libraryViewModel.addSongToPlaylist(song.id, playlistId)
                scope.launch {
                    snackbarHostState.showSnackbar("Added ${song.title} to playlist.")
                }
                songPendingPlaylistAdd = null
            },
            onCreatePlaylist = { name ->
                libraryViewModel.createPlaylistWithSong(name, song.id)
                scope.launch {
                    snackbarHostState.showSnackbar("Added ${song.title} to $name.")
                }
                songPendingPlaylistAdd = null
            },
        )
    }

    if (showCreatePlaylistDialog) {
        PlaylistNameDialog(
            title = "Create playlist",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                libraryViewModel.createPlaylist(name)
                showCreatePlaylistDialog = false
            },
        )
    }

    playlistBeingEdited?.let { playlist ->
        PlaylistNameDialog(
            title = "Rename playlist",
            initialValue = playlist.name,
            confirmLabel = "Save",
            onDismiss = { playlistBeingEdited = null },
            onConfirm = { name ->
                libraryViewModel.renamePlaylist(playlist.id, name)
                playlistBeingEdited = null
            },
        )
    }

    playlistBeingDeleted?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistBeingDeleted = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        libraryViewModel.deletePlaylist(playlist.id)
                        playlistBeingDeleted = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistBeingDeleted = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete playlist") },
            text = { Text("Remove ${playlist.name}? This only affects the local playlist, not the music files themselves.") },
        )
    }
}

@Composable
private fun LibraryScreen(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    folders: List<Folder>,
    genres: List<Genre>,
    playlists: List<PlaylistSummary>,
    selectedTab: LibraryTab,
    searchQuery: String,
    isRefreshing: Boolean,
    onSelectTab: (LibraryTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            placeholder = {
                Text("Search tracks, albums, artists", color = PulseTextFaint)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = PulseTextFaint,
                    modifier = Modifier.size(19.dp),
                )
            },
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
        Spacer(modifier = Modifier.height(14.dp))
        // Five pills no longer fit a phone width, and letting them wrap breaks a
        // label mid-word. Scrolling keeps every tab one row high and reachable.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            LibraryTab.entries.forEach { tab ->
                LibraryTabPill(
                    label = tab.label,
                    selected = selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        when (selectedTab) {
            LibraryTab.Songs -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (songs.isEmpty()) {
                    item {
                        EmptyLibraryCard("No songs found yet. Pull the scanner in Settings or add music to this device.")
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.id }) { _, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(song) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                    )
                }
            }

            LibraryTab.Albums -> CollectionList(
                items = albums,
                onClick = { album -> onPlaySongs(album.songs, 0) },
                artworkUri = { it.artworkUri },
                title = { it.title },
                subtitle = { "${it.artist} • ${it.songCount} songs" },
                leadingIcon = Icons.Rounded.Album,
            )

            LibraryTab.Artists -> CollectionList(
                items = artists,
                onClick = { artist -> onPlaySongs(artist.songs, 0) },
                artworkUri = { it.artworkUri },
                title = { it.name },
                subtitle = { "${it.albumCount} albums • ${it.songCount} songs" },
                leadingIcon = Icons.Rounded.Person,
            )

            LibraryTab.Genres -> CollectionList(
                items = genres,
                onClick = { genre -> onPlaySongs(genre.songs, 0) },
                artworkUri = { it.artworkUri },
                title = { it.name },
                subtitle = { "${it.songCount} songs" },
                leadingIcon = Icons.Rounded.Category,
            )

            LibraryTab.Folders -> CollectionList(
                items = folders,
                onClick = { folder -> onPlaySongs(folder.songs, 0) },
                artworkUri = { it.artworkUri },
                title = { it.displayName },
                subtitle = { "${it.songCount} songs • ${it.path}" },
                leadingIcon = Icons.Rounded.Folder,
            )
        }
    }
}

@Composable
private fun PlaylistsScreen(
    playlists: List<PlaylistSummary>,
    favorites: List<Song>,
    recentlyPlayed: List<Song>,
    mostPlayed: List<Song>,
    selectedPlaylistId: Long?,
    selectedPlaylistSongs: List<Song>,
    selectedQuickMix: QuickMix?,
    onSelectQuickMix: (QuickMix?) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onSelectPlaylist: (Long?) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRenamePlaylist: (PlaylistSummary) -> Unit,
    onDeletePlaylist: (PlaylistSummary) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onRemoveSong: (Long, Long) -> Unit,
    onMoveSong: (Long, Int, Int) -> Unit,
) {
    if (selectedQuickMix != null) {
        val mixSongs = when (selectedQuickMix) {
            QuickMix.Favorites -> favorites
            QuickMix.RecentlyPlayed -> recentlyPlayed
            QuickMix.MostPlayed -> mostPlayed
        }
        SongCollectionScreen(
            title = selectedQuickMix.label,
            songs = mixSongs,
            emptyMessage = when (selectedQuickMix) {
                QuickMix.Favorites -> "No favourites yet. Tap the heart on any track to pin it here."
                QuickMix.RecentlyPlayed -> "Nothing played yet. Start a track and it will show up here."
                QuickMix.MostPlayed -> "No play counts yet. Your most-played tracks will collect here."
            },
            onBack = { onSelectQuickMix(null) },
            onPlaySongs = onPlaySongs,
            onToggleFavorite = onToggleFavorite,
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
        )
        return
    }

    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }

    if (selectedPlaylist != null) {
        PlaylistDetailScreen(
            playlist = selectedPlaylist,
            songs = selectedPlaylistSongs,
            onBack = { onSelectPlaylist(null) },
            onPlaySongs = onPlaySongs,
            onToggleFavorite = onToggleFavorite,
            onPlayNext = onPlayNext,
            onRemoveSong = { songId -> onRemoveSong(songId, selectedPlaylist.id) },
            onMoveSong = { fromIndex, toIndex -> onMoveSong(selectedPlaylist.id, fromIndex, toIndex) },
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Quick mixes",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            QuickMixCard(
                title = QuickMix.Favorites.label,
                count = favorites.size,
                icon = Icons.Rounded.Favorite,
                onOpen = { onSelectQuickMix(QuickMix.Favorites) },
                onPlay = { onPlaySongs(favorites, 0) },
            )
        }
        item {
            QuickMixCard(
                title = QuickMix.RecentlyPlayed.label,
                count = recentlyPlayed.size,
                icon = Icons.Rounded.Refresh,
                onOpen = { onSelectQuickMix(QuickMix.RecentlyPlayed) },
                onPlay = { onPlaySongs(recentlyPlayed, 0) },
            )
        }
        item {
            QuickMixCard(
                title = QuickMix.MostPlayed.label,
                count = mostPlayed.size,
                icon = Icons.Rounded.Equalizer,
                onOpen = { onSelectQuickMix(QuickMix.MostPlayed) },
                onPlay = { onPlaySongs(mostPlayed, 0) },
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Your playlists",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                FilledTonalButton(onClick = onCreatePlaylist) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("New")
                }
            }
        }
        if (playlists.isEmpty()) {
            item {
                EmptyLibraryCard("No custom playlists yet. Create one and start pinning tracks for offline listening.")
            }
        }
        itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { _, playlist ->
            PlaylistCard(
                playlist = playlist,
                onOpen = { onSelectPlaylist(playlist.id) },
                onRename = { onRenamePlaylist(playlist) },
                onDelete = { onDeletePlaylist(playlist) },
            )
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlist: PlaylistSummary,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onRemoveSong: (Long) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "${playlist.songCount} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = { onPlaySongs(songs, 0) },
                enabled = songs.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Play")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (songs.isEmpty()) {
            EmptyLibraryCard("This playlist is empty. Add songs from the library view.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySongs(songs, index) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToPlaylist = null,
                        trailingContent = {
                            IconButton(
                                onClick = { onMoveSong(index, (index - 1).coerceAtLeast(0)) },
                                enabled = index > 0,
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
                            }
                            IconButton(
                                onClick = { onMoveSong(index, (index + 1).coerceAtMost(songs.lastIndex)) },
                                enabled = index < songs.lastIndex,
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                            }
                            IconButton(onClick = { onRemoveSong(song.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    lastScanAt: Long?,
    playbackState: PlaybackUiState,
    sleepTimerRemainingMs: Long?,
    dynamicColorEnabled: Boolean,
    onSetDynamicColor: (Boolean) -> Unit,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val speedSteps = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingCard(
            title = "Library scan",
            subtitle = "Last scan: ${formatLastScan(lastScanAt)}",
        ) {
            FilledTonalButton(onClick = onRefreshLibrary) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Rescan device")
            }
        }

        SettingCard(
            title = "Playback speed",
            subtitle = "Current speed: ${formatPlaybackSpeed(playbackState.playbackSpeed)}",
        ) {
            Slider(
                value = playbackState.playbackSpeed.coerceIn(0.75f, 2f),
                onValueChange = { onSetPlaybackSpeed((it * 4).toInt() / 4f) },
                valueRange = 0.75f..2f,
                steps = 4,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                speedSteps.forEach { speed ->
                    AssistChip(
                        onClick = { onSetPlaybackSpeed(speed) },
                        label = { Text(formatPlaybackSpeed(speed)) },
                    )
                }
            }
        }

        SettingCard(
            title = "Sleep timer",
            subtitle = sleepTimerRemainingMs?.let(::formatSleepTimerRemaining) ?: "Off",
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(15, 30, 60).forEach { minutes ->
                    AssistChip(
                        onClick = { onSetSleepTimer(minutes) },
                        label = { Text("$minutes min") },
                    )
                }
                AssistChip(
                    onClick = { onSetSleepTimer(null) },
                    label = { Text("Off") },
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingCard(
                title = "Dynamic color",
                subtitle = if (dynamicColorEnabled) {
                    "Accents follow your wallpaper. The canvas stays dark either way."
                } else {
                    "Using the Pulse violet palette."
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Use wallpaper colors",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = dynamicColorEnabled,
                        onCheckedChange = onSetDynamicColor,
                    )
                }
            }
        }

        SettingCard(
            title = "YouTube Music account",
            subtitle = if (isSignedIn) {
                "Signed in. Streaming requests are authenticated."
            } else {
                "Not signed in. YouTube refuses stream requests from anonymous clients, so playback of streamed tracks will fail."
            },
        ) {
            if (isSignedIn) {
                OutlinedButton(onClick = onSignOut) { Text("Sign out") }
            } else {
                OutlinedButton(onClick = onSignIn) { Text("Sign in") }
            }
        }

        SettingCard(
            title = "Background reliability",
            subtitle = "Some phones aggressively stop media services. Opening the battery optimization screen helps people keep playback alive.",
        ) {
            OutlinedButton(onClick = onOpenBatterySettings) {
                Text("Open battery settings")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingSheet(
    playbackState: PlaybackUiState,
    favoriteSong: Song?,
    onToggleFavorite: (Song) -> Unit,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
) {
    val currentItem = playbackState.currentItem ?: return
    val progress = if (playbackState.durationMs > 0) {
        playbackState.positionMs / playbackState.durationMs.toFloat()
    } else {
        0f
    }
    // Neighbours in the queue, used only as depth cues either side of the dial.
    val currentIndex = playbackState.queue.indexOfFirst { it.mediaId == currentItem.mediaId }
    val previousArt = playbackState.queue.getOrNull(currentIndex - 1)?.artworkUri
    val nextArt = playbackState.queue.getOrNull(currentIndex + 1)?.artworkUri

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        PulseBackground(bloomAlpha = 0.75f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = PulseText,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(GlassStroke),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (favoriteSong != null) {
                    IconButton(onClick = { onToggleFavorite(favoriteSong) }) {
                        Icon(
                            imageVector = if (favoriteSong.isFavorite) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Rounded.FavoriteBorder
                            },
                            contentDescription = "Favourite",
                            tint = if (favoriteSong.isFavorite) PulseMagenta else PulseText,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = currentItem.title,
                style = MaterialTheme.typography.headlineLarge,
                color = PulseText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = currentItem.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = PulseTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PeekArtwork(
                    artworkUri = previousArt,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                PeekArtwork(
                    artworkUri = nextArt,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
                CircularPlayer(
                    artworkUri = currentItem.artworkUri,
                    progress = progress,
                    onSeekFraction = { fraction ->
                        onSeekTo((fraction * playbackState.durationMs).toLong())
                    },
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatDuration(playbackState.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = PulseTextMuted,
                )
                Text(
                    formatDuration(playbackState.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = PulseTextMuted,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.shuffleEnabled) PulseMagenta else PulseTextFaint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onSkipPrevious) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous track",
                        tint = PulseText,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(PulseViolet, PulseMagenta)))
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onSkipNext) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next track",
                        tint = PulseText,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = if (playbackState.repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = when (playbackState.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            else -> "Repeat off"
                        },
                        tint = if (playbackState.repeatMode == Player.REPEAT_MODE_OFF) {
                            PulseTextFaint
                        } else {
                            PulseMagenta
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "Up next",
                style = MaterialTheme.typography.titleMedium,
                color = PulseTextMuted,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
            ) {
                playbackState.queue.forEach { queueItem ->
                    QueueRow(
                        item = queueItem,
                        isCurrent = queueItem.mediaId == currentItem.mediaId,
                        canMoveUp = queueItem.index > 0,
                        canMoveDown = queueItem.index < playbackState.queue.lastIndex,
                        onClick = { onPlayQueueItem(queueItem.index) },
                        onMoveUp = {
                            onMoveQueueItem(queueItem.index, (queueItem.index - 1).coerceAtLeast(0))
                        },
                        onMoveDown = {
                            onMoveQueueItem(
                                queueItem.index,
                                (queueItem.index + 1).coerceAtMost(playbackState.queue.lastIndex),
                            )
                        },
                        onRemove = { onRemoveQueueItem(queueItem.index) },
                    )
                }
            }
        }
        }
    }
}

/**
 * Queue row. Reordering and removal sit behind the overflow menu so the list
 * keeps the same quiet rhythm as the library — three visible icon buttons per
 * row turned the queue into a wall of chrome.
 */
@Composable
private fun QueueRow(
    item: QueueItemUiModel,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (isCurrent) Modifier.background(GlassFill) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(
            artworkUri = item.artworkUri,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(48.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) PulseMagenta else PulseText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodySmall,
                color = PulseTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Queue actions",
                    tint = PulseTextFaint,
                    modifier = Modifier.size(19.dp),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Move up") },
                    enabled = canMoveUp,
                    onClick = {
                        menuExpanded = false
                        onMoveUp()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Move down") },
                    enabled = canMoveDown,
                    onClick = {
                        menuExpanded = false
                        onMoveDown()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove from queue") },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${playlist.songCount} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRename) {
                Text("Rename")
            }
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun QuickMixCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "$count songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Tapping the card browses the collection; playing it outright stays
            // one tap away rather than being the only thing the card can do.
            IconButton(onClick = onPlay, enabled = count > 0) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play $title")
            }
        }
    }
}

/**
 * A back-navigable list of songs, used for the auto-generated quick mixes.
 * Mirrors [PlaylistDetailScreen] without the reorder and remove affordances —
 * these collections are derived from play history, so there is nothing to edit.
 */
@Composable
private fun SongCollectionScreen(
    title: String,
    songs: List<Song>,
    emptyMessage: String,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${songs.size} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = { onPlaySongs(songs, 0) },
                enabled = songs.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Play")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (songs.isEmpty()) {
            EmptyLibraryCard(emptyMessage)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySongs(songs, index) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content()
            },
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    song: Song,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("Add ${song.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (playlists.isEmpty()) {
                    Text(
                        text = "Create your first playlist below.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    playlists.forEach { playlist ->
                        FilledTonalButton(
                            onClick = { onAddToPlaylist(playlist.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = playlist.name,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                    HorizontalDivider()
                }
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("New playlist name") },
                )
                Button(
                    onClick = { onCreatePlaylist(newPlaylistName) },
                    enabled = newPlaylistName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create playlist")
                }
            }
        },
    )
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Playlist name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EmptyLibraryCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp),
        )
    }
}

@Composable
private fun <T> CollectionList(
    items: List<T>,
    onClick: (T) -> Unit,
    artworkUri: (T) -> String?,
    title: (T) -> String,
    subtitle: (T) -> String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (items.isEmpty()) {
            item {
                EmptyLibraryCard("Nothing matches this view yet. Try another tab or refresh the device scan.")
            }
        }
        itemsIndexed(items) { _, item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(item) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (artworkUri(item) != null) {
                        ArtworkImage(
                            artworkUri = artworkUri(item),
                            modifier = Modifier.size(64.dp),
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(leadingIcon, contentDescription = null)
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title(item),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle(item),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
