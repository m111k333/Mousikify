package com.mousikify.feature.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.component.TrackListItem
import com.mousikify.core.designsystem.component.TrackRowSkeleton
import com.mousikify.core.designsystem.theme.SpotifyBackground
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.component.AddToPlaylistDialog
import com.mousikify.core.designsystem.component.CreatePlaylistDialog
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.media.remote.RemotePlaylist
import com.mousikify.core.model.PlaybackStatus
import com.mousikify.core.model.Song
import kotlinx.coroutines.delay

@Composable
fun SearchRoute(
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onlinePagedTracks = viewModel.onlinePagedTracks.collectAsLazyPagingItems()
    SearchScreen(
        uiState = uiState,
        onlinePagedTracks = onlinePagedTracks,
        isTrackCached = viewModel::isTrackCached,
        onIntent = viewModel::onIntent
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onlinePagedTracks: LazyPagingItems<Song>,
    isTrackCached: (Song) -> Boolean,
    onIntent: (SearchIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isCurrentBuffering = uiState.playbackStatus is PlaybackStatus.Preparing ||
            uiState.playbackStatus is PlaybackStatus.Buffering

    BackHandler(enabled = uiState.openedPlaylist != null) {
        onIntent(SearchIntent.ClosePlaylistDetail)
    }

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(3500L)
            onIntent(SearchIntent.DismissStatusMessage)
        }
    }

    if (uiState.openedPlaylist != null) {
        RemotePlaylistDetailView(
            playlist = uiState.openedPlaylist,
            uiState = uiState,
            isTrackCached = isTrackCached,
            onIntent = onIntent,
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(SpotifyBackground)
        ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)
        )

        // Spotify Search Bar
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { onIntent(SearchIntent.UpdateQuery(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textStyle = TextStyle(
                color = Color(0xFF121212),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            placeholder = {
                Text(
                    text = "Search local songs or YouTube Music...",
                    color = Color(0xFF555555),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF121212))
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { onIntent(SearchIntent.ClearQuery) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF121212))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onIntent(SearchIntent.SubmitSearch)
                    focusManager.clearFocus()
                }
            ),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Color(0xFF121212),
                unfocusedTextColor = Color(0xFF121212),
                cursorColor = Color(0xFF121212),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedLeadingIconColor = Color(0xFF121212),
                unfocusedLeadingIconColor = Color(0xFF121212),
                focusedTrailingIconColor = Color(0xFF121212),
                unfocusedTrailingIconColor = Color(0xFF121212)
            ),
            singleLine = true
        )

        if (uiState.statusMessage != null) {
            Surface(
                color = SpotifyGreen.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onIntent(SearchIntent.DismissStatusMessage) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Dismiss",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Search Category Tabs
        if (uiState.query.isNotBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.activeTab == SearchTab.ALL,
                        onClick = { onIntent(SearchIntent.SelectTab(SearchTab.ALL)) },
                        label = { Text("All") },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = SpotifyCardBackground,
                            labelColor = SpotifyTextPrimary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.activeTab == SearchTab.ONLINE,
                        onClick = { onIntent(SearchIntent.SelectTab(SearchTab.ONLINE)) },
                        label = { Text("YouTube Music (${onlinePagedTracks.itemCount})") },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = SpotifyCardBackground,
                            labelColor = SpotifyTextPrimary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.activeTab == SearchTab.PLAYLISTS,
                        onClick = { onIntent(SearchIntent.SelectTab(SearchTab.PLAYLISTS)) },
                        label = { Text("Playlists") },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = SpotifyCardBackground,
                            labelColor = SpotifyTextPrimary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.activeTab == SearchTab.SONGS,
                        onClick = { onIntent(SearchIntent.SelectTab(SearchTab.SONGS)) },
                        label = { Text("On Device (${uiState.songs.size})") },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = SpotifyCardBackground,
                            labelColor = SpotifyTextPrimary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.activeTab == SearchTab.ARTISTS,
                        onClick = { onIntent(SearchIntent.SelectTab(SearchTab.ARTISTS)) },
                        label = { Text("Artists (${uiState.artists.size})") },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = SpotifyCardBackground,
                            labelColor = SpotifyTextPrimary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.activeTab == SearchTab.ALBUMS,
                        onClick = { onIntent(SearchIntent.SelectTab(SearchTab.ALBUMS)) },
                        label = { Text("Albums (${uiState.albums.size})") },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = SpotifyCardBackground,
                            labelColor = SpotifyTextPrimary
                        )
                    )
                }
            }
        }

        // Search Results or Recent Searches (Last 10)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            if (uiState.query.isBlank()) {
                if (uiState.recentSearches.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Recent searches",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextPrimary
                            )
                            TextButton(onClick = { onIntent(SearchIntent.ClearRecentSearches) }) {
                                Text(
                                    text = "Clear all",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SpotifyTextSecondary
                                )
                            }
                        }
                    }

                    items(uiState.recentSearches, key = { "recent_$it" }) { recentQuery ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onIntent(SearchIntent.SelectRecentSearch(recentQuery)) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = recentQuery,
                                style = MaterialTheme.typography.bodyLarge,
                                color = SpotifyTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onIntent(SearchIntent.RemoveRecentSearch(recentQuery)) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Remove from history",
                                    tint = SpotifyTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    item {
                        HorizontalDivider(
                            color = SpotifyCardBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (uiState.recentSearches.isEmpty()) 80.dp else 24.dp,
                                start = 24.dp,
                                end = 24.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Play what you love",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Search across your offline MP3 library and stream millions of songs directly from YouTube Music. Tap '+' on any streamed song to save it to a playlist for offline playback.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // 1. Local Offline Songs Section
                if ((uiState.activeTab == SearchTab.ALL || uiState.activeTab == SearchTab.SONGS) &&
                    uiState.songs.isNotEmpty()
                ) {
                    item {
                        Text(
                            text = "On This Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(uiState.songs, key = { "local_${it.id}" }) { song ->
                        val isThisTrack = song.id == uiState.currentSongId
                        TrackListItem(
                            song = song,
                            isPlaying = isThisTrack && uiState.isPlaying,
                            isBuffering = isThisTrack && isCurrentBuffering,
                            isCachedOffline = true,
                            onClick = { onIntent(SearchIntent.PlaySong(song, uiState.songs)) },
                            onAddToPlaylistClick = { onIntent(SearchIntent.ShowAddToPlaylistDialog(song)) },
                            onFavoriteToggle = { onIntent(SearchIntent.ToggleFavorite(song)) }
                        )
                    }
                }

                // 2. Keyless YouTube Music Online Streaming Section (Paging 3 Infinite Scroll)
                if (uiState.activeTab == SearchTab.ALL || uiState.activeTab == SearchTab.ONLINE) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "YouTube Music Stream",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextPrimary
                            )
                            Text(
                                text = "Tap + to save offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpotifyGreen
                            )
                        }
                    }

                    val refreshState = onlinePagedTracks.loadState.refresh
                    if (refreshState is LoadState.Loading && onlinePagedTracks.itemCount == 0) {
                        items(6) {
                            TrackRowSkeleton()
                        }
                    } else if (refreshState is LoadState.Error && onlinePagedTracks.itemCount == 0) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Unable to reach YouTube Music. Check connection.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SpotifyTextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onlinePagedTracks.retry() }) {
                                    Text("Retry", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    items(
                        count = onlinePagedTracks.itemCount,
                        key = { index ->
                            val item = onlinePagedTracks.peek(index)
                            "ytm_${item?.remoteVideoId ?: item?.id ?: index}_$index"
                        }
                    ) { index ->
                        val song = onlinePagedTracks[index]
                        if (song != null) {
                            val isThisTrack = song.id == uiState.currentSongId
                            val isCached = remember(song.id, uiState.cacheUpdateTick, uiState.pinnedVideoIds) {
                                isTrackCached(song) || (song.remoteVideoId != null && song.remoteVideoId in uiState.pinnedVideoIds)
                            }
                            TrackListItem(
                                song = song,
                                isPlaying = isThisTrack && uiState.isPlaying,
                                isBuffering = isThisTrack && isCurrentBuffering,
                                isCachedOffline = isCached,
                                onClick = {
                                    val snapshotQueue = onlinePagedTracks.itemSnapshotList.items
                                    onIntent(SearchIntent.PlaySong(song, snapshotQueue))
                                },
                                onAddToPlaylistClick = {
                                    onIntent(SearchIntent.ShowAddToPlaylistDialog(song))
                                },
                                onFavoriteToggle = { onIntent(SearchIntent.ToggleFavorite(song)) }
                            )
                        } else {
                            TrackRowSkeleton()
                        }
                    }

                    if (onlinePagedTracks.loadState.append is LoadState.Loading) {
                        items(2) {
                            TrackRowSkeleton()
                        }
                    }
                }

                // 2b. YouTube Music Playlists Section
                if (uiState.activeTab == SearchTab.PLAYLISTS) {
                    if (uiState.isSearchingPlaylists && uiState.onlinePlaylists.isEmpty()) {
                        items(6) {
                            TrackRowSkeleton()
                        }
                    } else if (uiState.playlistSearchError != null && uiState.onlinePlaylists.isEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Unable to reach YouTube Music playlists. Check connection.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SpotifyTextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onIntent(SearchIntent.RetryPlaylistSearch) }) {
                                    Text("Retry", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (uiState.onlinePlaylists.isEmpty()) {
                        item {
                            Text(
                                text = "No playlists found for '${uiState.query}'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpotifyTextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                            )
                        }
                    } else {
                        itemsIndexed(uiState.onlinePlaylists, key = { index, playlist -> "ytm_pl_${playlist.url}_$index" }) { _, playlist ->
                            RemotePlaylistItem(
                                playlist = playlist,
                                onClick = { onIntent(SearchIntent.OpenPlaylist(playlist)) }
                            )
                        }
                    }
                }

                // 3. Local Artists Section
                if (uiState.activeTab == SearchTab.ALL || uiState.activeTab == SearchTab.ARTISTS) {
                    items(uiState.artists, key = { "artist_${it.id}" }) { artist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyCardBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = SpotifyTextSecondary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Artist • ${artist.songCount} songs", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
                            }
                        }
                    }
                }

                // 4. Local Albums Section
                if (uiState.activeTab == SearchTab.ALL || uiState.activeTab == SearchTab.ALBUMS) {
                    items(uiState.albums, key = { "album_${it.id}" }) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SpotifyCardBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = null,
                                    tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                                    modifier = Modifier.size(24.dp)
                                )
                                AsyncImage(
                                    model = album.artworkUri,
                                    contentDescription = album.title,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Album • ${album.artist}", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Add to Playlist / Save Offline Dialog
    val targetSong = uiState.songToAddToPlaylist
    if (targetSong != null && !uiState.showCreatePlaylistDialog) {
        AddToPlaylistDialog(
            song = targetSong,
            playlists = uiState.playlists,
            onDismiss = { onIntent(SearchIntent.ShowAddToPlaylistDialog(null)) },
            onCreateNewPlaylist = { onIntent(SearchIntent.ShowCreatePlaylistDialog(true)) },
            onSelectPlaylist = { playlistId ->
                onIntent(SearchIntent.AddSongToPlaylist(playlistId, targetSong))
            }
        )
    }

    // Create New Playlist Dialog
    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { onIntent(SearchIntent.ShowCreatePlaylistDialog(false)) },
            onCreate = { name ->
                onIntent(SearchIntent.CreatePlaylistAndAddSong(name))
            }
        )
    }
}

