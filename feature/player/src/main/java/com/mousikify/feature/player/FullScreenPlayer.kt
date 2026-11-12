package com.mousikify.feature.player

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import com.mousikify.core.designsystem.component.AnimatedFavoriteButton
import com.mousikify.core.designsystem.component.bounceClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import kotlinx.coroutines.delay
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
import com.mousikify.core.designsystem.component.SongContextMenuBottomSheet
import com.mousikify.core.designsystem.palette.rememberDynamicGradientBrush
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.LyricLine
import com.mousikify.core.model.PlaybackContext
import com.mousikify.core.model.PlaybackContextType
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.RepeatMode
import com.mousikify.core.model.Song

@Composable
fun FullScreenPlayerRoute(
    onCollapse: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToContext: ((PlaybackContext) -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FullScreenPlayer(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onCollapse = onCollapse,
        onNavigateToEqualizer = onNavigateToEqualizer,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToContext = onNavigateToContext
    )
}

@Composable
fun FullScreenPlayer(
    uiState: PlayerUiState,
    onIntent: (PlayerIntent) -> Unit,
    onCollapse: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToContext: ((PlaybackContext) -> Unit)? = null,
    onOpenRadio: ((Song) -> Unit)? = null,
    onDragDelta: (Float) -> Unit = {},
    onDragFinished: (Float) -> Unit = {}
) {
    BackHandler(onBack = onCollapse)

    val song = uiState.currentSong ?: return
    val gradientBrush = rememberDynamicGradientBrush(seedColor = uiState.gradientColor)

    var isArtworkBloomed by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id) {
        isArtworkBloomed = true
    }
    val artworkScale by animateFloatAsState(
        targetValue = if (isArtworkBloomed) 1.0f else 0.92f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "ArtworkBloomScale"
    )

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(3000L)
            onIntent(PlayerIntent.DismissStatusMessage)
        }
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

                val activeContext = uiState.playbackContext ?: if (song.albumName.isNotBlank()) {
                    PlaybackContext(
                        title = song.albumName,
                        type = PlaybackContextType.ALBUM,
                        id = song.albumId.toString(),
                        albumId = if (song.albumId != 0L) song.albumId else null,
                        artistName = song.cleanArtistName,
                        isLocalOrSaved = song.isLocal || song.isAlbumSaved
                    )
                } else {
                    PlaybackContext(
                        title = "Your Library",
                        type = PlaybackContextType.LIBRARY,
                        isLocalOrSaved = true
                    )
                }

                val (categoryLabel, displayTitle) = remember(activeContext, song) {
                    when (activeContext.type) {
                        PlaybackContextType.PLAYLIST -> "PLAYING FROM PLAYLIST" to activeContext.title
                        PlaybackContextType.ALBUM -> "PLAYING FROM ALBUM" to activeContext.title
                        PlaybackContextType.ARTIST -> "PLAYING FROM ARTIST" to activeContext.title
                        PlaybackContextType.MIX -> "PLAYING FROM MIX" to activeContext.title
                        PlaybackContextType.LIBRARY -> {
                            if (activeContext.title.isNotBlank() && !activeContext.title.equals("Your Library", ignoreCase = true)) {
                                "PLAYING FROM PLAYLIST" to activeContext.title
                            } else if (song.albumName.isNotBlank() && !song.albumName.equals("YouTube Music", ignoreCase = true)) {
                                "PLAYING FROM ALBUM" to song.albumName
                            } else {
                                "PLAYING FROM YOUR LIBRARY" to "Your Library"
                            }
                        }
                        PlaybackContextType.SEARCH -> "PLAYING FROM SEARCH" to (activeContext.title.ifBlank { "Search" })
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = onNavigateToContext != null) {
                            onNavigateToContext?.invoke(activeContext)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.mousikify.core.designsystem.component.TrackSourceBadge(
                            isLocal = song.isLocal,
                            isCachedOffline = uiState.isCurrentTrackCached,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SpotifyTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (onNavigateToContext != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
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
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                    }
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                val artworkModel = song.artworkUri ?: "content://media/external/audio/albumart/${song.albumId}"

                val coverGestureModifier = Modifier
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

                    // Floating Circular Radio Button in bottom-right corner
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Surface(
                            onClick = { onOpenRadio?.invoke(song) },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.72f),
                            modifier = Modifier
                                .size(44.dp)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = "Song Radio",
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(22.dp)
                                )
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
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onNavigateToArtist(song.cleanArtistName) }
                    )
                }
                IconButton(onClick = { onIntent(PlayerIntent.ShowAddToPlaylistDialog(true)) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = SpotifyTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                AnimatedFavoriteButton(
                    isFavorite = song.isFavorite,
                    onToggle = { onIntent(PlayerIntent.ToggleFavorite) },
                    iconSize = 28.dp
                )
                val context = LocalContext.current
                IconButton(onClick = {
                    val shareUrl = com.mousikify.core.model.DeepLinkCodec.encodeTrackUrl(song)
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
                        .bounceClickable(minScale = 0.92f) { onIntent(PlayerIntent.TogglePlayPause) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = Color.Black,
                            strokeWidth = 3.dp
                        )
                    } else {
                        AnimatedContent(
                            targetState = uiState.isPlaying,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(150)))
                                    .togetherWith(scaleOut(targetScale = 0.7f, animationSpec = tween(100)) + fadeOut(tween(100)))
                            },
                            label = "FullScreenPlayPauseIconAnim"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
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



