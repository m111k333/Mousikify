package com.mousikify.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SpotifyTextPrimary,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
) {
    Text(
        text = text,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            animationMode = androidx.compose.foundation.MarqueeAnimationMode.Immediately,
            initialDelayMillis = 1500
        ),
        color = color,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun TrackListItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            val albumArtUri = "content://media/external/audio/albumart/${song.albumId}"
            AsyncImage(
                model = albumArtUri,
                contentDescription = song.albumName,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Playing",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) SpotifyGreen else SpotifyTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Heart Icon
        if (onFavoriteToggle != null) {
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (song.isFavorite) "Favorited" else "Not favorite",
                    tint = if (song.isFavorite) SpotifyGreen else SpotifyTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Context Menu
        if (onMenuClick != null) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Song options",
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
