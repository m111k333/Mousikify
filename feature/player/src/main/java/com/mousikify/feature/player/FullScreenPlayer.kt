package com.mousikify.feature.player

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.component.AddToPlaylistDialog
import com.mousikify.core.designsystem.component.CreatePlaylistDialog
import com.mousikify.core.designsystem.component.MarqueeText
import com.mousikify.core.designsystem.component.ModernSongSlider
import com.mousikify.core.designsystem.palette.rememberDynamicGradientBrush
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.LyricLine
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.RepeatMode
import com.mousikify.core.model.Song

@Composable
fun FullScreenPlayerRoute(
    onCollapse: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
    modifier: Modifier = Modifier,
    onDragDelta: (Float) -> Unit = {},
    onDragFinished: (Float) -> Unit = {}
) {
    BackHandler(onBack = onCollapse)

    val song = uiState.currentSong ?: return
    val gradientBrush = rememberDynamicGradientBrush(seedColor = uiState.gradientColor)

    var showCoverLyrics by rememberSaveable { mutableStateOf(false) }
    var showEditLyricsDialog by remember { mutableStateOf(false) }

    val coverLyricsListState = rememberLazyListState()
    val bottomLyricsListState = rememberLazyListState()

    // Smooth auto-scroll embedded album-cover lyrics & bottom lyrics card as active phrase advances
    LaunchedEffect(uiState.activeLyricIndex, showCoverLyrics, song.id) {
        if (uiState.activeLyricIndex >= 0 && uiState.lyrics.isNotEmpty()) {
            val targetIndex = (uiState.activeLyricIndex - 1).coerceAtLeast(0)
            if (showCoverLyrics) {
                coverLyricsListState.animateScrollToItem(index = targetIndex)
            }
            bottomLyricsListState.animateScrollToItem(index = targetIndex)
        }
    }

    if (showEditLyricsDialog) {
        EditLyricsDialog(
            songTitle = song.title,
            initialText = uiState.rawLyricsText,
            onDismiss = { showEditLyricsDialog = false },
            onSave = { updatedLyrics ->
                onIntent(PlayerIntent.SaveCustomLyrics(updatedLyrics))
                showEditLyricsDialog = false
                if (updatedLyrics.isNotBlank()) {
                    showCoverLyrics = true
                }
            }
        )
    }

    if (uiState.showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            song = song,
            playlists = uiState.playlists,
            onDismiss = { onIntent(PlayerIntent.ShowAddToPlaylistDialog(false)) },
            onCreateNewPlaylist = {
                onIntent(PlayerIntent.ShowAddToPlaylistDialog(false))
                onIntent(PlayerIntent.ShowCreatePlaylistDialog(true))
            },
            onSelectPlaylist = { playlistId ->
                onIntent(PlayerIntent.AddCurrentSongToPlaylist(playlistId))
            }
        )
    }

    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { onIntent(PlayerIntent.ShowCreatePlaylistDialog(false)) },
            onCreate = { name ->
                onIntent(PlayerIntent.CreatePlaylistAndAddCurrentSong(name))
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(com.mousikify.core.designsystem.theme.SpotifyBlack)
            .background(gradientBrush),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Spotify Top Header: Chevron Down + Playlist Context + Equalizer/Menu
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 12.dp, bottom = 20.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount)
                            },
                            onDragEnd = { onDragFinished(0f) },
                            onDragCancel = { onDragFinished(0f) }
                        )
                    },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.mousikify.core.designsystem.component.TrackSourceBadge(
                            isLocal = song.isLocal,
                            isCachedOffline = uiState.isCurrentTrackCached,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = if (song.isRemoteStream) "YOUTUBE MUSIC STREAM" else "PLAYING FROM YOUR LIBRARY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SpotifyTextSecondary
                        )
                    }
                    Text(
                        text = song.albumName.ifBlank { if (song.isRemoteStream) "YouTube Music" else "Local Offline Tracks" },
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

        // Center Album Artwork Card with Embedded Lyrics Overlay (Tap Cover or Lyrics Pill to Toggle, Drag Down to Collapse)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                val artworkModel = song.artworkUri ?: "content://media/external/audio/albumart/${song.albumId}"

                val coverGestureModifier = if (!showCoverLyrics) {
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            val velocityTracker = VelocityTracker()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                velocityTracker.resetTracking()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)
                                var totalDragY = 0f
                                var isDragging = false
                                val touchSlop = viewConfiguration.touchSlop
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    if (!change.pressed) {
                                        if (isDragging) {
                                            val velocityY = velocityTracker.calculateVelocity().y
                                            onDragFinished(velocityY)
                                        } else if (abs(totalDragY) < touchSlop) {
                                            showCoverLyrics = true
                                        }
                                        break
                                    }
                                    val deltaY = change.position.y - down.position.y
                                    totalDragY = deltaY
                                    if (!isDragging && deltaY > touchSlop) {
                                        isDragging = true
                                        change.consume()
                                        onDragDelta(deltaY)
                                    } else if (isDragging) {
                                        change.consume()
                                        onDragDelta(change.positionChange().y)
                                    }
                                }
                            }
                        }
                } else {
                    Modifier.fillMaxSize()
                }

                Box(
                    modifier = coverGestureModifier
                        .background(SpotifyCardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    // Fallback music note icon + Album Cover Artwork
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(80.dp)
                    )
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Crossfade(
                        targetState = showCoverLyrics,
                        animationSpec = tween(durationMillis = 240),
                        label = "AlbumCoverLyricsCrossfade"
                    ) { isLyricsVisible ->
                        if (!isLyricsVisible) {
                            // Album Cover Mode: Show sleek "Lyrics" pill button in bottom-right corner
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Surface(
                                    onClick = { showCoverLyrics = true },
                                    shape = RoundedCornerShape(50),
                                    color = Color.Black.copy(alpha = 0.72f),
                                    modifier = Modifier.border(
                                        width = 1.dp,
                                        color = if (uiState.lyrics.isNotEmpty()) SpotifyGreen.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(50)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lyrics,
                                            contentDescription = "Show Embedded Lyrics",
                                            tint = if (uiState.lyrics.isNotEmpty()) SpotifyGreen else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Lyrics",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        } else {
                            // Embedded Album Cover Lyrics Mode: Spotify-style real-time phrase & word highlighting
                            val isSyncedLyrics = uiState.lyrics.firstOrNull()?.isSynced == true
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.82f),
                                                uiState.gradientColor.copy(alpha = 0.88f),
                                                Color.Black.copy(alpha = 0.90f)
                                            )
                                        )
                                    )
                                    .padding(18.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Top Bar inside Album Cover Lyrics
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color.White.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = when {
                                                    uiState.lyrics.isEmpty() -> "OFFLINE LYRICS"
                                                    isSyncedLyrics -> "SYNCED LYRICS"
                                                    else -> "AUTO-SYNC LYRICS"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.8.sp
                                                ),
                                                color = if (uiState.lyrics.isNotEmpty()) SpotifyGreen else SpotifyTextSecondary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { showEditLyricsDialog = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Offline Lyrics",
                                                    tint = Color.White.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                onClick = { showCoverLyrics = false },
                                                shape = RoundedCornerShape(50),
                                                color = Color.White.copy(alpha = 0.16f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Album,
                                                        contentDescription = "Show Album Art",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = "Cover",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (uiState.lyrics.isEmpty()) {
                                        // Empty State inside Album Cover with 1-tap Offline Lyrics addition
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable { showCoverLyrics = false },
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lyrics,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.45f),
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "No embedded lyrics found in this audio file",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = Color.White.copy(alpha = 0.85f),
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Paste plain text or .lrc lyrics to save them offline with auto-highlighting",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SpotifyTextSecondary,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Button(
                                                onClick = { showEditLyricsDialog = true },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = SpotifyGreen,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Add Offline Lyrics",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            state = coverLyricsListState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(vertical = 36.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            itemsIndexed(
                                                items = uiState.lyrics,
                                                key = { index, line -> "${index}_${line.timestampMs}" }
                                            ) { index, line ->
                                                SpotifyLyricPhrase(
                                                    line = line,
                                                    lineIndex = index,
                                                    activeLyricIndex = uiState.activeLyricIndex,
                                                    currentPositionMs = if (index == uiState.activeLyricIndex && line.words.isNotEmpty()) uiState.currentPositionMs else 0L,
                                                    onClick = {
                                                        onIntent(PlayerIntent.SeekToLyric(line.timestampMs))
                                                    }
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
                IconButton(onClick = { showCoverLyrics = !showCoverLyrics }) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Toggle Cover Lyrics",
                        tint = if (showCoverLyrics) SpotifyGreen else SpotifyTextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = { onIntent(PlayerIntent.ShowAddToPlaylistDialog(true)) }) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = SpotifyTextPrimary,
                        modifier = Modifier.size(28.dp)
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
                val context = LocalContext.current
                IconButton(onClick = {
                    val shareUrl = if (song.remoteVideoId != null) {
                        "https://www.youtube.com/watch?v=${song.remoteVideoId}"
                    } else {
                        "https://www.youtube.com/results?search_query=${Uri.encode("${song.title} ${song.artistName}")}"
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, song.title)
                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Share song via")
                    context.startActivity(shareIntent)
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Modern Song Scrubber & Interactive Timestamps
        item {
            Spacer(modifier = Modifier.height(14.dp))
            ModernSongSlider(
                currentPositionMs = uiState.currentPositionMs,
                durationMs = uiState.durationMs,
                onSeek = { seekPos ->
                    onIntent(PlayerIntent.SeekTo(seekPos))
                },
                modifier = Modifier.fillMaxWidth()
            )
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

                // Signature Spotify Big White Play/Pause Button (with Buffering indicator)
                val isBuffering = uiState.playbackStatus is com.mousikify.core.model.PlaybackStatus.Preparing ||
                        uiState.playbackStatus is com.mousikify.core.model.PlaybackStatus.Buffering
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onIntent(PlayerIntent.TogglePlayPause) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = Color.Black,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
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

        // Bottom Synced Lyrics Card (Spotify Signature Design)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "TAP TO JUMP",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SpotifyTextSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { showEditLyricsDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Offline Lyrics",
                                        tint = SpotifyTextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            state = bottomLyricsListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            itemsIndexed(
                                items = uiState.lyrics,
                                key = { index, line -> "bottom_${index}_${line.timestampMs}" }
                            ) { index, line ->
                                SpotifyLyricPhrase(
                                    line = line,
                                    lineIndex = index,
                                    activeLyricIndex = uiState.activeLyricIndex,
                                    currentPositionMs = if (index == uiState.activeLyricIndex && line.words.isNotEmpty()) uiState.currentPositionMs else 0L,
                                    onClick = {
                                        onIntent(PlayerIntent.SeekToLyric(line.timestampMs))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.statusMessage != null) {
        Snackbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            containerColor = SpotifySurfaceElevated,
            contentColor = SpotifyTextPrimary,
            action = {
                TextButton(onClick = { onIntent(PlayerIntent.DismissStatusMessage) }) {
                    Text("OK", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Text(uiState.statusMessage)
        }
    }
    }
}



/**
 * Renders a single lyric phrase with Spotify-style real-time line & intra-phrase word-by-word
 * illumination as the song progresses.
 */
@Composable
private fun SpotifyLyricPhrase(
    line: LyricLine,
    lineIndex: Int,
    activeLyricIndex: Int,
    currentPositionMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = lineIndex == activeLyricIndex
    val isPast = activeLyricIndex >= 0 && lineIndex < activeLyricIndex

    if (isActive && line.words.isNotEmpty()) {
        val annotatedPhrase = buildAnnotatedString {
            line.words.forEachIndexed { idx, word ->
                val isWordSung = currentPositionMs >= word.timestampMs
                withStyle(
                    style = SpanStyle(
                        color = if (isWordSung) Color.White else Color.White.copy(alpha = 0.52f),
                        fontWeight = FontWeight.ExtraBold
                    )
                ) {
                    append(word.text)
                }
                if (idx < line.words.lastIndex) {
                    append(" ")
                }
            }
        }
        Text(
            text = annotatedPhrase,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp,
                lineHeight = 28.sp
            ),
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        )
    } else {
        val lineColor = when {
            isActive -> Color.White
            isPast -> Color.White.copy(alpha = 0.44f)
            else -> Color.White.copy(alpha = 0.28f)
        }
        Text(
            text = line.text,
            style = if (isActive) {
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    lineHeight = 28.sp
                )
            } else {
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    lineHeight = 24.sp
                )
            },
            color = lineColor,
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        )
    }
}

@Composable
private fun EditLyricsDialog(
    songTitle: String,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var lyricsText by remember(initialText) { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Column {
                Text(
                    text = "Offline Lyrics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = songTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Paste synced LRC ([mm:ss.xx] phrase) or plain text lyrics. Plain lyrics automatically sync and highlight across the song offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = lyricsText,
                    onValueChange = { lyricsText = it },
                    placeholder = {
                        Text(
                            text = "[00:12.50] First line of lyrics...\nOr paste plain text lyrics here",
                            color = SpotifyTextSecondary.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        cursorColor = SpotifyGreen
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(lyricsText) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen,
                    contentColor = Color.Black
                )
            ) {
                Text("Save Offline", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SpotifyTextSecondary)
            }
        }
    )
}

