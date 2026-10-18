package com.mousikify.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "",
    val recentlyPlayed: List<Song> = emptyList(),
    val forgottenFavorites: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val likedCount: Int = 0,
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false
)

sealed interface HomeIntent {
    data class PlaySong(val song: Song, val queue: List<Song>) : HomeIntent
    data class ToggleFavorite(val song: Song) : HomeIntent
    data object PlayLikedSongs : HomeIntent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val musicController: MusicController
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

    private val playbackState = combine(musicController.currentSong, musicController.isPlaying) { song, isPlaying ->
        Pair(song, isPlaying)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        songRepository.recentlyPlayed,
        songRepository.forgottenFavorites,
        playlistRepository.playlists,
        songRepository.favorites,
        playbackState
    ) { recent, forgotten, playlists, favorites, (currentSong, isPlaying) ->
        HomeUiState(
            greeting = greeting,
            recentlyPlayed = recent,
            forgottenFavorites = forgotten,
            playlists = playlists,
            likedCount = favorites.size,
            currentSongId = currentSong?.id,
            isPlaying = isPlaying,
            isLoading = false
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
                    val favorites = songRepository.favorites
                    // Play all favorites
                }
            }
        }
    }
}
