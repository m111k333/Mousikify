package com.mousikify.feature.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
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
import com.mousikify.core.designsystem.component.TrackListItem
import com.mousikify.core.designsystem.theme.SpotifyBackground
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.Album
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.Song

@Composable
fun HomeRoute(
    onNavigateToEqualizer: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
        onNavigateToEqualizer = onNavigateToEqualizer
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    onPickFolder: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = uiState.activeDetail != null) {
        onIntent(HomeIntent.CloseDetail)
    }

    if (uiState.activeDetail != null) {
        HomeDetailScreen(
            detail = uiState.activeDetail,
            uiState = uiState,
            onIntent = onIntent,
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // Spotify Top Header: Avatar + Filter Pills + Action Icons
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 12.dp, top = 24.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF3B5998), Color(0xFF1DB954)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "M",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

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

                        // Rescan icon button
                        IconButton(onClick = { onIntent(HomeIntent.Rescan) }) {
                            if (uiState.isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = SpotifyGreen,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Rescan library",
                                    tint = SpotifyTextSecondary
                                )
                            }
                        }

                        // Equalizer / Settings button
                        IconButton(onClick = onNavigateToEqualizer) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "Equalizer",
                                tint = SpotifyTextSecondary
                            )
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
                        totalSongCount = uiState.totalSongCount,
                        onLikedClick = { onIntent(HomeIntent.OpenLikedSongs) },
                        onPlaylistClick = { onIntent(HomeIntent.OpenPlaylist(it)) },
                        onMixClick = { onIntent(HomeIntent.OpenMix(it)) },
                        onImportFolderClick = onPickFolder
                    )
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
            if (uiState.smartMixes.isNotEmpty() && (selectedPill == "All" || selectedPill == "Music")) {
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

            // "Recently Played" Carousel
            if (uiState.recentlyPlayed.isNotEmpty() && selectedPill != "Playlists") {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "Recently played",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.recentlyPlayed, key = { it.id }) { song ->
                            SpotifySquareTrackCard(
                                song = song,
                                onClick = { onIntent(HomeIntent.PlaySong(song, uiState.recentlyPlayed)) }
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
                    TrackListItem(
                        song = song,
                        isPlaying = song.id == uiState.currentSongId && uiState.isPlaying,
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
                    TextButton(onClick = { onIntent(HomeIntent.DismissStatusMessage) }) {
                        Text("OK", color = SpotifyGreen)
                    }
                }
            ) {
                Text(uiState.statusMessage)
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
    totalSongCount: Int,
    onLikedClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onMixClick: (SmartMix) -> Unit,
    onImportFolderClick: () -> Unit,
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
                            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(24.dp))
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

        // Row 2: Second Playlist / Mix & Third Playlist / Mix
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
                            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(24.dp))
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

            val p3 = playlists.getOrNull(2)
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
                            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(24.dp))
                            if (!p3.customCoverUri.isNullOrBlank()) {
                                AsyncImage(model = p3.customCoverUri, contentDescription = p3.name, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    },
                    onClick = { onPlaylistClick(p3) },
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
                    title = "Heavy Rotation",
                    icon = {
                        Box(modifier = Modifier.size(56.dp).background(SpotifySurfaceElevated), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SpotifyGreen)
                        }
                    },
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 3: Downloaded Files & Select Folder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpotifyQuickTile(
                title = "$totalSongCount Songs",
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF2E2E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Downloads",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                onClick = onImportFolderClick,
                modifier = Modifier.weight(1f)
            )

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
            .clickable(onClick = onClick),
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SPOTIFY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f)
                    )
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
                imageVector = Icons.Default.QueueMusic,
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
private fun SpotifySquareTrackCard(
    song: Song,
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
    modifier: Modifier = Modifier
) {
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
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onIntent(HomeIntent.CloseDetail) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SpotifyTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // Header section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        headerArtwork()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Bar: Shuffle + Big Play Button
                        if (songs.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onIntent(HomeIntent.ToggleShuffle(songs)) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (uiState.isShuffleEnabled) SpotifyGreen else Color(0xFF888888),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onIntent(HomeIntent.PlayAll(songs, 0)) },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(SpotifyGreen)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play all",
                                        tint = Color.Black,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Song list
                if (songs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No songs in this collection", color = SpotifyTextSecondary)
                        }
                    }
                } else {
                    items(songs, key = { it.id }) { song ->
                        TrackListItem(
                            song = song,
                            isPlaying = song.id == uiState.currentSongId && uiState.isPlaying,
                            onClick = { onIntent(HomeIntent.PlaySong(song, songs)) },
                            onFavoriteToggle = { onIntent(HomeIntent.ToggleFavorite(song)) }
                        )
                    }
                }
            }
        }
    }
}
