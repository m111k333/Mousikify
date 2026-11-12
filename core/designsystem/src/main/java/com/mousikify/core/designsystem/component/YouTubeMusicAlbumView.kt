package com.mousikify.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.theme.SpotifyBackground
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.model.Album
import com.mousikify.core.model.AlbumProfile
import com.mousikify.core.model.AlbumType
import com.mousikify.core.model.Song

@Composable
fun YouTubeMusicAlbumView(
    albumProfile: AlbumProfile,
    currentSongId: Long?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    onBack: () -> Unit,
    onPlaySong: (Song, Int) -> Unit,
    onPlayAll: () -> Unit,
    onToggleShuffle: () -> Unit,
    onArtistClick: (String) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    onStartRadio: ((Song) -> Unit)? = null,
    onPlayNext: ((Song) -> Unit)? = null,
    onAddToQueue: ((Song) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onSaveAlbumLocally: (() -> Unit)? = null,
    onUnsaveAlbumLocally: (() -> Unit)? = null,
    isAlbumSaved: Boolean = false,
    isSavingAlbumLocally: Boolean = false,
    onDeleteSong: ((Song) -> Unit)? = null,
    cacheUpdateTick: Long = 0L,
    isTrackCached: (Song) -> Boolean = { it.isLocal },
    topBarActions: @Composable RowScope.() -> Unit = {}
) {
    val album = albumProfile.album
    val tracks = albumProfile.tracks
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var songPendingDelete by remember { mutableStateOf<Song?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SpotifyTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                topBarActions()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // Album Header Hero
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Square Album Cover Artwork
                        Box(
                            modifier = Modifier
                                .size(170.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpotifyCardBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                                modifier = Modifier.size(72.dp)
                            )
                            AsyncImage(
                                model = album.artworkUri,
                                contentDescription = album.title,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Album Title
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Clickable Artist Chip / Link
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onArtistClick(album.artist) }
                                .background(SpotifySurfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = album.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = SpotifyGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Metadata line: Type • Year • Tracks • Duration
                        val typeLabel = when (album.albumType) {
                            AlbumType.SINGLE -> "Single"
                            AlbumType.EP -> "EP"
                            AlbumType.ALBUM -> "Album"
                        }
                        val yearText = if (album.year > 0) "${album.year} • " else ""
                        val songCountText = if (tracks.size == 1) "1 song" else "${tracks.size} songs"
                        val durationMinutes = album.totalDurationMs / 60000
                        val durationText = if (durationMinutes > 0) ", $durationMinutes min" else ""

                        Text(
                            text = "$typeLabel • $yearText$songCountText$durationText",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action Bar: Play, Shuffle, Save Album
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconButton(onClick = onToggleShuffle) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (isShuffleEnabled) SpotifyGreen else SpotifyTextSecondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                val downloadedCount = remember(tracks, cacheUpdateTick) {
                                    tracks.count { if (it.isLocal) true else isTrackCached(it) }
                                }
                                DownloadProgressRing(
                                    downloadedCount = downloadedCount,
                                    totalCount = tracks.size,
                                    isCollectionSaved = isAlbumSaved || isSavingAlbumLocally
                                )

                                if (onSaveAlbumLocally != null || onUnsaveAlbumLocally != null) {
                                    Button(
                                        onClick = {
                                            if (isAlbumSaved) {
                                                onUnsaveAlbumLocally?.invoke()
                                            } else {
                                                onSaveAlbumLocally?.invoke()
                                            }
                                        },
                                        enabled = isAlbumSaved || (tracks.isNotEmpty() && !isSavingAlbumLocally),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAlbumSaved) SpotifyGreen else SpotifyCardBackground,
                                            contentColor = if (isAlbumSaved) Color.Black else SpotifyTextPrimary,
                                            disabledContainerColor = SpotifyCardBackground.copy(alpha = 0.4f),
                                            disabledContentColor = SpotifyTextSecondary.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        if (isSavingAlbumLocally) {
                                            CircularProgressIndicator(
                                                color = SpotifyGreen,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Saving...", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        } else if (isAlbumSaved) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Saved", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.BookmarkAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Save album", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            if (tracks.isNotEmpty()) {
                                IconButton(
                                    onClick = onPlayAll,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(SpotifyGreen)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play album",
                                        tint = Color.Black,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Numbered Tracklist
                itemsIndexed(tracks, key = { index, song -> "album_track_${song.id}_$index" }) { index, song ->
                    val isThisPlaying = song.id == currentSongId && isPlaying
                    val isRemovable = onDeleteSong != null && (isAlbumSaved || isTrackCached(song))
                    SwipeableSongRow(
                        onRemove = { songPendingDelete = song },
                        enabled = isRemovable
                    ) {
                        TrackListItem(
                            song = song,
                            isPlaying = isThisPlaying,
                            trackNumber = index + 1,
                            isCachedOffline = if (song.isLocal) true else isTrackCached(song),
                            onClick = { onPlaySong(song, index) },
                            onFavoriteToggle = { onToggleFavorite(song) },
                            onAddToPlaylistClick = onAddToPlaylist?.let { { it(song) } },
                            onMenuClick = { menuSong = song }
                        )
                    }
                }

                // Liner Notes / Album Footer
                if (tracks.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                        ) {
                            if (album.year > 0) {
                                Text(
                                    text = "Released ${album.year}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SpotifyTextSecondary
                                )
                            }
                            val copyright = album.recordLabel ?: "℗ ${if (album.year > 0) album.year else ""} ${album.artist}"
                            Text(
                                text = copyright,
                                style = MaterialTheme.typography.bodySmall,
                                color = SpotifyTextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Context Menu Bottom Sheet
        if (menuSong != null) {
            val s = menuSong!!
            SongContextMenuBottomSheet(
                song = s,
                onDismiss = { menuSong = null },
                onStartRadio = onStartRadio?.let { { it(s) } },
                onPlayNext = onPlayNext?.let { { it(s) } },
                onAddToQueue = onAddToQueue?.let { { it(s) } },
                onAddToPlaylist = onAddToPlaylist?.let { { it(s) } },
                onToggleFavorite = { onToggleFavorite(s) },
                onGoToArtist = onGoToArtist ?: { onArtistClick(it) },
                onGoToAlbum = null, // already on album!
                onDeleteFromDevice = if (onDeleteSong != null && (isAlbumSaved || isTrackCached(s))) {
                    { songPendingDelete = s }
                } else null
            )
        }

        if (songPendingDelete != null) {
            val song = songPendingDelete!!
            AlertDialog(
                onDismissRequest = { songPendingDelete = null },
                containerColor = SpotifyCardBackground,
                title = {
                    Text("Delete Track from Device?", color = SpotifyTextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "Are you sure you want to delete \"${song.title}\"? The downloaded audio file will be permanently removed from device storage.",
                        color = SpotifyTextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = songPendingDelete
                            songPendingDelete = null
                            if (target != null) {
                                onDeleteSong?.invoke(target)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE22134)),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { songPendingDelete = null }) {
                        Text("Cancel", color = SpotifyTextPrimary)
                    }
                }
            )
        }
    }
}
