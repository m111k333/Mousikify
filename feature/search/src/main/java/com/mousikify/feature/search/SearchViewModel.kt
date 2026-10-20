package com.mousikify.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.media.remote.RemotePlaylist
import com.mousikify.core.model.Album
import com.mousikify.core.model.Artist
import com.mousikify.core.model.PlaybackStatus
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab {
    ALL,
    ONLINE,
    PLAYLISTS,
    SONGS,
    ARTISTS,
    ALBUMS
}

data class SearchUiState(
    val query: String = "",
    val activeTab: SearchTab = SearchTab.ALL,
    val recentSearches: List<String> = emptyList(),
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val pinnedVideoIds: Set<String> = emptySet(),
    val songToAddToPlaylist: Song? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val statusMessage: String? = null,
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val playbackStatus: PlaybackStatus = PlaybackStatus.Idle,
    val cacheUpdateTick: Long = 0L,
    val onlinePlaylists: List<RemotePlaylist> = emptyList(),
    val isSearchingPlaylists: Boolean = false,
    val playlistSearchError: String? = null,
    val openedPlaylist: RemotePlaylist? = null,
    val openedPlaylistTracks: List<Song> = emptyList(),
    val isLoadingPlaylistTracks: Boolean = false,
    val playlistTracksError: String? = null,
    val isSavingPlaylistLocally: Boolean = false
)

sealed interface SearchIntent {
    data class UpdateQuery(val query: String) : SearchIntent
    data object SubmitSearch : SearchIntent
    data class SelectRecentSearch(val query: String) : SearchIntent
    data class RemoveRecentSearch(val query: String) : SearchIntent
    data object ClearRecentSearches : SearchIntent
    data class SelectTab(val tab: SearchTab) : SearchIntent
    data class PlaySong(val song: Song, val queue: List<Song>) : SearchIntent
    data class ToggleFavorite(val song: Song) : SearchIntent
    data class ShowAddToPlaylistDialog(val song: Song?) : SearchIntent
    data class ShowCreatePlaylistDialog(val show: Boolean) : SearchIntent
    data class AddSongToPlaylist(val playlistId: Long, val song: Song) : SearchIntent
    data class CreatePlaylistAndAddSong(val name: String) : SearchIntent
    data object DismissStatusMessage : SearchIntent
    data object ClearQuery : SearchIntent
    data object RetryPlaylistSearch : SearchIntent
    data class OpenPlaylist(val playlist: RemotePlaylist) : SearchIntent
    data object ClosePlaylistDetail : SearchIntent
    data object RetryLoadPlaylistTracks : SearchIntent
    data class SavePlaylistLocally(val playlist: RemotePlaylist, val tracks: List<Song>) : SearchIntent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackPreferences: PlaybackPreferences,
    private val remoteMusicRepository: RemoteMusicRepository,
    private val musicController: MusicController
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _activeTab = MutableStateFlow(SearchTab.ALL)
    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _playlistSearchData = MutableStateFlow(PlaylistSearchData())
    private val _playlistDetailData = MutableStateFlow(PlaylistDetailData())

    init {
        viewModelScope.launch {
            _query
                .debounce(350L)
                .distinctUntilChanged()
                .collect {
                    if (_activeTab.value == SearchTab.PLAYLISTS) {
                        triggerPlaylistSearch()
                    }
                }
        }
    }

