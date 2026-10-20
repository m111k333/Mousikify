package com.mousikify.feature.player

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.designsystem.palette.PaletteExtractor
import com.mousikify.core.designsystem.theme.SpotifyBlack
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.model.LyricLine
import com.mousikify.core.model.PlaybackStatus
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.RepeatMode
import com.mousikify.core.model.Song
import com.mousikify.core.scanner.lyrics.LrcParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.File
import javax.inject.Inject

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val playbackStatus: PlaybackStatus = PlaybackStatus.Idle,
    val isCurrentTrackCached: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val lyrics: List<LyricLine> = emptyList(),
    val rawLyricsText: String = "",
    val activeLyricIndex: Int = -1,
    val gradientColor: Color = SpotifyBlack,
    val playlists: List<Playlist> = emptyList(),
    val showAddToPlaylistDialog: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false,
    val statusMessage: String? = null
)

sealed interface PlayerIntent {
    data object TogglePlayPause : PlayerIntent
    data object SkipToNext : PlayerIntent
    data object SkipToPrevious : PlayerIntent
    data class SeekTo(val positionMs: Long) : PlayerIntent
    data object ToggleShuffle : PlayerIntent
    data object CycleRepeatMode : PlayerIntent
    data object ToggleFavorite : PlayerIntent
    data class SeekToLyric(val timestampMs: Long) : PlayerIntent
    data class SaveCustomLyrics(val rawLyrics: String) : PlayerIntent
    data class ShowAddToPlaylistDialog(val show: Boolean) : PlayerIntent
    data class ShowCreatePlaylistDialog(val show: Boolean) : PlayerIntent
    data class AddCurrentSongToPlaylist(val playlistId: Long) : PlayerIntent
    data class CreatePlaylistAndAddCurrentSong(val name: String) : PlayerIntent
    data object DismissStatusMessage : PlayerIntent
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicController: MusicController,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    private val _rawLyricsText = MutableStateFlow("")
    private val _gradientColor = MutableStateFlow(SpotifyBlack)
    private val _showAddToPlaylistDialog = MutableStateFlow(false)
    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private var lastLoadedSongId: Long? = null
    private var lyricsAndPaletteJob: kotlinx.coroutines.Job? = null

    companion object {
        private val paletteColorCache = androidx.collection.LruCache<Long, Color>(64)
        private val lyricsCache = androidx.collection.LruCache<Long, List<LyricLine>>(32)
        private val rawLyricsCache = androidx.collection.LruCache<Long, String>(32)
    }

    init {
        viewModelScope.launch {
            musicController.currentSong.collect { song ->
                if (song != null && song.id != lastLoadedSongId) {
                    lastLoadedSongId = song.id
                    loadLyricsAndPalette(song)
                }
            }
        }
    }

    private val playbackTiming = combine(
        musicController.currentPositionMs,
        musicController.durationMs,
        musicController.playbackStatus,
        musicController.cacheUpdateTick
    ) { pos, dur, status, tick -> PlayerTimingData(pos, dur, status, tick) }

    private val playbackControls = combine(
        musicController.isShuffleEnabled,
        musicController.repeatMode
    ) { shuf, rep -> Pair(shuf, rep) }

    private val playlistAndDialogState = combine(
        playlistRepository.playlists,
        songRepository.allSongs,
        _showAddToPlaylistDialog,
        _showCreatePlaylistDialog,
        _statusMessage
    ) { playlists, allSavedSongs, showAdd, showCreate, msg ->
        val pinnedIds = allSavedSongs.mapNotNull { it.remoteVideoId }.toSet()
        PlayerPlaylistData(playlists, pinnedIds, showAdd, showCreate, msg)
    }

    private val metadataState = combine(
        _lyrics,
        _rawLyricsText,
        _gradientColor,
        playlistAndDialogState
    ) { lyr, raw, grad, plData -> PlayerMetadataData(lyr, raw, grad, plData) }

