package com.mousikify.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import com.mousikify.core.designsystem.theme.SpotifyBlack
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.PlaybackContext
import com.mousikify.core.model.Song
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MiniPlayerBar(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.showBatteryPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(PlayerIntent.DismissBatteryPrompt) },
            containerColor = SpotifyCardBackground,
            title = { Text("Unrestricted Background Playback", color = SpotifyTextPrimary) },
            text = {
                Text(
                    "Android system battery optimization may pause playback after 3-4 songs when the screen is turned off.\n\nAllow Mousikify to run unrestricted for seamless background playback?",
                    color = SpotifyTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onIntent(PlayerIntent.ConfirmBatteryPrompt) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black)
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(PlayerIntent.DismissBatteryPrompt) }) {
                    Text("Not now", color = SpotifyTextSecondary)
                }
            }
        )
    }

    val currentSong = uiState.currentSong ?: return

    val progressFraction = if (uiState.durationMs > 0) {
        uiState.currentPositionMs.toFloat() / uiState.durationMs.toFloat()
    } else 0f

    val isBuffering = uiState.playbackStatus is com.mousikify.core.model.PlaybackStatus.Preparing ||
            uiState.playbackStatus is com.mousikify.core.model.PlaybackStatus.Buffering

    MiniPlayer(
        song = currentSong,
        isPlaying = uiState.isPlaying,
        isBuffering = isBuffering,
        isCachedOffline = uiState.isCurrentTrackCached,
        progressFraction = progressFraction,
        onClick = onExpand,
        onPlayPauseClick = { viewModel.onIntent(PlayerIntent.TogglePlayPause) },
        onFavoriteClick = { viewModel.onIntent(PlayerIntent.ToggleFavorite) },
        onSkipNext = { viewModel.onIntent(PlayerIntent.SkipToNext) },
        onSkipPrevious = { viewModel.onIntent(PlayerIntent.SkipToPrevious) },
        modifier = modifier
    )
}

@Composable
fun FullScreenPlayerOverlay(
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToContext: ((PlaybackContext) -> Unit)? = null,
    onOpenRadio: ((Song) -> Unit)? = null,
    deepLinkTrack: Song? = null,
    onDeepLinkTrackHandled: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    LaunchedEffect(deepLinkTrack) {
        if (deepLinkTrack != null) {
            viewModel.onIntent(PlayerIntent.PlayDeepLinkTrack(deepLinkTrack))
            onDeepLinkTrackHandled()
        }
    }

    BackHandler(enabled = isExpanded) {
        onCollapse()
    }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val offsetY = remember { Animatable(0f) }
    var isGestureDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            offsetY.snapTo(0f)
            isGestureDismissing = false
        }
    }

    fun onDragDelta(delta: Float) {
        coroutineScope.launch {
            // Apply rubber-band resistance as drag progresses
            val resistance = if (offsetY.value > 0f) {
                1f - (offsetY.value / screenHeightPx).coerceIn(0f, 0.45f)
            } else 1f
            val newOffset = (offsetY.value + delta * resistance).coerceAtLeast(0f)
            offsetY.snapTo(newOffset)
        }
    }

    fun onDragFinished(velocity: Float = 0f) {
        coroutineScope.launch {
            val threshold = with(density) { 100.dp.toPx() }
            if (offsetY.value > threshold || velocity > 1000f) {
                // Smoothly finish sliding down off-screen
                offsetY.animateTo(
                    targetValue = screenHeightPx,
                    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                )
                // Set gesture dismissing to suppress secondary exit animation from top
                isGestureDismissing = true
                onCollapse()
            } else {
                offsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
            }
        }
    }

    val nestedScrollConnection = remember(coroutineScope, density, screenHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f && offsetY.value > 0f) {
                    val newOffset = (offsetY.value + delta).coerceAtLeast(0f)
                    val consumed = newOffset - offsetY.value
                    coroutineScope.launch { offsetY.snapTo(newOffset) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0f) {
                    coroutineScope.launch {
                        val newOffset = (offsetY.value + delta).coerceAtLeast(0f)
                        offsetY.snapTo(newOffset)
                    }
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > 0f) {
                    onDragFinished(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return Velocity.Zero
            }
        }
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = if (isGestureDismissing) {
            ExitTransition.None
        } else {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 160))
        },
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt().coerceAtLeast(0)) }
            .nestedScroll(nestedScrollConnection)
            .background(SpotifyBlack)
    ) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        if (uiState.currentSong != null) {
            FullScreenPlayer(
                uiState = uiState,
                onIntent = viewModel::onIntent,
                onCollapse = onCollapse,
                onNavigateToEqualizer = {
                    onCollapse()
                    onNavigateToEqualizer()
                },
                onNavigateToArtist = {
                    onCollapse()
                    onNavigateToArtist(it)
                },
                onNavigateToContext = onNavigateToContext?.let { callback ->
                    { context ->
                        onCollapse()
                        callback(context)
                    }
                },
                onOpenRadio = onOpenRadio?.let { callback ->
                    { song ->
                        onCollapse()
                        callback(song)
                    }
                },
                onDragDelta = ::onDragDelta,
                onDragFinished = ::onDragFinished
            )
        }
    }
}

@Composable
fun PlayerBottomSheet(
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToArtist: (String) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        MiniPlayerBar(
            onExpand = { isExpanded = true },
            viewModel = viewModel
        )
        FullScreenPlayerOverlay(
            isExpanded = isExpanded,
            onCollapse = { isExpanded = false },
            onNavigateToEqualizer = onNavigateToEqualizer,
            onNavigateToArtist = onNavigateToArtist,
            viewModel = viewModel
        )
    }
}
