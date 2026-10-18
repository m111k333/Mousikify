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

data class HomeUiState(
    val greeting: String = "",
    val totalSongCount: Int = 0,
    val recentlyPlayed: List<Song> = emptyList(),
    val forgottenFavorites: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val likedCount: Int = 0,
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val statusMessage: String? = null
)

sealed interface HomeIntent {
    data class PlaySong(val song: Song, val queue: List<Song>) : HomeIntent
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
        HomeUiState(
            greeting = greeting,
            totalSongCount = allSongs.size,
            recentlyPlayed = recent,
            forgottenFavorites = forgotten,
            playlists = playlists,
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

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.PlaySong -> {
                musicController.playQueue(intent.queue, intent.queue.indexOfFirst { it.id == intent.song.id }.coerceAtLeast(0))
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
                        val count = safFolderScanner.scanFolder(intent.uri)
                        _statusMessage.value = if (count > 0) {
                            "Imported $count songs from folder!"
                        } else {
                            "No new audio files found in selected folder."
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
