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
import kotlinx.coroutines.Job
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

sealed interface LibraryDetailView {
    data class ArtistDetail(val artist: Artist, val songs: List<Song>) : LibraryDetailView
    data class AlbumDetail(val album: Album, val songs: List<Song>) : LibraryDetailView
    data class FolderDetail(val folder: Folder, val songs: List<Song>) : LibraryDetailView
    data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>) : LibraryDetailView
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
    val isPlaying: Boolean = false,
    val activeDetail: LibraryDetailView? = null
)

sealed interface LibraryIntent {
    data class SetFilter(val filter: LibraryFilter) : LibraryIntent
    data class PlaySong(val song: Song, val queue: List<Song>) : LibraryIntent
    data class PlayAll(val songs: List<Song>, val startIndex: Int = 0) : LibraryIntent
    data class ShufflePlay(val songs: List<Song>) : LibraryIntent
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
    data class OpenArtist(val artist: Artist) : LibraryIntent
    data class OpenAlbum(val album: Album) : LibraryIntent
    data class OpenFolder(val folder: Folder) : LibraryIntent
    data class OpenPlaylist(val playlist: Playlist) : LibraryIntent
    data object CloseDetail : LibraryIntent
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
    private val _activeDetail = MutableStateFlow<LibraryDetailView?>(null)
    private var detailJob: Job? = null

    private val selectionState = combine(_selectedSongIds, _showCreatePlaylistDialog) { ids, dlg ->
        Pair(ids, dlg)
    }

    private val scanAndDetailState = combine(_isScanning, _statusMessage, _activeDetail) { scanning, msg, detail ->
        Triple(scanning, msg, detail)
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
        scanAndDetailState,
        playbackState
    ) { content, (isScanning, statusMessage, activeDetail), (currentSong, isPlaying) ->
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
            isPlaying = isPlaying,
            activeDetail = activeDetail
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
            is LibraryIntent.PlayAll -> {
                musicController.playQueue(intent.songs, intent.startIndex.coerceAtLeast(0))
            }
            is LibraryIntent.ShufflePlay -> {
                val shuffled = intent.songs.shuffled()
                musicController.playQueue(shuffled, 0)
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
                        val result = safFolderScanner.scanFolder(intent.uri)
                        _statusMessage.value = if (result.newCount > 0) {
                            "Imported ${result.newCount} songs into playlist '${result.folderName}' (${result.totalCount} tracks total)!"
                        } else if (result.totalCount > 0) {
                            "Playlist '${result.folderName}' updated with ${result.totalCount} tracks."
                        } else {
                            "No audio files found in selected folder."
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
            is LibraryIntent.OpenArtist -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    songRepository.getSongsByArtist(intent.artist.id).collect { songs ->
                        _activeDetail.value = LibraryDetailView.ArtistDetail(intent.artist, songs)
                    }
                }
            }
            is LibraryIntent.OpenAlbum -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    songRepository.getSongsByAlbum(intent.album.id).collect { songs ->
                        _activeDetail.value = LibraryDetailView.AlbumDetail(intent.album, songs)
                    }
                }
            }
            is LibraryIntent.OpenFolder -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    songRepository.getSongsByFolder(intent.folder.path).collect { songs ->
                        _activeDetail.value = LibraryDetailView.FolderDetail(intent.folder, songs)
                    }
                }
            }
            is LibraryIntent.OpenPlaylist -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    playlistRepository.getPlaylistSongs(intent.playlist.id).collect { songs ->
                        _activeDetail.value = LibraryDetailView.PlaylistDetail(intent.playlist, songs)
                    }
                }
            }
            is LibraryIntent.CloseDetail -> {
                detailJob?.cancel()
                _activeDetail.value = null
            }
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
