package com.mousikify.feature.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    HomeScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(SpotifyBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SpotifyGreen)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Top Greeting
        item {
            Text(
                text = uiState.greeting,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 16.dp)
            )
        }

        // 2-Column Quick Jump Grid
        item {
            QuickJumpSection(
                likedCount = uiState.likedCount,
                playlists = uiState.playlists,
                onLikedClick = { onIntent(HomeIntent.PlayLikedSongs) }
            )
        }

        // Recently Played Carousel
        if (uiState.recentlyPlayed.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Recently played",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.recentlyPlayed) { song ->
                        RecentTrackCard(
                            song = song,
                            onClick = { onIntent(HomeIntent.PlaySong(song, uiState.recentlyPlayed)) }
                        )
                    }
                }
            }
        }

        // Forgotten Favorites (Smart query)
        if (uiState.forgottenFavorites.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Forgotten Favorites",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Rediscover the tracks you loved the most",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotifyTextSecondary
                    )
                }
            }

            items(uiState.forgottenFavorites.take(8)) { song ->
                TrackListItem(
                    song = song,
                    isPlaying = song.id == uiState.currentSongId && uiState.isPlaying,
                    onClick = { onIntent(HomeIntent.PlaySong(song, uiState.forgottenFavorites)) },
                    onFavoriteToggle = { onIntent(HomeIntent.ToggleFavorite(song)) }
                )
            }
        }
    }
}

@Composable
private fun QuickJumpSection(
    likedCount: Int,
    playlists: List<com.mousikify.core.model.Playlist>,
    onLikedClick: () -> Unit
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
            // Liked Songs Quick Tile
            QuickTile(
                title = "Liked Songs",
                subtitle = "$likedCount tracks",
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(SpotifyGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Liked",
                            tint = Color.White
                        )
                    }
                },
                onClick = onLikedClick,
                modifier = Modifier.weight(1f)
            )

            // First Playlist or General library tile
            val firstPlaylist = playlists.firstOrNull()
            QuickTile(
                title = firstPlaylist?.name ?: "All Playlists",
                subtitle = "Playlist",
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(SpotifySurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Playlists",
                            tint = SpotifyTextSecondary
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
private fun QuickTile(
    title: String,
    subtitle: String,
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
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SpotifyTextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecentTrackCard(
    song: Song,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground)
        ) {
            AsyncImage(
                model = "content://media/external/audio/albumart/${song.albumId}",
                contentDescription = song.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
