package com.mousikify.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
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

                    // Pills: "All", "Music"
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

                    // Settings / EQ button
                    IconButton(onClick = onNavigateToEqualizer) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = SpotifyTextSecondary
                        )
                    }
                }
            }

            // Greeting
            item {
                Text(
                    text = uiState.greeting,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Quick-Jump 2-Column Grid (Spotify style)
            item {
                QuickJumpSection(
                    likedCount = uiState.likedCount,
                    totalSongCount = uiState.totalSongCount,
                    onLikedClick = { onIntent(HomeIntent.PlayLikedSongs) },
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

            // Recently Played Section
            if (uiState.recentlyPlayed.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Recently played",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.recentlyPlayed) { song ->
                            SpotifySquareTrackCard(
                                song = song,
                                onClick = { onIntent(HomeIntent.PlaySong(song, uiState.recentlyPlayed)) }
                            )
                        }
                    }
                }
            }

            // Forgotten Favorites Section
            if (uiState.forgottenFavorites.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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

                items(uiState.forgottenFavorites.take(6)) { song ->
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
private fun QuickJumpSection(
    likedCount: Int,
    totalSongCount: Int,
    onLikedClick: () -> Unit,
    onImportFolderClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Liked Songs Quick Tile (Purple gradient heart)
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

            // Import Folder Quick Tile
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
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                onClick = onImportFolderClick,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Local Downloads Tile
            SpotifyQuickTile(
                title = "Downloaded Files",
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(SpotifySurfaceElevated),
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

            // Total Library Tracks Tile
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
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                onClick = {},
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
        Spacer(modifier = Modifier.height(10.dp))
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
