package com.mousikify.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MiniPlayerBar(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSong = uiState.currentSong ?: return

    val progressFraction = if (uiState.durationMs > 0) {
        uiState.currentPositionMs.toFloat() / uiState.durationMs.toFloat()
    } else 0f

    MiniPlayer(
        song = currentSong,
        isPlaying = uiState.isPlaying,
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
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(enabled = isExpanded) {
        onCollapse()
    }

    AnimatedVisibility(
        visible = isExpanded && uiState.currentSong != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 160)),
        modifier = modifier.fillMaxSize()
    ) {
        FullScreenPlayer(
            uiState = uiState,
            onIntent = viewModel::onIntent,
            onCollapse = onCollapse,
            onNavigateToEqualizer = {
                onCollapse()
                onNavigateToEqualizer()
            }
        )
    }
}

@Composable
fun PlayerBottomSheet(
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
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
            viewModel = viewModel
        )
    }
}
