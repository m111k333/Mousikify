package com.mousikify.core.media.controller

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.queue.QueueManager
import com.mousikify.core.media.service.MusicPlaybackService
import com.mousikify.core.model.RepeatMode
import com.mousikify.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicController @Inject constructor(
    @ApplicationContext private val context: Context,
    val queueManager: QueueManager,
    private val songRepository: SongRepository,
    private val playbackPreferences: PlaybackPreferences
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var mediaController: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var progressTrackingJob: Job? = null

    val currentSong: StateFlow<Song?> = queueManager.currentSong
    val currentQueue: StateFlow<List<Song>> = queueManager.currentQueue
    val isShuffleEnabled: StateFlow<Boolean> = queueManager.isShuffleEnabled
    val repeatMode: StateFlow<RepeatMode> = queueManager.repeatMode

    init {
        connectToService()
        startProgressTracker()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                setupControllerListener()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener() {
        val controller = mediaController ?: return
        _isPlaying.value = controller.isPlaying
        _durationMs.value = controller.duration.coerceAtLeast(0L)

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _durationMs.value = controller.duration.coerceAtLeast(0L)
            }
        })
    }

    private fun startProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    val dur = controller.duration.coerceAtLeast(0L)
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }
                }
                delay(100) // 100ms ticker for smooth progress and lyric sync
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val target = queueManager.setQueue(songs, startIndex)
        if (target != null) {
            playSongInternal(target)
        }
    }

    fun playSong(song: Song) {
        val queue = queueManager.currentQueue.value
        val index = queue.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            queueManager.playSongAt(index)
            playSongInternal(song)
        } else {
            // New queue with this song
            playQueue(listOf(song), 0)
        }
    }

    private fun playSongInternal(song: Song) {
        val controller = mediaController
        if (controller != null) {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(song.contentUri))
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                val current = currentSong.value
                if (current != null) {
                    playSongInternal(current)
                    return
                }
            }
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun skipToNext() {
        val next = queueManager.getNextSong()
        if (next != null) {
            playSongInternal(next)
        }
    }

    fun skipToPrevious() {
        val controller = mediaController
        if (controller != null && controller.currentPosition > 3000) {
            controller.seekTo(0)
            return
        }
        val prev = queueManager.getPreviousSong()
        if (prev != null) {
            playSongInternal(prev)
        }
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        queueManager.cycleRepeatMode()
    }

    fun toggleFavorite(song: Song) {
        scope.launch {
            songRepository.toggleFavorite(song.id, !song.isFavorite)
        }
    }
}
