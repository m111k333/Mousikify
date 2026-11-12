package com.mousikify.feature.library.generator

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.component.TrackListItem
import com.mousikify.core.designsystem.theme.SpotifyBackground
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifySurfaceElevated
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary
import com.mousikify.core.media.recommendation.DeezerDiscoveryDepth
import com.mousikify.core.media.recommendation.LastFmRelevance
import com.mousikify.core.media.recommendation.RadioRefreshConfig
import com.mousikify.core.media.recommendation.RadioSource
import com.mousikify.core.media.recommendation.YouTubeRadioMode
import com.mousikify.core.model.Artist
import com.mousikify.core.model.Song

@Composable
fun PlaylistGeneratorRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistGeneratorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    PlaylistGeneratorScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onAddSeed = viewModel::addSeed,
        onAddArtistSeed = viewModel::addArtistSeed,
        onRemoveSeed = viewModel::removeSeed,
        onTrackCountChanged = viewModel::onTrackCountChanged,
        onGenerateClick = { viewModel.generatePlaylist(forceRefresh = false) },
        onRerollClick = viewModel::openRefreshDialog,
        onOpenRefreshDialog = viewModel::openRefreshDialog,
        onDismissRefreshDialog = viewModel::dismissRefreshDialog,
        onConfirmRefreshConfig = viewModel::refreshPlaylistWithConfig,
        onPlayTrack = viewModel::playTrack,
        onPlayAll = viewModel::playAll,
        onShuffleAndPlayAll = viewModel::shuffleAndPlayAll,
        onToggleFavorite = viewModel::toggleFavorite,
        onDismissGeneratedView = viewModel::dismissGeneratedView,
        onSaveToLibrary = viewModel::savePlaylistToLibrary,
        onClearSavedNotification = viewModel::clearSavedNotification,
        defaultPlaylistTitle = viewModel.defaultPlaylistTitle(),
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistGeneratorScreen(
    uiState: PlaylistGeneratorUiState,
    onBackClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onAddSeed: (Song) -> Unit,
    onAddArtistSeed: (Artist) -> Unit,
    onRemoveSeed: (Long) -> Unit,
    onTrackCountChanged: (Int) -> Unit,
    onGenerateClick: () -> Unit,
    onRerollClick: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAndPlayAll: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDismissGeneratedView: () -> Unit,
    onSaveToLibrary: (String?, ((Long) -> Unit)?) -> Unit,
    onClearSavedNotification: () -> Unit,
    defaultPlaylistTitle: String,
    modifier: Modifier = Modifier,
    onOpenRefreshDialog: () -> Unit = onRerollClick,
    onDismissRefreshDialog: () -> Unit = {},
    onConfirmRefreshConfig: (RadioRefreshConfig, Int) -> Unit = { _, _ -> }
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showSaveDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.isShowingGeneratedView) {
        onDismissGeneratedView()
    }

    LaunchedEffect(uiState.savedPlaylistName) {
        uiState.savedPlaylistName?.let { name ->
            snackbarHostState.showSnackbar(
                message = "Saved \"$name\" to your library!",
                duration = SnackbarDuration.Short
            )
            onClearSavedNotification()
        }
    }

    if (showSaveDialog) {
        var playlistNameInput by remember { mutableStateOf(defaultPlaylistTitle) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = SpotifyCardBackground,
            title = {
                Text(
                    text = "Save to Library",
                    color = SpotifyTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Choose a name for your generated playlist:",
                        color = SpotifyTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen,
                            unfocusedBorderColor = SpotifyTextSecondary,
                            focusedTextColor = SpotifyTextPrimary,
                            unfocusedTextColor = SpotifyTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        onSaveToLibrary(playlistNameInput, null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = SpotifyTextSecondary)
                }
            }
        )
    }

    if (uiState.isShowRefreshDialog) {
        PlaylistRefreshDialog(
            config = uiState.refreshConfig,
            trackCount = uiState.trackCount,
            onDismiss = onDismissRefreshDialog,
            onConfirm = onConfirmRefreshConfig
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground),
        containerColor = SpotifyBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (uiState.isShowingGeneratedView) {
                        onDismissGeneratedView()
                    } else {
                        onBackClick()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SpotifyTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (uiState.isShowingGeneratedView) defaultPlaylistTitle else "Smart Playlist Generator",
                    color = SpotifyTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!uiState.isShowingGeneratedView && uiState.seeds.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpotifyGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (uiState.seeds.size == 1) "1 seed" else "${uiState.seeds.size} seeds",
                            color = SpotifyGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (uiState.isShowingGeneratedView && uiState.generatedTracks.isNotEmpty()) {
            // Dedicated Generated Playlist Page
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // Header Artwork & Details
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val firstCover = uiState.generatedTracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF8E24AA), Color(0xFF1DB954))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!firstCover.isNullOrBlank()) {
                                AsyncImage(
                                    model = firstCover,
                                    contentDescription = defaultPlaylistTitle,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = defaultPlaylistTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.isGenerating) "Refreshing mix..." else "Generated mix • ${uiState.generatedTracks.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Actions Row: Shuffle + Save + Re-roll + Big Play Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Shuffle
                                IconButton(onClick = onShuffleAndPlayAll) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = SpotifyTextSecondary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Save to Library
                                IconButton(onClick = { showSaveDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = "Save to Library",
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Re-roll / Refresh settings
                                IconButton(
                                    onClick = onOpenRefreshDialog,
                                    enabled = !uiState.isGenerating
                                ) {
                                    if (uiState.isGenerating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = SpotifyGreen,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Mix Settings & Refresh",
                                            tint = SpotifyTextSecondary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }

                            // Big Green Play Button
                            Button(
                                onClick = onPlayAll,
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                shape = CircleShape,
                                modifier = Modifier.size(52.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play all",
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Track rows
                itemsIndexed(uiState.generatedTracks, key = { index, s -> "${s.id}_$index" }) { index, song ->
                    TrackListItem(
                        song = song,
                        isPlaying = false,
                        onClick = { onPlayTrack(index) },
                        trackNumber = index + 1,
                        isFavorite = song.isFavorite,
                        onFavoriteToggle = { onToggleFavorite(song) }
                    )
                }
            }
        } else {
            // Seed Picker Search View
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // Active Seed Chips Row
                if (uiState.seeds.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.seeds.forEach { seed ->
                                    SeedCard(
                                        song = seed,
                                        onRemove = { onRemoveSeed(seed.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Search Bar (Styled like SearchScreen)
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            textStyle = TextStyle(
                                color = Color(0xFF121212),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            placeholder = {
                                Text(
                                    text = "Search songs or artists for seeds...",
                                    color = Color(0xFF555555),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFF121212)
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = Color(0xFF121212)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedTextColor = Color(0xFF121212),
                                unfocusedTextColor = Color(0xFF121212),
                                cursorColor = Color(0xFF121212),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Search Results (When querying)
                if (uiState.searchQuery.isNotBlank()) {
                    if (uiState.isSearching) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = SpotifyGreen,
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    }

                    // Matching Artists Shelf
                    if (uiState.searchArtists.isNotEmpty()) {
                        item {
                            Text(
                                text = "Artists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextPrimary,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(uiState.searchArtists) { artist ->
                                    val isAlreadySeed = uiState.seeds.any { it.artistName.equals(artist.name, ignoreCase = true) }
                                    ArtistSeedCard(
                                        artist = artist,
                                        isAdded = isAlreadySeed,
                                        onClick = { if (!isAlreadySeed) onAddArtistSeed(artist) }
                                    )
                                }
                            }
                        }
                    }

                    // Matching Songs List
                    if (uiState.searchResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Songs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextPrimary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(uiState.searchResults) { song ->
                            val isAlreadySeed = uiState.seeds.any { it.id == song.id }
                            SearchResultRow(
                                song = song,
                                isAdded = isAlreadySeed,
                                onSelect = { if (!isAlreadySeed) onAddSeed(song) }
                            )
                        }
                    }
                } else {
                    // Default State: Liked Songs & Recent Tracks Shelves

                    // 1. Liked Songs Shelf (with album cover)
                    if (uiState.recentlyLikedSongs.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFE91E63),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Your Liked Songs",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotifyTextPrimary
                                )
                            }
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.recentlyLikedSongs) { song ->
                                    val isAlreadySeed = uiState.seeds.any { it.id == song.id }
                                    CoverCard(
                                        song = song,
                                        isAdded = isAlreadySeed,
                                        onClick = { if (!isAlreadySeed) onAddSeed(song) }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Recently Played Shelf (with album cover)
                    if (uiState.recentlyPlayedSongs.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recently Played",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotifyTextPrimary
                                )
                            }
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.recentlyPlayedSongs) { song ->
                                    val isAlreadySeed = uiState.seeds.any { it.id == song.id }
                                    CoverCard(
                                        song = song,
                                        isAdded = isAlreadySeed,
                                        onClick = { if (!isAlreadySeed) onAddSeed(song) }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Playlist Length Options
                    item {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Playlist Length",
                                    color = SpotifyTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${uiState.trackCount} songs",
                                    color = SpotifyGreen,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(15, 25, 35).forEach { preset ->
                                    val isSelected = uiState.trackCount == preset
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onTrackCountChanged(preset) },
                                        label = { Text("$preset tracks") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SpotifyGreen,
                                            selectedLabelColor = Color.Black,
                                            containerColor = SpotifyCardBackground,
                                            labelColor = SpotifyTextPrimary
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }

                            Slider(
                                value = uiState.trackCount.toFloat(),
                                onValueChange = { onTrackCountChanged(it.toInt()) },
                                valueRange = 10f..50f,
                                steps = 7,
                                colors = SliderDefaults.colors(
                                    thumbColor = SpotifyGreen,
                                    activeTrackColor = SpotifyGreen,
                                    inactiveTrackColor = SpotifyCardBackground
                                ),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // 4. Create Playlist Button
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = onGenerateClick,
                                enabled = uiState.seeds.isNotEmpty() && !uiState.isGenerating,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SpotifyGreen,
                                    disabledContainerColor = SpotifyCardBackground
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                if (uiState.isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Creating your mix...", color = Color.Black, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (uiState.seeds.isNotEmpty()) Color.Black else SpotifyTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (uiState.seeds.isEmpty()) "Add seed songs above" else "Create Playlist",
                                        color = if (uiState.seeds.isNotEmpty()) Color.Black else SpotifyTextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    uiState.errorMessage?.let { error ->
                        item {
                            Text(
                                text = error,
                                color = Color(0xFFFF5252),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeedCard(
    song: Song,
    onRemove: () -> Unit
) {
    Surface(
        color = SpotifySurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                if (!song.artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = song.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.width(110.dp)) {
                Text(
                    text = song.title,
                    color = SpotifyTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artistName,
                    color = SpotifyTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove seed",
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun CoverCard(
    song: Song,
    isAdded: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground)
        ) {
            if (!song.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = song.title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Quick add / added indicator pill
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isAdded) SpotifyGreen else Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAdded) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (isAdded) "Added" else "Add",
                    tint = if (isAdded) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            color = if (isAdded) SpotifyGreen else SpotifyTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artistName,
            color = SpotifyTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistSeedCard(
    artist: Artist,
    isAdded: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(SpotifyCardBackground)
        ) {
            if (!artist.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artist.avatarUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isAdded) SpotifyGreen else Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAdded) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    tint = if (isAdded) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            color = if (isAdded) SpotifyGreen else SpotifyTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Artist",
            color = SpotifyTextSecondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchResultRow(
    song: Song,
    isAdded: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            if (!song.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isAdded) SpotifyGreen else SpotifyTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artistName,
                color = SpotifyTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isAdded) SpotifyGreen.copy(alpha = 0.2f) else SpotifyGreen)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isAdded) "Added" else "+ Add",
                color = if (isAdded) SpotifyGreen else Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PlaylistRefreshDialog(
    config: RadioRefreshConfig,
    trackCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (RadioRefreshConfig, Int) -> Unit
) {
    var currentConfig by remember(config) { mutableStateOf(config) }
    var currentCount by remember(trackCount) { mutableStateOf(trackCount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpotifyCardBackground,
        title = {
            Text(
                text = "Mix Generator Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SpotifyTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Discovery Source (Last.fm, Deezer, YouTube)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DISCOVERY SOURCE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GeneratorOptionChip(
                            text = "Last.fm",
                            selected = currentConfig.source == RadioSource.LAST_FM,
                            onClick = { currentConfig = currentConfig.copy(source = RadioSource.LAST_FM) },
                            modifier = Modifier.weight(1f)
                        )
                        GeneratorOptionChip(
                            text = "Deezer",
                            selected = currentConfig.source == RadioSource.DEEZER,
                            onClick = { currentConfig = currentConfig.copy(source = RadioSource.DEEZER) },
                            modifier = Modifier.weight(1f)
                        )
                        GeneratorOptionChip(
                            text = "YouTube",
                            selected = currentConfig.source == RadioSource.YOUTUBE_MUSIC,
                            onClick = { currentConfig = currentConfig.copy(source = RadioSource.YOUTUBE_MUSIC) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. Source-specific options
                when (currentConfig.source) {
                    RadioSource.LAST_FM -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "RELEVANCE TO SEEDS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextSecondary,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GeneratorOptionChip(
                                    text = "High Match",
                                    selected = currentConfig.relevance == LastFmRelevance.HIGH_MATCH,
                                    onClick = { currentConfig = currentConfig.copy(relevance = LastFmRelevance.HIGH_MATCH) },
                                    modifier = Modifier.weight(1f)
                                )
                                GeneratorOptionChip(
                                    text = "Balanced",
                                    selected = currentConfig.relevance == LastFmRelevance.BALANCED,
                                    onClick = { currentConfig = currentConfig.copy(relevance = LastFmRelevance.BALANCED) },
                                    modifier = Modifier.weight(1f)
                                )
                                GeneratorOptionChip(
                                    text = "Discovery",
                                    selected = currentConfig.relevance == LastFmRelevance.DEEP_DISCOVERY,
                                    onClick = { currentConfig = currentConfig.copy(relevance = LastFmRelevance.DEEP_DISCOVERY) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    RadioSource.DEEZER -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "DISCOVERY DEPTH",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextSecondary,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GeneratorOptionChip(
                                    text = "Top Hits",
                                    selected = currentConfig.deezerDepth == DeezerDiscoveryDepth.TOP_HITS,
                                    onClick = { currentConfig = currentConfig.copy(deezerDepth = DeezerDiscoveryDepth.TOP_HITS) },
                                    modifier = Modifier.weight(1f)
                                )
                                GeneratorOptionChip(
                                    text = "Balanced",
                                    selected = currentConfig.deezerDepth == DeezerDiscoveryDepth.BALANCED,
                                    onClick = { currentConfig = currentConfig.copy(deezerDepth = DeezerDiscoveryDepth.BALANCED) },
                                    modifier = Modifier.weight(1f)
                                )
                                GeneratorOptionChip(
                                    text = "Deep Cuts",
                                    selected = currentConfig.deezerDepth == DeezerDiscoveryDepth.DEEP_CUTS,
                                    onClick = { currentConfig = currentConfig.copy(deezerDepth = DeezerDiscoveryDepth.DEEP_CUTS) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    RadioSource.YOUTUBE_MUSIC -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SELECTION MODE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyTextSecondary,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GeneratorOptionChip(
                                    text = "Top Queue",
                                    selected = currentConfig.youtubeMode == YouTubeRadioMode.TOP_QUEUE,
                                    onClick = { currentConfig = currentConfig.copy(youtubeMode = YouTubeRadioMode.TOP_QUEUE) },
                                    modifier = Modifier.weight(1f)
                                )
                                GeneratorOptionChip(
                                    text = "Fresh Shuffle",
                                    selected = currentConfig.youtubeMode == YouTubeRadioMode.FRESH_SHUFFLE,
                                    onClick = { currentConfig = currentConfig.copy(youtubeMode = YouTubeRadioMode.FRESH_SHUFFLE) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 3. Number of Songs / Playlist Size
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NUMBER OF SONGS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyTextSecondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "$currentCount songs",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyGreen
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 25, 35, 50).forEach { countPreset ->
                            GeneratorOptionChip(
                                text = "$countPreset",
                                selected = currentCount == countPreset,
                                onClick = { currentCount = countPreset },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Slider(
                        value = currentCount.toFloat(),
                        onValueChange = { currentCount = it.toInt() },
                        valueRange = 10f..50f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = SpotifyGreen,
                            activeTrackColor = SpotifyGreen,
                            inactiveTrackColor = SpotifySurfaceElevated
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // 4. Songs from each artist
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SONGS FROM EACH ARTIST",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GeneratorOptionChip(
                            text = "Max 1",
                            selected = currentConfig.maxPerArtist == 1,
                            onClick = { currentConfig = currentConfig.copy(maxPerArtist = 1) },
                            modifier = Modifier.weight(1f)
                        )
                        GeneratorOptionChip(
                            text = "Max 2",
                            selected = currentConfig.maxPerArtist == 2,
                            onClick = { currentConfig = currentConfig.copy(maxPerArtist = 2) },
                            modifier = Modifier.weight(1f)
                        )
                        GeneratorOptionChip(
                            text = "Max 3",
                            selected = currentConfig.maxPerArtist == 3,
                            onClick = { currentConfig = currentConfig.copy(maxPerArtist = 3) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentConfig, currentCount) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Refresh Mix",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Cancel",
                    color = SpotifyTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    )
}

@Composable
private fun GeneratorOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SpotifyGreen else SpotifySurfaceElevated,
        contentColor = if (selected) Color.Black else SpotifyTextPrimary
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
