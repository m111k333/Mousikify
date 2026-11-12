package com.mousikify.feature.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.mousikify.core.designsystem.component.AddSongsToPlaylistDialog
import com.mousikify.core.designsystem.component.AddToPlaylistDialog
import com.mousikify.core.designsystem.component.CreatePlaylistDialog
import com.mousikify.core.designsystem.component.MediaDetailView
import com.mousikify.core.designsystem.component.SongContextMenuBottomSheet
import com.mousikify.core.designsystem.component.TrackListItem
import com.mousikify.core.designsystem.component.YouTubeMusicAlbumView
import com.mousikify.core.designsystem.component.YouTubeMusicArtistView
import com.mousikify.core.designsystem.component.bounceClickable


import com.mousikify.core.designsystem.theme.SpotifyBackground
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.Album
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.RecentlyPlayedItem
import com.mousikify.core.model.RecentlyPlayedType
import com.mousikify.core.model.Song

@Composable
fun HomeRoute(
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToPlaylistGenerator: () -> Unit = {},
    onOpenRadio: (Song) -> Unit = {},
    homeResetTrigger: Long = 0L,
    initialMixId: String? = null,
    onInitialMixHandled: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openRadioSong by viewModel.openRadioEvent.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(openRadioSong) {
        val song = openRadioSong
        if (song != null) {
            onOpenRadio(song)
            viewModel.consumeOpenRadioEvent()
        }
    }

    androidx.compose.runtime.LaunchedEffect(homeResetTrigger) {
        if (homeResetTrigger > 0L) {
            viewModel.onIntent(HomeIntent.CloseDetail)
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialMixId, uiState.smartMixes) {
        if (!initialMixId.isNullOrBlank() && uiState.smartMixes.isNotEmpty()) {
            val target = uiState.smartMixes.firstOrNull { it.id == initialMixId }
            if (target != null) {
                viewModel.onIntent(HomeIntent.OpenMix(target))
                onInitialMixHandled()
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onIntent(HomeIntent.ImportFolder(uri))
        }
    }

    HomeScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onPickFolder = { folderPicker.launch(null) },
        onNavigateToEqualizer = onNavigateToEqualizer,
        onNavigateToDiagnostics = onNavigateToDiagnostics,
        onNavigateToPlaylistGenerator = onNavigateToPlaylistGenerator,
        homeResetTrigger = homeResetTrigger,
        isTrackCached = viewModel::isTrackCached
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    onPickFolder: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToPlaylistGenerator: () -> Unit = {},
    homeResetTrigger: Long = 0L,
    isTrackCached: (Song) -> Boolean = { it.isLocal },
    modifier: Modifier = Modifier
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var showTrackHistorySheet by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(homeResetTrigger) {
        if (homeResetTrigger > 0L && uiState.activeDetail == null) {
            try {
                listState.animateScrollToItem(0)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(if (uiState.canUndo) 5000L else 3000L)
            onIntent(HomeIntent.DismissStatusMessage)
        }
    }

    BackHandler(enabled = uiState.activeDetail != null) {
        onIntent(HomeIntent.CloseDetail)
    }

    AnimatedContent(
        targetState = uiState.activeDetail,
        contentKey = { detail ->
            when (detail) {
                null -> "home_root"
                is HomeDetailView.PlaylistDetail -> "playlist_${detail.playlist.id}"
                is HomeDetailView.AlbumDetail -> "album_${detail.album.title.trim().lowercase()}_${detail.album.artist.trim().lowercase()}"
                is HomeDetailView.ArtistDetail -> "artist_${com.mousikify.core.model.cleanArtistName(detail.artist.name).trim().lowercase()}"
                is HomeDetailView.LikedSongsDetail -> "liked_songs"
                is HomeDetailView.MixDetail -> "mix_${detail.mix.id}"
            }
        },
        transitionSpec = {
            if (initialState != null && targetState != null) {
                (slideInHorizontally(animationSpec = tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 3 } + fadeIn(animationSpec = tween(180)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { -it / 3 } + fadeOut(animationSpec = tween(160)))
            } else if (targetState != null) {
                (slideInHorizontally(animationSpec = tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 3 } + fadeIn(animationSpec = tween(200)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { -it / 3 } + fadeOut(animationSpec = tween(160)))
            } else {
                (slideInHorizontally(animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 3 } + fadeIn(animationSpec = tween(180)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { it / 3 } + fadeOut(animationSpec = tween(180)))
            }
        },
        label = "HomeDetailTransition"
    ) { currentDetail ->
        if (currentDetail != null) {
            HomeDetailScreen(
                detail = currentDetail,
                uiState = uiState,
                onIntent = onIntent,
                isTrackCached = isTrackCached,
                modifier = modifier
            )
        } else {
            var selectedPill by remember { mutableStateOf("All") }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(SpotifyBackground)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // Spotify Top Header: Avatar + Filter Pills + Action Icons
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pills: "All", "Music", "Playlists"
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpotifyPill(
                                label = "All",
                                isSelected = selectedPill == "All",
                                onClick = { selectedPill = "All" }
                            )
                            SpotifyPill(
                                label = "Music",
                                isSelected = selectedPill == "Music",
                                onClick = { selectedPill = "Music" }
                            )
                            SpotifyPill(
                                label = "Playlists",
                                isSelected = selectedPill == "Playlists",
                                onClick = { selectedPill = "Playlists" }
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Equalizer button
                            IconButton(onClick = onNavigateToEqualizer) {
                                Icon(
                                    imageVector = Icons.Default.Equalizer,
                                    contentDescription = "Equalizer",
                                    tint = SpotifyTextSecondary
                                )
                            }

                            // Diagnostics gear button
                            IconButton(onClick = onNavigateToDiagnostics) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Diagnostics",
                                    tint = SpotifyTextSecondary
                                )
                            }
                        }
                    }
                }

                // Dynamic Greeting: "Good morning / afternoon / evening"
                item {
                    Text(
                        text = uiState.greeting,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Quick-Jump 2x3 Grid (6 Tiles)
                item {
                    QuickJumpGrid(
                        likedCount = uiState.likedCount,
                        playlists = uiState.playlists,
                        mixes = uiState.smartMixes,
                        albums = uiState.albums,
                        onLikedClick = { onIntent(HomeIntent.OpenLikedSongs) },
                        onPlaylistClick = { onIntent(HomeIntent.OpenPlaylist(it)) },
                        onMixClick = { onIntent(HomeIntent.OpenMix(it)) },
                        onAlbumClick = { onIntent(HomeIntent.OpenAlbum(it)) },
                        onImportFolderClick = onPickFolder,
                        onPlaylistGeneratorClick = onNavigateToPlaylistGenerator
                    )
                }

                // "Recently Played" Prominent Carousel
                val recentItems = uiState.recentlyPlayed
                if ((recentItems.isNotEmpty() || uiState.recentlyHeard.isNotEmpty()) && selectedPill != "Playlists") {
                    item {
                        Spacer(modifier = Modifier.height(28.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Recently played",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { showTrackHistorySheet = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "Recent 30 tracks history log",
                                        tint = SpotifyTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (recentItems.size > 1) {
                                Text(
                                    text = "${recentItems.size} items",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpotifyTextSecondary
                                )
                            }
                        }
                        if (recentItems.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(recentItems, key = { "recent_${it.type}_${it.referenceId}" }) { item ->
                                    SpotifyRecentlyPlayedCard(
                                        item = item,
                                        onClick = { onIntent(HomeIntent.OpenRecentlyPlayed(item)) }
                                    )
                                }
                            }
                        }
                    }
                }


            // Empty Library Hero Banner if no tracks found
            if (uiState.totalSongCount == 0 && !uiState.isLoading) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SpotifySurfaceElevated)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Import Your Music",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Mousikify plays your downloaded audio files offline. Select your music or downloads folder from your device files to import them now.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpotifyTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = onPickFolder,
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Music Folder", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onIntent(HomeIntent.Rescan) },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("Rescan Device Storage", color = SpotifyTextPrimary)
                            }
                        }
                    }
                }
            }

            // "Made for you" (Spotify Smart Mixes with vibrant gradients)
            if (uiState.smartMixes.isNotEmpty() && (selectedPill == "All" || selectedPill == "Music" || selectedPill == "Playlists")) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "Made for you",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.smartMixes, key = { it.id }) { mix ->
                            SpotifySmartMixCard(
                                mix = mix,
                                onClick = { onIntent(HomeIntent.OpenMix(mix)) }
                            )
                        }
                    }
                }
            }

            // "Your Playlists" Carousel (Preview of user's playlists & folder imports)
            if (uiState.playlists.isNotEmpty() && (selectedPill == "All" || selectedPill == "Playlists")) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Playlists",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.playlists.size} playlists",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyTextSecondary
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            SpotifyPlaylistCard(
                                playlist = playlist,
                                onClick = { onIntent(HomeIntent.OpenPlaylist(playlist)) }
                            )
                        }
                    }
                }
            }

            // "Your Albums" Carousel (Preview of user's offline albums)
            if (uiState.albums.isNotEmpty() && (selectedPill == "All" || selectedPill == "Music")) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Albums",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.albums.size} albums",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyTextSecondary
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.albums, key = { it.id }) { album ->
                            SpotifyAlbumCard(
                                album = album,
                                onClick = { onIntent(HomeIntent.OpenAlbum(album)) }
                            )
                        }
                    }
                }
            }


            // "Recently Added" Carousel
            if (uiState.recentlyAdded.isNotEmpty() && selectedPill != "Playlists") {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "Recently added",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.recentlyAdded, key = { it.id }) { song ->
                            SpotifySquareTrackCard(
                                song = song,
                                onClick = { onIntent(HomeIntent.PlaySong(song, uiState.recentlyAdded)) }
                            )
                        }
                    }
                }
            }

            // "Forgotten Favorites" Section
            if (uiState.forgottenFavorites.isNotEmpty() && selectedPill != "Playlists") {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            text = "Forgotten favorites",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Rediscover your top offline music",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyTextSecondary
                        )
                    }
                }

                items(uiState.forgottenFavorites.take(6), key = { it.id }) { song ->
                    val isCached = remember(song.id, uiState.cacheUpdateTick) {
                        if (song.isLocal) true else isTrackCached(song)
                    }
                    TrackListItem(
                        song = song,
                        isPlaying = song.id == uiState.currentSongId && uiState.isPlaying,
                        isCachedOffline = isCached,
                        isFavorite = uiState.isFavorite(song),
                        onClick = { onIntent(HomeIntent.PlaySong(song, uiState.forgottenFavorites)) },
                        onFavoriteToggle = { onIntent(HomeIntent.ToggleFavorite(song)) }
                    )
                }
            }
        }

        // Status / Import notification banner
        if (uiState.statusMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = SpotifySurfaceElevated,
                contentColor = SpotifyTextPrimary,
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.canUndo) {
                            TextButton(onClick = { onIntent(HomeIntent.UndoDelete) }) {
                                Text("UNDO", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                        TextButton(onClick = { onIntent(HomeIntent.DismissStatusMessage) }) {
                            Text(if (uiState.canUndo) "DISMISS" else "OK", color = SpotifyTextSecondary)
                        }
                    }
                }
            ) {
                Text(uiState.statusMessage)
            }
        }

        if (showTrackHistorySheet) {
            val recentTracks = remember(uiState.recentlyHeard, uiState.recentlyPlayed) {
                if (uiState.recentlyHeard.isNotEmpty()) {
                    uiState.recentlyHeard.take(30)
                } else {
                    uiState.recentlyPlayed.mapNotNull { it.song }.distinctBy { it.contentUri }.take(30)
                }
            }
            ModalBottomSheet(
                onDismissRequest = { showTrackHistorySheet = false },
                containerColor = SpotifyCardBackground,
                contentColor = SpotifyTextPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Recent 30 Tracks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextPrimary
                            )
                        }
                        Text(
                            text = "${recentTracks.size} tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyTextSecondary
                        )
                    }

                    if (recentTracks.isEmpty()) {
                        Text(
                            text = "No tracks played recently.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyTextSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            itemsIndexed(
                                items = recentTracks,
                                key = { index, song -> "history_track_${song.contentUri}_$index" }
                            ) { index, song ->
                                val isPlayingThis = song.id == uiState.currentSongId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onIntent(HomeIntent.PlaySong(song, recentTracks))
                                        }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isPlayingThis) SpotifyGreen else SpotifyTextSecondary,
                                        fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isPlayingThis) SpotifyGreen else SpotifyTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (song.artistName.isNotBlank()) {
                                            Text(
                                                text = song.artistName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SpotifyTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
}

@Composable
private fun SpotifyPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) SpotifyGreen else SpotifyCardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else SpotifyTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun QuickJumpGrid(
    likedCount: Int,
    playlists: List<Playlist>,
    mixes: List<SmartMix>,
    albums: List<Album> = emptyList(),
    onLikedClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onMixClick: (SmartMix) -> Unit,
    onAlbumClick: (Album) -> Unit = {},
    onImportFolderClick: () -> Unit,
    onPlaylistGeneratorClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Liked Songs & First Playlist / Mix
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpotifyQuickTile(
                title = "Liked Songs",
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF450AF5), Color(0xFF8E8EE5), Color(0xFFC4EFD9))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Liked",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                onClick = onLikedClick,
                modifier = Modifier.weight(1f)
            )

            val p1 = playlists.firstOrNull()
            val m1 = mixes.firstOrNull()
            if (p1 != null) {
                SpotifyQuickTile(
                    title = p1.name,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(SpotifySurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(24.dp))
                            if (!p1.customCoverUri.isNullOrBlank()) {
                                AsyncImage(model = p1.customCoverUri, contentDescription = p1.name, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    },
                    onClick = { onPlaylistClick(p1) },
                    modifier = Modifier.weight(1f)
                )
            } else if (m1 != null) {
                SpotifyQuickTile(
                    title = m1.title,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Brush.linearGradient(m1.gradientColors.map { Color(it) })),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    },
                    onClick = { onMixClick(m1) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                SpotifyQuickTile(
                    title = "Offline Mix",
                    icon = {
                        Box(modifier = Modifier.size(56.dp).background(SpotifySurfaceElevated), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = SpotifyGreen)
                        }
                    },
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 2: Second Playlist / Mix & Smart Generator (Custom Mix)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val p2 = playlists.getOrNull(1)
            val m2 = mixes.getOrNull(1) ?: mixes.firstOrNull()
            if (p2 != null) {
                SpotifyQuickTile(
                    title = p2.name,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(SpotifySurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(24.dp))
                            if (!p2.customCoverUri.isNullOrBlank()) {
                                AsyncImage(model = p2.customCoverUri, contentDescription = p2.name, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    },
                    onClick = { onPlaylistClick(p2) },
                    modifier = Modifier.weight(1f)
                )
            } else if (m2 != null) {
                SpotifyQuickTile(
                    title = m2.title,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Brush.linearGradient(m2.gradientColors.map { Color(it) })),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    },
                    onClick = { onMixClick(m2) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                SpotifyQuickTile(
                    title = "Daily Mix",
                    icon = {
                        Box(modifier = Modifier.size(56.dp).background(SpotifySurfaceElevated), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SpotifyGreen)
                        }
                    },
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
            }

            // Elegant Smart Playlist Generator tile
            SpotifyQuickTile(
                title = "Custom Mix",
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF8E24AA), Color(0xFF1DB954))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Smart Playlist Generator",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                onClick = onPlaylistGeneratorClick,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 3: Third Playlist / Album & Select Folder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val p3 = playlists.getOrNull(2)
            val a1 = albums.firstOrNull()
            val m3 = mixes.getOrNull(2)
            if (p3 != null) {
                SpotifyQuickTile(
                    title = p3.name,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(SpotifySurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(24.dp))
                            if (!p3.customCoverUri.isNullOrBlank()) {
                                AsyncImage(model = p3.customCoverUri, contentDescription = p3.name, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    },
                    onClick = { onPlaylistClick(p3) },
                    modifier = Modifier.weight(1f)
                )
            } else if (a1 != null) {
                SpotifyQuickTile(
                    title = a1.title,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(SpotifySurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Album, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(24.dp))
                            if (!a1.artworkUri.isNullOrBlank()) {
                                AsyncImage(model = a1.artworkUri, contentDescription = a1.title, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    },
                    onClick = { onAlbumClick(a1) },
                    modifier = Modifier.weight(1f)
                )
            } else if (m3 != null) {
                SpotifyQuickTile(
                    title = m3.title,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Brush.linearGradient(m3.gradientColors.map { Color(it) })),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    },
                    onClick = { onMixClick(m3) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                SpotifyQuickTile(
                    title = "Discover",
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF2E2E2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
            }

            SpotifyQuickTile(
                title = "Select Folder",
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF1E3A2B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Import",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                onClick = onImportFolderClick,
                modifier = Modifier.weight(1f)
            )
        }
    }

}

@Composable
private fun SpotifyQuickTile(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SpotifyCardBackground)
            .bounceClickable(minScale = 0.97f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {

        icon()
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

@Composable
private fun SpotifySmartMixCard(
    mix: SmartMix,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(mix.gradientColors.map { Color(it) }))
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = mix.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Floating Spotify Green play button
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = mix.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = mix.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpotifyPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            )
            if (!playlist.customCoverUri.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.customCoverUri,
                    contentDescription = playlist.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Playlist • Offline",
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyTextSecondary
        )
    }
}

@Composable
private fun SpotifyRecentlyPlayedCard(
    item: RecentlyPlayedItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .bounceClickable(minScale = 0.95f, onClick = onClick)
    ) {
        val defaultIcon = when (item.type) {
            RecentlyPlayedType.TRACK -> Icons.Default.MusicNote
            RecentlyPlayedType.ALBUM -> Icons.Default.Album
            RecentlyPlayedType.PLAYLIST -> Icons.AutoMirrored.Filled.QueueMusic
        }

        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = defaultIcon,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(38.dp)
            )
            val artworkModel = item.artworkUri ?: item.song?.let {
                "content://media/external/audio/albumart/${it.albumId}"
            }
            if (!artworkModel.isNullOrBlank()) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = item.title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = SpotifyTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpotifySquareTrackCard(
    song: Song,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .bounceClickable(minScale = 0.95f, onClick = onClick)
    ) {

        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(36.dp)
            )
            val artworkModel = song.artworkUri ?: "content://media/external/audio/albumart/${song.albumId}"
            AsyncImage(
                model = artworkModel,
                contentDescription = song.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodyMedium,
            color = SpotifyTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpotifyAlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(40.dp)
            )
            AsyncImage(
                model = album.artworkUri,
                contentDescription = album.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeDetailScreen(
    detail: HomeDetailView,
    uiState: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    isTrackCached: (Song) -> Boolean,
    modifier: Modifier = Modifier
) {
    if (detail is HomeDetailView.ArtistDetail && detail.artistProfile != null) {
        YouTubeMusicArtistView(
            artistProfile = detail.artistProfile,
            currentSongId = uiState.currentSongId,
            isPlaying = uiState.isPlaying,
            onBack = { onIntent(HomeIntent.CloseDetail) },
            onPlaySong = { song, queue -> onIntent(HomeIntent.PlaySong(song, queue)) },
            onShuffleArtist = { onIntent(HomeIntent.ToggleShuffle(detail.artistProfile.topSongs)) },
            onAlbumClick = { onIntent(HomeIntent.OpenAlbum(it)) },
            onSimilarArtistClick = { onIntent(HomeIntent.OpenArtistByName(it.name)) },
            onToggleFavorite = { onIntent(HomeIntent.ToggleFavorite(it)) },
            onAddToPlaylist = { onIntent(HomeIntent.AddSongObjectToPlaylist(0L, it)) },
            onPlayNext = { onIntent(HomeIntent.PlayNext(it)) },
            onAddToQueue = { onIntent(HomeIntent.AddToQueue(it)) },
            onGoToAlbum = { alb, art -> onIntent(HomeIntent.OpenAlbumByName(alb, art)) },
            isTrackCached = isTrackCached,
            isTrackFavorite = { uiState.isFavorite(it) },
            modifier = modifier
        )
        return
    }

    if (detail is HomeDetailView.AlbumDetail && detail.albumProfile != null) {
        YouTubeMusicAlbumView(
            albumProfile = detail.albumProfile,
            currentSongId = uiState.currentSongId,
            isPlaying = uiState.isPlaying,
            isShuffleEnabled = uiState.isShuffleEnabled,
            onBack = { onIntent(HomeIntent.CloseDetail) },
            onPlaySong = { song, index -> onIntent(HomeIntent.PlayAll(detail.albumProfile.tracks, index)) },
            onPlayAll = { onIntent(HomeIntent.PlayAll(detail.albumProfile.tracks, 0)) },
            onToggleShuffle = { onIntent(HomeIntent.ToggleShuffle(detail.albumProfile.tracks)) },
            onArtistClick = { onIntent(HomeIntent.OpenArtistByName(it)) },
            onToggleFavorite = { onIntent(HomeIntent.ToggleFavorite(it)) },
            onAddToPlaylist = { onIntent(HomeIntent.AddSongObjectToPlaylist(0L, it)) },
            onStartRadio = { onIntent(HomeIntent.StartRadio(it)) },
            onPlayNext = { onIntent(HomeIntent.PlayNext(it)) },
            onAddToQueue = { onIntent(HomeIntent.AddToQueue(it)) },
            onGoToArtist = { onIntent(HomeIntent.OpenArtistByName(it)) },
            isAlbumSaved = uiState.isAlbumSaved(detail.albumProfile),
            isTrackCached = isTrackCached,
            isTrackFavorite = { uiState.isFavorite(it) },
            modifier = modifier
        )
        return
    }

    val title: String
    val subtitle: String
    val songs: List<Song>
    val headerArtwork: @Composable () -> Unit

    when (detail) {
        is HomeDetailView.PlaylistDetail -> {
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
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
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
        is HomeDetailView.AlbumDetail -> {
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
        is HomeDetailView.LikedSongsDetail -> {
            title = "Liked Songs"
            subtitle = "${detail.songs.size} songs • Playlist"
            songs = detail.songs
            headerArtwork = {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF450AF5), Color(0xFF8E8EE5), Color(0xFFC4EFD9))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }
        is HomeDetailView.MixDetail -> {
            title = detail.mix.title
            subtitle = detail.mix.subtitle
            songs = detail.songs
            headerArtwork = {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(detail.mix.gradientColors.map { Color(it) })),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }
        is HomeDetailView.ArtistDetail -> {
            title = detail.artist.name
            subtitle = "${detail.songs.size} songs • Artist"
            songs = detail.songs
            headerArtwork = {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(SpotifyCardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(60.dp)
                    )
                }
            }
        }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var isEditMode by remember(detail) { mutableStateOf(false) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistForSongDialog by remember { mutableStateOf(false) }

    if (songToAddToPlaylist != null && !showCreatePlaylistForSongDialog) {
        val target = songToAddToPlaylist!!
        AddToPlaylistDialog(
            song = target,
            playlists = uiState.playlists,
            onDismiss = { songToAddToPlaylist = null },
            onCreateNewPlaylist = { showCreatePlaylistForSongDialog = true },
            onSelectPlaylist = { playlistId ->
                onIntent(HomeIntent.AddSongObjectToPlaylist(playlistId, target))
                songToAddToPlaylist = null
            }
        )
    }

    if (showCreatePlaylistForSongDialog && songToAddToPlaylist != null) {
        val target = songToAddToPlaylist!!
        CreatePlaylistDialog(
            onDismiss = {
                showCreatePlaylistForSongDialog = false
                songToAddToPlaylist = null
            },
            onCreate = { playlistName ->
                showCreatePlaylistForSongDialog = false
                onIntent(HomeIntent.CreatePlaylistAndAddSong(playlistName, target))
                songToAddToPlaylist = null
            }
        )
    }

    BackHandler(enabled = isEditMode) {
        isEditMode = false
    }

    if (showRenameDialog && detail is HomeDetailView.PlaylistDetail) {
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
                            onIntent(HomeIntent.RenamePlaylist(detail.playlist.id, newName.trim()))
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

    if (showAddSongsDialog && detail is HomeDetailView.PlaylistDetail) {
        AddSongsToPlaylistDialog(
            playlist = detail.playlist,
            allSongs = uiState.allSongs,
            currentPlaylistSongs = detail.songs,
            onAddSong = { songId -> onIntent(HomeIntent.AddSongToPlaylist(detail.playlist.id, songId)) },
            onRemoveSong = { songId -> onIntent(HomeIntent.RemoveSongFromPlaylist(detail.playlist.id, songId)) },
            onDismiss = { showAddSongsDialog = false }
        )
    }

    if (showDeleteConfirmDialog && detail is HomeDetailView.PlaylistDetail) {
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
                        onIntent(HomeIntent.DeletePlaylist(detail.playlist.id))
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

    val isSavedPlaylist = detail is HomeDetailView.PlaylistDetail && detail.playlist.id > 0
    val isUnsavedGeneratedMix = detail is HomeDetailView.PlaylistDetail && detail.playlist.id < 0

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
                onIntent(HomeIntent.CloseDetail)
            }
        },
        onPlaySong = { song, _ -> onIntent(HomeIntent.PlaySong(song, songs)) },
        onPlayAll = { onIntent(HomeIntent.PlayAll(songs, 0)) },
        onToggleShuffle = { onIntent(HomeIntent.ToggleShuffle(songs)) },
        onToggleFavorite = { onIntent(HomeIntent.ToggleFavorite(it)) },
        onSwipeRemove = if (isSavedPlaylist) {
            { song -> onIntent(HomeIntent.RemoveSongFromPlaylist((detail as HomeDetailView.PlaylistDetail).playlist.id, song.id)) }
        } else null,
        isCollectionSaved = isSavedPlaylist,
        isSongCached = isTrackCached,
        isTrackFavorite = { uiState.isFavorite(it) },
        isEditMode = isEditMode && isSavedPlaylist,
        onReorderSongs = if (isSavedPlaylist) {
            { orderedIds -> onIntent(HomeIntent.ReorderPlaylist((detail as HomeDetailView.PlaylistDetail).playlist.id, orderedIds)) }
        } else null,
        modifier = modifier,
        topBarActions = {
            if (isSavedPlaylist) {
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
            if (detail is HomeDetailView.PlaylistDetail && songs.isNotEmpty()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val shareUrl = com.mousikify.core.model.DeepLinkCodec.encodePlaylistUrl(detail.playlist.name, songs)
                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, detail.playlist.name)
                            putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                        }
                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Share playlist via"))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share playlist",
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (isSavedPlaylist) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showAddSongsDialog = true },
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
            } else if (isUnsavedGeneratedMix) {
                val mixDetail = detail as HomeDetailView.PlaylistDetail
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onIntent(HomeIntent.SavePlaylistToLibrary(mixDetail.playlist.name, songs)) },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add to Library",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Library", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SpotifyGreen)
                }
            } else if (detail is HomeDetailView.MixDetail && detail.mix.id == "discover_mix") {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onIntent(HomeIntent.RefreshDiscoverMix) },
                    enabled = !uiState.isDiscoverRefreshing,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SpotifyTextPrimary,
                        disabledContentColor = SpotifyTextSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    if (uiState.isDiscoverRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = SpotifyGreen,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh mix",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isDiscoverRefreshing) "Refreshing..." else "Refresh mix",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SpotifyTextPrimary
                    )
                }
            }
        },
        emptyContent = {
            if (detail is HomeDetailView.PlaylistDetail) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("This playlist is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SpotifyTextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showAddSongsDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add songs", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No songs in this collection", color = SpotifyTextSecondary)
                }
            }
        },
        songTrailingContent = { song ->
            if (detail is HomeDetailView.PlaylistDetail) {
                AnimatedVisibility(
                    visible = isEditMode,
                    enter = fadeIn(animationSpec = tween(150)) + expandHorizontally(),
                    exit = fadeOut(animationSpec = tween(150)) + shrinkHorizontally()
                ) {
                    IconButton(
                        onClick = { onIntent(HomeIntent.RemoveSongFromPlaylist(detail.playlist.id, song.id)) },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove from playlist",
                            tint = Color(0xFFE22134),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else if (detail is HomeDetailView.MixDetail && detail.mix.id == "discover_mix") {
                IconButton(
                    onClick = { songToAddToPlaylist = song },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    )
}