@Composable
private fun RemotePlaylistItem(
    playlist: RemotePlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp)
            )
            if (!playlist.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = SpotifyTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitle = buildString {
                append("Playlist")
                if (playlist.uploader.isNotBlank()) {
                    append(" • ")
                    append(playlist.uploader)
                }
                if (playlist.streamCount > 0) {
                    append(" • ")
                    append("${playlist.streamCount} songs")
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RemotePlaylistDetailView(
    playlist: RemotePlaylist,
    uiState: SearchUiState,
    isTrackCached: (Song) -> Boolean,
    onIntent: (SearchIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCurrentBuffering = uiState.playbackStatus is PlaybackStatus.Preparing ||
            uiState.playbackStatus is PlaybackStatus.Buffering

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            IconButton(
                onClick = { onIntent(SearchIntent.ClosePlaylistDetail) },
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpotifyTextPrimary
                )
            }
        }

        if (uiState.statusMessage != null) {
            item {
                Surface(
                    color = SpotifyGreen.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyGreen,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onIntent(SearchIntent.DismissStatusMessage) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Dismiss",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyCardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(64.dp)
                    )
                    if (!playlist.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = playlist.thumbnailUrl,
                            contentDescription = playlist.name,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                val subtitle = buildString {
                    append("YouTube Music")
                    if (playlist.uploader.isNotBlank()) {
                        append(" • ")
                        append(playlist.uploader)
                    }
                    if (uiState.openedPlaylistTracks.isNotEmpty()) {
                        append(" • ")
                        append("${uiState.openedPlaylistTracks.size} songs")
                    } else if (playlist.streamCount > 0) {
                        append(" • ")
                        append("${playlist.streamCount} songs")
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (uiState.openedPlaylistTracks.isNotEmpty()) {
                                onIntent(SearchIntent.PlaySong(uiState.openedPlaylistTracks.first(), uiState.openedPlaylistTracks))
                            }
                        },
                        enabled = uiState.openedPlaylistTracks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            contentColor = Color.Black,
                            disabledContainerColor = SpotifyGreen.copy(alpha = 0.4f),
                            disabledContentColor = Color.Black.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onIntent(SearchIntent.SavePlaylistLocally(playlist, uiState.openedPlaylistTracks))
                        },
                        enabled = uiState.openedPlaylistTracks.isNotEmpty() && !uiState.isSavingPlaylistLocally,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyCardBackground,
                            contentColor = SpotifyTextPrimary,
                            disabledContainerColor = SpotifyCardBackground.copy(alpha = 0.4f),
                            disabledContentColor = SpotifyTextSecondary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        if (uiState.isSavingPlaylistLocally) {
                            CircularProgressIndicator(
                                color = SpotifyGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...", fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save playlist", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (uiState.isLoadingPlaylistTracks) {
            items(8) {
                TrackRowSkeleton()
            }
        } else if (uiState.playlistTracksError != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Unable to load playlist tracks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotifyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { onIntent(SearchIntent.RetryLoadPlaylistTracks) }) {
                        Text("Retry", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            itemsIndexed(
                uiState.openedPlaylistTracks,
                key = { index, song -> "pl_track_${song.remoteVideoId ?: song.id}_$index" }
            ) { _, song ->
                val isThisTrack = song.id == uiState.currentSongId
                val isCached = remember(song.id, uiState.cacheUpdateTick, uiState.pinnedVideoIds) {
                    isTrackCached(song) || (song.remoteVideoId != null && song.remoteVideoId in uiState.pinnedVideoIds)
                }
                TrackListItem(
                    song = song,
                    isPlaying = isThisTrack && uiState.isPlaying,
                    isBuffering = isThisTrack && isCurrentBuffering,
                    isCachedOffline = isCached,
                    onClick = {
                        onIntent(SearchIntent.PlaySong(song, uiState.openedPlaylistTracks))
                    },
                    onAddToPlaylistClick = {
                        onIntent(SearchIntent.ShowAddToPlaylistDialog(song))
                    },
                    onFavoriteToggle = { onIntent(SearchIntent.ToggleFavorite(song)) }
                )
            }
        }
    }
}
