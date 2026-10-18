package com.mousikify.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.model.Album
import com.mousikify.core.model.Artist
import com.mousikify.core.model.Folder
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
    val isScanning: Boolean = false,
    val statusMessage: String? = null,
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
    data class ImportFolder(val uri: Uri) : LibraryIntent
    data object RescanLibrary : LibraryIntent
    data object DismissStatusMessage : LibraryIntent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val musicController: MusicController,
    private val mediaStoreScanner: MediaStoreScanner,
    private val safFolderScanner: SafFolderScanner,
    private val preferences: PlaybackPreferences
) : ViewModel() {

    private val _activeFilter = MutableStateFlow(LibraryFilter.ALL_SONGS)
    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    private val _isScanning = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    private val selectionState = combine(_selectedSongIds, _showCreatePlaylistDialog) { ids, dlg ->
        Pair(ids, dlg)
    }

    private val scanState = combine(_isScanning, _statusMessage) { scanning, msg ->
        Pair(scanning, msg)
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

    private val contentState = combine(
        _activeFilter,
        mainLibrary,
        mediaCollections,
        selectionState
    ) { filter, (songs, playlists), (artists, albums, folders), (selectedIds, showDialog) ->
        Tuple4(filter, songs, playlists, artists, albums, folders, selectedIds, showDialog)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        contentState,
        scanState,
        playbackState
    ) { content, (isScanning, statusMessage), (currentSong, isPlaying) ->
        LibraryUiState(
            activeFilter = content.filter,
            songs = content.songs,
            playlists = content.playlists,
            artists = content.artists,
            albums = content.albums,
            folders = content.folders,
            selectedSongIds = content.selectedIds,
            isMultiSelectMode = content.selectedIds.isNotEmpty(),
            showCreatePlaylistDialog = content.showDialog,
            isScanning = isScanning,
            statusMessage = statusMessage,
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
            is LibraryIntent.SetFilter -> _activeFilter.value = intent.filter
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
            is LibraryIntent.ClearSelection -> _selectedSongIds.value = emptySet()
            is LibraryIntent.SelectAll -> {
                _selectedSongIds.value = uiState.value.songs.map { it.id }.toSet()
            }
            is LibraryIntent.ShowCreatePlaylist -> _showCreatePlaylistDialog.value = intent.show
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
            is LibraryIntent.ToggleFavorite -> musicController.toggleFavorite(intent.song)
            is LibraryIntent.ImportFolder -> {
                viewModelScope.launch {
                    _isScanning.value = true
                    try {
                        // Persist SAF permission so we can read this folder across reboots
                        context.contentResolver.takePersistableUriPermission(
                            intent.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        preferences.addCustomFolderUri(intent.uri.toString())
                        val count = safFolderScanner.scanFolder(intent.uri)
                        _statusMessage.value = if (count > 0) {
                            "Successfully imported $count songs from folder!"
                        } else {
                            "No new audio files found in selected folder."
                        }
                    } catch (e: Exception) {
                        _statusMessage.value = "Failed to import folder: ${e.localizedMessage}"
                    } finally {
                        _isScanning.value = false
                    }
                }
            }
            is LibraryIntent.RescanLibrary -> {
                viewModelScope.launch {
                    _isScanning.value = true
                    try {
                        val res = mediaStoreScanner.scanMediaStore()
                        _statusMessage.value = "Found ${res.totalScanned} tracks (${res.addedOrUpdatedCount} updated)"
                    } catch (e: Exception) {
                        _statusMessage.value = "Scan error: ${e.localizedMessage}"
                    } finally {
                        _isScanning.value = false
                    }
                }
            }
            is LibraryIntent.DismissStatusMessage -> _statusMessage.value = null
        }
    }
}

private data class Tuple4(
    val filter: LibraryFilter,
    val songs: List<Song>,
    val playlists: List<Playlist>,
    val artists: List<Artist>,
    val albums: List<Album>,
    val folders: List<Folder>,
    val selectedIds: Set<Long>,
    val showDialog: Boolean
)
