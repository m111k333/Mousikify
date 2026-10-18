package com.mousikify.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PlayerBottomSheet(
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }

    // Intercept back button when full-screen player is open so it collapses back to mini player
    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    if (uiState.currentSong == null) return

    Box(modifier = modifier.fillMaxWidth()) {
        // Collapsed Mini-Player
        if (!isExpanded) {
            val progressFraction = if (uiState.durationMs > 0) {
                uiState.currentPositionMs.toFloat() / uiState.durationMs.toFloat()
            } else 0f

            MiniPlayer(
                song = uiState.currentSong,
                isPlaying = uiState.isPlaying,
                progressFraction = progressFraction,
                onClick = { isExpanded = true },
                onPlayPauseClick = { viewModel.onIntent(PlayerIntent.TogglePlayPause) },
                onFavoriteClick = { viewModel.onIntent(PlayerIntent.ToggleFavorite) },
                onSkipNext = { viewModel.onIntent(PlayerIntent.SkipToNext) },
                onSkipPrevious = { viewModel.onIntent(PlayerIntent.SkipToPrevious) }
            )
        }

        // Full Screen Expanded Sheet
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            FullScreenPlayer(
                uiState = uiState,
                onIntent = viewModel::onIntent,
                onCollapse = { isExpanded = false },
                onNavigateToEqualizer = {
                    isExpanded = false
                    onNavigateToEqualizer()
                }
            )
        }
    }
}
