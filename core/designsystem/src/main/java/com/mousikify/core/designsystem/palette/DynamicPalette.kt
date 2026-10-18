package com.mousikify.core.designsystem.palette

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import com.mousikify.core.designsystem.theme.SpotifyBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PaletteExtractor {

    suspend fun extractGradientSeedColor(bitmap: Bitmap?): Color = withContext(Dispatchers.Default) {
        if (bitmap == null) return@withContext SpotifyBlack

        val palette = Palette.from(bitmap)
            .maximumColorCount(16)
            .generate()

        val swatch = palette.vibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.mutedSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.darkMutedSwatch

        if (swatch != null) {
            var color = Color(swatch.rgb)
            // Clamp luminance to ensure contrast with white text (WCAG)
            if (color.luminance() > 0.45f) {
                val factor = 0.45f / color.luminance()
                color = Color(
                    red = (color.red * factor).coerceIn(0f, 1f),
                    green = (color.green * factor).coerceIn(0f, 1f),
                    blue = (color.blue * factor).coerceIn(0f, 1f),
                    alpha = 1f
                )
            }
            color
        } else {
            SpotifyBlack
        }
    }
}

@Composable
fun rememberDynamicGradientBrush(
    seedColor: Color,
    bottomColor: Color = SpotifyBlack,
    durationMs: Int = 600
): Brush {
    val animatedColor by animateColorAsState(
        targetValue = seedColor,
        animationSpec = tween(durationMillis = durationMs),
        label = "DynamicPaletteTransition"
    )

    return remember(animatedColor, bottomColor) {
        Brush.verticalGradient(
            colors = listOf(
                animatedColor.copy(alpha = 0.85f),
                bottomColor.copy(alpha = 0.95f),
                bottomColor
            )
        )
    }
}
