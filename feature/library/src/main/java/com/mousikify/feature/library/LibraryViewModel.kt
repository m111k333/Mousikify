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
import com.mousikify.core.media.recommendation.PlaylistGenerationEngine
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.model.Album
import com.mousikify.core.model.AlbumProfile
import com.mousikify.core.model.Artist
import com.mousikify.core.model.ArtistProfile
import com.mousikify.core.model.Folder
import com.mousikify.core.model.PlaybackContext
import com.mousikify.core.model.PlaybackContextType
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.Song
import com.mousikify.core.scanner.indexer.MediaStoreScanner
import com.mousikify.core.scanner.indexer.SafFolderScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryFilter {
    PLAYLISTS,
    ALBUMS,
    ARTISTS,
    ALL_SONGS,
    FOLDERS
}

enum class LibrarySortOrder(val label: String) {
    ALPHABETICAL_ASC("Alphabetical (A–Z)"),
    ALPHABETICAL_DESC("Alphabetical (Z–A)"),
    RECENTLY_ADDED("Recently Added"),
    MOST_SONGS_OR_PLAYED("Most Songs / Played")
}

sealed interface LibraryDetailView {
    data class ArtistDetail(
        val artist: Artist,
        val songs: List<Song>,
        val artistProfile: ArtistProfile? = null
    ) : LibraryDetailView

    data class AlbumDetail(
        val album: Album,
        val songs: List<Song>,
        val albumProfile: AlbumProfile? = null
    ) : LibraryDetailView

    data class FolderDetail(val folder: Folder, val songs: List<Song>) : LibraryDetailView
    data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>) : LibraryDetailView
}

data class LibraryUiState(
    val activeFilter: LibraryFilter = LibraryFilter.PLAYLISTS,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENTLY_ADDED,
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
    val isShuffleEnabled: Boolean = false,
    val activeDetail: LibraryDetailView? = null,
    val songToAddToPlaylist: Song? = null,
    val showAddToPlaylistForSelection: Boolean = false,
    val showAddSongsPickerForPlaylist: Playlist? = null,
    val pinnedVideoIds: Set<String> = emptySet(),
    val cacheUpdateTick: Long = 0L,
    val canUndo: Boolean = false,
    val isSavingAlbumLocally: Boolean = false,
    val favoriteSongIds: Set<Long> = emptySet(),
    val favoriteVideoIds: Set<String> = emptySet(),
    val enrichmentSuggestions: List<Song> = emptyList(),
    val isEnriching: Boolean = false
) {
    fun isFavorite(song: Song): Boolean {
        if (song.id in favoriteSongIds) return true
        val vid = song.remoteVideoId
        if (vid != null && vid in favoriteVideoIds) return true
        return song.isFavorite
    }

    fun isAlbumSaved(albumProfile: AlbumProfile): Boolean =
        SongRepository.isStreamingAlbumSaved(
            album = albumProfile.album,
            tracks = albumProfile.tracks,
            librarySongs = songs
        )
}

sealed interface LibraryIntent {
    data class SetFilter(val filter: LibraryFilter) : LibraryIntent
    data class SetSortOrder(val sortOrder: LibrarySortOrder) : LibraryIntent
    data object CycleSortOrder : LibraryIntent
    data class PlaySong(val song: Song, val queue: List<Song>) : LibraryIntent
    data class PlayAll(val songs: List<Song>, val startIndex: Int = 0) : LibraryIntent
    data class ToggleShuffle(val songs: List<Song>? = null) : LibraryIntent
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
    data class OpenArtistByName(val artistName: String) : LibraryIntent
    data class OpenAlbumByName(val albumTitle: String, val artistName: String = "") : LibraryIntent
    data class PlayNext(val song: Song) : LibraryIntent
    data class AddToQueue(val song: Song) : LibraryIntent
    data class OpenFolder(val folder: Folder) : LibraryIntent
    data class DeleteFolder(val folder: Folder) : LibraryIntent
    data class OpenPlaylist(val playlist: Playlist) : LibraryIntent
    data object CloseDetail : LibraryIntent
    data class ShowAddToPlaylistDialog(val song: Song?) : LibraryIntent
    data class ShowAddToPlaylistForSelection(val show: Boolean) : LibraryIntent
    data class ShowAddSongsPicker(val playlist: Playlist?) : LibraryIntent
    data class AddSongToPlaylist(val playlistId: Long, val songId: Long) : LibraryIntent
    data class RemoveSongFromPlaylist(val playlistId: Long, val songId: Long) : LibraryIntent
    data class DeletePlaylist(val playlistId: Long) : LibraryIntent
    data class RenamePlaylist(val playlistId: Long, val newName: String) : LibraryIntent
    data class ReorderPlaylist(val playlistId: Long, val orderedSongIds: List<Long>) : LibraryIntent
    data class DeleteSongFromDevice(val song: Song) : LibraryIntent
    data class SaveAlbumLocally(val album: Album, val tracks: List<Song>) : LibraryIntent
    data class DeleteAlbumFromDevice(val album: Album, val songs: List<Song>) : LibraryIntent
    data class RemoveSongFromLibrary(val song: Song) : LibraryIntent
    data object UndoDelete : LibraryIntent
    data class EnrichPlaylist(val playlistId: Long) : LibraryIntent
    data class AddEnrichedSong(val playlistId: Long, val song: Song) : LibraryIntent
    data object ClearEnrichment : LibraryIntent
    data class SharePlaylist(val playlist: Playlist, val songs: List<Song>? = null) : LibraryIntent
    data class OpenSharedPlaylistPreview(val name: String, val songs: List<Song>) : LibraryIntent
    data class SaveSharedPlaylistToLibrary(val name: String, val songs: List<Song>) : LibraryIntent
}


