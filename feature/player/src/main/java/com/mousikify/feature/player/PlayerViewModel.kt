package com.mousikify.feature.player

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.designsystem.palette.PaletteExtractor
import com.mousikify.core.designsystem.theme.SpotifyBlack
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.model.LyricLine
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
import java.io.File
import javax.inject.Inject

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val lyrics: List<LyricLine> = emptyList(),
    val activeLyricIndex: Int = -1,
    val gradientColor: Color = SpotifyBlack
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
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicController: MusicController,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    private val _gradientColor = MutableStateFlow(SpotifyBlack)
    private var lastLoadedSongId: Long? = null

    companion object {
        private val paletteColorCache = androidx.collection.LruCache<Long, Color>(64)
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
        musicController.durationMs
    ) { pos, dur -> Pair(pos, dur) }

    private val playbackControls = combine(
        musicController.isShuffleEnabled,
        musicController.repeatMode
    ) { shuf, rep -> Pair(shuf, rep) }

    private val metadataState = combine(
        _lyrics,
        _gradientColor
    ) { lyr, grad -> Pair(lyr, grad) }

    val uiState: StateFlow<PlayerUiState> = combine(
        musicController.currentSong,
        musicController.isPlaying,
        playbackTiming,
        playbackControls,
        metadataState
    ) { song, isPlaying, (position, duration), (shuffle, repeat), (lyrics, gradient) ->
        val activeIndex = if (lyrics.isNotEmpty()) {
            val idx = lyrics.indexOfLast { it.timestampMs <= position }
            idx.coerceAtLeast(0)
        } else -1

        PlayerUiState(
            currentSong = song,
            isPlaying = isPlaying,
            currentPositionMs = position,
            durationMs = if (duration > 0L) duration else (song?.durationMs ?: 0L),
            isShuffleEnabled = shuffle,
            repeatMode = repeat,
            lyrics = lyrics,
            activeLyricIndex = activeIndex,
            gradientColor = gradient
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState()
    )

    private fun loadLyricsAndPalette(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            // Load lyrics
            var loadedLyrics = emptyList<LyricLine>()
            if (!song.lrcPath.isNullOrEmpty()) {
                val file = File(song.lrcPath!!)
                if (file.exists()) {
                    loadedLyrics = LrcParser.parseFile(file)
                }
            }
            _lyrics.value = loadedLyrics

            // Check palette color cache first
            val cachedColor = paletteColorCache.get(song.id)
            if (cachedColor != null) {
                _gradientColor.value = cachedColor
                return@launch
            }

            // Extract dynamic palette color from embedded artwork
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(song.contentUri))
                val pictureBytes = retriever.embeddedPicture
                if (pictureBytes != null) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bitmap = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, options)
                    val color = bitmap?.let { PaletteExtractor.extractGradientSeedColor(it) } ?: SpotifyBlack
                    paletteColorCache.put(song.id, color)
                    _gradientColor.value = color
                    bitmap?.recycle()
                } else {
                    _gradientColor.value = SpotifyBlack
                }
            } catch (e: Exception) {
                _gradientColor.value = SpotifyBlack
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
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
        }
    }
}
