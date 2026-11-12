package com.mousikify.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.media.recommendation.SearchDiscoveryEngine
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.media.remote.RemotePlaylist
import com.mousikify.core.model.Album
import com.mousikify.core.model.AlbumProfile
import com.mousikify.core.model.Artist
import com.mousikify.core.model.ArtistProfile
import com.mousikify.core.model.GenreCategory
import com.mousikify.core.model.PlaybackContext
import com.mousikify.core.model.PlaybackContextType
import com.mousikify.core.model.PlaybackStatus
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.SearchDiscoveryData
import com.mousikify.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
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
    val onlineSongs: List<Song> = emptyList(),
    val onlineArtists: List<Artist> = emptyList(),
    val onlineAlbums: List<Album> = emptyList(),
    val onlinePlaylists: List<RemotePlaylist> = emptyList(),
    val isSearchingOnline: Boolean = false,
    val onlineSearchError: String? = null,
    val isSearchingPlaylists: Boolean = false,
    val playlistSearchError: String? = null,
    val openedPlaylist: RemotePlaylist? = null,
    val openedPlaylistTracks: List<Song> = emptyList(),
    val isLoadingPlaylistTracks: Boolean = false,
    val playlistTracksError: String? = null,
    val isSavingPlaylistLocally: Boolean = false,
    val isSavingAlbumLocally: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val openedArtistProfile: ArtistProfile? = null,
    val openedAlbumProfile: AlbumProfile? = null,
    val allSavedAlbums: List<Album> = emptyList(),
    val allLibrarySongs: List<Song> = emptyList(),
    val canUndo: Boolean = false,
    val favoriteSongIds: Set<Long> = emptySet(),
    val favoriteVideoIds: Set<String> = emptySet(),
    val discoveryData: SearchDiscoveryData = SearchDiscoveryData()
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
            librarySongs = allLibrarySongs
        )
}

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
    data object UndoDelete : SearchIntent
    data object ClearQuery : SearchIntent
    data object RetryPlaylistSearch : SearchIntent
    data object RetryOnlineSearch : SearchIntent
    data class OpenPlaylist(val playlist: RemotePlaylist) : SearchIntent
    data object ClosePlaylistDetail : SearchIntent
    data object RetryLoadPlaylistTracks : SearchIntent
    data class SavePlaylistLocally(val playlist: RemotePlaylist, val tracks: List<Song>) : SearchIntent
    data class UnsavePlaylistLocally(val playlist: RemotePlaylist, val tracks: List<Song>) : SearchIntent
    data class SaveAlbumLocally(val album: Album, val tracks: List<Song>) : SearchIntent
    data class UnsaveAlbumLocally(val album: Album, val tracks: List<Song>) : SearchIntent
    data class DeleteSongFromAlbum(val song: Song) : SearchIntent
    data class ToggleShuffle(val songs: List<Song>? = null) : SearchIntent
    data class OpenArtist(val artist: Artist) : SearchIntent
    data class OpenArtistByName(val artistName: String) : SearchIntent
    data class OpenAlbum(val album: Album) : SearchIntent
    data class OpenAlbumByName(val albumTitle: String, val artistName: String) : SearchIntent
    data object CloseDetail : SearchIntent
    data class PlayNext(val song: Song) : SearchIntent
    data class AddToQueue(val song: Song) : SearchIntent
    data class StartRadio(val song: Song) : SearchIntent
    data class StartArtistRadio(val artistName: String) : SearchIntent
    data class PlayArtistTopSongs(val artistName: String, val shuffle: Boolean = false) : SearchIntent
    data object ResetToSearchRoot : SearchIntent
    data object RefreshDiscovery : SearchIntent
    data class SelectGenre(val genre: GenreCategory) : SearchIntent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackPreferences: PlaybackPreferences,
    private val remoteMusicRepository: RemoteMusicRepository,
    private val musicController: MusicController,
    private val searchDiscoveryEngine: SearchDiscoveryEngine
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _activeTab = MutableStateFlow(SearchTab.ALL)
    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _canUndo = MutableStateFlow(false)
    private var lastDeletedAlbumSong: Song? = null
    private val _onlineSearchData = MutableStateFlow(OnlineSearchData())
    private val _playlistDetailData = MutableStateFlow(PlaylistDetailData())
    private val _openedArtistProfile = MutableStateFlow<ArtistProfile?>(null)
    private val _openedAlbumProfile = MutableStateFlow<AlbumProfile?>(null)
    private val _discoveryData = MutableStateFlow(
        searchDiscoveryEngine.loadCachedDiscovery() ?: SearchDiscoveryData(
            genres = SearchDiscoveryEngine.CURATED_GENRES,
            isLoading = true
        )
    )
    private var detailJob: Job? = null
    private var onlineSearchJob: Job? = null

    init {
        refreshDiscoveryInternal(force = false)
        viewModelScope.launch {
            _query
                .debounce(350L)
                .distinctUntilChanged()
                .collect { q ->
                    val trimmed = q.trim()
                    if (trimmed.length >= 2) {
                        triggerUnifiedOnlineSearch(trimmed)
                    } else {
                        _onlineSearchData.value = OnlineSearchData()
                    }
                }
        }
    }

    private fun refreshDiscoveryInternal(force: Boolean) {
        viewModelScope.launch {
            _discoveryData.value = _discoveryData.value.copy(isLoading = true)
            val resolved = searchDiscoveryEngine.getOrRefreshDiscovery(forceRefresh = force)
            _discoveryData.value = resolved.copy(isLoading = false)
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
        _statusMessage,
        _canUndo
    ) { playlists, songToAdd, showCreate, statusMsg, canUndo ->
         SearchDialogState(playlists, songToAdd, showCreate, statusMsg, canUndo)
    }

    private val favoriteFlow = combine(
        musicController.favoriteSongIds,
        musicController.favoriteVideoIds
    ) { songIds, videoIds -> Pair(songIds, videoIds) }

    private val playbackState = combine(
        musicController.currentSong,
        musicController.isPlaying,
        musicController.playbackStatus,
        combine(musicController.isShuffleEnabled, musicController.cacheUpdateTick) { shuffle, tick -> Pair(shuffle, tick) },
        favoriteFlow
    ) { song, isPlaying, status, (isShuffle, cacheTick), (favSongIds, favVideoIds) ->
        SearchPlaybackState(song, isPlaying, status, isShuffle, cacheTick, favSongIds, favVideoIds)
    }

    private val localCatalogState = combine(
        _query.flatMapLatest { songRepository.searchSongs(it) },
        songRepository.artists,
        songRepository.albums,
        songRepository.allSongs
    ) { matchingSongs, allArtists, allAlbums, allSavedSongs ->
        val pinnedIds = allSavedSongs.mapNotNull { it.remoteVideoId }.toSet()
        LocalCatalogData(matchingSongs, allArtists, allAlbums, pinnedIds, allSavedSongs)
    }

    private val onlineDataState = combine(
        _onlineSearchData,
        _playlistDetailData
    ) { search, detail ->
        Pair(search, detail)
    }

    private val _isSavingAlbumLocally = MutableStateFlow(false)

    private val detailProfilesState = combine(
        _openedArtistProfile,
        _openedAlbumProfile,
        _isSavingAlbumLocally
    ) { artist, album, isSavingAlbum ->
        Triple(artist, album, isSavingAlbum)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        searchInputs,
        localCatalogState,
        dialogAndPlaylistState,
        playbackState,
        combine(onlineDataState, detailProfilesState, _discoveryData) { onlineData, profiles, discovery ->
            Triple(onlineData, profiles, discovery)
        }
    ) { (query, tab, recentSearches), catalog, dialogState, pbState, (onlineData, profiles, discovery) ->
        val (searchData, detailData) = onlineData
        val (artistProfile, albumProfile, isSavingAlbum) = profiles
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
            onlineSongs = searchData.songs,
            onlineArtists = searchData.artists,
            onlineAlbums = searchData.albums,
            onlinePlaylists = searchData.playlists,
            isSearchingOnline = searchData.isSearching,
            onlineSearchError = searchData.error,
            isSearchingPlaylists = searchData.isSearching,
            playlistSearchError = searchData.error,
            openedPlaylist = detailData.playlist,
            openedPlaylistTracks = detailData.tracks,
            isLoadingPlaylistTracks = detailData.isLoading,
            playlistTracksError = detailData.error,
            isSavingPlaylistLocally = detailData.isSaving,
            isSavingAlbumLocally = isSavingAlbum,
            isShuffleEnabled = pbState.isShuffleEnabled,
            openedArtistProfile = artistProfile,
            openedAlbumProfile = albumProfile,
            allSavedAlbums = catalog.allAlbums,
            allLibrarySongs = catalog.allSavedSongs,
            canUndo = dialogState.canUndo,
            favoriteSongIds = pbState.favoriteSongIds,
            favoriteVideoIds = pbState.favoriteVideoIds,
            discoveryData = discovery
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState()
    )

    fun isSongFavorite(song: Song): Boolean = uiState.value.isFavorite(song)

    fun isTrackCached(song: Song): Boolean =
        if (song.isLocal) true else musicController.isSongCached(song)

    private fun recordCurrentSearchQueryIfValid() {
        val current = _query.value.trim()
        if (current.length >= 2) {
            viewModelScope.launch {
                playbackPreferences.addRecentSearchQuery(current)
            }
        }
    }

    private fun triggerUnifiedOnlineSearch(query: String) {
        onlineSearchJob?.cancel()
        onlineSearchJob = viewModelScope.launch {
            _onlineSearchData.value = _onlineSearchData.value.copy(
                isSearching = true,
                error = null
            )
            try {
                coroutineScope {
                    val songsDef = async { remoteMusicRepository.searchMusicQuick(query) }
                    val artistsDef = async { remoteMusicRepository.searchArtists(query) }
                    val albumsDef = async { remoteMusicRepository.searchAlbums(query) }
                    val playlistsDef = async { remoteMusicRepository.searchPlaylists(query) }

                    val songs = songsDef.await().getOrDefault(emptyList())
                    val artists = artistsDef.await().getOrDefault(emptyList())
                    val albums = albumsDef.await().getOrDefault(emptyList())
                    val playlists = playlistsDef.await().getOrDefault(emptyList())

                    _onlineSearchData.value = OnlineSearchData(
                        songs = songs,
                        artists = artists,
                        albums = albums,
                        playlists = playlists,
                        isSearching = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _onlineSearchData.value = _onlineSearchData.value.copy(
                    isSearching = false,
                    error = e.message ?: "Failed to search online"
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
            is SearchIntent.RefreshDiscovery -> {
                refreshDiscoveryInternal(force = true)
            }
            is SearchIntent.SelectGenre -> {
                _query.value = intent.genre.queryKeyword
                _activeTab.value = SearchTab.SONGS
                _openedArtistProfile.value = null
                _openedAlbumProfile.value = null
                _playlistDetailData.value = PlaylistDetailData()
                triggerUnifiedOnlineSearch(intent.genre.queryKeyword)
            }
            is SearchIntent.UpdateQuery -> _query.value = intent.query
            is SearchIntent.SubmitSearch -> {
                recordCurrentSearchQueryIfValid()
                val q = _query.value.trim()
                if (q.length >= 2) triggerUnifiedOnlineSearch(q)
            }
            is SearchIntent.SelectRecentSearch -> {
                _query.value = intent.query
                viewModelScope.launch {
                    playbackPreferences.addRecentSearchQuery(intent.query)
                }
                if (intent.query.trim().length >= 2) {
                    triggerUnifiedOnlineSearch(intent.query.trim())
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
                _onlineSearchData.value = OnlineSearchData()
                _playlistDetailData.value = PlaylistDetailData()
                _openedArtistProfile.value = null
                _openedAlbumProfile.value = null
            }
            is SearchIntent.SelectTab -> {
                _activeTab.value = intent.tab
                _playlistDetailData.value = PlaylistDetailData()
                _openedArtistProfile.value = null
                _openedAlbumProfile.value = null
            }
            is SearchIntent.PlaySong -> {
                recordCurrentSearchQueryIfValid()
                val startIdx = intent.queue.indexOfFirst { it.id == intent.song.id }.coerceAtLeast(0)
                val context = when {
                    _openedAlbumProfile.value != null -> {
                        val profile = _openedAlbumProfile.value!!
                        val album = profile.album
                        val isSaved = uiState.value.isAlbumSaved(profile)
                        PlaybackContext(
                            title = album.title,
                            type = PlaybackContextType.ALBUM,
                            id = album.id.toString(),
                            albumId = if (album.id != 0L) album.id else null,
                            artistName = album.artist,
                            isLocalOrSaved = isSaved
                        )
                    }
                    _openedArtistProfile.value != null -> {
                        val artist = _openedArtistProfile.value!!.artist
                        PlaybackContext(
                            title = artist.name,
                            type = PlaybackContextType.ARTIST,
                            id = artist.id.toString(),
                            artistName = artist.name,
                            isLocalOrSaved = false
                        )
                    }
                    _playlistDetailData.value.playlist != null -> {
                        val pl = _playlistDetailData.value.playlist!!
                        PlaybackContext(
                            title = pl.name,
                            type = PlaybackContextType.PLAYLIST,
                            id = pl.url,
                            remotePlaylistUrl = pl.url,
                            artworkUri = pl.thumbnailUrl,
                            isLocalOrSaved = false
                        )
                    }
                    else -> {
                        PlaybackContext(
                            title = "Search",
                            type = PlaybackContextType.SEARCH
                        )
                    }
                }
                musicController.playQueue(intent.queue, startIdx, context = context)
                val currentQuery = _query.value.trim()
                if (intent.song.isRemoteStream && intent.queue.size < 15) {
                    viewModelScope.launch {
                        val queryToSearch = if (currentQuery.length >= 2) currentQuery else intent.song.artistName
                        val fullTracksResult = remoteMusicRepository.searchSongsByArtist(queryToSearch, limit = 25)
                        val fullTracks = fullTracksResult.getOrNull().orEmpty()
                        if (fullTracks.isNotEmpty()) {
                            musicController.queueManager.appendSongs(fullTracks)
                        }
                    }
                }
            }
            is SearchIntent.ToggleFavorite -> {
                val song = intent.song
                val willBeFav = !isSongFavorite(song)
                musicController.toggleFavorite(song)

                _openedAlbumProfile.value?.let { profile ->
                    val updatedTracks = profile.tracks.map {
                        if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                            it.copy(isFavorite = willBeFav)
                        } else it
                    }
                    _openedAlbumProfile.value = profile.copy(tracks = updatedTracks)
                }

                _playlistDetailData.value.let { detail ->
                    val updatedTracks = detail.tracks.map {
                        if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                            it.copy(isFavorite = willBeFav)
                        } else it
                    }
                    _playlistDetailData.value = detail.copy(tracks = updatedTracks)
                }

                _openedArtistProfile.value?.let { profile ->
                    val updatedTop = profile.topSongs.map {
                        if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                            it.copy(isFavorite = willBeFav)
                        } else it
                    }
                    val updatedFeatured = profile.featuredIn.map {
                        if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                            it.copy(isFavorite = willBeFav)
                        } else it
                    }
                    _openedArtistProfile.value = profile.copy(topSongs = updatedTop, featuredIn = updatedFeatured)
                }

                _onlineSearchData.value.let { data ->
                    val updatedSongs = data.songs.map {
                        if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                            it.copy(isFavorite = willBeFav)
                        } else it
                    }
                    _onlineSearchData.value = data.copy(songs = updatedSongs)
                }
            }
            is SearchIntent.ShowAddToPlaylistDialog -> {
                _songToAddToPlaylist.value = intent.song
            }
            is SearchIntent.ShowCreatePlaylistDialog -> {
                _showCreatePlaylistDialog.value = intent.show
            }
            is SearchIntent.AddSongToPlaylist -> {
                viewModelScope.launch {
                    val resolvedSong = playlistRepository.addSongObjectToPlaylist(intent.playlistId, intent.song)
                    val shouldDownload = resolvedSong.isRemoteStream && !musicController.isSongCached(resolvedSong)
                    if (shouldDownload) {
                        musicController.downloadSongForOffline(resolvedSong)
                    }
                    _songToAddToPlaylist.value = null
                    _statusMessage.value = if (shouldDownload) {
                        "Saved '${resolvedSong.title}' to playlist & downloading for offline play"
                    } else {
                        "Added '${resolvedSong.title}' to playlist"
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
                        val resolvedSong = playlistRepository.addSongObjectToPlaylist(playlistId, song)
                        val shouldDownload = resolvedSong.isRemoteStream && !musicController.isSongCached(resolvedSong)
                        if (shouldDownload) {
                            musicController.downloadSongForOffline(resolvedSong)
                        }
                        _statusMessage.value = if (shouldDownload) {
                            "Created '$trimmed' & downloading '${resolvedSong.title}' for offline play"
                        } else {
                            "Created '$trimmed' with '${resolvedSong.title}'"
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
                _canUndo.value = false
                lastDeletedAlbumSong = null
            }
            is SearchIntent.UndoDelete -> {
                viewModelScope.launch {
                    val s = lastDeletedAlbumSong
                    if (s != null) {
                        songRepository.upsertSongs(listOf(s))
                        if (s.isRemoteStream && !musicController.isSongCached(s)) {
                            musicController.downloadSongForOffline(s)
                        }
                        _openedAlbumProfile.value?.let { currentProfile ->
                            if (currentProfile.tracks.none { it.id == s.id }) {
                                val updated = (currentProfile.tracks + s).sortedBy { it.trackNumber }
                                _openedAlbumProfile.value = currentProfile.copy(tracks = updated)
                            }
                        }
                        _statusMessage.value = "Restored '${s.title}'"
                        lastDeletedAlbumSong = null
                        _canUndo.value = false
                    }
                }
            }
            is SearchIntent.RetryPlaylistSearch,
            is SearchIntent.RetryOnlineSearch -> {
                val q = _query.value.trim()
                if (q.length >= 2) triggerUnifiedOnlineSearch(q)
            }
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
                            val resolvedSong = playlistRepository.addSongObjectToPlaylist(playlistId, track)
                            if (resolvedSong.isRemoteStream && !musicController.isSongCached(resolvedSong)) {
                                musicController.downloadSongForOffline(resolvedSong)
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
            is SearchIntent.UnsavePlaylistLocally -> {
                val playlist = intent.playlist
                viewModelScope.launch {
                    try {
                        playlistRepository.deletePlaylistByName(playlist.name)
                        songRepository.deleteNonFavoriteRemoteSongs()
                        musicController.pruneOrphanedCache()
                        _statusMessage.value = "Removed '${playlist.name}' and cleaned up unused offline downloads"
                    } catch (e: Exception) {
                        _statusMessage.value = "Failed to unsave playlist: ${e.message}"
                    }
                }
            }
            is SearchIntent.SaveAlbumLocally -> {
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
            is SearchIntent.UnsaveAlbumLocally -> {
                val album = intent.album
                val tracks = intent.tracks
                viewModelScope.launch {
                    try {
                        val albumTracks = if (tracks.isNotEmpty()) {
                            tracks
                        } else {
                            songRepository.getSongsByAlbum(album.id).firstOrNull() ?: emptyList()
                        }
                        musicController.deleteDownloadedTracks(albumTracks)
                        playlistRepository.deletePlaylistByName(album.title)
                        _statusMessage.value = "Removed '${album.title}' and deleted offline downloads"
                    } catch (e: Exception) {
                        _statusMessage.value = "Failed to remove album: ${e.message}"
                    }
                }
            }
            is SearchIntent.DeleteSongFromAlbum -> {
                viewModelScope.launch {
                    val s = intent.song
                    lastDeletedAlbumSong = s
                    _canUndo.value = true
                    if (s.isRemoteStream) {
                        musicController.deleteDownloadedTrack(s)
                    } else {
                        songRepository.deleteSongs(listOf(s.id))
                    }
                    _openedAlbumProfile.value?.let { currentProfile ->
                        val updatedTracks = currentProfile.tracks.filter { it.id != s.id }
                        _openedAlbumProfile.value = currentProfile.copy(tracks = updatedTracks)
                    }
                    _statusMessage.value = "Removed '${s.title}' from album"
                }
            }
            is SearchIntent.ToggleShuffle -> handleToggleShuffle(intent.songs)
            is SearchIntent.OpenArtist -> {
                detailJob?.cancel()
                _openedArtistProfile.value = ArtistProfile(
                    artist = intent.artist,
                    isLoading = true
                )
                detailJob = viewModelScope.launch {
                    val localProfile = runCatching { songRepository.getArtistProfile(intent.artist.id).firstOrNull() }.getOrNull()
                    if (localProfile != null) {
                        _openedArtistProfile.value = localProfile.copy(isLoading = true)
                    }
                    val remote = remoteMusicRepository.getRemoteArtistProfile(
                        artistName = intent.artist.name,
                        channelUrl = intent.artist.channelUrl,
                        avatarUrl = intent.artist.avatarUrl
                    ).getOrNull()
                    if (remote != null) {
                        _openedArtistProfile.value = remote.copy(
                            artist = remote.artist.copy(
                                id = intent.artist.id,
                                avatarUrl = remote.artist.avatarUrl ?: intent.artist.avatarUrl
                            ),
                            isLoading = false
                        )
                    } else if (localProfile != null) {
                        _openedArtistProfile.value = localProfile.copy(
                            artist = localProfile.artist.copy(
                                id = intent.artist.id,
                                avatarUrl = localProfile.artist.avatarUrl ?: intent.artist.avatarUrl
                            ),
                            isLoading = false
                        )
                    } else {
                        _openedArtistProfile.value = ArtistProfile(intent.artist, isLoading = false)
                    }
                }
            }
            is SearchIntent.OpenArtistByName -> {
                detailJob?.cancel()
                val cleanName = com.mousikify.core.model.cleanArtistName(intent.artistName)
                val matched = uiState.value.onlineArtists.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
                    ?: uiState.value.artists.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
                val fallbackArtwork = uiState.value.onlineSongs.firstOrNull {
                    it.cleanArtistName.equals(cleanName, ignoreCase = true) && !it.artworkUri.isNullOrBlank()
                }?.artworkUri ?: uiState.value.songs.firstOrNull {
                    it.cleanArtistName.equals(cleanName, ignoreCase = true) && !it.artworkUri.isNullOrBlank()
                }?.artworkUri
                val initialArtist = matched?.copy(avatarUrl = matched.avatarUrl ?: fallbackArtwork) ?: Artist(
                    id = cleanName.hashCode().toLong(),
                    name = cleanName,
                    songCount = 0,
                    avatarUrl = fallbackArtwork
                )
                _openedArtistProfile.value = ArtistProfile(
                    artist = initialArtist,
                    isLoading = true
                )
                detailJob = viewModelScope.launch {
                    val localProfile = runCatching { songRepository.getArtistProfileByName(cleanName).firstOrNull() }.getOrNull()
                    if (localProfile != null) {
                        _openedArtistProfile.value = localProfile.copy(
                            artist = localProfile.artist.copy(
                                id = initialArtist.id,
                                avatarUrl = localProfile.artist.avatarUrl ?: initialArtist.avatarUrl
                            ),
                            isLoading = true
                        )
                    }
                    val remote = remoteMusicRepository.getRemoteArtistProfile(
                        artistName = cleanName,
                        channelUrl = matched?.channelUrl,
                        avatarUrl = initialArtist.avatarUrl
                    ).getOrNull()
                    if (remote != null) {
                        _openedArtistProfile.value = remote.copy(
                            artist = remote.artist.copy(
                                id = initialArtist.id,
                                avatarUrl = remote.artist.avatarUrl ?: initialArtist.avatarUrl
                            ),
                            isLoading = false
                        )
                    } else if (localProfile != null) {
                        _openedArtistProfile.value = localProfile.copy(
                            artist = localProfile.artist.copy(
                                id = initialArtist.id,
                                avatarUrl = localProfile.artist.avatarUrl ?: initialArtist.avatarUrl
                            ),
                            isLoading = false
                        )
                    } else {
                        _openedArtistProfile.value = ArtistProfile(initialArtist, isLoading = false)
                    }
                }
            }
            is SearchIntent.OpenAlbum -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    if (!intent.album.playlistUrl.isNullOrBlank()) {
                        val remote = remoteMusicRepository.getRemoteAlbumProfile(
                            albumTitle = intent.album.title,
                            artistName = intent.album.artist,
                            artworkUri = intent.album.artworkUri,
                            playlistUrl = intent.album.playlistUrl
                        ).getOrNull()
                        _openedAlbumProfile.value = remote
                    } else {
                        songRepository.getAlbumProfile(intent.album.id).collect { profile ->
                            if (profile != null) {
                                _openedAlbumProfile.value = profile
                            } else {
                                val remote = remoteMusicRepository.getRemoteAlbumProfile(
                                    albumTitle = intent.album.title,
                                    artistName = intent.album.artist,
                                    artworkUri = intent.album.artworkUri
                                ).getOrNull()
                                _openedAlbumProfile.value = remote
                            }
                        }
                    }
                }
            }
            is SearchIntent.OpenAlbumByName -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    val local = uiState.value.albums.firstOrNull { it.title.equals(intent.albumTitle, ignoreCase = true) }
                    if (local != null) {
                        onIntent(SearchIntent.OpenAlbum(local))
                    } else {
                        val matched = uiState.value.onlineAlbums.firstOrNull { it.title.equals(intent.albumTitle, ignoreCase = true) }
                        val remote = remoteMusicRepository.getRemoteAlbumProfile(
                            albumTitle = intent.albumTitle,
                            artistName = intent.artistName,
                            artworkUri = matched?.artworkUri,
                            playlistUrl = matched?.playlistUrl
                        ).getOrNull()
                        _openedAlbumProfile.value = remote
                    }
                }
            }
            is SearchIntent.CloseDetail -> {
                detailJob?.cancel()
                if (_openedAlbumProfile.value != null && _openedArtistProfile.value != null) {
                    _openedAlbumProfile.value = null
                } else {
                    _openedArtistProfile.value = null
                    _openedAlbumProfile.value = null
                }
            }
            is SearchIntent.ResetToSearchRoot -> {
                detailJob?.cancel()
                _openedArtistProfile.value = null
                _openedAlbumProfile.value = null
                _playlistDetailData.value = PlaylistDetailData()
                _query.value = ""
                _activeTab.value = SearchTab.ALL
            }
            is SearchIntent.PlayNext -> musicController.playNext(intent.song)
            is SearchIntent.AddToQueue -> musicController.addToQueue(intent.song)
            is SearchIntent.StartRadio -> musicController.startRadio(intent.song)
            is SearchIntent.StartArtistRadio -> musicController.startArtistRadio(com.mousikify.core.model.cleanArtistName(intent.artistName))
            is SearchIntent.PlayArtistTopSongs -> {
                val cleanName = com.mousikify.core.model.cleanArtistName(intent.artistName)
                viewModelScope.launch {
                    val songs = remoteMusicRepository.searchSongsByArtist(cleanName, limit = 20)
                        .getOrDefault(emptyList())
                    if (songs.isNotEmpty()) {
                        val context = PlaybackContext(
                            title = cleanName,
                            type = PlaybackContextType.ARTIST,
                            id = cleanName.hashCode().toLong().toString(),
                            artistName = cleanName
                        )
                        if (intent.shuffle) {
                            musicController.setShuffleEnabled(true)
                            val startIndex = songs.indices.random()
                            musicController.playQueue(songs, startIndex, context = context)
                        } else {
                            musicController.setShuffleEnabled(false)
                            musicController.playQueue(songs, 0, context = context)
                        }
                    }
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
                    val context = when {
                        _openedAlbumProfile.value != null -> {
                            val profile = _openedAlbumProfile.value!!
                            val album = profile.album
                            val isSaved = uiState.value.isAlbumSaved(profile)
                            PlaybackContext(
                                title = album.title,
                                type = PlaybackContextType.ALBUM,
                                id = album.id.toString(),
                                albumId = if (album.id != 0L) album.id else null,
                                artistName = album.artist,
                                isLocalOrSaved = isSaved
                            )
                        }
                        _openedArtistProfile.value != null -> {
                            val artist = _openedArtistProfile.value!!.artist
                            PlaybackContext(
                                title = artist.name,
                                type = PlaybackContextType.ARTIST,
                                id = artist.id.toString(),
                                artistName = artist.name,
                                isLocalOrSaved = false
                            )
                        }
                        _playlistDetailData.value.playlist != null -> {
                            val pl = _playlistDetailData.value.playlist!!
                            PlaybackContext(
                                title = pl.name,
                                type = PlaybackContextType.PLAYLIST,
                                id = pl.url,
                                remotePlaylistUrl = pl.url,
                                artworkUri = pl.thumbnailUrl,
                                isLocalOrSaved = false
                            )
                        }
                        else -> null
                    }
                    musicController.playQueue(songs, startIndex, context = context)
                }
            } else {
                musicController.toggleShuffle()
            }
        }
    }
}

private data class OnlineSearchData(
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
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
    val pinnedVideoIds: Set<String>,
    val allSavedSongs: List<Song> = emptyList()
)

private data class SearchDialogState(
    val playlists: List<Playlist>,
    val songToAddToPlaylist: Song?,
    val showCreatePlaylistDialog: Boolean,
    val statusMessage: String?,
    val canUndo: Boolean
)

private data class SearchPlaybackState(
    val currentSong: Song?,
    val isPlaying: Boolean,
    val status: PlaybackStatus,
    val isShuffleEnabled: Boolean,
    val cacheTick: Long,
    val favoriteSongIds: Set<Long>,
    val favoriteVideoIds: Set<String>
)
