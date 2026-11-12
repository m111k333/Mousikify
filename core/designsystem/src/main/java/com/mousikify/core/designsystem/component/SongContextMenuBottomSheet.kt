package com.mousikify.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongContextMenuBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    onStartRadio: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onGoToAlbum: ((String, String) -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDeleteFromDevice: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SpotifySurfaceElevated,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 16.dp)
        ) {
            // Header: Artwork, Title, Artist, Duration
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SpotifyCardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                    val artUri = song.artworkUri ?: "content://media/external/audio/albumart/${song.albumId}"
                    AsyncImage(
                        model = artUri,
                        contentDescription = song.title,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song.title,
                            color = SpotifyTextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (song.isExplicit) {
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "E",
                                    color = SpotifyTextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val durationMinutes = song.durationMs / 60000
                    val durationSeconds = (song.durationMs % 60000) / 1000
                    val durationText = if (song.durationMs > 0) String.format("%d:%02d", durationMinutes, durationSeconds) else ""
                    val subtitle = if (durationText.isNotEmpty()) "${song.artistName} • $durationText" else song.artistName

                    Text(
                        text = subtitle,
                        color = SpotifyTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Action Items
            if (onStartRadio != null) {
                ContextMenuRow(
                    icon = Icons.Default.Radio,
                    text = "Start radio",
                    onClick = {
                        onDismiss()
                        onStartRadio()
                    }
                )
            }

            if (onPlayNext != null) {
                ContextMenuRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    text = "Play next",
                    onClick = {
                        onDismiss()
                        onPlayNext()
                    }
                )
            }

            if (onAddToQueue != null) {
                ContextMenuRow(
                    icon = Icons.Default.Queue,
                    text = "Add to queue",
                    onClick = {
                        onDismiss()
                        onAddToQueue()
                    }
                )
            }

            if (onAddToPlaylist != null) {
                ContextMenuRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    text = "Add to playlist",
                    onClick = {
                        onDismiss()
                        onAddToPlaylist()
                    }
                )
            }

            if (onToggleFavorite != null) {
                ContextMenuRow(
                    icon = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    text = if (song.isFavorite) "Remove from Liked Songs" else "Save to Liked Songs",
                    tint = if (song.isFavorite) SpotifyGreen else SpotifyTextPrimary,
                    onClick = {
                        onDismiss()
                        onToggleFavorite()
                    }
                )
            }

            if (onGoToArtist != null && song.artistName.isNotBlank() && !song.artistName.equals("<unknown>", ignoreCase = true)) {
                ContextMenuRow(
                    icon = Icons.Default.Person,
                    text = "Go to artist (${song.artistName})",
                    onClick = {
                        onDismiss()
                        onGoToArtist(song.artistName)
                    }
                )
            }

            if (onGoToAlbum != null && song.albumName.isNotBlank() && !song.albumName.equals("Unknown Album", ignoreCase = true)) {
                ContextMenuRow(
                    icon = Icons.Default.Album,
                    text = "Go to album (${song.albumName})",
                    onClick = {
                        onDismiss()
                        onGoToAlbum(song.albumName, song.artistName)
                    }
                )
            }

            if (onDownload != null) {
                ContextMenuRow(
                    icon = Icons.Default.Download,
                    text = "Download / Save offline",
                    onClick = {
                        onDismiss()
                        onDownload()
                    }
                )
            }

            if (onShare != null) {
                ContextMenuRow(
                    icon = Icons.Default.Share,
                    text = "Share song",
                    onClick = {
                        onDismiss()
                        onShare()
                    }
                )
            }

            if (onDeleteFromDevice != null) {
                ContextMenuRow(
                    icon = Icons.Default.DeleteOutline,
                    text = "Delete from device",
                    tint = Color(0xFFE22134),
                    onClick = {
                        onDismiss()
                        onDeleteFromDevice()
                    }
                )
            }
        }
    }
}

@Composable
private fun ContextMenuRow(
    icon: ImageVector,
    text: String,
    tint: Color = SpotifyTextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