    val uiState: StateFlow<PlayerUiState> = combine(
        musicController.currentSong,
        musicController.isPlaying,
        playbackTiming,
        playbackControls,
        metadataState
    ) { song, isPlaying, timing, (shuffle, repeat), meta ->
        val activeIndex = if (meta.lyrics.isNotEmpty()) {
            val idx = meta.lyrics.indexOfLast { it.timestampMs <= timing.position }
            idx.coerceAtLeast(0)
        } else -1

        val isCachedOrPinned = song?.let {
            musicController.isSongCached(it) || (it.remoteVideoId != null && it.remoteVideoId in meta.playlistData.pinnedIds)
        } ?: false

        PlayerUiState(
            currentSong = song,
            isPlaying = isPlaying,
            playbackStatus = timing.status,
            isCurrentTrackCached = isCachedOrPinned,
            currentPositionMs = timing.position,
            durationMs = if (timing.duration > 0L) timing.duration else (song?.durationMs ?: 0L),
            isShuffleEnabled = shuffle,
            repeatMode = repeat,
            lyrics = meta.lyrics,
            rawLyricsText = meta.rawLyrics,
            activeLyricIndex = activeIndex,
            gradientColor = meta.gradient,
            playlists = meta.playlistData.playlists,
            showAddToPlaylistDialog = meta.playlistData.showAddDialog,
            showCreatePlaylistDialog = meta.playlistData.showCreateDialog,
            statusMessage = meta.playlistData.statusMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState()
    )

    private fun loadLyricsAndPalette(song: Song) {
        // Check palette & lyrics caches immediately on main thread for 0ms instant transition
        val cachedColor = paletteColorCache.get(song.id)
        if (cachedColor != null) {
            _gradientColor.value = cachedColor
        }
        val cachedLyrics = lyricsCache.get(song.id)
        val cachedRaw = rawLyricsCache.get(song.id)
        if (cachedLyrics != null && cachedRaw != null) {
            _lyrics.value = cachedLyrics
            _rawLyricsText.value = cachedRaw
        }

        lyricsAndPaletteJob?.cancel()
        lyricsAndPaletteJob = viewModelScope.launch(Dispatchers.IO) {
            // Load embedded, sidecar, or cached lyrics 100% offline
            if (cachedLyrics == null || cachedRaw == null) {
                val raw = com.mousikify.core.scanner.lyrics.OfflineLyricsExtractor.readRawLyrics(context, song).orEmpty()
                val parsed = if (raw.isNotBlank()) {
                    LrcParser.parse(raw, song.durationMs)
                } else {
                    emptyList()
                }
                lyricsCache.put(song.id, parsed)
                rawLyricsCache.put(song.id, raw)
                _lyrics.value = parsed
                _rawLyricsText.value = raw

                if (raw.isNotBlank() && song.lrcPath.isNullOrBlank()) {
                    val cachedFile = com.mousikify.core.scanner.lyrics.OfflineLyricsExtractor.getCachedLyricsFile(context, song.id)
                    if (cachedFile.exists()) {
                        try {
                            songRepository.updateLrcPath(song.id, cachedFile.absolutePath)
                        } catch (_: Exception) {}
                    }
                }
            }

            if (cachedColor != null) return@launch

            var bitmap: android.graphics.Bitmap? = null
            try {
                val cleanVideoId = song.remoteVideoId?.removePrefix("ytm://")?.substringBefore("?")?.trim()
                val offlineArtFile = if (!cleanVideoId.isNullOrEmpty()) {
                    File(File(context.cacheDir, "offline_artwork_cache"), "$cleanVideoId.jpg").takeIf { it.exists() && it.length() > 0L }
                } else null

                val artModel: Any? = offlineArtFile ?: song.artworkUri ?: if (song.albumId > 0L) {
                    "content://media/external/audio/albumart/${song.albumId}"
                } else if (song.isLocal) {
                    song.contentUri
                } else null

                if (artModel != null) {
                    val request = ImageRequest.Builder(context)
                        .data(artModel)
                        .size(128, 128)
                        .allowHardware(false)
                        .build()
                    bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
                }

                // Fallback for local audio files: extract embedded artwork via MediaMetadataRetriever if Coil could not resolve
                if (bitmap == null && song.isLocal) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, Uri.parse(song.contentUri))
                        retriever.embeddedPicture?.let { pictureBytes ->
                            val options = BitmapFactory.Options().apply { inSampleSize = 8 }
                            bitmap = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, options)
                        }
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }

                val color = bitmap?.let { PaletteExtractor.extractGradientSeedColor(it) } ?: SpotifyBlack
                paletteColorCache.put(song.id, color)
                _gradientColor.value = color
            } catch (_: Exception) {
                _gradientColor.value = SpotifyBlack
            }
            // Never recycle here: the bitmap may be Coil's shared memory-cache instance still drawn by the UI.
        }
    }

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.TogglePlayPause -> musicController.togglePlayPause()
            is PlayerIntent.SkipToNext -> musicController.skipToNext()
            is PlayerIntent.SkipToPrevious -> musicController.skipToPrevious()
            is PlayerIntent.SeekTo -> musicController.seekTo(intent.positionMs)
            is PlayerIntent.ToggleShuffle -> musicController.toggleShuffle()
            is PlayerIntent.CycleRepeatMode -> musicController.cycleRepeatMode()
            is PlayerIntent.ToggleFavorite -> {
                val song = uiState.value.currentSong
                if (song != null) {
                    musicController.toggleFavorite(song)
                }
            }
            is PlayerIntent.SeekToLyric -> musicController.seekTo(intent.timestampMs)
            is PlayerIntent.SaveCustomLyrics -> {
                val song = uiState.value.currentSong ?: return
                viewModelScope.launch(Dispatchers.IO) {
                    val trimmed = intent.rawLyrics.trim()
                    val file = com.mousikify.core.scanner.lyrics.OfflineLyricsExtractor.saveCustomLyrics(
                        context,
                        song.id,
                        trimmed
                    )
                    val parsed = if (trimmed.isNotEmpty()) {
                        LrcParser.parse(trimmed, song.durationMs)
                    } else {
                        emptyList()
                    }
                    lyricsCache.put(song.id, parsed)
                    rawLyricsCache.put(song.id, trimmed)
                    _lyrics.value = parsed
                    _rawLyricsText.value = trimmed
                    try {
                        songRepository.updateLrcPath(song.id, file?.absolutePath.orEmpty())
                    } catch (_: Exception) {}
                }
            }
            is PlayerIntent.ShowAddToPlaylistDialog -> {
                _showAddToPlaylistDialog.value = intent.show
            }
            is PlayerIntent.ShowCreatePlaylistDialog -> {
                _showCreatePlaylistDialog.value = intent.show
            }
            is PlayerIntent.AddCurrentSongToPlaylist -> {
                val song = uiState.value.currentSong ?: return
                viewModelScope.launch {
                    playlistRepository.addSongObjectToPlaylist(intent.playlistId, song)
                    if (song.isRemoteStream) {
                        musicController.downloadSongForOffline(song)
                    }
                    _showAddToPlaylistDialog.value = false
                    _statusMessage.value = "Added '${song.title}' to playlist"
                }
            }
            is PlayerIntent.CreatePlaylistAndAddCurrentSong -> {
                val trimmed = intent.name.trim()
                if (trimmed.isEmpty()) return
                val song = uiState.value.currentSong
                viewModelScope.launch {
                    val playlistId = playlistRepository.createPlaylist(trimmed)
                    if (song != null) {
                        playlistRepository.addSongObjectToPlaylist(playlistId, song)
                        if (song.isRemoteStream) {
                            musicController.downloadSongForOffline(song)
                        }
                        _statusMessage.value = "Created '$trimmed' & added '${song.title}'"
                    } else {
                        _statusMessage.value = "Created playlist '$trimmed'"
                    }
                    _showCreatePlaylistDialog.value = false
                    _showAddToPlaylistDialog.value = false
                }
            }
            is PlayerIntent.DismissStatusMessage -> {
                _statusMessage.value = null
            }
        }
    }
}

private data class PlayerTimingData(
    val position: Long,
    val duration: Long,
    val status: PlaybackStatus,
    val cacheTick: Long
)

private data class PlayerPlaylistData(
    val playlists: List<Playlist>,
    val pinnedIds: Set<String>,
    val showAddDialog: Boolean,
    val showCreateDialog: Boolean,
    val statusMessage: String?
)

private data class PlayerMetadataData(
    val lyrics: List<LyricLine>,
    val rawLyrics: String,
    val gradient: Color,
    val playlistData: PlayerPlaylistData
)
