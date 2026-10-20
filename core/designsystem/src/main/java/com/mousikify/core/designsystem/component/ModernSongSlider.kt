package com.mousikify.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary

@Composable
fun ModernSongSlider(
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.22f),
    activeThumbColor: Color = Color.White
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    var showRemainingTime by remember { mutableStateOf(false) }

    val effectivePosition = if (isDragging) dragPositionMs.toLong() else currentPositionMs
    val totalDuration = durationMs.coerceAtLeast(1L)
    val fraction = (effectivePosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

    // Animated smooth visual transitions
    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "TrackHeight"
    )
    val animatedThumbRadius by animateDpAsState(
        targetValue = if (isDragging) 7.5.dp else 4.5.dp,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "ThumbRadius"
    )
    val animatedActiveTrackColor by animateColorAsState(
        targetValue = if (isDragging) SpotifyGreen else activeColor,
        animationSpec = tween(durationMillis = 150),
        label = "ActiveColor"
    )
    val animatedLeftTimeColor by animateColorAsState(
        targetValue = if (isDragging) Color.White else SpotifyTextSecondary,
        animationSpec = tween(durationMillis = 150),
        label = "LeftTimeColor"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Scrubber Touch Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(totalDuration) {
                    detectTapGestures(
                        onPress = { offset ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val width = size.width.toFloat()
                            val tapFraction = (offset.x / width).coerceIn(0f, 1f)
                            val seekTarget = (tapFraction * totalDuration).toLong()
                            onSeek(seekTarget)
                        }
                    )
                }
                .pointerInput(totalDuration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isDragging = true
                            val width = size.width.toFloat()
                            val dragFraction = (offset.x / width).coerceIn(0f, 1f)
                            dragPositionMs = dragFraction * totalDuration
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val width = size.width.toFloat()
                            val dragFraction = (change.position.x / width).coerceIn(0f, 1f)
                            dragPositionMs = dragFraction * totalDuration
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek(dragPositionMs.toLong().coerceIn(0L, totalDuration))
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val trackHeightPx = animatedTrackHeight.toPx()
                val thumbRadiusPx = animatedThumbRadius.toPx()
                val cornerRadiusPx = trackHeightPx / 2f
                val centerY = canvasHeight / 2f

                val activeWidth = (canvasWidth * fraction).coerceIn(0f, canvasWidth)

                // 1. Inactive Track (Soft Frosted Pill)
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(canvasWidth, trackHeightPx),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )

                // 2. Active Track (Brilliant Solid Pill)
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = animatedActiveTrackColor,
                        topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                        size = Size(activeWidth, trackHeightPx),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                }

                // 3. Thumb with soft drop shadow when dragging
                if (isDragging) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = thumbRadiusPx + 3f,
                        center = Offset(activeWidth, centerY + 1.5f)
                    )
                }
                drawCircle(
                    color = activeThumbColor,
                    radius = thumbRadiusPx,
                    center = Offset(activeWidth, centerY)
                )
            }
        }

        // Timestamps below
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(effectivePosition),
                fontSize = 12.sp,
                fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium,
                color = animatedLeftTimeColor,
                letterSpacing = 0.4.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            val rightTimestamp = if (showRemainingTime) {
                val remaining = (totalDuration - effectivePosition).coerceAtLeast(0L)
                "-${formatDuration(remaining)}"
            } else {
                formatDuration(totalDuration)
            }

            Box(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures {
                        showRemainingTime = !showRemainingTime
                    }
                }
            ) {
                Text(
                    text = rightTimestamp,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SpotifyTextSecondary,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}
