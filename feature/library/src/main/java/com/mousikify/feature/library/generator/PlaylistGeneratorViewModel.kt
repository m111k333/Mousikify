package com.mousikify.feature.library.generator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.media.recommendation.PlaylistGenerationEngine
import com.mousikify.core.media.recommendation.RadioRefreshConfig
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.model.Artist
import com.mousikify.core.model.PlaybackContext
import com.mousikify.core.model.PlaybackContextType
import com.mousikify.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistGeneratorUiState(
    val seeds: List<Song> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Song> = emptyList(),
    val searchArtists: List<Artist> = emptyList(),
    val isSearching: Boolean = false,
    val recentlyLikedSongs: List<Song> = emptyList(),
    val recentlyPlayedSongs: List<Song> = emptyList(),
    val trackCount: Int = PlaylistGenerationEngine.DEFAULT_TARGET_COUNT,
    val isGenerating: Boolean = false,
    val generatedTracks: List<Song> = emptyList(),
    val isShowingGeneratedView: Boolean = false,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val savedPlaylistName: String? = null,
    val refreshConfig: RadioRefreshConfig = RadioRefreshConfig(),
    val isShowRefreshDialog: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class PlaylistGeneratorViewModel internal constructor(
    private val playlistGenerationEngine: PlaylistGenerationEngine?,
    private val remoteMusicRepository: RemoteMusicRepository?,
    private val songRepository: SongRepository?,
    private val playlistRepository: PlaylistRepository?,
    private val musicController: MusicController?,
    private val testFavorites: List<Song>? = null,
    private val testRecentSongs: List<Song>? = null,
    private val testSearcher: (suspend (String) -> List<Song>)? = null,
    private val testArtistSearcher: (suspend (String) -> List<Artist>)? = null,
    private val testGenerator: (suspend (List<Song>, Int, Boolean) -> List<Song>)? = null,
    private val testPlaylistSaver: (suspend (String, List<Song>) -> Long)? = null
) : ViewModel() {

    @Inject
    constructor(
        playlistGenerationEngine: PlaylistGenerationEngine,
        remoteMusicRepository: RemoteMusicRepository,
        songRepository: SongRepository,
        playlistRepository: PlaylistRepository,
        musicController: MusicController
    ) : this(
        playlistGenerationEngine = playlistGenerationEngine,
        remoteMusicRepository = remoteMusicRepository,
        songRepository = songRepository,
        playlistRepository = playlistRepository,
        musicController = musicController,
        testFavorites = null,
        testRecentSongs = null,
        testSearcher = null,
        testArtistSearcher = null,
        testGenerator = null,
        testPlaylistSaver = null
    )

    internal constructor(
        favorites: List<Song> = emptyList(),
        recentSongs: List<Song> = emptyList(),
        searcher: suspend (String) -> List<Song> = { emptyList() },
        artistSearcher: suspend (String) -> List<Artist> = { emptyList() },
        generator: suspend (List<Song>, Int, Boolean) -> List<Song> = { _, _, _ -> emptyList() },
        saver: suspend (String, List<Song>) -> Long = { _, _ -> 1L }
    ) : this(
        playlistGenerationEngine = null,
        remoteMusicRepository = null,
        songRepository = null,
        playlistRepository = null,
        musicController = null,
        testFavorites = favorites,
        testRecentSongs = recentSongs,
        testSearcher = searcher,
        testArtistSearcher = artistSearcher,
        testGenerator = generator,
        testPlaylistSaver = saver
    )

    private val _uiState = MutableStateFlow(PlaylistGeneratorUiState())
    val uiState: StateFlow<PlaylistGeneratorUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private var generateJob: Job? = null
    private var currentMixId: String? = null

    init {
        // Observe user's favorite tracks for quick selection
        if (testFavorites != null) {
            _uiState.update { it.copy(recentlyLikedSongs = testFavorites) }
        } else if (songRepository != null) {
            viewModelScope.launch {
                songRepository.favorites.collect { favorites ->
                    val topFavorites = favorites.take(15)
                    _uiState.update { it.copy(recentlyLikedSongs = topFavorites) }
                }
            }
        }

        // Observe user's recently played tracks
        if (testRecentSongs != null) {
            _uiState.update { it.copy(recentlyPlayedSongs = testRecentSongs) }
        } else if (songRepository != null) {
            viewModelScope.launch {
                songRepository.recentlyPlayedItems.collect { items ->
                    val recentSongs = items
                        .mapNotNull { it.song }
                        .distinctBy { it.id }
                        .take(15)
                    _uiState.update { it.copy(recentlyPlayedSongs = recentSongs) }
                }
            }
        }

        // Debounced online search
        searchQueryFlow
            .debounce(350L)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.trim().length >= 2) {
                    performSearch(query.trim())
                } else {
                    _uiState.update { it.copy(searchResults = emptyList(), searchArtists = emptyList(), isSearching = false) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val songsDeferred = async {
                runCatching {
                    testSearcher?.invoke(query)
                        ?: remoteMusicRepository?.searchMusicQuick(query)?.getOrDefault(emptyList()).orEmpty()
                }.getOrDefault(emptyList())
            }
            val artistsDeferred = async {
                runCatching {
                    testArtistSearcher?.invoke(query)
                        ?: remoteMusicRepository?.searchArtists(query, limit = 6)?.getOrDefault(emptyList()).orEmpty()
                }.getOrDefault(emptyList())
            }
            val songs = songsDeferred.await()
            val artists = artistsDeferred.await()
            _uiState.update {
                it.copy(
                    searchResults = songs,
                    searchArtists = artists,
                    isSearching = false
                )
            }
        }
    }

    fun addSeed(song: Song) {
        _uiState.update { state ->
            if (state.seeds.any { it.id == song.id }) {
                state
            } else {
                state.copy(
                    seeds = state.seeds + song,
                    searchQuery = "",
                    searchResults = emptyList(),
                    searchArtists = emptyList(),
                    isSearching = false
                )
            }
        }
        searchQueryFlow.value = ""
    }

    fun addArtistSeed(artist: Artist) {
        viewModelScope.launch {
            val topSong = runCatching {
                remoteMusicRepository?.searchSongsByArtist(artist.name, limit = 1)?.getOrNull()?.firstOrNull()
            }.getOrNull()

            val seedSong = topSong ?: Song(
                id = if (artist.id != 0L) artist.id else (artist.name.hashCode().toLong() and 0x7FFFFFFF),
                contentUri = "artist://${artist.name}",
                title = artist.name,
                artistId = artist.id,
                artistName = artist.name,
                albumId = 0L,
                albumName = artist.name,
                durationMs = 180000L,
                trackNumber = 1,
                year = 0,
                folderPath = "",
                dateAdded = System.currentTimeMillis(),
                dateModified = System.currentTimeMillis(),
                artworkUri = artist.avatarUrl
            )
            addSeed(seedSong)
        }
    }

    fun removeSeed(songId: Long) {
        _uiState.update { state ->
            state.copy(seeds = state.seeds.filterNot { it.id == songId })
        }
    }

    fun onTrackCountChanged(count: Int) {
        val clamped = count.coerceIn(
            PlaylistGenerationEngine.MIN_TARGET_COUNT,
            PlaylistGenerationEngine.MAX_TARGET_COUNT
        )
        _uiState.update { it.copy(trackCount = clamped) }
    }

    fun dismissGeneratedView() {
        _uiState.update { it.copy(isShowingGeneratedView = false) }
    }

    fun openRefreshDialog() {
        _uiState.update { it.copy(isShowRefreshDialog = true) }
    }

    fun dismissRefreshDialog() {
        _uiState.update { it.copy(isShowRefreshDialog = false) }
    }

    fun refreshPlaylistWithConfig(newConfig: RadioRefreshConfig, newTrackCount: Int) {
        _uiState.update {
            it.copy(
                refreshConfig = newConfig,
                trackCount = newTrackCount.coerceIn(
                    PlaylistGenerationEngine.MIN_TARGET_COUNT,
                    PlaylistGenerationEngine.MAX_TARGET_COUNT
                ),
                isShowRefreshDialog = false
            )
        }
        generatePlaylist(forceRefresh = true)
    }

    fun generatePlaylist(forceRefresh: Boolean = false) {
        val currentSeeds = _uiState.value.seeds
        if (currentSeeds.isEmpty()) return

        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }
            val count = _uiState.value.trackCount
            val config = _uiState.value.refreshConfig
            try {
                val tracks = testGenerator?.invoke(currentSeeds, count, forceRefresh)
                    ?: playlistGenerationEngine?.generatePlaylist(
                        seeds = currentSeeds,
                        targetCount = count,
                        includeSeedsAtTop = true,
                        forceRefresh = forceRefresh,
                        config = config
                    ).orEmpty()
                if (tracks.isNotEmpty()) {
                    val mixId = "genmix_${System.currentTimeMillis()}"
                    currentMixId = mixId
                    songRepository?.cacheGeneratedMix(mixId, tracks)
                }
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generatedTracks = tracks,
                        isShowingGeneratedView = tracks.isNotEmpty(),
                        errorMessage = if (tracks.isEmpty()) "No similar tracks found for these seeds." else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = "Failed to generate playlist: ${e.message ?: "Network error"}"
                    )
                }
            }
        }
    }

    fun playTrack(index: Int) {
        val tracks = _uiState.value.generatedTracks
        if (tracks.isNotEmpty() && index in tracks.indices) {
            val mixId = currentMixId ?: "genmix_${System.currentTimeMillis()}".also {
                currentMixId = it
                viewModelScope.launch { songRepository?.cacheGeneratedMix(it, tracks) }
            }
            val context = PlaybackContext(
                type = PlaybackContextType.PLAYLIST,
                id = mixId,
                title = defaultPlaylistTitle(),
                artworkUri = tracks.firstOrNull()?.artworkUri
            )
            musicController?.playQueue(tracks, startIndex = index, context = context)
        }
    }

    fun playAll() {
        playTrack(0)
    }

    fun shuffleAndPlayAll() {
        val tracks = _uiState.value.generatedTracks
        if (tracks.isNotEmpty()) {
            musicController?.setShuffleEnabled(true)
            val startIndex = tracks.indices.random()
            val mixId = currentMixId ?: "genmix_${System.currentTimeMillis()}".also {
                currentMixId = it
                viewModelScope.launch { songRepository?.cacheGeneratedMix(it, tracks) }
            }
            val context = PlaybackContext(
                type = PlaybackContextType.PLAYLIST,
                id = mixId,
                title = defaultPlaylistTitle(),
                artworkUri = tracks.firstOrNull()?.artworkUri
            )
            musicController?.playQueue(tracks, startIndex = startIndex, context = context)
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val currentFav = song.isFavorite || _uiState.value.recentlyLikedSongs.any { it.id == song.id }
            val newFav = !currentFav
            songRepository?.toggleFavorite(song.id, newFav)
            _uiState.update { state ->
                val updatedGenerated = state.generatedTracks.map {
                    if (it.id == song.id) it.copy(isFavorite = newFav) else it
                }
                val updatedLiked = if (newFav) {
                    if (state.recentlyLikedSongs.none { it.id == song.id }) {
                        listOf(song.copy(isFavorite = true)) + state.recentlyLikedSongs
                    } else state.recentlyLikedSongs
                } else {
                    state.recentlyLikedSongs.filterNot { it.id == song.id }
                }
                state.copy(
                    generatedTracks = updatedGenerated,
                    recentlyLikedSongs = updatedLiked
                )
            }
        }
    }

    fun savePlaylistToLibrary(customName: String? = null, onComplete: ((Long) -> Unit)? = null) {
        val tracks = _uiState.value.generatedTracks
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val playlistName = customName?.trim()?.takeIf { it.isNotEmpty() } ?: defaultPlaylistTitle()
                val playlistId = if (testPlaylistSaver != null) {
                    testPlaylistSaver.invoke(playlistName, tracks)
                } else {
                    val coverArt = tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
                    val id = playlistRepository?.createPlaylist(name = playlistName, coverUri = coverArt) ?: 0L
                    tracks.forEach { track ->
                        playlistRepository?.addSongObjectToPlaylist(id, track)
                    }
                    id
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedPlaylistName = playlistName
                    )
                }
                onComplete?.invoke(playlistId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Error saving playlist: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearSavedNotification() {
        _uiState.update { it.copy(savedPlaylistName = null) }
    }

    fun defaultPlaylistTitle(): String {
        val seeds = _uiState.value.seeds
        return when {
            seeds.isEmpty() -> "Smart Mix"
            seeds.size == 1 -> "Mix: ${seeds[0].title}"
            seeds.size == 2 -> "Mix: ${seeds[0].title} & ${seeds[1].title}"
            else -> "Mix: ${seeds[0].title} & ${seeds.size - 1} more"
        }
    }

    companion object {
        const val MAX_SEEDS = 5
    }
}