@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val musicController: MusicController,
    private val remoteMusicRepository: RemoteMusicRepository,
    private val mediaStoreScanner: MediaStoreScanner,
    private val safFolderScanner: SafFolderScanner,
    private val preferences: PlaybackPreferences,
    private val playlistGenerationEngine: PlaylistGenerationEngine
) : ViewModel() {

    private val _activeFilter = MutableStateFlow(LibraryFilter.PLAYLISTS)
    private val _sortOrder = MutableStateFlow(LibrarySortOrder.RECENTLY_ADDED)
    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    private val _isScanning = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _activeDetail = MutableStateFlow<LibraryDetailView?>(null)
    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    private val _showAddToPlaylistForSelection = MutableStateFlow(false)
    private val _showAddSongsPickerForPlaylist = MutableStateFlow<Playlist?>(null)
    private val _enrichmentSuggestions = MutableStateFlow<List<Song>>(emptyList())
    private val _isEnriching = MutableStateFlow(false)

    val enrichmentSuggestions: StateFlow<List<Song>> = _enrichmentSuggestions.asStateFlow()
    val isEnriching: StateFlow<Boolean> = _isEnriching.asStateFlow()
    private var detailJob: Job? = null

    init {
        viewModelScope.launch {
            val songs = songRepository.allSongs.firstOrNull() ?: emptyList()
            val savedAlbumTitles = songs.filter { it.isAlbumSaved }.map { it.albumName.lowercase().trim() }.toSet()
            if (savedAlbumTitles.isNotEmpty()) {
                val allPlaylists = playlistRepository.playlists.firstOrNull().orEmpty()
                for (pl in allPlaylists) {
                    if (pl.name.lowercase().trim() in savedAlbumTitles) {
                        playlistRepository.deletePlaylist(pl.id)
                    }
                }
            }
        }
    }

    private val filterAndSortState = combine(_activeFilter, _sortOrder) { filter, sort ->
        Pair(filter, sort)
    }

    private val selectionState = combine(_selectedSongIds, _showCreatePlaylistDialog) { ids, dlg ->
        Pair(ids, dlg)
    }

    private val _canUndo = MutableStateFlow(false)
    private val _isSavingAlbumLocally = MutableStateFlow(false)
    private var lastRemovedPlaylistSong: Pair<Long, Song>? = null
    private var lastRemovedLibrarySong: Song? = null

    private val scanAndDetailState = combine(
        _isScanning,
        _statusMessage,
        _activeDetail,
        _canUndo,
        combine(_isSavingAlbumLocally, _enrichmentSuggestions, _isEnriching) { isSaving, suggestions, isEnriching ->
            Triple(isSaving, suggestions, isEnriching)
        }
    ) { scanning, msg, detail, canUndo, (isSavingAlbum, suggestions, enriching) ->
        TupleScanDetail(scanning, msg, detail, canUndo, isSavingAlbum, suggestions, enriching)
    }

    private val favoriteFlow = combine(
        musicController.favoriteSongIds,
        musicController.favoriteVideoIds
    ) { songIds, videoIds -> Pair(songIds, videoIds) }

    private val playbackState = combine(
        musicController.currentSong,
        musicController.isPlaying,
        musicController.isShuffleEnabled,
        musicController.cacheUpdateTick,
        favoriteFlow
    ) { song, isPlaying, shuffle, cacheTick, (favSongIds, favVideoIds) ->
        LibraryPlaybackState(song, isPlaying, shuffle, cacheTick, favSongIds, favVideoIds)
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

    private val playlistDialogsState = combine(
        _songToAddToPlaylist,
        _showAddToPlaylistForSelection,
        _showAddSongsPickerForPlaylist
    ) { songToAdd, showSelection, pickerPlaylist ->
        Triple(songToAdd, showSelection, pickerPlaylist)
    }

    private val contentState = combine(
        filterAndSortState,
        mainLibrary,
        mediaCollections,
        selectionState
    ) { (filter, sortOrder), (songs, playlists), (artists, albums, folders), (selectedIds, showDialog) ->
        val sortedSongs = when (sortOrder) {
            LibrarySortOrder.ALPHABETICAL_ASC -> songs.sortedBy { it.title.lowercase() }
            LibrarySortOrder.ALPHABETICAL_DESC -> songs.sortedByDescending { it.title.lowercase() }
            LibrarySortOrder.RECENTLY_ADDED -> songs.sortedByDescending { it.dateAdded }
            LibrarySortOrder.MOST_SONGS_OR_PLAYED -> songs.sortedByDescending { it.playCount }
        }
        val sortedPlaylists = when (sortOrder) {
            LibrarySortOrder.ALPHABETICAL_ASC -> playlists.sortedBy { it.name.lowercase() }
            LibrarySortOrder.ALPHABETICAL_DESC -> playlists.sortedByDescending { it.name.lowercase() }
            LibrarySortOrder.RECENTLY_ADDED -> playlists.sortedByDescending { it.createdAt }
            LibrarySortOrder.MOST_SONGS_OR_PLAYED -> playlists.sortedByDescending { it.songCount }
        }
        val sortedArtists = when (sortOrder) {
            LibrarySortOrder.ALPHABETICAL_ASC -> artists.sortedBy { it.name.lowercase() }
            LibrarySortOrder.ALPHABETICAL_DESC -> artists.sortedByDescending { it.name.lowercase() }
            LibrarySortOrder.RECENTLY_ADDED -> artists.sortedByDescending { it.id }
            LibrarySortOrder.MOST_SONGS_OR_PLAYED -> artists.sortedByDescending { it.songCount }
        }
        val sortedAlbums = when (sortOrder) {
            LibrarySortOrder.ALPHABETICAL_ASC -> albums.sortedBy { it.title.lowercase() }
            LibrarySortOrder.ALPHABETICAL_DESC -> albums.sortedByDescending { it.title.lowercase() }
            LibrarySortOrder.RECENTLY_ADDED -> albums.sortedByDescending { it.year }
            LibrarySortOrder.MOST_SONGS_OR_PLAYED -> albums.sortedByDescending { it.songCount }
        }
        val sortedFolders = when (sortOrder) {
            LibrarySortOrder.ALPHABETICAL_ASC -> folders.sortedBy { it.name.lowercase() }
            LibrarySortOrder.ALPHABETICAL_DESC -> folders.sortedByDescending { it.name.lowercase() }
            LibrarySortOrder.RECENTLY_ADDED -> folders.sortedByDescending { it.songCount }
            LibrarySortOrder.MOST_SONGS_OR_PLAYED -> folders.sortedByDescending { it.songCount }
        }
        val pinnedVideoIds = songs.mapNotNull { it.remoteVideoId }.toSet()
        Tuple4(filter, sortOrder, sortedSongs, sortedPlaylists, sortedArtists, sortedAlbums, sortedFolders, selectedIds, showDialog, pinnedVideoIds)
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<LibraryUiState> = combine(
        contentState,
        scanAndDetailState,
        playbackState,
        playlistDialogsState
    ) { content, (isScanning, statusMessage, activeDetail, canUndo, isSavingAlbum, enrichmentSuggestions, isEnriching), pbState, (songToAdd, showSelection, pickerPlaylist) ->
        LibraryUiState(
            activeFilter = content.filter,
            sortOrder = content.sortOrder,
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
            currentSongId = pbState.currentSong?.id,
            isPlaying = pbState.isPlaying,
            isShuffleEnabled = pbState.isShuffleEnabled,
            activeDetail = activeDetail,
            songToAddToPlaylist = songToAdd,
            showAddToPlaylistForSelection = showSelection,
            showAddSongsPickerForPlaylist = pickerPlaylist,
            pinnedVideoIds = content.pinnedVideoIds,
            cacheUpdateTick = pbState.cacheTick,
            canUndo = canUndo,
            isSavingAlbumLocally = isSavingAlbum,
            favoriteSongIds = pbState.favoriteSongIds,
            favoriteVideoIds = pbState.favoriteVideoIds,
            enrichmentSuggestions = enrichmentSuggestions,
            isEnriching = isEnriching
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun isSongFavorite(song: Song): Boolean = uiState.value.isFavorite(song)

    fun isTrackCached(song: Song): Boolean {
        if (song.isLocal) return true
        return musicController.isSongCached(song)
    }

    fun onIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.SetFilter -> _activeFilter.value = intent.filter
            is LibraryIntent.SetSortOrder -> _sortOrder.value = intent.sortOrder
            is LibraryIntent.CycleSortOrder -> {
                val entries = LibrarySortOrder.entries
                val nextIndex = (entries.indexOf(_sortOrder.value) + 1) % entries.size
                _sortOrder.value = entries[nextIndex]
            }
            is LibraryIntent.PlaySong -> {
                val context = when (val detail = _activeDetail.value) {
                    is LibraryDetailView.PlaylistDetail -> PlaybackContext(
                        title = detail.playlist.name,
                        type = PlaybackContextType.PLAYLIST,
                        id = detail.playlist.id.toString(),
                        playlistId = detail.playlist.id,
                        isLocalOrSaved = true
                    )
                    is LibraryDetailView.AlbumDetail -> PlaybackContext(
                        title = detail.album.title,
                        type = PlaybackContextType.ALBUM,
                        id = detail.album.id.toString(),
                        albumId = detail.album.id,
                        artistName = detail.album.artist,
                        isLocalOrSaved = true
                    )
                    is LibraryDetailView.ArtistDetail -> PlaybackContext(
                        title = detail.artist.name,
                        type = PlaybackContextType.ARTIST,
                        id = detail.artist.id.toString(),
                        artistName = detail.artist.name,
                        isLocalOrSaved = true
                    )
                    is LibraryDetailView.FolderDetail -> PlaybackContext(
                        title = detail.folder.name,
                        type = PlaybackContextType.PLAYLIST,
                        id = detail.folder.path,
                        isLocalOrSaved = true
                    )
                    else -> PlaybackContext(
                        title = intent.song.albumName.ifEmpty { "Library" },
                        type = PlaybackContextType.ALBUM,
                        id = intent.song.albumId.toString(),
                        albumId = if (intent.song.albumId != 0L) intent.song.albumId else null,
                        artistName = intent.song.artistName,
                        isLocalOrSaved = true
                    )
                }
                musicController.playQueue(
                    intent.queue,
                    intent.queue.indexOfFirst { it.id == intent.song.id }.coerceAtLeast(0),
                    context = context
                )
            }
            is LibraryIntent.PlayAll -> {
                val context = when (val detail = _activeDetail.value) {
                    is LibraryDetailView.PlaylistDetail -> PlaybackContext(
                        title = detail.playlist.name,
                        type = PlaybackContextType.PLAYLIST,
                        id = detail.playlist.id.toString(),
                        playlistId = detail.playlist.id,
                        isLocalOrSaved = true
                    )
                    is LibraryDetailView.AlbumDetail -> PlaybackContext(
                        title = detail.album.title,
                        type = PlaybackContextType.ALBUM,
                        id = detail.album.id.toString(),
                        albumId = detail.album.id,
                        artistName = detail.album.artist,
                        isLocalOrSaved = true
                    )
                    is LibraryDetailView.ArtistDetail -> PlaybackContext(
                        title = detail.artist.name,
                        type = PlaybackContextType.ARTIST,
                        id = detail.artist.id.toString(),
                        artistName = detail.artist.name,
                        isLocalOrSaved = true
                    )
                    is LibraryDetailView.FolderDetail -> PlaybackContext(
                        title = detail.folder.name,
                        type = PlaybackContextType.PLAYLIST,
                        id = detail.folder.path,
                        isLocalOrSaved = true
                    )
                    else -> PlaybackContext(
                        title = "Your Library",
                        type = PlaybackContextType.LIBRARY,
                        isLocalOrSaved = true
                    )
                }
                musicController.playQueue(intent.songs, intent.startIndex.coerceAtLeast(0), context = context)
            }
            is LibraryIntent.ToggleShuffle -> {
                handleToggleShuffle(intent.songs)
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
            is LibraryIntent.ShowAddToPlaylistDialog -> _songToAddToPlaylist.value = intent.song
            is LibraryIntent.ShowAddToPlaylistForSelection -> _showAddToPlaylistForSelection.value = intent.show
            is LibraryIntent.ShowAddSongsPicker -> _showAddSongsPickerForPlaylist.value = intent.playlist
            is LibraryIntent.AddSongToPlaylist -> {
                viewModelScope.launch {
                    val targetSong = _songToAddToPlaylist.value?.takeIf { it.id == intent.songId }
                        ?: songRepository.getSongByIdSync(intent.songId)
                    val resolvedSong = if (targetSong != null) {
                        playlistRepository.addSongObjectToPlaylist(intent.playlistId, targetSong)
                    } else {
                        playlistRepository.addSongToPlaylist(intent.playlistId, intent.songId)
                        null
                    }
                    if (resolvedSong != null && resolvedSong.isRemoteStream && !musicController.isSongCached(resolvedSong)) {
                        musicController.downloadSongForOffline(resolvedSong)
                    }
                    _statusMessage.value = "Added to playlist"
                }
            }
            is LibraryIntent.RemoveSongFromPlaylist -> {
                viewModelScope.launch {
                    val song = songRepository.getSongByIdSync(intent.songId)
                    if (song != null) {
                        lastRemovedPlaylistSong = Pair(intent.playlistId, song)
                        lastRemovedLibrarySong = null
                        _canUndo.value = true
                    }
                    playlistRepository.removeSongFromPlaylist(intent.playlistId, intent.songId)
                    songRepository.deleteNonFavoriteRemoteSongs()
                    musicController.pruneOrphanedCache()
                    _statusMessage.value = "Removed from playlist"
                }
            }
            is LibraryIntent.DeletePlaylist -> {
                viewModelScope.launch {
                    playlistRepository.deletePlaylist(intent.playlistId)
                    songRepository.deleteNonFavoriteRemoteSongs()
                    musicController.pruneOrphanedCache()
                    if (_activeDetail.value is LibraryDetailView.PlaylistDetail &&
                        (_activeDetail.value as LibraryDetailView.PlaylistDetail).playlist.id == intent.playlistId
                    ) {
                        _activeDetail.value = null
                    }
                    _statusMessage.value = "Playlist deleted"
                }
            }
            is LibraryIntent.DeleteFolder -> {
                viewModelScope.launch {
                    songRepository.deleteFolderAndOrphanSongs(intent.folder.path)
                    songRepository.deleteNonFavoriteRemoteSongs()
                    musicController.pruneOrphanedCache()
                    if (_activeDetail.value is LibraryDetailView.FolderDetail &&
                        (_activeDetail.value as LibraryDetailView.FolderDetail).folder.path == intent.folder.path
                    ) {
                        _activeDetail.value = null
                    }
                    _statusMessage.value = "Folder '${intent.folder.name}' deleted"
                }
            }
            is LibraryIntent.RenamePlaylist -> {
                viewModelScope.launch {
                    val trimmed = intent.newName.trim()
                    if (trimmed.isNotEmpty()) {
                        playlistRepository.renamePlaylist(intent.playlistId, trimmed)
                        _statusMessage.value = "Playlist renamed to '$trimmed'"
                    }
                }
            }
            is LibraryIntent.ReorderPlaylist -> {
                viewModelScope.launch {
                    playlistRepository.reorderPlaylist(intent.playlistId, intent.orderedSongIds)
                }
            }
            is LibraryIntent.DeleteSongFromDevice -> {
                viewModelScope.launch {
                    lastRemovedLibrarySong = intent.song
                    lastRemovedPlaylistSong = null
                    _canUndo.value = true
                    musicController.deleteDownloadedTrack(intent.song)
                    _statusMessage.value = "Deleted '${intent.song.title}' from device"
                }
            }
            is LibraryIntent.DeleteAlbumFromDevice -> {
                viewModelScope.launch {
                    val albumTracks = if (intent.songs.isNotEmpty()) {
                        intent.songs
                    } else {
                        songRepository.getSongsByAlbum(intent.album.id).firstOrNull() ?: emptyList()
                    }
                    musicController.deleteDownloadedTracks(albumTracks)
                    playlistRepository.deletePlaylistByName(intent.album.title)
                    if (_activeDetail.value is LibraryDetailView.AlbumDetail &&
                        (_activeDetail.value as LibraryDetailView.AlbumDetail).album.id == intent.album.id
                    ) {
                        _activeDetail.value = null
                    }
                    _statusMessage.value = "Deleted '${intent.album.title}' from device"
                }
            }
            is LibraryIntent.RemoveSongFromLibrary -> {
                viewModelScope.launch {
                    lastRemovedLibrarySong = intent.song
                    lastRemovedPlaylistSong = null
                    _canUndo.value = true
                    if (intent.song.isRemoteStream) {
                        musicController.deleteDownloadedTrack(intent.song)
                    } else {
                        songRepository.deleteSongs(listOf(intent.song.id))
                    }
                    _statusMessage.value = "Removed '${intent.song.title}' from library"
                }
            }
            is LibraryIntent.CreatePlaylist -> {
                viewModelScope.launch {
                    val id = playlistRepository.createPlaylist(intent.name)
                    var addedCount = 0
                    if (_selectedSongIds.value.isNotEmpty()) {
                        addedCount = _selectedSongIds.value.size
                        _selectedSongIds.value.forEach { songId ->
                            playlistRepository.addSongToPlaylist(id, songId)
                            val song = songRepository.getSongByIdSync(songId)
                            if (song != null && song.isRemoteStream && !musicController.isSongCached(song)) {
                                musicController.downloadSongForOffline(song)
                            }
                        }
                        _selectedSongIds.value = emptySet()
                    } else if (_songToAddToPlaylist.value != null) {
                        addedCount = 1
                        val song = _songToAddToPlaylist.value!!
                        val resolvedSong = playlistRepository.addSongObjectToPlaylist(id, song)
                        if (resolvedSong.isRemoteStream && !musicController.isSongCached(resolvedSong)) {
                            musicController.downloadSongForOffline(resolvedSong)
                        }
                        _songToAddToPlaylist.value = null
                    }
                    _showCreatePlaylistDialog.value = false
                    _showAddToPlaylistForSelection.value = false
                    _statusMessage.value = if (addedCount > 0) {
                        "Created playlist '${intent.name}' with $addedCount songs"
                    } else {
                        "Created playlist '${intent.name}'"
                    }
                }
            }
            is LibraryIntent.AddSelectedToPlaylist -> {
                viewModelScope.launch {
                    val ids = _selectedSongIds.value.toList()
                    ids.forEach { songId ->
                        playlistRepository.addSongToPlaylist(intent.playlistId, songId)
                        val song = songRepository.getSongByIdSync(songId)
                        if (song != null && song.isRemoteStream && !musicController.isSongCached(song)) {
                            musicController.downloadSongForOffline(song)
                        }
                    }
                    _selectedSongIds.value = emptySet()
                    _showAddToPlaylistForSelection.value = false
                    _statusMessage.value = "Added ${ids.size} songs to playlist"
                }
            }
            is LibraryIntent.ToggleFavorite -> {
                val song = intent.song
                val willBeFav = !isSongFavorite(song)
                musicController.toggleFavorite(song)

                _activeDetail.value?.let { detail ->
                    when (detail) {
                        is LibraryDetailView.AlbumDetail -> {
                            val updatedTracks = detail.albumProfile?.tracks?.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            val updatedSongs = detail.songs.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            _activeDetail.value = detail.copy(
                                songs = updatedSongs,
                                albumProfile = detail.albumProfile?.copy(tracks = updatedTracks ?: detail.albumProfile.tracks)
                            )
                        }
                        is LibraryDetailView.ArtistDetail -> {
                            val updatedTop = detail.artistProfile?.topSongs?.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            val updatedFeatured = detail.artistProfile?.featuredIn?.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            val updatedSongs = detail.songs.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            _activeDetail.value = detail.copy(
                                songs = updatedSongs,
                                artistProfile = detail.artistProfile?.copy(
                                    topSongs = updatedTop ?: detail.artistProfile.topSongs,
                                    featuredIn = updatedFeatured ?: detail.artistProfile.featuredIn
                                )
                            )
                        }
                        is LibraryDetailView.PlaylistDetail -> {
                            val updatedSongs = detail.songs.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            _activeDetail.value = detail.copy(songs = updatedSongs)
                        }
                        is LibraryDetailView.FolderDetail -> {
                            val updatedSongs = detail.songs.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            _activeDetail.value = detail.copy(songs = updatedSongs)
                        }
                    }
                }
            }
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
            is LibraryIntent.DismissStatusMessage -> {
                _statusMessage.value = null
                _canUndo.value = false
                lastRemovedPlaylistSong = null
                lastRemovedLibrarySong = null
            }
            is LibraryIntent.UndoDelete -> {
                viewModelScope.launch {
                    val plSong = lastRemovedPlaylistSong
                    val libSong = lastRemovedLibrarySong
                    if (plSong != null) {
                        val (playlistId, song) = plSong
                        songRepository.upsertSongs(listOf(song))
                        playlistRepository.addSongObjectToPlaylist(playlistId, song)
                        if (song.isRemoteStream && !musicController.isSongCached(song)) {
                            musicController.downloadSongForOffline(song)
                        }
                        _statusMessage.value = "Restored '${song.title}' to playlist"
                        lastRemovedPlaylistSong = null
                        _canUndo.value = false
                    } else if (libSong != null) {
                        songRepository.upsertSongs(listOf(libSong))
                        if (libSong.isRemoteStream && !musicController.isSongCached(libSong)) {
                            musicController.downloadSongForOffline(libSong)
                        }
                        _statusMessage.value = "Restored '${libSong.title}'"
                        lastRemovedLibrarySong = null
                        _canUndo.value = false
                    }
                }
            }
            is LibraryIntent.PlayNext -> musicController.playNext(intent.song)
            is LibraryIntent.AddToQueue -> musicController.addToQueue(intent.song)
            is LibraryIntent.OpenArtist -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    val localProfile = runCatching { songRepository.getArtistProfile(intent.artist.id).firstOrNull() }.getOrNull()
                    if (localProfile != null) {
                        _activeDetail.value = LibraryDetailView.ArtistDetail(
                            artist = localProfile.artist,
                            songs = localProfile.topSongs,
                            artistProfile = localProfile.copy(isLoading = true)
                        )
                    } else {
                        _activeDetail.value = LibraryDetailView.ArtistDetail(
                            artist = intent.artist,
                            songs = emptyList(),
                            artistProfile = ArtistProfile(intent.artist, isLoading = true)
                        )
                    }
                    val remote = remoteMusicRepository.getRemoteArtistProfile(
                        artistName = intent.artist.name,
                        channelUrl = intent.artist.channelUrl,
                        avatarUrl = intent.artist.avatarUrl
                    ).getOrNull()
                    if (remote != null) {
                        _activeDetail.value = LibraryDetailView.ArtistDetail(
                            artist = remote.artist,
                            songs = remote.topSongs,
                            artistProfile = remote.copy(isLoading = false)
                        )
                    } else if (localProfile != null) {
                        _activeDetail.value = LibraryDetailView.ArtistDetail(
                            artist = localProfile.artist,
                            songs = localProfile.topSongs,
                            artistProfile = localProfile.copy(isLoading = false)
                        )
                    } else {
                        _activeDetail.value = LibraryDetailView.ArtistDetail(
                            artist = intent.artist,
                            songs = emptyList(),
                            artistProfile = ArtistProfile(intent.artist, isLoading = false)
                        )
                    }
                }
            }
            is LibraryIntent.OpenArtistByName -> {
                val cleanName = com.mousikify.core.model.cleanArtistName(intent.artistName)
                detailJob?.cancel()
                val fallbackArtwork = uiState.value.songs.firstOrNull {
                    it.cleanArtistName.equals(cleanName, ignoreCase = true) && !it.artworkUri.isNullOrBlank()
                }?.artworkUri
                detailJob = viewModelScope.launch {
                    val localProfile = runCatching { songRepository.getArtistProfileByName(cleanName).firstOrNull() }.getOrNull()
                    val initialArtist = (localProfile?.artist ?: Artist(
                        id = cleanName.hashCode().toLong(),
                        name = cleanName,
                        songCount = 0
                    )).let { it.copy(avatarUrl = it.avatarUrl ?: fallbackArtwork) }

                    if (localProfile != null) {
                        _activeDetail.value = LibraryDetailView.ArtistDetail(
                            artist = initialArtist,
                            songs = localProfile.topSongs,
                            artistProfile = localProfile.copy(artist = initialArtist, isLoading = true)
                        )
                    } else {
                        _activeDetail.value = LibraryDetailView.ArtistDetail(
                            artist = initialArtist,
                            songs = emptyList(),
                            artistProfile = ArtistProfile(initialArtist, isLoading = true)
                        )
                    }
                    val remote = remoteMusicRepository.getRemoteArtistProfile(
                        artistName = cleanName,
                        channelUrl = initialArtist.channelUrl,
                        avatarUrl = initialArtist.avatarUrl
                    ).getOrNull()
                    if (remote != null) {
                        _activeDetail.value = LibraryDetailView.ArtistDetail(
                            artist = remote.artist.copy(avatarUrl = remote.artist.avatarUrl ?: initialArtist.avatarUrl),
                            songs = remote.topSongs,
                            artistProfile = remote.copy(artist = remote.artist.copy(avatarUrl = remote.artist.avatarUrl ?: initialArtist.avatarUrl), isLoading = false)
                        )
                    } else if (localProfile != null) {
                        _activeDetail.value = LibraryDetailView.ArtistDetail(
                            artist = initialArtist,
                            songs = localProfile.topSongs,
                            artistProfile = localProfile.copy(artist = initialArtist, isLoading = false)
                        )
                    } else {
                        _activeDetail.value = LibraryDetailView.ArtistDetail(
                            artist = initialArtist,
                            songs = emptyList(),
                            artistProfile = ArtistProfile(initialArtist, isLoading = false)
                        )
                    }
                }
            }
            is LibraryIntent.SaveAlbumLocally -> {
                if (_isSavingAlbumLocally.value) return
                val album = intent.album
                val tracks = intent.tracks
                if (tracks.isEmpty()) return
                viewModelScope.launch {
                    _isSavingAlbumLocally.value = true
                    try {
                        val preparedTracks = songRepository.prepareAndSaveAlbumTracks(album, tracks)
                        playlistRepository.deletePlaylistByName(album.title)
                        for (track in preparedTracks) {
                            if (track.isRemoteStream) {
                                musicController.downloadSongForOffline(track)
                            }
                        }
                        _statusMessage.value = "Saved '${album.title}' with ${tracks.size} songs & downloading for offline play"
                    } catch (e: Exception) {
                        _statusMessage.value = "Failed to save album: ${e.message}"
                    } finally {
                        _isSavingAlbumLocally.value = false
                    }
                }
            }
            is LibraryIntent.OpenAlbum -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    val isExplicitlyRemote = !intent.album.playlistUrl.isNullOrBlank() &&
                        !SongRepository.isStreamingAlbumSaved(intent.album, emptyList(), uiState.value.songs)
                    if (isExplicitlyRemote) {
                        val remote = remoteMusicRepository.getRemoteAlbumProfile(
                            albumTitle = intent.album.title,
                            artistName = intent.album.artist,
                            artworkUri = intent.album.artworkUri,
                            playlistUrl = intent.album.playlistUrl
                        ).getOrNull()
                        if (remote != null) {
                            _activeDetail.value = LibraryDetailView.AlbumDetail(
                                album = remote.album,
                                songs = remote.tracks,
                                albumProfile = remote
                            )
                            return@launch
                        }
                    }
                    songRepository.getAlbumProfile(intent.album.id).collect { profile ->
                        if (profile != null) {
                            _activeDetail.value = LibraryDetailView.AlbumDetail(
                                album = profile.album,
                                songs = profile.tracks,
                                albumProfile = profile
                            )
                        } else {
                            songRepository.getSongsByAlbum(intent.album.id).collect { songs ->
                                _activeDetail.value = LibraryDetailView.AlbumDetail(intent.album, songs)
                            }
                        }
                    }
                }
            }
            is LibraryIntent.OpenAlbumByName -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    val local = uiState.value.albums.firstOrNull { 
                        it.title.equals(intent.albumTitle, ignoreCase = true) 
                    }
                    if (local != null) {
                        onIntent(LibraryIntent.OpenAlbum(local))
                    } else {
                        val remoteAlbum = remoteMusicRepository.getRemoteAlbumProfile(intent.albumTitle, intent.artistName).getOrNull()
                        if (remoteAlbum != null) {
                            _activeDetail.value = LibraryDetailView.AlbumDetail(
                                album = remoteAlbum.album,
                                songs = remoteAlbum.tracks,
                                albumProfile = remoteAlbum
                            )
                        }
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
                _enrichmentSuggestions.value = emptyList()
                _isEnriching.value = false
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    combine(
                        playlistRepository.getPlaylistById(intent.playlist.id),
                        playlistRepository.getPlaylistSongs(intent.playlist.id)
                    ) { updatedPlaylist, songs ->
                        Pair(updatedPlaylist ?: intent.playlist, songs)
                    }.collect { (playlist, songs) ->
                        songs.forEach { song ->
                            if (song.isRemoteStream && !musicController.isSongCached(song)) {
                                musicController.downloadSongForOffline(song)
                            }
                        }
                        _activeDetail.value = LibraryDetailView.PlaylistDetail(playlist, songs)
                    }
                }
            }
            is LibraryIntent.CloseDetail -> {
                _enrichmentSuggestions.value = emptyList()
                _isEnriching.value = false
                detailJob?.cancel()
                _activeDetail.value = null
            }
            is LibraryIntent.EnrichPlaylist -> {
                viewModelScope.launch {
                    val detail = _activeDetail.value as? LibraryDetailView.PlaylistDetail
                    val existingSongs = if (detail != null && detail.playlist.id == intent.playlistId) {
                        detail.songs
                    } else {
                        playlistRepository.getPlaylistSongs(intent.playlistId).firstOrNull() ?: emptyList()
                    }
                    if (existingSongs.isEmpty()) {
                        _statusMessage.value = "Add at least one song to enrich this playlist"
                        return@launch
                    }
                    _isEnriching.value = true
                    try {
                        val suggestions = playlistGenerationEngine.enrichPlaylist(
                            existingTracks = existingSongs,
                            targetCount = 5,
                            forceRefresh = true
                        )
                        _enrichmentSuggestions.value = suggestions
                        if (suggestions.isEmpty()) {
                            _statusMessage.value = "No similar recommendations found"
                        }
                    } catch (e: Exception) {
                        _statusMessage.value = "Failed to find recommendations: ${e.message}"
                    } finally {
                        _isEnriching.value = false
                    }
                }
            }
            is LibraryIntent.AddEnrichedSong -> {
                viewModelScope.launch {
                    val resolvedSong = playlistRepository.addSongObjectToPlaylist(intent.playlistId, intent.song)
                    if (resolvedSong.isRemoteStream && !musicController.isSongCached(resolvedSong)) {
                        musicController.downloadSongForOffline(resolvedSong)
                    }
                    _enrichmentSuggestions.value = _enrichmentSuggestions.value.filterNot {
                        it.id == intent.song.id ||
                            (!intent.song.remoteVideoId.isNullOrEmpty() && it.remoteVideoId == intent.song.remoteVideoId)
                    }
                    _statusMessage.value = "Added '${intent.song.title}' to playlist"
                }
            }
            is LibraryIntent.ClearEnrichment -> {
                _enrichmentSuggestions.value = emptyList()
                _isEnriching.value = false
            }
            is LibraryIntent.SharePlaylist -> {
                viewModelScope.launch {
                    val tracks = intent.songs ?: playlistRepository.getPlaylistSongs(intent.playlist.id).firstOrNull().orEmpty()
                    if (tracks.isEmpty()) {
                        _statusMessage.value = "Add at least one song before sharing this playlist"
                        return@launch
                    }
                    val shareUrl = com.mousikify.core.model.DeepLinkCodec.encodePlaylistUrl(intent.playlist.name, tracks)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, intent.playlist.name)
                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                    }
                    val chooser = Intent.createChooser(sendIntent, "Share playlist via").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                }
            }
            is LibraryIntent.OpenSharedPlaylistPreview -> {
                _enrichmentSuggestions.value = emptyList()
                _isEnriching.value = false
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    val resolvedSongs = intent.songs.map { song ->
                        playlistRepository.resolveExistingLibrarySong(song) ?: song
                    }
                    val coverUri = resolvedSongs.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
                    val previewPlaylist = Playlist(
                        id = -2L,
                        name = intent.name.ifBlank { "Shared Playlist" },
                        createdAt = System.currentTimeMillis(),
                        customCoverUri = coverUri,
                        songCount = resolvedSongs.size
                    )
                    _activeDetail.value = LibraryDetailView.PlaylistDetail(previewPlaylist, resolvedSongs)
                }
            }
            is LibraryIntent.SaveSharedPlaylistToLibrary -> {
                viewModelScope.launch {
                    val trimmed = intent.name.trim().ifEmpty { "Shared Playlist" }
                    val coverUri = intent.songs.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
                    val newId = playlistRepository.createPlaylist(trimmed, coverUri)
                    val savedSongs = intent.songs.map { song ->
                        val resolved = playlistRepository.addSongObjectToPlaylist(newId, song)
                        if (resolved.isRemoteStream && !musicController.isSongCached(resolved)) {
                            musicController.downloadSongForOffline(resolved)
                        }
                        resolved
                    }
                    val savedPlaylist = playlistRepository.getPlaylistById(newId).firstOrNull()
                        ?: Playlist(id = newId, name = trimmed, createdAt = System.currentTimeMillis(), customCoverUri = coverUri, songCount = savedSongs.size)
                    _activeDetail.value = LibraryDetailView.PlaylistDetail(savedPlaylist, savedSongs)
                    _statusMessage.value = "Saved '$trimmed' to library"
                    onIntent(LibraryIntent.OpenPlaylist(savedPlaylist))
                }
            }
        }
    }

    private fun handleToggleShuffle(songs: List<Song>?) {
        if (musicController.isShuffleEnabled.value) {
            musicController.toggleShuffle()
        } else {
            if (!songs.isNullOrEmpty()) {
                val currentQueueIds = musicController.currentQueue.value.map { it.id }
                val targetIds = songs.map { it.id }
                if (currentQueueIds.isNotEmpty() && currentQueueIds == targetIds) {
                    musicController.toggleShuffle()
                } else {
                    musicController.setShuffleEnabled(true)
                    val startIndex = songs.indices.random()
                    val context = when (val detail = _activeDetail.value) {
                        is LibraryDetailView.PlaylistDetail -> PlaybackContext(
                            title = detail.playlist.name,
                            type = PlaybackContextType.PLAYLIST,
                            id = detail.playlist.id.toString(),
                            playlistId = detail.playlist.id,
                            isLocalOrSaved = true
                        )
                        is LibraryDetailView.AlbumDetail -> PlaybackContext(
                            title = detail.album.title,
                            type = PlaybackContextType.ALBUM,
                            id = detail.album.id.toString(),
                            albumId = detail.album.id,
                            artistName = detail.album.artist,
                            isLocalOrSaved = true
                        )
                        is LibraryDetailView.ArtistDetail -> PlaybackContext(
                            title = detail.artist.name,
                            type = PlaybackContextType.ARTIST,
                            id = detail.artist.id.toString(),
                            artistName = detail.artist.name,
                            isLocalOrSaved = true
                        )
                        is LibraryDetailView.FolderDetail -> PlaybackContext(
                            title = detail.folder.name,
                            type = PlaybackContextType.PLAYLIST,
                            id = detail.folder.path,
                            isLocalOrSaved = true
                        )
                        else -> PlaybackContext(
                            title = "Your Library",
                            type = PlaybackContextType.LIBRARY,
                            isLocalOrSaved = true
                        )
                    }
                    musicController.playQueue(songs, startIndex, context = context)
                }
            } else {
                musicController.toggleShuffle()
            }
        }
    }
}

private data class TupleScanDetail(
    val isScanning: Boolean,
    val statusMessage: String?,
    val activeDetail: LibraryDetailView?,
    val canUndo: Boolean,
    val isSavingAlbum: Boolean = false,
    val enrichmentSuggestions: List<Song> = emptyList(),
    val isEnriching: Boolean = false
)

private data class LibraryPlaybackState(
    val currentSong: Song?,
    val isPlaying: Boolean,
    val isShuffleEnabled: Boolean,
    val cacheTick: Long,
    val favoriteSongIds: Set<Long>,
    val favoriteVideoIds: Set<String>
)

private data class Tuple4(
    val filter: LibraryFilter,
    val sortOrder: LibrarySortOrder,
    val songs: List<Song>,
    val playlists: List<Playlist>,
    val artists: List<Artist>,
    val albums: List<Album>,
    val folders: List<Folder>,
    val selectedIds: Set<Long>,
    val showDialog: Boolean,
    val pinnedVideoIds: Set<String> = emptySet()
)
