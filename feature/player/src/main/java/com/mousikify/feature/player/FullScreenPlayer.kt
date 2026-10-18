package com.mousikify.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.component.MarqueeText
import com.mousikify.core.designsystem.palette.rememberDynamicGradientBrush
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.RepeatMode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility

@Composable
fun FullScreenPlayerRoute(
    onCollapse: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    FullScreenPlayer(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onCollapse = onCollapse,
        onNavigateToEqualizer = onNavigateToEqualizer
    )
}

@Composable
fun FullScreenPlayer(
    uiState: PlayerUiState,
    onIntent: (PlayerIntent) -> Unit,
    onCollapse: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCollapse)

    val song = uiState.currentSong ?: return
    val gradientBrush = rememberDynamicGradientBrush(seedColor = uiState.gradientColor)

    var isUserDraggingSlider by remember { mutableStateOf(false) }
    var userSliderPosition by remember { mutableFloatStateOf(0f) }

    val lyricsListState = rememberLazyListState()

    // Smooth auto-scroll lyrics
    LaunchedEffect(uiState.activeLyricIndex) {
        if (uiState.activeLyricIndex >= 0 && uiState.lyrics.isNotEmpty()) {
            lyricsListState.animateScrollToItem(
                index = (uiState.activeLyricIndex - 1).coerceAtLeast(0)
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Spotify Top Header: Chevron Down + Playlist Context + Equalizer/Menu
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = SpotifyTextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM YOUR LIBRARY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = SpotifyTextSecondary
                    )
                    Text(
                        text = song.albumName.ifBlank { "Local Offline Tracks" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onNavigateToEqualizer) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizer",
                        tint = SpotifyTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Center Album Artwork Card (Large square, rounded corners, subtle elevation)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SpotifyCardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(80.dp)
                    )
                    val artworkModel = song.artworkUri ?: "content://media/external/audio/albumart/${song.albumId}"
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Title, Artist, & Favorite Heart (Spotify style)
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = SpotifyTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.artistName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpotifyTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onIntent(PlayerIntent.ToggleFavorite) }) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) SpotifyGreen else SpotifyTextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Scrubber / Progress Bar & Exact Spotify Timestamps
        item {
            Spacer(modifier = Modifier.height(14.dp))
            val currentPos = if (isUserDraggingSlider) userSliderPosition.toLong() else uiState.currentPositionMs
            val duration = uiState.durationMs.coerceAtLeast(1L)

            Slider(
                value = (if (isUserDraggingSlider) userSliderPosition else currentPos.toFloat()).coerceIn(0f, duration.toFloat()),
                onValueChange = {
                    isUserDraggingSlider = true
                    userSliderPosition = it
                },
                onValueChangeFinished = {
                    isUserDraggingSlider = false
                    onIntent(PlayerIntent.SeekTo(userSliderPosition.toLong()))
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentPos),
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyTextSecondary
                )
                Text(
                    text = formatMs(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyTextSecondary
                )
            }
        }

        // Spotify Playback Controls: Shuffle, Prev, White Circle Play, Next, Repeat
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Icon
                IconButton(onClick = { onIntent(PlayerIntent.ToggleShuffle) }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (uiState.isShuffleEnabled) SpotifyGreen else Color(0xFF888888),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous Track
                IconButton(onClick = { onIntent(PlayerIntent.SkipToPrevious) }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = SpotifyTextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Signature Spotify Big White Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onIntent(PlayerIntent.TogglePlayPause) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next Track
                IconButton(onClick = { onIntent(PlayerIntent.SkipToNext) }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = SpotifyTextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Repeat Mode
                IconButton(onClick = { onIntent(PlayerIntent.CycleRepeatMode) }) {
                    Icon(
                        imageVector = if (uiState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (uiState.repeatMode != RepeatMode.OFF) SpotifyGreen else Color(0xFF888888),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Spotify Bottom Actions Row: Devices, Share, Queue
        item {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = "Connect to device",
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Synced Lyrics Card (Spotify Signature Design)
        if (uiState.lyrics.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = uiState.gradientColor.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lyrics",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextPrimary
                            )
                            Text(
                                text = "TAP TO JUMP",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SpotifyTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            state = lyricsListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            itemsIndexed(uiState.lyrics) { index, line ->
                                val isActive = index == uiState.activeLyricIndex
                                Text(
                                    text = line.text,
                                    style = if (isActive) MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp) else MaterialTheme.typography.titleMedium,
                                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onIntent(PlayerIntent.SeekToLyric(line.timestampMs)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
