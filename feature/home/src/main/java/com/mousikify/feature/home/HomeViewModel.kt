package com.mousikify.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.Song
import com.mousikify.core.scanner.indexer.MediaStoreScanner
import com.mousikify.core.scanner.indexer.SafFolderScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class SmartMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val gradientColors: List<Long>,
    val songs: List<Song>
)

data class HomeUiState(
    val greeting: String = "",
    val totalSongCount: Int = 0,
    val recentlyPlayed: List<Song> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
    val forgottenFavorites: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val smartMixes: List<SmartMix> = emptyList(),
    val likedCount: Int = 0,
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val statusMessage: String? = null
)

sealed interface HomeIntent {
    data class PlaySong(val song: Song, val queue: List<Song>) : HomeIntent
    data class PlayMix(val mix: SmartMix) : HomeIntent
    data class PlayPlaylist(val playlist: Playlist) : HomeIntent
    data class ToggleFavorite(val song: Song) : HomeIntent
    data object PlayLikedSongs : HomeIntent
    data class ImportFolder(val uri: Uri) : HomeIntent
    data object Rescan : HomeIntent
    data object DismissStatusMessage : HomeIntent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val musicController: MusicController,
    private val mediaStoreScanner: MediaStoreScanner,
    private val safFolderScanner: SafFolderScanner,
    private val preferences: PlaybackPreferences
) : ViewModel() {

    private val greeting: String
        get() {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..11 -> "Good morning"
                in 12..17 -> "Good afternoon"
                else -> "Good evening"
            }
        }

    private val _isScanning = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    private val playbackState = combine(musicController.currentSong, musicController.isPlaying) { song, isPlaying ->
        Pair(song, isPlaying)
    }

    private val scanState = combine(_isScanning, _statusMessage) { scanning, msg ->
        Pair(scanning, msg)
    }

    private val songsAndPlaylists = combine(
        songRepository.allSongs,
        playlistRepository.playlists,
        songRepository.favorites
    ) { allSongs, playlists, favorites ->
        Triple(allSongs, playlists, favorites)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        songsAndPlaylists,
        songRepository.recentlyPlayed,
        songRepository.forgottenFavorites,
        playbackState,
        scanState
    ) { (allSongs, playlists, favorites), recent, forgotten, (currentSong, isPlaying), (isScanning, statusMsg) ->
        val mixes = generateSmartMixes(allSongs, favorites)
        val recentlyAdded = allSongs.sortedByDescending { it.dateAdded }.take(20)

        HomeUiState(
            greeting = greeting,
            totalSongCount = allSongs.size,
            recentlyPlayed = recent,
            recentlyAdded = recentlyAdded,
            forgottenFavorites = forgotten,
            playlists = playlists,
            smartMixes = mixes,
            likedCount = favorites.size,
            currentSongId = currentSong?.id,
            isPlaying = isPlaying,
            isLoading = false,
            isScanning = isScanning,
            statusMessage = statusMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(greeting = greeting, isLoading = true)
    )

    private fun generateSmartMixes(allSongs: List<Song>, favorites: List<Song>): List<SmartMix> {
        if (allSongs.isEmpty()) return emptyList()

        val mixes = mutableListOf<SmartMix>()
        val artists = allSongs.map { it.artistName }.filter { it.isNotBlank() && it != "<unknown>" }.distinct()

        // 1. Daily Mix 1: Top artists mix
        val topArtistsStr = artists.take(3).joinToString(", ")
        mixes.add(
            SmartMix(
                id = "daily_mix_1",
                title = "Daily Mix 1",
                subtitle = if (topArtistsStr.isNotBlank()) "$topArtistsStr, and more" else "Curated for your day",
                gradientColors = listOf(0xFF1E3264, 0xFF8D67AB),
                songs = allSongs.shuffled(kotlin.random.Random(42))
            )
        )

        // 2. Daily Mix 2: Fresh rotation
        if (allSongs.size > 2) {
            val otherArtists = artists.drop(3).take(3).joinToString(", ")
            mixes.add(
                SmartMix(
                    id = "daily_mix_2",
                    title = "Daily Mix 2",
                    subtitle = if (otherArtists.isNotBlank()) "$otherArtists, and more" else "Diverse offline rotation",
                    gradientColors = listOf(0xFF8400E7, 0xFFE91429),
                    songs = allSongs.shuffled(kotlin.random.Random(1337))
                )
            )
        }

        // 3. Chill Mix
        mixes.add(
            SmartMix(
                id = "chill_mix",
                title = "Chill Mix",
                subtitle = "Relaxing offline background vibes",
                gradientColors = listOf(0xFF1E7B5C, 0xFF1DB954),
                songs = allSongs.shuffled(kotlin.random.Random(999))
            )
        )

        // 4. Heavy Rotation / Most Played
        val topPlayed = allSongs.sortedByDescending { it.playCount }.take(25)
        mixes.add(
            SmartMix(
                id = "heavy_rotation",
                title = "Heavy Rotation",
                subtitle = "Your most played tracks on repeat",
                gradientColors = listOf(0xFFBA5A31, 0xFFE8115B),
                songs = if (topPlayed.any { it.playCount > 0 }) topPlayed else allSongs.take(25)
            )
        )

        // 5. Favorites Mix
        if (favorites.isNotEmpty()) {
            mixes.add(
                SmartMix(
                    id = "favorites_mix",
                    title = "Favorites Mix",
                    subtitle = "Only the offline tracks you love",
                    gradientColors = listOf(0xFF450AF5, 0xFFC4EFD9),
                    songs = favorites
                )
            )
        }

        return mixes
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.PlaySong -> {
                musicController.playQueue(intent.queue, intent.queue.indexOfFirst { it.id == intent.song.id }.coerceAtLeast(0))
            }
            is HomeIntent.PlayMix -> {
                if (intent.mix.songs.isNotEmpty()) {
                    musicController.playQueue(intent.mix.songs, 0)
                }
            }
            is HomeIntent.PlayPlaylist -> {
                viewModelScope.launch {
                    val songs = playlistRepository.getPlaylistSongs(intent.playlist.id).first()
                    if (songs.isNotEmpty()) {
                        musicController.playQueue(songs, 0)
                    }
                }
            }
            is HomeIntent.ToggleFavorite -> {
                musicController.toggleFavorite(intent.song)
            }
            is HomeIntent.PlayLikedSongs -> {
                viewModelScope.launch {
                    val favorites = songRepository.favorites.first()
                    if (favorites.isNotEmpty()) {
                        musicController.playQueue(favorites, 0)
                    }
                }
            }
            is HomeIntent.ImportFolder -> {
                viewModelScope.launch {
                    _isScanning.value = true
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            intent.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        preferences.addCustomFolderUri(intent.uri.toString())
                        val result = safFolderScanner.scanFolder(intent.uri)
                        _statusMessage.value = if (result.newCount > 0) {
                            "Imported ${result.newCount} songs into playlist '${result.folderName}' (${result.totalCount} tracks total)!"
                        } else if (result.totalCount > 0) {
                            "Playlist '${result.folderName}' created with ${result.totalCount} tracks."
                        } else {
                            "No audio files found in selected folder."
                        }
                    } catch (e: Exception) {
                        _statusMessage.value = "Import error: ${e.localizedMessage}"
                    } finally {
                        _isScanning.value = false
                    }
                }
            }
            is HomeIntent.Rescan -> {
                viewModelScope.launch {
                    _isScanning.value = true
                    try {
                        val res = mediaStoreScanner.scanMediaStore()
                        _statusMessage.value = "Scan complete: ${res.totalScanned} songs found."
                    } catch (e: Exception) {
                        _statusMessage.value = "Scan error: ${e.localizedMessage}"
                    } finally {
                        _isScanning.value = false
                    }
                }
            }
            is HomeIntent.DismissStatusMessage -> _statusMessage.value = null
        }
    }
}
