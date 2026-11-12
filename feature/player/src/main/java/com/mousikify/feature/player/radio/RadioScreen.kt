package com.mousikify.feature.player.radio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.component.MediaDetailView
import com.mousikify.core.designsystem.theme.SpotifyBackground
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.media.recommendation.DeezerDiscoveryDepth
import com.mousikify.core.media.recommendation.LastFmRelevance
import com.mousikify.core.media.recommendation.RadioRefreshConfig
import com.mousikify.core.media.recommendation.RadioSource
import com.mousikify.core.media.recommendation.YouTubeRadioMode
import com.mousikify.core.model.Song
import kotlinx.coroutines.delay

@Composable
fun RadioRoute(
    seedSong: Song,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RadioViewModel = hiltViewModel()
) {
    LaunchedEffect(seedSong.id) {
        viewModel.onIntent(RadioIntent.Initialize(seedSong))
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RadioScreen(
        uiState = uiState,
        seedSong = seedSong,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        isSongCached = viewModel::isSongCached,
        modifier = modifier
    )
}

@Composable
fun RadioScreen(
    uiState: RadioUiState,
    seedSong: Song,
    onIntent: (RadioIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isSongCached: (Song) -> Boolean = { it.isLocal }
) {
    BackHandler(onBack = onBack)

    val currentSeed = uiState.seedSong ?: seedSong
    val title = "${currentSeed.title} Radio"
    val subtitle = if (uiState.isLoading) {
        "Finding similar tracks to ${currentSeed.title}..."
    } else {
        "Based on ${currentSeed.title} • ${currentSeed.artistName} • ${uiState.songs.size} songs"
    }

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(3000L)
            onIntent(RadioIntent.DismissStatusMessage)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(SpotifyBackground)) {
        MediaDetailView(
            title = title,
            subtitle = subtitle,
            songs = uiState.songs,
            currentSongId = uiState.currentSongId,
            isPlaying = uiState.isPlaying,
            isShuffleEnabled = uiState.isShuffleEnabled,
            onBack = onBack,
            onPlaySong = { song, index -> onIntent(RadioIntent.PlaySong(song, index)) },
            onPlayAll = { onIntent(RadioIntent.PlayAll) },
            onToggleShuffle = { onIntent(RadioIntent.ToggleShuffle) },
            onToggleFavorite = { song -> onIntent(RadioIntent.ToggleFavorite(song)) },
            isTrackFavorite = { uiState.isFavorite(it) },
            isCollectionSaved = uiState.isSavedToLibrary,
            isSongCached = isSongCached,
            headerArtwork = {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF1E3264),
                                    Color(0xFF006450),
                                    SpotifyCardBackground
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val cover = currentSeed.artworkUri ?: "content://media/external/audio/albumart/${currentSeed.albumId}"
                    AsyncImage(
                        model = cover,
                        contentDescription = currentSeed.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Floating radio indicator badge on top of artwork
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = "Song Radio",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            actionBarActions = {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onIntent(RadioIntent.SaveToLibrary) },
                    enabled = !uiState.isSavedToLibrary && uiState.songs.isNotEmpty(),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (uiState.isSavedToLibrary) SpotifyGreen else Color.White.copy(alpha = 0.35f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (uiState.isSavedToLibrary) SpotifyGreen else SpotifyTextPrimary,
                        disabledContentColor = if (uiState.isSavedToLibrary) SpotifyGreen else SpotifyTextSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isSavedToLibrary) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = if (uiState.isSavedToLibrary) SpotifyGreen else SpotifyTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isSavedToLibrary) "Saved to Library" else "Save to Library",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (uiState.isSavedToLibrary) SpotifyGreen else SpotifyTextPrimary
                    )
                }
                if (uiState.songs.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onIntent(RadioIntent.OpenRefreshDialog) },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-tune Radio",
                            tint = SpotifyTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            emptyContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = SpotifyGreen,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tuning into ${currentSeed.title} Radio...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyTextSecondary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = SpotifyTextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Couldn't load radio mix",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Check your connection and try again",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { onIntent(RadioIntent.Retry) },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, SpotifyGreen),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SpotifyGreen
                            )
                        ) {
                            Text("Retry", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        )

        // Floating feedback snackbar
        if (uiState.statusMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = SpotifySurfaceElevated,
                contentColor = SpotifyTextPrimary,
                action = {
                    TextButton(onClick = { onIntent(RadioIntent.DismissStatusMessage) }) {
                        Text("OK", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                    }
                }
            ) {
                Text(uiState.statusMessage)
            }
        }

        if (uiState.showRefreshDialog) {
            RadioRefreshDialog(
                config = uiState.refreshConfig,
                onDismiss = { onIntent(RadioIntent.DismissRefreshDialog) },
                onConfirm = { config -> onIntent(RadioIntent.ConfirmRefresh(config)) }
            )
        }
    }
}

