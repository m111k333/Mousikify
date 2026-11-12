package com.mousikify.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.component.AddToPlaylistDialog
import com.mousikify.core.designsystem.component.CreatePlaylistDialog
import com.mousikify.core.designsystem.component.SongContextMenuBottomSheet
import com.mousikify.core.designsystem.component.TrackListItem
import com.mousikify.core.designsystem.component.TrackRowSkeleton
import com.mousikify.core.designsystem.component.DownloadProgressRing
import com.mousikify.core.designsystem.component.YouTubeMusicAlbumView
import com.mousikify.core.designsystem.component.YouTubeMusicArtistView
import com.mousikify.core.designsystem.theme.SpotifyBackground
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.media.remote.RemotePlaylist
import com.mousikify.core.model.Album
import com.mousikify.core.model.Artist
import com.mousikify.core.model.GenreCategory
import com.mousikify.core.model.PlaybackStatus
import com.mousikify.core.model.Song
import kotlinx.coroutines.delay

@Composable
fun SearchRoute(
    initialArtistName: String? = null,
    onInitialArtistHandled: () -> Unit = {},
    initialAlbum: Pair<String, String>? = null,
    onInitialAlbumHandled: () -> Unit = {},
    searchResetTrigger: Long = 0L,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onlinePagedTracks = viewModel.onlinePagedTracks.collectAsLazyPagingItems()

    LaunchedEffect(searchResetTrigger) {
        if (searchResetTrigger > 0L) {
            viewModel.onIntent(SearchIntent.ResetToSearchRoot)
        }
    }

    LaunchedEffect(initialArtistName) {
        if (!initialArtistName.isNullOrBlank()) {
            viewModel.onIntent(SearchIntent.OpenArtistByName(initialArtistName))
            onInitialArtistHandled()
        }
    }

    LaunchedEffect(initialAlbum) {
        if (initialAlbum != null) {
            viewModel.onIntent(SearchIntent.OpenAlbumByName(initialAlbum.first, initialAlbum.second))
            onInitialAlbumHandled()
        }
    }

    SearchScreen(
        uiState = uiState,
        onlinePagedTracks = onlinePagedTracks,
        isTrackCached = viewModel::isTrackCached,
        onIntent = viewModel::onIntent
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onlinePagedTracks: LazyPagingItems<Song>,
    isTrackCached: (Song) -> Boolean,
    onIntent: (SearchIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isCurrentBuffering = uiState.playbackStatus is PlaybackStatus.Preparing ||
            uiState.playbackStatus is PlaybackStatus.Buffering

    var selectedSongForContextMenu by remember { mutableStateOf<Song?>(null) }
    val communitySongs = remember(uiState.onlineSongs) {
        uiState.onlineSongs.filter { it.artistName.contains("• via", ignoreCase = true) }
    }
    val renderedCommunityUris = remember(communitySongs) {
        communitySongs.map { it.contentUri }.toSet()
    }

    BackHandler(enabled = uiState.openedArtistProfile != null || uiState.openedAlbumProfile != null || uiState.openedPlaylist != null) {
        if (uiState.openedArtistProfile != null || uiState.openedAlbumProfile != null) {
            onIntent(SearchIntent.CloseDetail)
        } else {
            onIntent(SearchIntent.ClosePlaylistDetail)
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(if (uiState.canUndo) 5000L else 3000L)
            onIntent(SearchIntent.DismissStatusMessage)
        }
    }

    val activeSearchView = remember(uiState.openedAlbumProfile, uiState.openedArtistProfile, uiState.openedPlaylist) {
        when {
            uiState.openedAlbumProfile != null -> "album_${uiState.openedAlbumProfile.album.title.trim().lowercase()}_${uiState.openedAlbumProfile.album.artist.trim().lowercase()}"
            uiState.openedArtistProfile != null -> "artist_${com.mousikify.core.model.cleanArtistName(uiState.openedArtistProfile.artist.name).trim().lowercase()}"
            uiState.openedPlaylist != null -> "playlist_${uiState.openedPlaylist.url}"
            else -> "search_root"
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = activeSearchView,
            transitionSpec = {
                if (initialState == "search_root" && targetState != "search_root") {
                    (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(animationSpec = tween(200)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { -it / 3 } + fadeOut(animationSpec = tween(160)))
                } else if (initialState != "search_root" && targetState == "search_root") {
                    (slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(animationSpec = tween(180)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { it / 3 } + fadeOut(animationSpec = tween(180)))
                } else {
                    (slideInHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(animationSpec = tween(180)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { -it / 3 } + fadeOut(animationSpec = tween(160)))
                }
            },
            label = "SearchDetailTransition"
        ) { currentView ->
            when {
                currentView.startsWith("album_") && uiState.openedAlbumProfile != null -> {
                    val albumProfile = uiState.openedAlbumProfile
                    val isAlbumSaved = uiState.isAlbumSaved(albumProfile)
                    YouTubeMusicAlbumView(
                        albumProfile = albumProfile,
                        currentSongId = uiState.currentSongId,
                        isPlaying = uiState.isPlaying,
                        isShuffleEnabled = uiState.isShuffleEnabled,
                        onBack = { onIntent(SearchIntent.CloseDetail) },
                        onPlaySong = { song, _ -> onIntent(SearchIntent.PlaySong(song, albumProfile.tracks)) },
                        onPlayAll = {
                            val tracks = albumProfile.tracks
                            if (tracks.isNotEmpty()) onIntent(SearchIntent.PlaySong(tracks.first(), tracks))
                        },
                        onToggleShuffle = { onIntent(SearchIntent.ToggleShuffle(albumProfile.tracks)) },
                        onArtistClick = { onIntent(SearchIntent.OpenArtistByName(it)) },
                        onToggleFavorite = { onIntent(SearchIntent.ToggleFavorite(it)) },
                        onAddToPlaylist = { onIntent(SearchIntent.ShowAddToPlaylistDialog(it)) },
                        onStartRadio = { onIntent(SearchIntent.StartRadio(it)) },
                        onPlayNext = { onIntent(SearchIntent.PlayNext(it)) },
                        onAddToQueue = { onIntent(SearchIntent.AddToQueue(it)) },
                        onGoToArtist = { onIntent(SearchIntent.OpenArtistByName(it)) },
                        onSaveAlbumLocally = {
                            onIntent(SearchIntent.SaveAlbumLocally(albumProfile.album, albumProfile.tracks))
                        },
                        onUnsaveAlbumLocally = {
                            onIntent(SearchIntent.UnsaveAlbumLocally(albumProfile.album, albumProfile.tracks))
                        },
                        isAlbumSaved = isAlbumSaved,
                        isSavingAlbumLocally = uiState.isSavingAlbumLocally,
                        onDeleteSong = { song -> onIntent(SearchIntent.DeleteSongFromAlbum(song)) },
                        cacheUpdateTick = uiState.cacheUpdateTick,
                        isTrackCached = isTrackCached,
                        isTrackFavorite = { uiState.isFavorite(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                currentView.startsWith("artist_") && uiState.openedArtistProfile != null -> {
                    val artistProfile = uiState.openedArtistProfile
                    YouTubeMusicArtistView(
                        artistProfile = artistProfile,
                        currentSongId = uiState.currentSongId,
                        isPlaying = uiState.isPlaying,
                        onBack = { onIntent(SearchIntent.CloseDetail) },
                        onPlaySong = { song, queue -> onIntent(SearchIntent.PlaySong(song, queue)) },
                        onShuffleArtist = { onIntent(SearchIntent.ToggleShuffle(artistProfile.topSongs)) },
                        onAlbumClick = { onIntent(SearchIntent.OpenAlbum(it)) },
                        onSimilarArtistClick = { onIntent(SearchIntent.OpenArtistByName(it.name)) },
                        onToggleFavorite = { onIntent(SearchIntent.ToggleFavorite(it)) },
                        onAddToPlaylist = { onIntent(SearchIntent.ShowAddToPlaylistDialog(it)) },
                        onPlayNext = { onIntent(SearchIntent.PlayNext(it)) },
                        onAddToQueue = { onIntent(SearchIntent.AddToQueue(it)) },
                        onGoToAlbum = { alb, art -> onIntent(SearchIntent.OpenAlbumByName(alb, art)) },
                        isTrackCached = isTrackCached,
                        isTrackFavorite = { uiState.isFavorite(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                currentView.startsWith("playlist_") && uiState.openedPlaylist != null -> {
                    val openedPlaylist = uiState.openedPlaylist
                    RemotePlaylistDetailView(
                        playlist = openedPlaylist,
                        uiState = uiState,
                        isTrackCached = isTrackCached,
                        onIntent = onIntent,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SpotifyBackground)
                    ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { onIntent(SearchIntent.UpdateQuery(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textStyle = TextStyle(
                    color = Color(0xFF121212),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                placeholder = {
                    Text(
                        text = "Search songs, albums, artists, playlists...",
                        color = Color(0xFF555555),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF121212))
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { onIntent(SearchIntent.ClearQuery) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF121212))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onIntent(SearchIntent.SubmitSearch)
                        focusManager.clearFocus()
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color(0xFF121212),
                    unfocusedTextColor = Color(0xFF121212),
                    cursorColor = Color(0xFF121212),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedLeadingIconColor = Color(0xFF121212),
                    unfocusedLeadingIconColor = Color(0xFF121212),
                    focusedTrailingIconColor = Color(0xFF121212),
                    unfocusedTrailingIconColor = Color(0xFF121212)
                ),
                singleLine = true
            )

            if (uiState.statusMessage != null) {
                Surface(
                    color = SpotifyGreen.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyGreen,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.canUndo) {
                            TextButton(
                                onClick = { onIntent(SearchIntent.UndoDelete) },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text("UNDO", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(
                            onClick = { onIntent(SearchIntent.DismissStatusMessage) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Dismiss",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Category Filter Chips
            if (uiState.query.isNotBlank()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.activeTab == SearchTab.ALL,
                            onClick = { onIntent(SearchIntent.SelectTab(SearchTab.ALL)) },
                            label = { Text("All") },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotifyGreen,
                                selectedLabelColor = Color.Black,
                                containerColor = SpotifyCardBackground,
                                labelColor = SpotifyTextPrimary
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.activeTab == SearchTab.SONGS || uiState.activeTab == SearchTab.ONLINE,
                            onClick = { onIntent(SearchIntent.SelectTab(SearchTab.SONGS)) },
                            label = { Text("Songs") },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotifyGreen,
                                selectedLabelColor = Color.Black,
                                containerColor = SpotifyCardBackground,
                                labelColor = SpotifyTextPrimary
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.activeTab == SearchTab.ARTISTS,
                            onClick = { onIntent(SearchIntent.SelectTab(SearchTab.ARTISTS)) },
                            label = {
                                val totalArtists = uiState.onlineArtists.size + uiState.artists.size
                                Text(if (totalArtists > 0) "Artists ($totalArtists)" else "Artists")
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotifyGreen,
                                selectedLabelColor = Color.Black,
                                containerColor = SpotifyCardBackground,
                                labelColor = SpotifyTextPrimary
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.activeTab == SearchTab.ALBUMS,
                            onClick = { onIntent(SearchIntent.SelectTab(SearchTab.ALBUMS)) },
                            label = {
                                val totalAlbums = uiState.onlineAlbums.size + uiState.albums.size
                                Text(if (totalAlbums > 0) "Albums ($totalAlbums)" else "Albums")
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotifyGreen,
                                selectedLabelColor = Color.Black,
                                containerColor = SpotifyCardBackground,
                                labelColor = SpotifyTextPrimary
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.activeTab == SearchTab.PLAYLISTS,
                            onClick = { onIntent(SearchIntent.SelectTab(SearchTab.PLAYLISTS)) },
                            label = {
                                val totalPlaylists = uiState.onlinePlaylists.size + uiState.playlists.size
                                Text(if (totalPlaylists > 0) "Playlists ($totalPlaylists)" else "Playlists")
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotifyGreen,
                                selectedLabelColor = Color.Black,
                                containerColor = SpotifyCardBackground,
                                labelColor = SpotifyTextPrimary
                            )
                        )
                    }
                }
            }

            // Main Content Area
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (uiState.query.isBlank()) {
                    // 1. Compact Recent Searches Chips
                    if (uiState.recentSearches.isNotEmpty()) {
                        item(key = "discovery_recent_chips") {
                            SearchRecentQueryChips(
                                recentSearches = uiState.recentSearches,
                                onSelect = { onIntent(SearchIntent.SelectRecentSearch(it)) },
                                onRemove = { onIntent(SearchIntent.RemoveRecentSearch(it)) },
                                onClearAll = { onIntent(SearchIntent.ClearRecentSearches) }
                            )
                        }
                    }

                    // 2. "Recommended for You" Songs Carousel
                    if (uiState.discoveryData.recommendedSongs.isNotEmpty() || uiState.discoveryData.isLoading) {
                        item(key = "discovery_recommended_songs") {
                            RecommendedSongsDiscoverySection(
                                songs = uiState.discoveryData.recommendedSongs,
                                currentSongId = uiState.currentSongId,
                                isPlaying = uiState.isPlaying,
                                onPlaySong = { song, queue -> onIntent(SearchIntent.PlaySong(song, queue)) },
                                onRefresh = { onIntent(SearchIntent.RefreshDiscovery) },
                                isRefreshing = uiState.discoveryData.isLoading
                            )
                        }
                    }

                    // 3. "Explore Artists" Carousel
                    if (uiState.discoveryData.exploreArtists.isNotEmpty()) {
                        item(key = "discovery_explore_artists") {
                            ExploreArtistsDiscoverySection(
                                artists = uiState.discoveryData.exploreArtists,
                                onArtistClick = { onIntent(SearchIntent.OpenArtistByName(it)) }
                            )
                        }
                    }

                    // 4. "Explore Albums" Carousel
                    if (uiState.discoveryData.exploreAlbums.isNotEmpty()) {
                        item(key = "discovery_explore_albums") {
                            ExploreAlbumsDiscoverySection(
                                albums = uiState.discoveryData.exploreAlbums,
                                onAlbumClick = { title, artist -> onIntent(SearchIntent.OpenAlbumByName(title, artist)) }
                            )
                        }
                    }

                    // 5. "Browse Genres & Moods" 2-Column Grid
                    if (uiState.discoveryData.genres.isNotEmpty()) {
                        item(key = "discovery_genres_grid") {
                            BrowseGenresDiscoveryGrid(
                                genres = uiState.discoveryData.genres,
                                onSelectGenre = { onIntent(SearchIntent.SelectGenre(it)) }
                            )
                        }
                    }
                } else {
                    // TAB: ALL (YouTube Music multi-section view)
                    if (uiState.activeTab == SearchTab.ALL) {
                        // 1. Top Result Card
                        val topArtist = uiState.onlineArtists.firstOrNull() ?: uiState.artists.firstOrNull()
                        val topAlbum = uiState.onlineAlbums.firstOrNull() ?: uiState.albums.firstOrNull()
                        val topSong = uiState.songs.firstOrNull() ?: uiState.onlineSongs.firstOrNull()

                        if (topArtist != null) {
                            item {
                                TopResultArtistCard(
                                    artist = topArtist,
                                    onClick = { onIntent(SearchIntent.OpenArtist(topArtist)) },
                                    onPlay = { onIntent(SearchIntent.PlayArtistTopSongs(topArtist.name, shuffle = false)) },
                                    onShuffle = { onIntent(SearchIntent.PlayArtistTopSongs(topArtist.name, shuffle = true)) }
                                )
                            }
                        } else if (topAlbum != null) {
                            item {
                                TopResultAlbumCard(
                                    album = topAlbum,
                                    onClick = { onIntent(SearchIntent.OpenAlbum(topAlbum)) }
                                )
                            }
                        } else if (topSong != null) {
                            item {
                                TopResultSongCard(
                                    song = topSong,
                                    isPlaying = topSong.id == uiState.currentSongId && uiState.isPlaying,
                                    onPlay = { onIntent(SearchIntent.PlaySong(topSong, listOf(topSong))) }
                                )
                            }
                        }

                        // 2. Songs Shelf (Top 4 tracks)
                        val combinedSongs = (uiState.songs + uiState.onlineSongs).distinctBy { it.remoteVideoId ?: it.title + it.artistName }.take(5)
                        if (combinedSongs.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = "Songs",
                                    onSeeAll = { onIntent(SearchIntent.SelectTab(SearchTab.SONGS)) }
                                )
                            }
                            items(combinedSongs, key = { "all_song_${it.remoteVideoId ?: it.id}" }) { song ->
                                val isThisTrack = song.id == uiState.currentSongId
                                val isCached = remember(song.id, uiState.cacheUpdateTick) {
                                    if (song.isLocal) true else isTrackCached(song)
                                }
                                TrackListItem(
                                    song = song,
                                    isPlaying = isThisTrack && uiState.isPlaying,
                                    isBuffering = isThisTrack && isCurrentBuffering,
                                    isCachedOffline = isCached,
                                    isFavorite = uiState.isFavorite(song),
                                    onClick = { onIntent(SearchIntent.PlaySong(song, combinedSongs)) },
                                    onAddToPlaylistClick = { onIntent(SearchIntent.ShowAddToPlaylistDialog(song)) },
                                    onFavoriteToggle = { onIntent(SearchIntent.ToggleFavorite(song)) },
                                    onMenuClick = { selectedSongForContextMenu = song }
                                )
                            }
                        }

                        // 3. Artists Shelf (Horizontal Carousel)
                        val combinedArtists = (uiState.onlineArtists + uiState.artists).distinctBy { it.name.lowercase() }
                        if (combinedArtists.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = "Artists",
                                    onSeeAll = { onIntent(SearchIntent.SelectTab(SearchTab.ARTISTS)) }
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    items(combinedArtists, key = { "all_art_${it.id}_${it.name}" }) { artist ->
                                        ArtistCarouselCard(
                                            artist = artist,
                                            onClick = { onIntent(SearchIntent.OpenArtist(artist)) }
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Albums Shelf (Horizontal Carousel)
                        val combinedAlbums = (uiState.onlineAlbums + uiState.albums).distinctBy { it.title.lowercase() + it.artist.lowercase() }
                        if (combinedAlbums.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = "Albums",
                                    onSeeAll = { onIntent(SearchIntent.SelectTab(SearchTab.ALBUMS)) }
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    items(combinedAlbums, key = { "all_alb_${it.id}_${it.title}" }) { album ->
                                        AlbumCarouselCard(
                                            album = album,
                                            onClick = { onIntent(SearchIntent.OpenAlbum(album)) }
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Playlists Shelf (Horizontal Carousel)
                        if (uiState.onlinePlaylists.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = "Community Playlists",
                                    onSeeAll = { onIntent(SearchIntent.SelectTab(SearchTab.PLAYLISTS)) }
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    itemsIndexed(uiState.onlinePlaylists, key = { index, pl -> "all_pl_${pl.url}_$index" }) { _, playlist ->
                                        PlaylistCarouselCard(
                                            playlist = playlist,
                                            onClick = { onIntent(SearchIntent.OpenPlaylist(playlist)) }
                                        )
                                    }
                                }
                            }
                        }

                        // Loading state when searching initially
                        if (uiState.isSearchingOnline && combinedSongs.isEmpty() && combinedArtists.isEmpty()) {
                            items(5) {
                                TrackRowSkeleton()
                            }
                        }
                    }

                    // TAB: SONGS (Full infinite scroll stream)
                    if (uiState.activeTab == SearchTab.SONGS || uiState.activeTab == SearchTab.ONLINE) {
                        // Local song matches
                        if (uiState.songs.isNotEmpty()) {
                            item {
                                Text(
                                    text = "On Device (${uiState.songs.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotifyTextPrimary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(uiState.songs, key = { "song_loc_${it.id}" }) { song ->
                                val isThisTrack = song.id == uiState.currentSongId
                                val isCached = remember(song.id, uiState.cacheUpdateTick) {
                                    if (song.isLocal) true else isTrackCached(song)
                                }
                                TrackListItem(
                                    song = song,
                                    isPlaying = isThisTrack && uiState.isPlaying,
                                    isBuffering = isThisTrack && isCurrentBuffering,
                                    isCachedOffline = isCached,
                                    isFavorite = uiState.isFavorite(song),
                                    onClick = { onIntent(SearchIntent.PlaySong(song, uiState.songs)) },
                                    onAddToPlaylistClick = { onIntent(SearchIntent.ShowAddToPlaylistDialog(song)) },
                                    onFavoriteToggle = { onIntent(SearchIntent.ToggleFavorite(song)) },
                                    onMenuClick = { selectedSongForContextMenu = song }
                                )
                            }
                        }

                        if (communitySongs.isNotEmpty()) {
                            item {
                                Text(
                                    text = "From Community Playlists",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotifyGreen,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                            items(communitySongs, key = { "comm_song_${it.remoteVideoId ?: it.id}" }) { song ->
                                val isThisTrack = song.id == uiState.currentSongId
                                val isCached = remember(song.id, uiState.cacheUpdateTick) {
                                    if (song.isLocal) true else isTrackCached(song)
                                }
                                TrackListItem(
                                    song = song,
                                    isPlaying = isThisTrack && uiState.isPlaying,
                                    isBuffering = isThisTrack && isCurrentBuffering,
                                    isCachedOffline = isCached,
                                    isFavorite = uiState.isFavorite(song),
                                    onClick = {
                                        onIntent(SearchIntent.PlaySong(song, communitySongs + onlinePagedTracks.itemSnapshotList.items))
                                    },
                                    onAddToPlaylistClick = {
                                        onIntent(SearchIntent.ShowAddToPlaylistDialog(song))
                                    },
                                    onFavoriteToggle = { onIntent(SearchIntent.ToggleFavorite(song)) },
                                    onMenuClick = { selectedSongForContextMenu = song }
                                )
                            }
                        }

                        item {
                            Text(
                                text = "YouTube Music Streams",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }

                        val refreshState = onlinePagedTracks.loadState.refresh
                        if (refreshState is LoadState.Loading && onlinePagedTracks.itemCount == 0) {
                            items(6) {
                                TrackRowSkeleton()
                            }
                        } else if (refreshState is LoadState.Error && onlinePagedTracks.itemCount == 0) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Unable to reach YouTube Music.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SpotifyTextSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { onlinePagedTracks.retry() }) {
                                        Text("Retry", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        items(
                            count = onlinePagedTracks.itemCount,
                            key = { index ->
                                val item = onlinePagedTracks.peek(index)
                                "ytm_page_${item?.remoteVideoId ?: item?.id ?: index}_$index"
                            }
                        ) { index ->
                            val song = onlinePagedTracks[index]
                            if (song != null) {
                                if (song.contentUri !in renderedCommunityUris) {
                                    val isThisTrack = song.id == uiState.currentSongId
                                    val isCached = remember(song.id, uiState.cacheUpdateTick) {
                                        if (song.isLocal) true else isTrackCached(song)
                                    }
                                    TrackListItem(
                                        song = song,
                                        isPlaying = isThisTrack && uiState.isPlaying,
                                        isBuffering = isThisTrack && isCurrentBuffering,
                                        isCachedOffline = isCached,
                                        isFavorite = uiState.isFavorite(song),
                                        onClick = {
                                            val snapshotQueue = onlinePagedTracks.itemSnapshotList.items
                                            onIntent(SearchIntent.PlaySong(song, snapshotQueue))
                                        },
                                        onAddToPlaylistClick = {
                                            onIntent(SearchIntent.ShowAddToPlaylistDialog(song))
                                        },
                                        onFavoriteToggle = { onIntent(SearchIntent.ToggleFavorite(song)) },
                                        onMenuClick = { selectedSongForContextMenu = song }
                                    )
                                }
                            } else {
                                TrackRowSkeleton()
                            }
                        }

                        if (onlinePagedTracks.loadState.append is LoadState.Loading) {
                            items(2) {
                                TrackRowSkeleton()
                            }
                        }
                    }

                    // TAB: ARTISTS (Online + Local Artist Profiles)
                    if (uiState.activeTab == SearchTab.ARTISTS) {
                        val allArtists = (uiState.onlineArtists + uiState.artists).distinctBy { it.name.lowercase() }
                        if (uiState.isSearchingOnline && allArtists.isEmpty()) {
                            items(5) {
                                TrackRowSkeleton()
                            }
                        } else if (allArtists.isEmpty()) {
                            item {
                                Text(
                                    text = "No artists found for '${uiState.query}'",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SpotifyTextSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                                )
                            }
                        } else {
                            items(allArtists, key = { "tab_art_${it.id}_${it.name}" }) { artist ->
                                ArtistListRow(
                                    artist = artist,
                                    onClick = { onIntent(SearchIntent.OpenArtist(artist)) }
                                )
                            }
                        }
                    }

                    // TAB: ALBUMS (Online + Local Albums)
                    if (uiState.activeTab == SearchTab.ALBUMS) {
                        val allAlbums = (uiState.onlineAlbums + uiState.albums).distinctBy { it.title.lowercase() + it.artist.lowercase() }
                        if (uiState.isSearchingOnline && allAlbums.isEmpty()) {
                            items(5) {
                                TrackRowSkeleton()
                            }
                        } else if (allAlbums.isEmpty()) {
                            item {
                                Text(
                                    text = "No albums found for '${uiState.query}'",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SpotifyTextSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                                )
                            }
                        } else {
                            items(allAlbums, key = { "tab_alb_${it.id}_${it.title}" }) { album ->
                                AlbumListRow(
                                    album = album,
                                    onClick = { onIntent(SearchIntent.OpenAlbum(album)) }
                                )
                            }
                        }
                    }

                    // TAB: PLAYLISTS (Online Playlists)
                    if (uiState.activeTab == SearchTab.PLAYLISTS) {
                        if (uiState.isSearchingPlaylists && uiState.onlinePlaylists.isEmpty()) {
                            items(5) {
                                TrackRowSkeleton()
                            }
                        } else if (uiState.playlistSearchError != null && uiState.onlinePlaylists.isEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Unable to reach YouTube Music playlists.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SpotifyTextSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { onIntent(SearchIntent.RetryOnlineSearch) }) {
                                        Text("Retry", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else if (uiState.onlinePlaylists.isEmpty()) {
                            item {
                                Text(
                                    text = "No playlists found for '${uiState.query}'",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SpotifyTextSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                                )
                            }
                        } else {
                            itemsIndexed(uiState.onlinePlaylists, key = { index, playlist -> "tab_pl_${playlist.url}_$index" }) { _, playlist ->
                                RemotePlaylistItem(
                                    playlist = playlist,
                                    onClick = { onIntent(SearchIntent.OpenPlaylist(playlist)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

    // Floating Snackbar with Undo
    if (uiState.statusMessage != null) {
        Snackbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            containerColor = SpotifySurfaceElevated,
            contentColor = SpotifyTextPrimary,
            action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.canUndo) {
                        TextButton(onClick = { onIntent(SearchIntent.UndoDelete) }) {
                            Text("UNDO", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { onIntent(SearchIntent.DismissStatusMessage) }) {
                        Text(if (uiState.canUndo) "DISMISS" else "OK", color = SpotifyTextSecondary)
                    }
                }
            }
        ) {
            Text(uiState.statusMessage)
        }
    }
}

    // Add to Playlist Dialog
    val targetSong = uiState.songToAddToPlaylist
    if (targetSong != null && !uiState.showCreatePlaylistDialog) {
        AddToPlaylistDialog(
            song = targetSong,
            playlists = uiState.playlists,
            onDismiss = { onIntent(SearchIntent.ShowAddToPlaylistDialog(null)) },
            onCreateNewPlaylist = { onIntent(SearchIntent.ShowCreatePlaylistDialog(true)) },
            onSelectPlaylist = { playlistId ->
                onIntent(SearchIntent.AddSongToPlaylist(playlistId, targetSong))
            }
        )
    }

    // Create New Playlist Dialog
    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { onIntent(SearchIntent.ShowCreatePlaylistDialog(false)) },
            onCreate = { name ->
                onIntent(SearchIntent.CreatePlaylistAndAddSong(name))
            }
        )
    }

    // YouTube Music Song Context Menu Bottom Sheet
    if (selectedSongForContextMenu != null) {
        val song = selectedSongForContextMenu!!
        val context = LocalContext.current
        SongContextMenuBottomSheet(
            song = song,
            isFavorite = uiState.isFavorite(song),
            onDismiss = { selectedSongForContextMenu = null },
            onToggleFavorite = {
                onIntent(SearchIntent.ToggleFavorite(song))
                selectedSongForContextMenu = null
            },
            onStartRadio = {
                onIntent(SearchIntent.StartRadio(song))
                selectedSongForContextMenu = null
            },
            onPlayNext = {
                onIntent(SearchIntent.PlayNext(song))
                selectedSongForContextMenu = null
            },
            onAddToQueue = {
                onIntent(SearchIntent.AddToQueue(song))
                selectedSongForContextMenu = null
            },
            onAddToPlaylist = {
                onIntent(SearchIntent.ShowAddToPlaylistDialog(song))
                selectedSongForContextMenu = null
            },
            onGoToArtist = { artistName ->
                onIntent(SearchIntent.OpenArtistByName(artistName))
                selectedSongForContextMenu = null
            },
            onGoToAlbum = { albumTitle, artistName ->
                onIntent(SearchIntent.OpenAlbumByName(albumTitle, artistName))
                selectedSongForContextMenu = null
            },
            onShare = {
                val shareUrl = com.mousikify.core.model.DeepLinkCodec.encodeTrackUrl(song)
                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, song.title)
                    putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, "Share song via"))
                selectedSongForContextMenu = null
            }
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Subcomponents for YouTube Music Search Display
// -------------------------------------------------------------------------------------------------

@Composable
private fun SearchSectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = SpotifyTextPrimary
        )
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelMedium,
                    color = SpotifyGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TopResultArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SpotifyCardBackground,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "TOP RESULT",
                style = MaterialTheme.typography.labelSmall,
                color = SpotifyGreen,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF282828)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = SpotifyTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(38.dp)
                    )
                    if (!artist.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artist.avatarUrl,
                            contentDescription = artist.name,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = artist.monthlyListeners?.ifBlank { "Artist" } ?: "Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotifyTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = onShuffle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = SpotifyTextPrimary
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TopResultAlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SpotifyCardBackground,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = SpotifyTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(38.dp)
                )
                if (!album.artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = album.artworkUri,
                        contentDescription = album.title,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOP RESULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyGreen,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = buildString {
                    append("Album • ")
                    append(album.artist)
                    if (album.year > 0) {
                        append(" • ")
                        append(album.year)
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyTextSecondary
                )
            }
        }
    }
}

@Composable
private fun TopResultSongCard(
    song: Song,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SpotifyCardBackground,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
                if (!song.artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = song.artworkUri,
                        contentDescription = song.title,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOP RESULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyGreen,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) SpotifyGreen else SpotifyTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Song • ${song.artistName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyTextSecondary
                )
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistCarouselCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(42.dp)
            )
            if (!artist.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artist.avatarUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = SpotifyTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AlbumCarouselCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
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
            if (!album.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = album.artworkUri,
                    contentDescription = album.title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = SpotifyTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Album • ${album.artist}",
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCarouselCard(
    playlist: RemotePlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(48.dp)
            )
            if (!playlist.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = SpotifyTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (playlist.uploader.isNotBlank()) "Playlist • ${playlist.uploader}" else "Playlist",
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistListRow(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = SpotifyTextSecondary)
            if (!artist.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artist.avatarUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = SpotifyTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = artist.monthlyListeners?.ifBlank { "Artist" } ?: "Artist"
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyTextSecondary
            )
        }
    }
}

@Composable
private fun AlbumListRow(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp)
            )
            if (!album.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = album.artworkUri,
                    contentDescription = album.title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = SpotifyTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = buildString {
                append("Album • ")
                append(album.artist)
                if (album.year > 0) {
                    append(" • ")
                    append(album.year)
                }
            }
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RemotePlaylistItem(
    playlist: RemotePlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp)
            )
            if (!playlist.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = SpotifyTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitle = buildString {
                append("Playlist")
                if (playlist.uploader.isNotBlank()) {
                    append(" • ")
                    append(playlist.uploader)
                }
                if (playlist.streamCount > 0) {
                    append(" • ")
                    append("${playlist.streamCount} songs")
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RemotePlaylistDetailView(
    playlist: RemotePlaylist,
    uiState: SearchUiState,
    isTrackCached: (Song) -> Boolean,
    onIntent: (SearchIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            IconButton(
                onClick = { onIntent(SearchIntent.ClosePlaylistDetail) },
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpotifyTextPrimary
                )
            }
        }

        if (uiState.statusMessage != null) {
            item {
                Surface(
                    color = SpotifyGreen.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyGreen,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.canUndo) {
                            TextButton(
                                onClick = { onIntent(SearchIntent.UndoDelete) },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text("UNDO", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(
                            onClick = { onIntent(SearchIntent.DismissStatusMessage) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Dismiss",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyCardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(64.dp)
                    )
                    if (!playlist.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = playlist.thumbnailUrl,
                            contentDescription = playlist.name,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                val subtitle = buildString {
                    append("YouTube Music")
                    if (playlist.uploader.isNotBlank()) {
                        append(" • ")
                        append(playlist.uploader)
                    }
                    if (uiState.openedPlaylistTracks.isNotEmpty()) {
                        append(" • ")
                        append("${uiState.openedPlaylistTracks.size} songs")
                    } else if (playlist.streamCount > 0) {
                        append(" • ")
                        append("${playlist.streamCount} songs")
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                val isPlaylistSaved = remember(playlist.name, uiState.playlists) {
                    uiState.playlists.any { it.name.equals(playlist.name, ignoreCase = true) }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (uiState.openedPlaylistTracks.isNotEmpty()) {
                                onIntent(SearchIntent.ToggleShuffle(uiState.openedPlaylistTracks))
                            }
                        },
                        enabled = uiState.openedPlaylistTracks.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (uiState.isShuffleEnabled) SpotifyGreen else SpotifyTextSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    val downloadedCount = remember(uiState.openedPlaylistTracks, uiState.cacheUpdateTick) {
                        uiState.openedPlaylistTracks.count { if (it.isLocal) true else isTrackCached(it) }
                    }
                    DownloadProgressRing(
                        downloadedCount = downloadedCount,
                        totalCount = uiState.openedPlaylistTracks.size,
                        isCollectionSaved = isPlaylistSaved || uiState.isSavingPlaylistLocally
                    )

                    Button(
                        onClick = {
                            if (uiState.openedPlaylistTracks.isNotEmpty()) {
                                onIntent(SearchIntent.PlaySong(uiState.openedPlaylistTracks.first(), uiState.openedPlaylistTracks))
                            }
                        },
                        enabled = uiState.openedPlaylistTracks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            contentColor = Color.Black,
                            disabledContainerColor = SpotifyGreen.copy(alpha = 0.4f),
                            disabledContentColor = Color.Black.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (isPlaylistSaved) {
                                onIntent(SearchIntent.UnsavePlaylistLocally(playlist, uiState.openedPlaylistTracks))
                            } else {
                                onIntent(SearchIntent.SavePlaylistLocally(playlist, uiState.openedPlaylistTracks))
                            }
                        },
                        enabled = isPlaylistSaved || (uiState.openedPlaylistTracks.isNotEmpty() && !uiState.isSavingPlaylistLocally),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaylistSaved) SpotifyGreen else SpotifyCardBackground,
                            contentColor = if (isPlaylistSaved) Color.Black else SpotifyTextPrimary,
                            disabledContainerColor = if (isPlaylistSaved) SpotifyGreen.copy(alpha = 0.4f) else SpotifyCardBackground.copy(alpha = 0.4f),
                            disabledContentColor = if (isPlaylistSaved) Color.Black.copy(alpha = 0.5f) else SpotifyTextSecondary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        if (uiState.isSavingPlaylistLocally) {
                            CircularProgressIndicator(
                                color = if (isPlaylistSaved) Color.Black else SpotifyGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saving...", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        } else {
                            Icon(
                                imageVector = if (isPlaylistSaved) Icons.Default.CheckCircle else Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                tint = if (isPlaylistSaved) Color.Black else SpotifyTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlaylistSaved) "Saved" else "Save playlist",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isPlaylistSaved) Color.Black else SpotifyTextPrimary
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isLoadingPlaylistTracks) {
            items(8) {
                TrackRowSkeleton()
            }
        } else if (uiState.playlistTracksError != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Unable to load playlist tracks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotifyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { onIntent(SearchIntent.RetryLoadPlaylistTracks) }) {
                        Text("Retry", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            itemsIndexed(
                uiState.openedPlaylistTracks,
                key = { index, song -> "pl_track_${song.remoteVideoId ?: song.id}_$index" }
            ) { _, song ->
                val isThisTrack = song.id == uiState.currentSongId
                val isCached = remember(song.id, uiState.cacheUpdateTick) {
                    if (song.isLocal) true else isTrackCached(song)
                }
                TrackListItem(
                    song = song,
                    isPlaying = isThisTrack && uiState.isPlaying,
                    isBuffering = false,
                    isCachedOffline = isCached,
                    isFavorite = uiState.isFavorite(song),
                    onClick = {
                        onIntent(SearchIntent.PlaySong(song, uiState.openedPlaylistTracks))
                    },
                    onAddToPlaylistClick = {
                        onIntent(SearchIntent.ShowAddToPlaylistDialog(song))
                    },
                    onFavoriteToggle = { onIntent(SearchIntent.ToggleFavorite(song)) }
                )
            }
        }
    }
}

@Composable
private fun SearchRecentQueryChips(
    recentSearches: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (recentSearches.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SpotifyTextPrimary
            )
            TextButton(
                onClick = onClearAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyTextSecondary
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            items(recentSearches, key = { "recent_chip_$it" }) { query ->
                Surface(
                    color = SpotifyCardBackground,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable { onSelect(query) }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = SpotifyTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = query,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onRemove(query) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Remove search",
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedSongsDiscoverySection(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySong: (Song, List<Song>) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty() && !isRefreshing) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Recommended for you",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary
                )
                Text(
                    text = "Based on your music taste",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyTextSecondary
                )
            }
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.size(32.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = SpotifyGreen,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh recommendations",
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(songs, key = { "rec_${it.id}_${it.title}" }) { song ->
                val isCurrent = song.id == currentSongId
                Column(
                    modifier = Modifier
                        .width(136.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPlaySong(song, songs) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifyCardBackground)
                    ) {
                        AsyncImage(
                            model = song.artworkUri,
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent && isPlaying) SpotifyGreen else Color.Black.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = if (isCurrent && isPlaying) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrent) SpotifyGreen else SpotifyTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artistName,
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreArtistsDiscoverySection(
    artists: List<Artist>,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = "Explore Artists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SpotifyTextPrimary
            )
            Text(
                text = "Similar artists you might enjoy",
                style = MaterialTheme.typography.labelSmall,
                color = SpotifyTextSecondary
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            items(artists, key = { "exp_art_${it.name}" }) { artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(92.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onArtistClick(artist.name) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(SpotifyCardBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!artist.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = artist.avatarUrl,
                                contentDescription = artist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SpotifyTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = artist.monthlyListeners ?: "Artist",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreAlbumsDiscoverySection(
    albums: List<Album>,
    onAlbumClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = "Explore Albums",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SpotifyTextPrimary
            )
            Text(
                text = "Popular releases and collections",
                style = MaterialTheme.typography.labelSmall,
                color = SpotifyTextSecondary
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            items(albums, key = { "exp_alb_${it.title}_${it.artist}" }) { album ->
                Column(
                    modifier = Modifier
                        .width(128.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAlbumClick(album.title, album.artist) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(128.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifyCardBackground)
                    ) {
                        if (!album.artworkUri.isNullOrBlank()) {
                            AsyncImage(
                                model = album.artworkUri,
                                contentDescription = album.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(44.dp).align(Alignment.Center)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SpotifyTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseGenresDiscoveryGrid(
    genres: List<GenreCategory>,
    onSelectGenre: (GenreCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp)) {
        Text(
            text = "Browse Genres & Moods",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = SpotifyTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val pairs = remember(genres) { genres.chunked(2) }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (pair in pairs) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (genre in pair) {
                        val brush = remember(genre.gradientColors) {
                            val colors = genre.gradientColors.map { Color(it) }
                            if (colors.size >= 2) Brush.linearGradient(colors)
                            else Brush.linearGradient(listOf(Color(0xFF333333), Color(0xFF1E1E1E)))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(88.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(brush)
                                .clickable { onSelectGenre(genre) }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = genre.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                            Icon(
                                imageVector = when (genre.id) {
                                    "genre_pop", "genre_electronic" -> Icons.Default.MusicNote
                                    "genre_rock" -> Icons.Default.Radio
                                    "genre_hiphop" -> Icons.Default.PlayArrow
                                    else -> Icons.Default.Album
                                },
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .size(42.dp)
                                    .align(Alignment.BottomEnd)
                                    .rotate(-15f)
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
