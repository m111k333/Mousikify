package com.mousikify.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.model.Album
import com.mousikify.core.model.Artist
import com.mousikify.core.model.Folder
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryFilter {
    ALL_SONGS,
    PLAYLISTS,
    ARTISTS,
    ALBUMS,
    FOLDERS
}

data class LibraryUiState(
    val activeFilter: LibraryFilter = LibraryFilter.ALL_SONGS,
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedSongIds: Set<Long> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false,
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false
)

sealed interface LibraryIntent {
    data class SetFilter(val filter: LibraryFilter) : LibraryIntent
    data class PlaySong(val song: Song, val queue: List<Song>) : LibraryIntent
    data class ToggleSelectSong(val songId: Long) : LibraryIntent
    data object ClearSelection : LibraryIntent
    data object SelectAll : LibraryIntent
    data class ShowCreatePlaylist(val show: Boolean) : LibraryIntent
    data class CreatePlaylist(val name: String) : LibraryIntent
    data class AddSelectedToPlaylist(val playlistId: Long) : LibraryIntent
    data class ToggleFavorite(val song: Song) : LibraryIntent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val musicController: MusicController
) : ViewModel() {

    private val _activeFilter = MutableStateFlow(LibraryFilter.ALL_SONGS)
    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _showCreatePlaylistDialog = MutableStateFlow(false)

    private val selectionState = combine(_selectedSongIds, _showCreatePlaylistDialog) { ids, dlg ->
        Pair(ids, dlg)
    }

    private val playbackState = combine(musicController.currentSong, musicController.isPlaying) { song, isPlaying ->
        Pair(song, isPlaying)
    }

    private val mediaCollections = combine(
        songRepository.artists,
        songRepository.albums,
        songRepository.folders
    ) { artists, albums, folders ->
        Triple(artists, albums, folders)
    }

    private val mainLibrary = combine(
        songRepository.allSongs,
        playlistRepository.playlists
    ) { songs, playlists ->
        Pair(songs, playlists)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        _activeFilter,
        mainLibrary,
        mediaCollections,
        selectionState,
        playbackState
    ) { filter, (songs, playlists), (artists, albums, folders), (selectedIds, showDialog), (currentSong, isPlaying) ->
        LibraryUiState(
            activeFilter = filter,
            songs = songs,
            playlists = playlists,
            artists = artists,
            albums = albums,
            folders = folders,
            selectedSongIds = selectedIds,
            isMultiSelectMode = selectedIds.isNotEmpty(),
            showCreatePlaylistDialog = showDialog,
            currentSongId = currentSong?.id,
            isPlaying = isPlaying
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun onIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.SetFilter -> {
                _activeFilter.value = intent.filter
            }
            is LibraryIntent.PlaySong -> {
                musicController.playQueue(intent.queue, intent.queue.indexOfFirst { it.id == intent.song.id }.coerceAtLeast(0))
            }
            is LibraryIntent.ToggleSelectSong -> {
                val current = _selectedSongIds.value.toMutableSet()
                if (current.contains(intent.songId)) {
                    current.remove(intent.songId)
                } else {
                    current.add(intent.songId)
                }
                _selectedSongIds.value = current
            }
            is LibraryIntent.ClearSelection -> {
                _selectedSongIds.value = emptySet()
            }
            is LibraryIntent.SelectAll -> {
                _selectedSongIds.value = uiState.value.songs.map { it.id }.toSet()
            }
            is LibraryIntent.ShowCreatePlaylist -> {
                _showCreatePlaylistDialog.value = intent.show
            }
            is LibraryIntent.CreatePlaylist -> {
                viewModelScope.launch {
                    val id = playlistRepository.createPlaylist(intent.name)
                    _selectedSongIds.value.forEach { songId ->
                        playlistRepository.addSongToPlaylist(id, songId)
                    }
                    _selectedSongIds.value = emptySet()
                    _showCreatePlaylistDialog.value = false
                }
            }
            is LibraryIntent.AddSelectedToPlaylist -> {
                viewModelScope.launch {
                    _selectedSongIds.value.forEach { songId ->
                        playlistRepository.addSongToPlaylist(intent.playlistId, songId)
                    }
                    _selectedSongIds.value = emptySet()
                }
            }
            is LibraryIntent.ToggleFavorite -> {
                musicController.toggleFavorite(intent.song)
            }
        }
    }
}