@Composable
fun RadioRefreshDialog(
    config: RadioRefreshConfig,
    onDismiss: () -> Unit,
    onConfirm: (RadioRefreshConfig) -> Unit
) {
    var currentConfig by remember(config) { mutableStateOf(config) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpotifyCardBackground,
        title = {
            Text(
                text = "Radio Mix Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SpotifyTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Source selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DISCOVERY SOURCE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioOptionChip(
                            text = "Last.fm",
                            selected = currentConfig.source == RadioSource.LAST_FM,
                            onClick = { currentConfig = currentConfig.copy(source = RadioSource.LAST_FM) },
                            modifier = Modifier.weight(1f)
                        )
                        RadioOptionChip(
                            text = "Deezer",
                            selected = currentConfig.source == RadioSource.DEEZER,
                            onClick = { currentConfig = currentConfig.copy(source = RadioSource.DEEZER) },
                            modifier = Modifier.weight(1f)
                        )
                        RadioOptionChip(
                            text = "YouTube",
                            selected = currentConfig.source == RadioSource.YOUTUBE_MUSIC,
                            onClick = { currentConfig = currentConfig.copy(source = RadioSource.YOUTUBE_MUSIC) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. Source-specific options
                when (currentConfig.source) {
                    RadioSource.LAST_FM -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "RELEVANCE TO REFERENCE SONG",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextSecondary,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioOptionChip(
                                    text = "High Match",
                                    selected = currentConfig.relevance == LastFmRelevance.HIGH_MATCH,
                                    onClick = { currentConfig = currentConfig.copy(relevance = LastFmRelevance.HIGH_MATCH) },
                                    modifier = Modifier.weight(1f)
                                )
                                RadioOptionChip(
                                    text = "Balanced",
                                    selected = currentConfig.relevance == LastFmRelevance.BALANCED,
                                    onClick = { currentConfig = currentConfig.copy(relevance = LastFmRelevance.BALANCED) },
                                    modifier = Modifier.weight(1f)
                                )
                                RadioOptionChip(
                                    text = "Discovery",
                                    selected = currentConfig.relevance == LastFmRelevance.DEEP_DISCOVERY,
                                    onClick = { currentConfig = currentConfig.copy(relevance = LastFmRelevance.DEEP_DISCOVERY) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    RadioSource.DEEZER -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "DISCOVERY DEPTH",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextSecondary,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioOptionChip(
                                    text = "Top Hits",
                                    selected = currentConfig.deezerDepth == DeezerDiscoveryDepth.TOP_HITS,
                                    onClick = { currentConfig = currentConfig.copy(deezerDepth = DeezerDiscoveryDepth.TOP_HITS) },
                                    modifier = Modifier.weight(1f)
                                )
                                RadioOptionChip(
                                    text = "Balanced",
                                    selected = currentConfig.deezerDepth == DeezerDiscoveryDepth.BALANCED,
                                    onClick = { currentConfig = currentConfig.copy(deezerDepth = DeezerDiscoveryDepth.BALANCED) },
                                    modifier = Modifier.weight(1f)
                                )
                                RadioOptionChip(
                                    text = "Deep Cuts",
                                    selected = currentConfig.deezerDepth == DeezerDiscoveryDepth.DEEP_CUTS,
                                    onClick = { currentConfig = currentConfig.copy(deezerDepth = DeezerDiscoveryDepth.DEEP_CUTS) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    RadioSource.YOUTUBE_MUSIC -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SELECTION MODE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextSecondary,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioOptionChip(
                                    text = "Top Queue",
                                    selected = currentConfig.youtubeMode == YouTubeRadioMode.TOP_QUEUE,
                                    onClick = { currentConfig = currentConfig.copy(youtubeMode = YouTubeRadioMode.TOP_QUEUE) },
                                    modifier = Modifier.weight(1f)
                                )
                                RadioOptionChip(
                                    text = "Fresh Shuffle",
                                    selected = currentConfig.youtubeMode == YouTubeRadioMode.FRESH_SHUFFLE,
                                    onClick = { currentConfig = currentConfig.copy(youtubeMode = YouTubeRadioMode.FRESH_SHUFFLE) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 3. Artist Variety
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ARTIST VARIETY",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioOptionChip(
                            text = "Max 1",
                            selected = currentConfig.maxPerArtist == 1,
                            onClick = { currentConfig = currentConfig.copy(maxPerArtist = 1) },
                            modifier = Modifier.weight(1f)
                        )
                        RadioOptionChip(
                            text = "Max 2",
                            selected = currentConfig.maxPerArtist == 2,
                            onClick = { currentConfig = currentConfig.copy(maxPerArtist = 2) },
                            modifier = Modifier.weight(1f)
                        )
                        RadioOptionChip(
                            text = "Max 3",
                            selected = currentConfig.maxPerArtist == 3,
                            onClick = { currentConfig = currentConfig.copy(maxPerArtist = 3) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentConfig) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Refresh Mix",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Cancel",
                    color = SpotifyTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    )
}

@Composable
private fun RadioOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) SpotifyGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) SpotifyGreen else Color.White.copy(alpha = 0.15f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) SpotifyGreen else SpotifyTextSecondary,
                maxLines = 1
            )
        }
    }
}
