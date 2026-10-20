package com.mousikify.feature.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.component.AddSongsToPlaylistDialog
import com.mousikify.core.designsystem.component.AddToPlaylistDialog
import com.mousikify.core.designsystem.component.CreatePlaylistDialog
import com.mousikify.core.designsystem.component.MediaDetailView
import com.mousikify.core.designsystem.component.TrackListItem
import com.mousikify.core.designsystem.theme.SpotifyBackground
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.Album
import com.mousikify.core.model.Artist
import com.mousikify.core.model.Folder
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.Song

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onIntent(LibraryIntent.ImportFolder(uri))
        }
    }

    LibraryScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onPickFolder = { folderPicker.launch(null) }
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onIntent: (LibraryIntent) -> Unit,
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Intercept back button when inside a drill-down detail screen
    BackHandler(enabled = uiState.activeDetail != null) {
        onIntent(LibraryIntent.CloseDetail)
    }

    var showPlusMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
    ) {
        AnimatedContent(
            targetState = uiState.activeDetail,
            contentKey = { detail ->
                when (detail) {
                    null -> "library_root"
                    is LibraryDetailView.PlaylistDetail -> "playlist_${detail.playlist.id}"
                    is LibraryDetailView.ArtistDetail -> "artist_${detail.artist.name}"
                    is LibraryDetailView.AlbumDetail -> "album_${detail.album.id}"
                    is LibraryDetailView.FolderDetail -> "folder_${detail.folder.path}"
                }
            },
            transitionSpec = {
                if (initialState != null && targetState != null) {
                    androidx.compose.animation.EnterTransition.None.togetherWith(androidx.compose.animation.ExitTransition.None)
                } else if (targetState != null) {
                    (slideInHorizontally(animationSpec = tween(240)) { it / 3 } + fadeIn(animationSpec = tween(200)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { -it / 3 } + fadeOut(animationSpec = tween(160)))
                } else {
                    (slideInHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeIn(animationSpec = tween(180)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { it / 3 } + fadeOut(animationSpec = tween(180)))
                }
            },
            label = "LibraryDetailTransition"
        ) { currentDetail ->
            if (currentDetail != null) {
                SpotifyDetailScreen(
                    detail = currentDetail,
                    uiState = uiState,
                    onIntent = onIntent,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
            // Spotify Header: "Your Library" with Search & Plus
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(onClick = { showPlusMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = SpotifyTextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showPlusMenu,
                        onDismissRequest = { showPlusMenu = false },
                        containerColor = SpotifyCardBackground
                    ) {
                        DropdownMenuItem(
                            text = { Text("Select music folder from device", color = SpotifyTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = SpotifyGreen) },
                            onClick = {
                                showPlusMenu = false
                                onPickFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Create new playlist", color = SpotifyTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = SpotifyGreen) },
                            onClick = {
                                showPlusMenu = false
                                onIntent(LibraryIntent.ShowCreatePlaylist(true))
                            }
                        )
                    }
                }
            }

            // Spotify Category Filter Pills (Ordered: Playlists, Albums, Artists, Songs, Folders)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SpotifyFilterPill(
                        selected = uiState.activeFilter == LibraryFilter.PLAYLISTS,
                        label = "Playlists (${uiState.playlists.size})",
                        onClick = { onIntent(LibraryIntent.SetFilter(LibraryFilter.PLAYLISTS)) }
                    )
                }
                item {
                    SpotifyFilterPill(
                        selected = uiState.activeFilter == LibraryFilter.ALBUMS,
                        label = "Albums (${uiState.albums.size})",
                        onClick = { onIntent(LibraryIntent.SetFilter(LibraryFilter.ALBUMS)) }
                    )
                }
                item {
                    SpotifyFilterPill(
                        selected = uiState.activeFilter == LibraryFilter.ARTISTS,
                        label = "Artists (${uiState.artists.size})",
                        onClick = { onIntent(LibraryIntent.SetFilter(LibraryFilter.ARTISTS)) }
                    )
                }
                item {
                    SpotifyFilterPill(
                        selected = uiState.activeFilter == LibraryFilter.ALL_SONGS,
                        label = "Songs (${uiState.songs.size})",
                        onClick = { onIntent(LibraryIntent.SetFilter(LibraryFilter.ALL_SONGS)) }
                    )
                }
                item {
                    SpotifyFilterPill(
                        selected = uiState.activeFilter == LibraryFilter.FOLDERS,
                        label = "Folders (${uiState.folders.size})",
                        onClick = { onIntent(LibraryIntent.SetFilter(LibraryFilter.FOLDERS)) }
                    )
                }
            }

            // Sub-header: Sort Menu + Import Folder shortcut
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showSortMenu = true }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort order",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.sortOrder.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor = SpotifyCardBackground
                    ) {
                        LibrarySortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = order.label,
                                        color = if (uiState.sortOrder == order) SpotifyGreen else SpotifyTextPrimary,
                                        fontWeight = if (uiState.sortOrder == order) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (uiState.sortOrder == order) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    showSortMenu = false
                                    onIntent(LibraryIntent.SetSortOrder(order))
                                }
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onPickFolder,
                    colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Folder", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Multi-Select Batch Toolbar
            AnimatedVisibility(
                visible = uiState.isMultiSelectMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = SpotifySurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onIntent(LibraryIntent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close selection", tint = SpotifyTextPrimary)
                        }
                        Text(
                            text = "${uiState.selectedSongIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onIntent(LibraryIntent.SelectAll) }) {
                            Text("Select All", color = SpotifyGreen)
                        }
                        IconButton(onClick = { onIntent(LibraryIntent.ShowAddToPlaylistForSelection(true)) }) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist", tint = SpotifyGreen)
                        }
                    }
                }
            }

            // Empty Library State
            val isCurrentFilterEmpty = when (uiState.activeFilter) {
                LibraryFilter.PLAYLISTS -> uiState.playlists.isEmpty()
                LibraryFilter.ALBUMS -> uiState.albums.isEmpty()
                LibraryFilter.ARTISTS -> uiState.artists.isEmpty()
                LibraryFilter.ALL_SONGS -> uiState.songs.isEmpty()
                LibraryFilter.FOLDERS -> uiState.folders.isEmpty()
            }

            if (isCurrentFilterEmpty && !uiState.isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.activeFilter == LibraryFilter.PLAYLISTS) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyCardBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Create your first playlist",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "It's easy, we'll help you organize your favorite tracks.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpotifyTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { onIntent(LibraryIntent.ShowCreatePlaylist(true)) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (uiState.songs.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyCardBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No music in your library yet",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Import songs directly from your device folders (Downloads, Music, SD Card) or rescan device storage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpotifyTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onPickFolder,
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Music Folder", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { onIntent(LibraryIntent.RescanLibrary) },
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = SpotifyTextPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rescan Storage", color = SpotifyTextPrimary)
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No items found in this section",
                                style = MaterialTheme.typography.titleMedium,
                                color = SpotifyTextSecondary
                            )
                        }
                    }
                }
            } else {
                // Content List
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    when (uiState.activeFilter) {
                        LibraryFilter.PLAYLISTS -> {
                            items(uiState.playlists, key = { it.id }) { playlist ->
                                SpotifyPlaylistItem(
                                    playlist = playlist,
                                    onClick = { onIntent(LibraryIntent.OpenPlaylist(playlist)) }
                                )
                            }
                        }
                        LibraryFilter.ALBUMS -> {
                            items(uiState.albums, key = { it.id }) { album ->
                                SpotifyAlbumItem(
                                    album = album,
                                    onClick = { onIntent(LibraryIntent.OpenAlbum(album)) }
                                )
                            }
                        }
                        LibraryFilter.ARTISTS -> {
                            items(uiState.artists, key = { it.id }) { artist ->
                                SpotifyArtistItem(
                                    artist = artist,
                                    onClick = { onIntent(LibraryIntent.OpenArtist(artist)) }
                                )
                            }
                        }
                        LibraryFilter.ALL_SONGS -> {
                            items(uiState.songs, key = { it.id }) { song ->
                                TrackListItem(
                                    song = song,
                                    isPlaying = song.id == uiState.currentSongId && uiState.isPlaying,
                                    isSelected = uiState.selectedSongIds.contains(song.id),
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    onClick = {
                                        if (uiState.isMultiSelectMode) {
                                            onIntent(LibraryIntent.ToggleSelectSong(song.id))
                                        } else {
                                            onIntent(LibraryIntent.PlaySong(song, uiState.songs))
                                        }
                                    },
                                    onFavoriteToggle = { onIntent(LibraryIntent.ToggleFavorite(song)) },
                                    onAddToPlaylistClick = { onIntent(LibraryIntent.ShowAddToPlaylistDialog(song)) }
                                )
                            }
                        }
                        LibraryFilter.FOLDERS -> {
                            items(uiState.folders, key = { it.path }) { folder ->
                                SpotifyFolderItem(
                                    folder = folder,
                                    onClick = { onIntent(LibraryIntent.OpenFolder(folder)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
        }

        // Notification Snackbar
        if (uiState.statusMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = SpotifySurfaceElevated,
                contentColor = SpotifyTextPrimary,
                action = {
                    TextButton(onClick = { onIntent(LibraryIntent.DismissStatusMessage) }) {
                        Text("OK", color = SpotifyGreen)
                    }
                }
            ) {
                Text(uiState.statusMessage)
            }
        }

        // Create Playlist Dialog
        if (uiState.showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { onIntent(LibraryIntent.ShowCreatePlaylist(false)) },
                onCreate = { name -> onIntent(LibraryIntent.CreatePlaylist(name)) }
            )
        }

        // Add to Playlist Dialog (Pick a playlist for a song or selected songs)
        if (uiState.songToAddToPlaylist != null || uiState.showAddToPlaylistForSelection) {
            AddToPlaylistDialog(
                song = uiState.songToAddToPlaylist,
                selectedSongCount = uiState.selectedSongIds.size,
                playlists = uiState.playlists,
                onDismiss = {
                    onIntent(LibraryIntent.ShowAddToPlaylistDialog(null))
                    onIntent(LibraryIntent.ShowAddToPlaylistForSelection(false))
                },
                onCreateNewPlaylist = {
                    onIntent(LibraryIntent.ShowCreatePlaylist(true))
                },
                onSelectPlaylist = { playlistId ->
                    if (uiState.songToAddToPlaylist != null) {
                        onIntent(LibraryIntent.AddSongToPlaylist(playlistId, uiState.songToAddToPlaylist.id))
                        onIntent(LibraryIntent.ShowAddToPlaylistDialog(null))
                    } else if (uiState.showAddToPlaylistForSelection) {
                        onIntent(LibraryIntent.AddSelectedToPlaylist(playlistId))
                    }
                }
            )
        }

        // Add Songs Picker Dialog (Inside a playlist, pick songs to add to it)
        val pickerPlaylist = uiState.showAddSongsPickerForPlaylist
        if (pickerPlaylist != null) {
            val currentSongs = (uiState.activeDetail as? LibraryDetailView.PlaylistDetail)?.songs ?: emptyList()
            AddSongsToPlaylistDialog(
                playlist = pickerPlaylist,
                allSongs = uiState.songs,
                currentPlaylistSongs = currentSongs,
                onAddSong = { songId ->
                    onIntent(LibraryIntent.AddSongToPlaylist(pickerPlaylist.id, songId))
                },
                onRemoveSong = { songId ->
                    onIntent(LibraryIntent.RemoveSongFromPlaylist(pickerPlaylist.id, songId))
                },
                onDismiss = {
                    onIntent(LibraryIntent.ShowAddSongsPicker(null))
                }
            )
        }
    }
}

@Composable
private fun SpotifyDetailScreen(
    detail: LibraryDetailView,
    uiState: LibraryUiState,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val title: String
    val subtitle: String
    val songs: List<Song>
    val headerArtwork: @Composable () -> Unit

    when (detail) {
        is LibraryDetailView.ArtistDetail -> {
            title = detail.artist.name
            subtitle = "${detail.songs.size} songs • Artist"
            songs = detail.songs
            headerArtwork = {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(SpotifyCardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(68.dp)
                    )
                }
            }
        }
        is LibraryDetailView.AlbumDetail -> {
            title = detail.album.title
            subtitle = "${detail.album.artist} • ${detail.songs.size} songs"
            songs = detail.songs
            headerArtwork = {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyCardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(60.dp)
                    )
                    AsyncImage(
                        model = detail.album.artworkUri,
                        contentDescription = detail.album.title,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        is LibraryDetailView.FolderDetail -> {
            title = detail.folder.name
            subtitle = "${detail.songs.size} songs • ${detail.folder.path}"
            songs = detail.songs
            headerArtwork = {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpotifySurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }
        }
        is LibraryDetailView.PlaylistDetail -> {
            title = detail.playlist.name
            subtitle = "${detail.songs.size} songs • Playlist"
            songs = detail.songs
            headerArtwork = {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyCardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(60.dp)
                    )
                    val cover = detail.playlist.customCoverUri
                        ?: detail.songs.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
                    if (!cover.isNullOrBlank()) {
                        AsyncImage(
                            model = cover,
                            contentDescription = detail.playlist.name,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var isEditMode by remember(detail) { mutableStateOf(false) }

    BackHandler(enabled = isEditMode) {
        isEditMode = false
    }

    if (showRenameDialog && detail is LibraryDetailView.PlaylistDetail) {
        var newName by remember(detail.playlist.name) { mutableStateOf(detail.playlist.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = SpotifyCardBackground,
            title = {
                Text("Rename playlist", color = SpotifyTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Playlist name", color = SpotifyTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = SpotifyTextSecondary,
                        focusedTextColor = SpotifyTextPrimary,
                        unfocusedTextColor = SpotifyTextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            showRenameDialog = false
                            onIntent(LibraryIntent.RenamePlaylist(detail.playlist.id, newName.trim()))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = SpotifyTextSecondary)
                }
            }
        )
    }

    if (showDeleteConfirmDialog && detail is LibraryDetailView.PlaylistDetail) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = SpotifyCardBackground,
            title = {
                Text("Delete Playlist?", color = SpotifyTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${detail.playlist.name}\"? This playlist will be removed from your library.",
                    color = SpotifyTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onIntent(LibraryIntent.DeletePlaylist(detail.playlist.id))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE22134)),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = SpotifyTextPrimary)
                }
            }
        )
    }

    MediaDetailView(
        title = title,
        subtitle = subtitle,
        songs = songs,
        headerArtwork = headerArtwork,
        currentSongId = uiState.currentSongId,
        isPlaying = uiState.isPlaying,
        isShuffleEnabled = uiState.isShuffleEnabled,
        onBack = {
            if (isEditMode) {
                isEditMode = false
            } else {
                onIntent(LibraryIntent.CloseDetail)
            }
        },
        onPlaySong = { _, index -> onIntent(LibraryIntent.PlayAll(songs, index)) },
        onPlayAll = { onIntent(LibraryIntent.PlayAll(songs, 0)) },
        onToggleShuffle = { onIntent(LibraryIntent.ToggleShuffle(songs)) },
        onToggleFavorite = { onIntent(LibraryIntent.ToggleFavorite(it)) },
        modifier = modifier,
        topBarActions = {
            if (detail is LibraryDetailView.PlaylistDetail) {
                TextButton(onClick = { isEditMode = !isEditMode }) {
                    Text(
                        text = if (isEditMode) "Done" else "Edit",
                        color = if (isEditMode) SpotifyGreen else SpotifyTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                AnimatedVisibility(
                    visible = isEditMode,
                    enter = fadeIn(animationSpec = tween(180)) + expandHorizontally(),
                    exit = fadeOut(animationSpec = tween(150)) + shrinkHorizontally()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename Playlist",
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Playlist",
                                tint = Color(0xFFE22134),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        },
        actionBarActions = {
            if (detail is LibraryDetailView.PlaylistDetail) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onIntent(LibraryIntent.ShowAddSongsPicker(detail.playlist)) },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, SpotifyTextSecondary.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyTextPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add songs", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                AnimatedVisibility(
                    visible = isEditMode,
                    enter = fadeIn(animationSpec = tween(180)) + expandHorizontally(),
                    exit = fadeOut(animationSpec = tween(150)) + shrinkHorizontally()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { showRenameDialog = true },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, SpotifyTextSecondary.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyTextPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rename", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        emptyContent = {
            if (detail is LibraryDetailView.PlaylistDetail) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, bottom = 40.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SpotifyCardBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This playlist is empty",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Find songs from your library to add to this playlist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotifyTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { onIntent(LibraryIntent.ShowAddSongsPicker(detail.playlist)) },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to this playlist", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No songs found in this section",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpotifyTextSecondary
                    )
                }
            }
        },
        songTrailingContent = { song ->
            val playlistId = (detail as? LibraryDetailView.PlaylistDetail)?.playlist?.id
            IconButton(
                onClick = { onIntent(LibraryIntent.ShowAddToPlaylistDialog(song)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (playlistId != null) {
                AnimatedVisibility(
                    visible = isEditMode,
                    enter = fadeIn(animationSpec = tween(150)) + expandHorizontally(),
                    exit = fadeOut(animationSpec = tween(150)) + shrinkHorizontally()
                ) {
                    IconButton(
                        onClick = { onIntent(LibraryIntent.RemoveSongFromPlaylist(playlistId, song.id)) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove from playlist",
                            tint = Color(0xFFE22134),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SpotifyFilterPill(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) SpotifyGreen else SpotifyCardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else SpotifyTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


@Composable
private fun SpotifyPlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(28.dp))
            if (!playlist.customCoverUri.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.customCoverUri,
                    contentDescription = playlist.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = playlist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            val songCountText = if (playlist.songCount == 1) "1 song" else "${playlist.songCount} songs"
            Text(text = "$songCountText • Playlist", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
        }
    }
}

@Composable
private fun SpotifyFolderItem(
    folder: Folder,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifySurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "${folder.songCount} songs • ${folder.path}", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SpotifyArtistItem(
    artist: Artist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(text = "Artist • ${artist.songCount} songs", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
        }
    }
}

@Composable
private fun SpotifyAlbumItem(
    album: Album,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(32.dp)
            )
            AsyncImage(
                model = album.artworkUri,
                contentDescription = album.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(text = "Album • ${album.artist}", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
        }
    }
}