    /**
     * Paging 3 stream of YouTube Music songs with 350ms debounce to avoid redundant requests while typing.
     */
    val onlinePagedTracks: Flow<PagingData<Song>> = _query
        .debounce(350L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.trim().length < 2) {
                flowOf(PagingData.empty())
            } else {
                remoteMusicRepository.searchMusicPaged(query.trim())
            }
        }
        .cachedIn(viewModelScope)

    private val searchInputs = combine(_query, _activeTab, playbackPreferences.recentSearchQueries) { query, tab, recent ->
        Triple(query, tab, recent)
    }

    private val dialogAndPlaylistState = combine(
        playlistRepository.playlists,
        _songToAddToPlaylist,
        _showCreatePlaylistDialog,
        _statusMessage
    ) { playlists, songToAdd, showCreate, statusMsg ->
         SearchDialogState(playlists, songToAdd, showCreate, statusMsg)
    }

    private val playbackState = combine(
        musicController.currentSong,
        musicController.isPlaying,
        musicController.playbackStatus,
        musicController.cacheUpdateTick
    ) { song, isPlaying, status, cacheTick ->
        SearchPlaybackState(song, isPlaying, status, cacheTick)
    }

    private val localCatalogState = combine(
        _query.flatMapLatest { songRepository.searchSongs(it) },
        songRepository.artists,
        songRepository.albums,
        songRepository.allSongs
    ) { matchingSongs, allArtists, allAlbums, allSavedSongs ->
        val pinnedIds = allSavedSongs.mapNotNull { it.remoteVideoId }.toSet()
        LocalCatalogData(matchingSongs, allArtists, allAlbums, pinnedIds)
    }

    private val onlinePlaylistState = combine(
        _playlistSearchData,
        _playlistDetailData
    ) { search, detail ->
        Pair(search, detail)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        searchInputs,
        localCatalogState,
        dialogAndPlaylistState,
        playbackState,
        onlinePlaylistState
    ) { (query, tab, recentSearches), catalog, dialogState, pbState, (searchData, detailData) ->
        val filteredArtists = if (query.isBlank()) emptyList() else {
            catalog.allArtists.filter { it.name.contains(query, ignoreCase = true) }
        }
        val filteredAlbums = if (query.isBlank()) emptyList() else {
            catalog.allAlbums.filter { it.title.contains(query, ignoreCase = true) }
        }

        SearchUiState(
            query = query,
            activeTab = tab,
            recentSearches = recentSearches,
            songs = catalog.matchingSongs,
            artists = filteredArtists,
            albums = filteredAlbums,
            playlists = dialogState.playlists,
            pinnedVideoIds = catalog.pinnedVideoIds,
            songToAddToPlaylist = dialogState.songToAddToPlaylist,
            showCreatePlaylistDialog = dialogState.showCreatePlaylistDialog,
            statusMessage = dialogState.statusMessage,
            currentSongId = pbState.currentSong?.id,
            isPlaying = pbState.isPlaying,
            playbackStatus = pbState.status,
            cacheUpdateTick = pbState.cacheTick,
            onlinePlaylists = searchData.playlists,
            isSearchingPlaylists = searchData.isSearching,
            playlistSearchError = searchData.error,
            openedPlaylist = detailData.playlist,
            openedPlaylistTracks = detailData.tracks,
            isLoadingPlaylistTracks = detailData.isLoading,
            playlistTracksError = detailData.error,
            isSavingPlaylistLocally = detailData.isSaving
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState()
    )

    fun isTrackCached(song: Song): Boolean =
        musicController.isSongCached(song) || (song.remoteVideoId != null && song.remoteVideoId in uiState.value.pinnedVideoIds)

    private fun recordCurrentSearchQueryIfValid() {
        val current = _query.value.trim()
        if (current.length >= 2) {
            viewModelScope.launch {
                playbackPreferences.addRecentSearchQuery(current)
            }
        }
    }

    private fun triggerPlaylistSearch() {
        val tab = _activeTab.value
        val query = _query.value.trim()
        if (tab != SearchTab.PLAYLISTS) return
        if (query.length < 2) {
            _playlistSearchData.value = PlaylistSearchData()
            return
        }
        viewModelScope.launch {
            _playlistSearchData.value = _playlistSearchData.value.copy(
                isSearching = true,
                error = null
            )
            remoteMusicRepository.searchPlaylists(query)
                .onSuccess { list ->
                    _playlistSearchData.value = PlaylistSearchData(
                        playlists = list,
                        isSearching = false,
                        error = null
                    )
                }
                .onFailure { e ->
                    _playlistSearchData.value = _playlistSearchData.value.copy(
                        isSearching = false,
                        error = e.message ?: "Failed to search playlists"
                    )
                }
        }
    }

    private fun loadTracksForPlaylist(playlist: RemotePlaylist) {
        viewModelScope.launch {
            _playlistDetailData.value = PlaylistDetailData(
                playlist = playlist,
                tracks = emptyList(),
                isLoading = true,
                error = null,
                isSaving = false
            )
            remoteMusicRepository.getPlaylistTracks(playlist.url)
                .onSuccess { tracks ->
                    _playlistDetailData.value = _playlistDetailData.value.copy(
                        tracks = tracks,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { e ->
                    _playlistDetailData.value = _playlistDetailData.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load playlist tracks"
                    )
                }
        }
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery -> _query.value = intent.query
            is SearchIntent.SubmitSearch -> recordCurrentSearchQueryIfValid()
            is SearchIntent.SelectRecentSearch -> {
                _query.value = intent.query
                viewModelScope.launch {
                    playbackPreferences.addRecentSearchQuery(intent.query)
                }
            }
            is SearchIntent.RemoveRecentSearch -> {
                viewModelScope.launch {
                    playbackPreferences.removeRecentSearchQuery(intent.query)
                }
            }
            is SearchIntent.ClearRecentSearches -> {
                viewModelScope.launch {
                    playbackPreferences.clearRecentSearchQueries()
                }
            }
            is SearchIntent.ClearQuery -> {
                _query.value = ""
                _playlistSearchData.value = PlaylistSearchData()
                _playlistDetailData.value = PlaylistDetailData()
            }
            is SearchIntent.SelectTab -> {
                _activeTab.value = intent.tab
                _playlistDetailData.value = PlaylistDetailData()
                if (intent.tab == SearchTab.PLAYLISTS && _playlistSearchData.value.playlists.isEmpty() && _query.value.trim().length >= 2) {
                    triggerPlaylistSearch()
                }
            }
            is SearchIntent.PlaySong -> {
                recordCurrentSearchQueryIfValid()
                val startIdx = intent.queue.indexOfFirst { it.id == intent.song.id }.coerceAtLeast(0)
                musicController.playQueue(intent.queue, startIdx)
            }
            is SearchIntent.ToggleFavorite -> {
                musicController.toggleFavorite(intent.song)
            }
            is SearchIntent.ShowAddToPlaylistDialog -> {
                _songToAddToPlaylist.value = intent.song
            }
            is SearchIntent.ShowCreatePlaylistDialog -> {
                _showCreatePlaylistDialog.value = intent.show
            }
            is SearchIntent.AddSongToPlaylist -> {
                viewModelScope.launch {
                    playlistRepository.addSongObjectToPlaylist(intent.playlistId, intent.song)
                    if (intent.song.isRemoteStream) {
                        musicController.downloadSongForOffline(intent.song)
                    }
                    _songToAddToPlaylist.value = null
                    _statusMessage.value = if (intent.song.isRemoteStream) {
                        "Saved '${intent.song.title}' to playlist & downloading for offline play"
                    } else {
                        "Added '${intent.song.title}' to playlist"
                    }
                }
            }
            is SearchIntent.CreatePlaylistAndAddSong -> {
                val trimmed = intent.name.trim()
                if (trimmed.isEmpty()) return
                viewModelScope.launch {
                    val playlistId = playlistRepository.createPlaylist(trimmed)
                    val song = _songToAddToPlaylist.value
                    if (song != null) {
                        playlistRepository.addSongObjectToPlaylist(playlistId, song)
                        if (song.isRemoteStream) {
                            musicController.downloadSongForOffline(song)
                        }
                        _statusMessage.value = if (song.isRemoteStream) {
                            "Created '$trimmed' & downloading '${song.title}' for offline play"
                        } else {
                            "Created '$trimmed' with '${song.title}'"
                        }
                    } else {
                        _statusMessage.value = "Created playlist '$trimmed'"
                    }
                    _showCreatePlaylistDialog.value = false
                    _songToAddToPlaylist.value = null
                }
            }
            is SearchIntent.DismissStatusMessage -> {
                _statusMessage.value = null
            }
            is SearchIntent.RetryPlaylistSearch -> triggerPlaylistSearch()
            is SearchIntent.OpenPlaylist -> loadTracksForPlaylist(intent.playlist)
            is SearchIntent.ClosePlaylistDetail -> _playlistDetailData.value = PlaylistDetailData()
            is SearchIntent.RetryLoadPlaylistTracks -> {
                _playlistDetailData.value.playlist?.let { loadTracksForPlaylist(it) }
            }
            is SearchIntent.SavePlaylistLocally -> {
                if (_playlistDetailData.value.isSaving) return
                val playlist = intent.playlist
                val tracks = intent.tracks
                if (tracks.isEmpty()) return
                viewModelScope.launch {
                    _playlistDetailData.value = _playlistDetailData.value.copy(isSaving = true)
                    try {
                        val playlistId = playlistRepository.createPlaylist(playlist.name, playlist.thumbnailUrl)
                        for (track in tracks) {
                            playlistRepository.addSongObjectToPlaylist(playlistId, track)
                            if (track.isRemoteStream) {
                                musicController.downloadSongForOffline(track)
                            }
                        }
                        _statusMessage.value = "Saved '${playlist.name}' with ${tracks.size} songs & downloading for offline play"
                    } catch (e: Exception) {
                        _statusMessage.value = "Failed to save playlist: ${e.message}"
                    } finally {
                        _playlistDetailData.value = _playlistDetailData.value.copy(isSaving = false)
                    }
                }
            }
        }
    }
}

private data class PlaylistSearchData(
    val playlists: List<RemotePlaylist> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)

private data class PlaylistDetailData(
    val playlist: RemotePlaylist? = null,
    val tracks: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false
)

private data class LocalCatalogData(
    val matchingSongs: List<Song>,
    val allArtists: List<Artist>,
    val allAlbums: List<Album>,
    val pinnedVideoIds: Set<String>
)

private data class SearchDialogState(
    val playlists: List<Playlist>,
    val songToAddToPlaylist: Song?,
    val showCreatePlaylistDialog: Boolean,
    val statusMessage: String?
)

private data class SearchPlaybackState(
    val currentSong: Song?,
    val isPlaying: Boolean,
    val status: PlaybackStatus,
    val cacheTick: Long
)
