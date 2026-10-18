package com.mousikify.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
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
    LibraryScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onIntent(LibraryIntent.ShowCreatePlaylist(true)) }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Playlist",
                        tint = SpotifyTextPrimary
                    )
                }
            }

            // Animated Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                item {
                    FilterChipItem(
                        selected = uiState.activeFilter == LibraryFilter.ALL_SONGS,
                        label = "Tracks",
                        onClick = { onIntent(LibraryIntent.SetFilter(LibraryFilter.ALL_SONGS)) }
                    )
                }
                item {
                    FilterChipItem(
                        selected = uiState.activeFilter == LibraryFilter.PLAYLISTS,
                        label = "Playlists",
                        onClick = { onIntent(LibraryIntent.SetFilter(LibraryFilter.PLAYLISTS)) }
                    )
                }
                item {
                    FilterChipItem(
                        selected = uiState.activeFilter == LibraryFilter.ALBUMS,
                        label = "Albums",
                        onClick = { onIntent(LibraryFilter.ALBUMS.let { LibraryIntent.SetFilter(it) }) }
                    )
                }
                item {
                    FilterChipItem(
                        selected = uiState.activeFilter == LibraryFilter.ARTISTS,
                        label = "Artists",
                        onClick = { onIntent(LibraryIntent.SetFilter(LibraryFilter.ARTISTS)) }
                    )
                }
                item {
                    FilterChipItem(
                        selected = uiState.activeFilter == LibraryFilter.FOLDERS,
                        label = "Folders",
                        onClick = { onIntent(LibraryIntent.SetFilter(LibraryFilter.FOLDERS)) }
                    )
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
                        IconButton(onClick = { onIntent(LibraryIntent.ShowCreatePlaylist(true)) }) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist", tint = SpotifyGreen)
                        }
                    }
                }
            }

            // Filtered Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                when (uiState.activeFilter) {
                    LibraryFilter.ALL_SONGS -> {
                        items(uiState.songs, key = { it.id }) { song ->
                            SelectableTrackItem(
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
                                onLongClick = {
                                    onIntent(LibraryIntent.ToggleSelectSong(song.id))
                                },
                                onFavoriteToggle = { onIntent(LibraryIntent.ToggleFavorite(song)) }
                            )
                        }
                    }
                    LibraryFilter.PLAYLISTS -> {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            PlaylistItemRow(playlist = playlist, onClick = {})
                        }
                    }
                    LibraryFilter.ALBUMS -> {
                        items(uiState.albums, key = { it.id }) { album ->
                            AlbumItemRow(album = album, onClick = {})
                        }
                    }
                    LibraryFilter.ARTISTS -> {
                        items(uiState.artists, key = { it.id }) { artist ->
                            ArtistItemRow(artist = artist, onClick = {})
                        }
                    }
                    LibraryFilter.FOLDERS -> {
                        items(uiState.folders, key = { it.path }) { folder ->
                            FolderItemRow(folder = folder, onClick = {})
                        }
                    }
                }
            }
        }

        // Create Playlist Dialog
        if (uiState.showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { onIntent(LibraryIntent.ShowCreatePlaylist(false)) },
                onCreate = { name -> onIntent(LibraryIntent.CreatePlaylist(name)) }
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SpotifyGreen,
            selectedLabelColor = Color.Black,
            containerColor = SpotifyCardBackground,
            labelColor = SpotifyTextPrimary
        ),
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableTrackItem(
    song: Song,
    isPlaying: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = SpotifyGreen,
                    checkmarkColor = Color.Black
                ),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        TrackListItem(
            song = song,
            isPlaying = isPlaying,
            onClick = onClick,
            onFavoriteToggle = onFavoriteToggle,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlaylistItemRow(
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
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = SpotifyTextSecondary)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = playlist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = "Playlist", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
        }
    }
}

@Composable
private fun AlbumItemRow(
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
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyCardBackground)
        ) {
            AsyncImage(
                model = album.artworkUri,
                contentDescription = album.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = "${album.artist} • ${album.songCount} songs", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
        }
    }
}

@Composable
private fun ArtistItemRow(
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
                .size(56.dp)
                .clip(CircleShape)
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = SpotifyTextSecondary)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = "${artist.songCount} tracks", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
        }
    }
}

@Composable
private fun FolderItemRow(
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
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = SpotifyTextSecondary)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = "${folder.songCount} tracks • ${folder.path}", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpotifyCardBackground,
        title = { Text("Give your playlist a name", color = SpotifyTextPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("My playlist #1", color = SpotifyTextSecondary) },
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
                onClick = { if (name.isNotBlank()) onCreate(name) },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                Text("Create", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SpotifyTextSecondary)
            }
        }
    )
}
