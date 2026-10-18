package com.mousikify.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.model.Album
import com.mousikify.core.model.Artist
import com.mousikify.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SearchTab {
    ALL,
    SONGS,
    ARTISTS,
    ALBUMS
}

data class SearchUiState(
    val query: String = "",
    val activeTab: SearchTab = SearchTab.ALL,
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false
)

sealed interface SearchIntent {
    data class UpdateQuery(val query: String) : SearchIntent
    data class SelectTab(val tab: SearchTab) : SearchIntent
    data class PlaySong(val song: Song, val queue: List<Song>) : SearchIntent
    data class ToggleFavorite(val song: Song) : SearchIntent
    data object ClearQuery : SearchIntent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val musicController: MusicController
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _activeTab = MutableStateFlow(SearchTab.ALL)

    private val searchInputs = combine(_query, _activeTab) { query, tab ->
        Pair(query, tab)
    }

    private val playbackState = combine(musicController.currentSong, musicController.isPlaying) { song, isPlaying ->
        Pair(song, isPlaying)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        searchInputs,
        _query.flatMapLatest { songRepository.searchSongs(it) },
        songRepository.artists,
        songRepository.albums,
        playbackState
    ) { (query, tab), matchingSongs, allArtists, allAlbums, (currentSong, isPlaying) ->
        val filteredArtists = if (query.isBlank()) emptyList() else {
            allArtists.filter { it.name.contains(query, ignoreCase = true) }
        }
        val filteredAlbums = if (query.isBlank()) emptyList() else {
            allAlbums.filter { it.title.contains(query, ignoreCase = true) }
        }

        SearchUiState(
            query = query,
            activeTab = tab,
            songs = matchingSongs,
            artists = filteredArtists,
            albums = filteredAlbums,
            currentSongId = currentSong?.id,
            isPlaying = isPlaying
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState()
    )

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery -> _query.value = intent.query
            is SearchIntent.ClearQuery -> _query.value = ""
            is SearchIntent.SelectTab -> _activeTab.value = intent.tab
            is SearchIntent.PlaySong -> {
                musicController.playQueue(intent.queue, intent.queue.indexOfFirst { it.id == intent.song.id }.coerceAtLeast(0))
            }
            is SearchIntent.ToggleFavorite -> {
                musicController.toggleFavorite(intent.song)
            }
        }
    }
}
