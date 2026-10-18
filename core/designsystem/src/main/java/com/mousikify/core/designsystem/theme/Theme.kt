package com.mousikify.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SpotifyGreen = Color(0xFF1DB954)
val SpotifyGreenBright = Color(0xFF1ED760)
val SpotifyBlack = Color(0xFF121212)
val SpotifyBackground = Color(0xFF121212)
val SpotifySurface = Color(0xFF181818)
val SpotifySurfaceElevated = Color(0xFF242424)
val SpotifyCardBackground = Color(0xFF282828)
val SpotifyTextPrimary = Color(0xFFFFFFFF)
val SpotifyTextSecondary = Color(0xFFB3B3B3)
val SpotifyDivider = Color(0xFF2A2A2A)

private val MousikifyColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    secondary = SpotifyGreenBright,
    onSecondary = Color.Black,
    background = SpotifyBlack,
    onBackground = SpotifyTextPrimary,
    surface = SpotifySurface,
    onSurface = SpotifyTextPrimary,
    surfaceVariant = SpotifyCardBackground,
    onSurfaceVariant = SpotifyTextSecondary
)

val MousikifyTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = SpotifyTextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = SpotifyTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = SpotifyTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = SpotifyTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = SpotifyTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = SpotifyTextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = SpotifyTextSecondary
    )
)

@Composable
fun MousikifyTheme(
    content: @Composable () -> Unit
) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = MousikifyColorScheme,
        typography = MousikifyTypography,
        content = content
    )
}
