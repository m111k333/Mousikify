package com.mousikify.feature.player.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.media.recommendation.RadioRefreshConfig
import com.mousikify.core.media.recommendation.SongRadioDiscoveryEngine
import com.mousikify.core.model.PlaybackContext
import com.mousikify.core.model.PlaybackContextType
import com.mousikify.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class RadioUiState(
    val seedSong: Song? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val isSavedToLibrary: Boolean = false,
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val statusMessage: String? = null,
    val favoriteSongIds: Set<Long> = emptySet(),
    val favoriteVideoIds: Set<String> = emptySet(),
    val cacheUpdateTick: Long = 0L,
    val refreshConfig: RadioRefreshConfig = RadioRefreshConfig(),
    val showRefreshDialog: Boolean = false
) {
    fun isFavorite(song: Song): Boolean {
        if (song.id in favoriteSongIds) return true
        val vid = song.remoteVideoId
        if (vid != null && vid in favoriteVideoIds) return true
        return song.isFavorite
    }
}

sealed interface RadioIntent {
    data class Initialize(val song: Song) : RadioIntent
    data class PlaySong(val song: Song, val index: Int) : RadioIntent
    data object PlayAll : RadioIntent
    data object ToggleShuffle : RadioIntent
    data class ToggleFavorite(val song: Song) : RadioIntent
    data object SaveToLibrary : RadioIntent
    data object Retry : RadioIntent
    data object DismissStatusMessage : RadioIntent
    data object OpenRefreshDialog : RadioIntent
    data object DismissRefreshDialog : RadioIntent
    data class ConfirmRefresh(val config: RadioRefreshConfig) : RadioIntent
}

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val radioDiscoveryEngine: SongRadioDiscoveryEngine,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val musicController: MusicController
) : ViewModel() {

    private val _seedSong = MutableStateFlow<Song?>(null)
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _isSavedToLibrary = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _refreshConfig = MutableStateFlow(RadioRefreshConfig())
    private val _showRefreshDialog = MutableStateFlow(false)

    val uiState: StateFlow<RadioUiState> = combine(
        combine(_seedSong, _songs, _isLoading, _isSavedToLibrary) { seed, songs, loading, saved ->
            Triple(seed, songs, Pair(loading, saved))
        },
        combine(_refreshConfig, _showRefreshDialog, _statusMessage) { cfg, show, msg ->
            Triple(cfg, show, msg)
        },
        musicController.playbackSessionState
    ) { (seed, songs, statePair), (cfg, showDialog, msg), session ->
        val (loading, saved) = statePair
        val resolvedSongs = songs.map { s ->
            val isFav = session.isFavorite(s)
            if (s.isFavorite != isFav) s.copy(isFavorite = isFav) else s
        }
        RadioUiState(
            seedSong = seed,
            songs = resolvedSongs,
            isLoading = loading,
            isSavedToLibrary = saved,
            currentSongId = session.currentSong?.id,
            isPlaying = session.isPlaying,
            isShuffleEnabled = session.isShuffleEnabled,
            statusMessage = msg,
            favoriteSongIds = session.favoriteSongIds,
            favoriteVideoIds = session.favoriteVideoIds,
            cacheUpdateTick = session.cacheUpdateTick,
            refreshConfig = cfg,
            showRefreshDialog = showDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RadioUiState()
    )

    fun isSongCached(song: Song): Boolean = musicController.isSongCached(song)

    fun onIntent(intent: RadioIntent) {
        when (intent) {
            is RadioIntent.Initialize -> initializeRadio(intent.song)
            is RadioIntent.PlaySong -> playSong(intent.song, intent.index)
            is RadioIntent.PlayAll -> playAll()
            is RadioIntent.ToggleShuffle -> musicController.toggleShuffle()
            is RadioIntent.ToggleFavorite -> toggleFavorite(intent.song)
            is RadioIntent.SaveToLibrary -> saveToLibrary()
            is RadioIntent.Retry -> retry()
            is RadioIntent.DismissStatusMessage -> _statusMessage.value = null
            is RadioIntent.OpenRefreshDialog -> _showRefreshDialog.value = true
            is RadioIntent.DismissRefreshDialog -> _showRefreshDialog.value = false
            is RadioIntent.ConfirmRefresh -> confirmRefresh(intent.config)
        }
    }

    private fun initializeRadio(song: Song) {
        val currentSeed = _seedSong.value
        if (currentSeed != null &&
            (currentSeed.id == song.id || SongRadioDiscoveryEngine.getSeedRadioKey(currentSeed) == SongRadioDiscoveryEngine.getSeedRadioKey(song)) &&
            _songs.value.isNotEmpty()
        ) return

        _seedSong.value = song
        _isSavedToLibrary.value = false

        // 1. Instant render from cache if available
        val cached = radioDiscoveryEngine.getCachedSongRadio(song)
        if (!cached.isNullOrEmpty()) {
            val firstCached = cached.first()
            if (song.id < 0 && firstCached.title.equals(song.title, ignoreCase = true)) {
                _seedSong.value = firstCached.copy(artworkUri = firstCached.artworkUri ?: song.artworkUri)
            }
            _songs.value = cached
            _isLoading.value = false
        } else {
            _songs.value = emptyList()
            _isLoading.value = true
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Check if user already has this exact Radio playlist saved in their Library
            var savedSongs: List<Song> = emptyList()
            runCatching {
                val playlists = playlistRepository.playlists.firstOrNull().orEmpty()
                val targetName = "${song.title} Radio"
                val matchingPlaylist = playlists.firstOrNull { it.name.equals(targetName, ignoreCase = true) }
                if (matchingPlaylist != null) {
                    _isSavedToLibrary.value = true
                    savedSongs = playlistRepository.getPlaylistSongs(matchingPlaylist.id).firstOrNull().orEmpty()
                }
            }

            if (savedSongs.isNotEmpty()) {
                _songs.value = savedSongs
                _isLoading.value = false
                if (cached.isNullOrEmpty()) {
                    radioDiscoveryEngine.saveCachedSongRadio(_seedSong.value ?: song, savedSongs)
                }
            } else if (cached.isNullOrEmpty()) {
                // If neither saved in Library nor cached, generate fresh from network
                val generated = radioDiscoveryEngine.generateSongRadio(song)
                _songs.value = if (generated.size > 1) generated else emptyList()
                _isLoading.value = false
            }
        }
    }

    private fun confirmRefresh(config: RadioRefreshConfig) {
        val song = _seedSong.value ?: return
        _refreshConfig.value = config
        _showRefreshDialog.value = false
        _isSavedToLibrary.value = false
        _isLoading.value = true
        _songs.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val generated = radioDiscoveryEngine.generateSongRadio(
                seedSong = song,
                forceRefresh = true,
                config = config
            )
            _songs.value = if (generated.size > 1) generated else emptyList()
            _isLoading.value = false
        }
    }

    private fun retry() {
        val song = _seedSong.value ?: return
        _isLoading.value = true
        _songs.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val generated = radioDiscoveryEngine.generateSongRadio(
                seedSong = song,
                forceRefresh = true,
                config = _refreshConfig.value
            )
            _songs.value = if (generated.size > 1) generated else emptyList()
            _isLoading.value = false
        }
    }

    private fun playSong(song: Song, index: Int) {
        val seed = _seedSong.value ?: song
        val list = _songs.value.ifEmpty { listOf(song) }
        val playIndex = if (index in list.indices) index else 0
        val targetSong = list[playIndex]
        val context = PlaybackContext(
            title = "${seed.title} Radio",
            type = PlaybackContextType.PLAYLIST,
            id = "radio_${SongRadioDiscoveryEngine.getSeedRadioKey(seed)}",
            artworkUri = seed.artworkUri ?: targetSong.artworkUri,
            artistName = seed.artistName
        )
        if (list.size > 1) {
            radioDiscoveryEngine.saveCachedSongRadio(seed, list)
        }
        musicController.playQueue(list, playIndex, context = context)
        musicController.recordRadioPlaylistInRecentlyPlayed(seed, targetSong, context)
    }

    private fun playAll() {
        val list = _songs.value
        if (list.isNotEmpty()) {
            playSong(list.first(), 0)
        }
    }

    private fun toggleFavorite(song: Song) {
        val willBeFav = !uiState.value.isFavorite(song)
        musicController.toggleFavorite(song)
        val updated = _songs.value.map {
            if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                it.copy(isFavorite = willBeFav)
            } else it
        }
        _songs.value = updated
        _seedSong.value?.let { seed ->
            if (updated.size > 1) {
                radioDiscoveryEngine.saveCachedSongRadio(seed, updated)
            }
        }
    }

    private fun saveToLibrary() {
        val seed = _seedSong.value ?: return
        val list = _songs.value
        if (list.isEmpty() || _isSavedToLibrary.value) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingPlaylists = playlistRepository.playlists.firstOrNull().orEmpty()
                val baseName = "${seed.title} Radio"
                val existingNames = existingPlaylists.map { it.name.trim().lowercase(Locale.ROOT) }.toSet()

                var playlistName = baseName
                if (existingNames.contains(baseName.lowercase(Locale.ROOT))) {
                    var counter = 2
                    while (existingNames.contains("${baseName} #$counter".lowercase(Locale.ROOT))) {
                        counter++
                    }
                    playlistName = "${baseName} #$counter"
                }

                val playlistId = playlistRepository.createPlaylist(playlistName, seed.artworkUri)
                for (track in list) {
                    val resolvedSong = playlistRepository.addSongObjectToPlaylist(playlistId, track)
                    if (resolvedSong.isRemoteStream && !musicController.isSongCached(resolvedSong)) {
                        musicController.downloadSongForOffline(resolvedSong)
                    }
                }
                _isSavedToLibrary.value = true
                _statusMessage.value = "Saved \"$playlistName\" to your Library"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to save playlist: ${e.message}"
            }
        }
    }
}
