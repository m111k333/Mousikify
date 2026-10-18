package com.mousikify.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.component.MarqueeText
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.Song

@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    progressFraction: Float,
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SpotifySurfaceElevated)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art thumbnail
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SpotifyCardBackground)
            ) {
                AsyncImage(
                    model = "content://media/external/audio/albumart/${song.albumId}",
                    contentDescription = song.title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title and Artist
            Column(
                modifier = Modifier.weight(1f)
            ) {
                MarqueeText(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SpotifyTextPrimary
                )
                Text(
                    text = song.artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favorite button
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) SpotifyGreen else SpotifyTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Play / Pause button
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = SpotifyTextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Bottom progress indicator bar
        LinearProgressIndicator(
            progress = { progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
            color = SpotifyGreen,
            trackColor = Color.Transparent
        )
    }
}
