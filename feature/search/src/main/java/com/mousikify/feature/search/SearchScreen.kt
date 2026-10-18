package com.mousikify.feature.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mousikify.core.designsystem.component.TrackListItem
import com.mousikify.core.designsystem.theme.SpotifyBackground
import com.mousikify.core.designsystem.theme.SpotifyCardBackground
import com.mousikify.core.designsystem.theme.SpotifyGreen
import com.mousikify.core.designsystem.theme.SpotifyTextPrimary
import com.mousikify.core.designsystem.theme.SpotifyTextSecondary

@Composable
fun SearchRoute(
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    SearchScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onIntent: (SearchIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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

        // Spotify Search Bar
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { onIntent(SearchIntent.UpdateQuery(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("What do you want to listen to?", color = Color(0xFF757575)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Black)
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { onIntent(SearchIntent.ClearQuery) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Black)
                    }
                }
            },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                cursorColor = SpotifyGreen,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Search Category Tabs
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
                        selected = uiState.activeTab == SearchTab.SONGS,
                        onClick = { onIntent(SearchIntent.SelectTab(SearchTab.SONGS)) },
                        label = { Text("Songs (${uiState.songs.size})") },
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
                        label = { Text("Artists (${uiState.artists.size})") },
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
                        label = { Text("Albums (${uiState.albums.size})") },
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

        // Search Results
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            if (uiState.query.isBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Play what you love\nSearch for artists, songs, or albums",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SpotifyTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                if (uiState.activeTab == SearchTab.ALL || uiState.activeTab == SearchTab.SONGS) {
                    items(uiState.songs) { song ->
                        TrackListItem(
                            song = song,
                            isPlaying = song.id == uiState.currentSongId && uiState.isPlaying,
                            onClick = { onIntent(SearchIntent.PlaySong(song, uiState.songs)) },
                            onFavoriteToggle = { onIntent(SearchIntent.ToggleFavorite(song)) }
                        )
                    }
                }

                if (uiState.activeTab == SearchTab.ALL || uiState.activeTab == SearchTab.ARTISTS) {
                    items(uiState.artists) { artist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyCardBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = SpotifyTextSecondary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Artist • ${artist.songCount} songs", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
                            }
                        }
                    }
                }

                if (uiState.activeTab == SearchTab.ALL || uiState.activeTab == SearchTab.ALBUMS) {
                    items(uiState.albums) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SpotifyCardBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = null,
                                    tint = SpotifyTextSecondary.copy(alpha = 0.35f),
                                    modifier = Modifier.size(24.dp)
                                )
                                AsyncImage(
                                    model = album.artworkUri,
                                    contentDescription = album.title,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Album • ${album.artist}", style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
