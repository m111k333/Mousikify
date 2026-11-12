package com.mousikify.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.mousikify.core.model.Artist
import com.mousikify.core.model.ArtistProfile
import com.mousikify.core.model.Song

@Composable
fun YouTubeMusicArtistView(
    artistProfile: ArtistProfile,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onShuffleArtist: () -> Unit,
    onStartArtistRadio: () -> Unit = {},
    onAlbumClick: (Album) -> Unit,
    onSimilarArtistClick: (Artist) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    onPlayNext: ((Song) -> Unit)? = null,
    onAddToQueue: ((Song) -> Unit)? = null,
    onGoToAlbum: ((String, String) -> Unit)? = null,
    isTrackCached: (Song) -> Boolean = { it.isLocal },
    topBarActions: @Composable RowScope.() -> Unit = {}
) {
    val artist = artistProfile.artist
    val topSongs = artistProfile.topSongs
    val albums = artistProfile.albums
    val singles = artistProfile.singles
    val similar = artistProfile.similarArtists
    val featuredIn = artistProfile.featuredIn
    val isLoading = artistProfile.isLoading
    val listState = rememberLazyListState()

    LaunchedEffect(artist.name, artist.id) {
        listState.scrollToItem(0)
    }

    var menuSong by remember { mutableStateOf<Song?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
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
                    text = artist.name,
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // Hero Header with Gradient Scrim
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        // Background Artist Image
                        if (!artist.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = artist.avatarUrl,
                                contentDescription = artist.name,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(SpotifyCardBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = SpotifyTextSecondary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(96.dp)
                                )
                            }
                        }

                        // Gradient Scrim for contrast
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            SpotifyBackground.copy(alpha = 0.7f),
                                            SpotifyBackground
                                        )
                                    )
                                )
                        )

                        // Artist Name & Listener Stats inside Hero
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = SpotifyTextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val statsText = if (isLoading) "Loading artist..." else (artist.monthlyListeners ?: "${artist.songCount} songs")
                            Text(
                                text = statsText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpotifyTextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Action Buttons: Play Top Songs, Shuffle
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play Button (Primary)
                        Button(
                            onClick = {
                                if (topSongs.isNotEmpty()) onPlaySong(topSongs.first(), topSongs)
                            },
                            enabled = !isLoading && topSongs.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpotifyGreen,
                                disabledContainerColor = SpotifyGreen.copy(alpha = 0.4f),
                                contentColor = Color.Black,
                                disabledContentColor = Color.Black.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        // Shuffle Button (Secondary)
                        OutlinedButton(
                            onClick = onShuffleArtist,
                            enabled = !isLoading && topSongs.isNotEmpty(),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                tint = SpotifyTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", color = SpotifyTextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Top Songs Section
                if (isLoading) {
                    item {
                        Text(
                            text = "Top Songs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
                        )
                        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                        val columnWidth = screenWidth * 0.88f
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(2) {
                                TopSongsSkeletonColumn(columnWidth = columnWidth)
                            }
                        }
                    }
                } else if (topSongs.isNotEmpty()) {
                    item {
                        Text(
                            text = "Top Songs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
                        )

                        val songColumns = remember(topSongs) { topSongs.chunked(4) }
                        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                        val columnWidth = screenWidth * 0.88f

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(songColumns, key = { colIndex, _ -> "top_songs_col_$colIndex" }) { _, columnTracks ->
                                Column(
                                    modifier = Modifier.width(columnWidth),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    columnTracks.forEach { song ->
                                        val isThisPlaying = song.id == currentSongId && isPlaying
                                        CompactSongRow(
                                            song = song,
                                            isPlaying = isThisPlaying,
                                            isCached = if (song.isLocal) true else isTrackCached(song),
                                            onClick = { onPlaySong(song, topSongs) },
                                            onToggleFavorite = { onToggleFavorite(song) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Albums Skeleton / Real Carousel
                if (isLoading) {
                    item {
                        Text(
                            text = "Albums",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(4) {
                                AlbumCardSkeleton()
                            }
                        }
                    }
                } else if (albums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Albums",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(albums, key = { it.id }) { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = { onAlbumClick(album) }
                                )
                            }
                        }
                    }
                }

                // Singles & EPs Carousel
                if (singles.isNotEmpty()) {
                    item {
                        Text(
                            text = "Singles & EPs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(singles, key = { it.id }) { single ->
                                AlbumCard(
                                    album = single,
                                    onClick = { onAlbumClick(single) }
                                )
                            }
                        }
                    }
                }

                // Fans Might Also Like / Similar Artists Carousel
                if (similar.isNotEmpty()) {
                    item {
                        Text(
                            text = "Fans might also like",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 12.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(similar, key = { it.name }) { simArtist ->
                                CircularArtistCard(
                                    artist = simArtist,
                                    onClick = { onSimilarArtistClick(simArtist) }
                                )
                            }
                        }
                    }
                }

                // Featured In / Appears On Carousel
                if (featuredIn.isNotEmpty()) {
                    item {
                        Text(
                            text = "Appears On",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
                        )
                    }
                    itemsIndexed(featuredIn.take(5), key = { idx, s -> "featured_${s.id}_$idx" }) { _, song ->
                        TrackListItem(
                            song = song,
                            isPlaying = song.id == currentSongId && isPlaying,
                            onClick = { onPlaySong(song, featuredIn) },
                            onFavoriteToggle = { onToggleFavorite(song) },
                            onAddToPlaylistClick = onAddToPlaylist?.let { { it(song) } },
                            onMenuClick = { menuSong = song }
                        )
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
                onStartRadio = null,
                onPlayNext = onPlayNext?.let { { it(s) } },
                onAddToQueue = onAddToQueue?.let { { it(s) } },
                onAddToPlaylist = onAddToPlaylist?.let { { it(s) } },
                onToggleFavorite = { onToggleFavorite(s) },
                onGoToArtist = null, // already on artist
                onGoToAlbum = onGoToAlbum
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(135.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(48.dp)
            )
            AsyncImage(
                model = album.artworkUri,
                contentDescription = album.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = SpotifyTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val yearLabel = if (album.year > 0) "${album.year} • " else ""
        Text(
            text = "$yearLabel${if (album.songCount > 0) "${album.songCount} songs" else "Album"}",
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CircularArtistCard(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(54.dp)
            )
            AsyncImage(
                model = artist.avatarUrl,
                contentDescription = artist.name,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = SpotifyTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = artist.monthlyListeners ?: "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompactSongRow(
    song: Song,
    isPlaying: Boolean,
    isCached: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = song.artworkUri,
                contentDescription = song.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerBars(barColor = SpotifyGreen)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isPlaying) SpotifyGreen else SpotifyTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = if (song.artistName.isNotBlank()) song.artistName else "Song"
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (song.isFavorite) SpotifyGreen else SpotifyTextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TopSongsSkeletonColumn(
    columnWidth: Dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "TopSongsSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SkeletonAlpha"
    )
    Column(
        modifier = modifier.width(columnWidth),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SpotifyCardBackground.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SpotifyCardBackground.copy(alpha = alpha))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SpotifyCardBackground.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCardSkeleton(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "AlbumSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlbumAlpha"
    )
    Column(modifier = modifier.width(135.dp)) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(13.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyCardBackground.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyCardBackground.copy(alpha = alpha))
        )
    }
}
