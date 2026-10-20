package com.mousikify.core.media.controller

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.cache.MediaCacheManager
import com.mousikify.core.media.queue.QueueManager
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.media.service.MusicPlaybackService
import com.mousikify.core.model.PlaybackStatus
import com.mousikify.core.model.RepeatMode
import com.mousikify.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    private val playbackPreferences: PlaybackPreferences,
    private val cacheManager: MediaCacheManager,
    private val remoteMusicRepository: RemoteMusicRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var mediaController: MediaController? = null
    private var controllerListener: Player.Listener? = null
    private var activePlayJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackStatus = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
    val playbackStatus: StateFlow<PlaybackStatus> = _playbackStatus.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var progressTrackingJob: Job? = null

    val currentSong: StateFlow<Song?> = queueManager.currentSong
    val currentQueue: StateFlow<List<Song>> = queueManager.currentQueue
    val isShuffleEnabled: StateFlow<Boolean> = queueManager.isShuffleEnabled
    val repeatMode: StateFlow<RepeatMode> = queueManager.repeatMode
    val cacheUpdateTick: StateFlow<Long> = cacheManager.cacheUpdateTick

    init {
        connectToService()
        startProgressTracker()
    }

    fun isSongCached(song: Song): Boolean {
        val videoId = song.remoteVideoId ?: return true
        return cacheManager.isDownloadedLocally(videoId)
    }

    fun downloadSongForOffline(song: Song) {
        val videoId = song.remoteVideoId ?: return
        cacheManager.downloadFullTrackForOffline(song)
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
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    private var pendingPlaySong: Song? = null

    private fun setupControllerListener() {
        val controller = mediaController ?: return
        _isPlaying.value = controller.isPlaying
        _durationMs.value = controller.duration.coerceAtLeast(0L)
        updatePlaybackStatus(controller)

        controllerListener?.let {
            try { controller.removeListener(it) } catch (_: Exception) {}
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                updatePlaybackStatus(controller)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                val dur = controller.duration.coerceAtLeast(0L)
                if (dur > 0L) {
                    _durationMs.value = dur
                }
                updatePlaybackStatus(controller)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = controller.duration.coerceAtLeast(0L)
                if (dur > 0L) {
                    _durationMs.value = dur
                }
                updatePlaybackStatus(controller)
                if (playbackState == Player.STATE_ENDED) {
                    if (!queueManager.isCrossfading.value) {
                        val current = queueManager.currentSong.value
                        val mediaId = controller.currentMediaItem?.mediaId?.toLongOrNull()
                        if (current != null && mediaId != null && current.id == mediaId) {
                            skipToNext()
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackStatus.value = PlaybackStatus.Error(
                    errorCode = error.errorCode,
                    message = error.localizedMessage ?: "Stream playback error",
                    isRecoverable = true
                )
            }
        }
        controllerListener = listener
        controller.addListener(listener)

        pendingPlaySong?.let { song ->
            pendingPlaySong = null
            playSongInternal(song)
        }
    }

    private fun updatePlaybackStatus(controller: MediaController) {
        val current = currentSong.value
        _playbackStatus.value = when (controller.playbackState) {
            Player.STATE_IDLE -> PlaybackStatus.Idle
            Player.STATE_BUFFERING -> {
                if (current?.isRemoteStream == true && controller.currentPosition <= 100L) {
                    PlaybackStatus.Preparing(isResolvingStream = true)
                } else {
                    PlaybackStatus.Buffering(controller.bufferedPercentage)
                }
            }
            Player.STATE_READY -> {
                if (controller.isPlaying) PlaybackStatus.Playing else PlaybackStatus.Paused
            }
            Player.STATE_ENDED -> PlaybackStatus.Idle
            else -> PlaybackStatus.Idle
        }
    }

    private fun startProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    _bufferedPositionMs.value = controller.bufferedPosition.coerceAtLeast(pos)
                    val dur = controller.duration.coerceAtLeast(0L)
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }
                    delay(125)
                } else {
                    delay(500)
                }
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

    @OptIn(UnstableApi::class)
    private fun playSongInternal(song: Song) {
        activePlayJob?.cancel()

        _currentPositionMs.value = 0L
        _bufferedPositionMs.value = 0L
        _durationMs.value = song.durationMs
        if (song.isRemoteStream) {
            _playbackStatus.value = PlaybackStatus.Preparing(isResolvingStream = true)
        }
        val controller = mediaController
        if (controller == null) {
            pendingPlaySong = song
            connectToService()
            return
        }

        activePlayJob = scope.launch {
            try {
                val resolvedUri: android.net.Uri
                val resolvedMimeType: String?
                if (song.isRemoteStream) {
                    val videoId = song.remoteVideoId.orEmpty()
                    cacheManager.onRemoteTrackPlayed(videoId, song.artworkUri)
                    val localFile = cacheManager.getDownloadedMusicFile(videoId)
                    if (localFile != null && localFile.exists() && localFile.length() > 0L) {
                        resolvedUri = android.net.Uri.fromFile(localFile)
                        resolvedMimeType = null
                    } else {
                        val manifestResult = remoteMusicRepository.resolveStreamManifest(videoId)
                        val manifest = manifestResult.getOrNull()
                        if (manifest != null) {
                            if (manifest.durationMs > 0L) {
                                _durationMs.value = manifest.durationMs
                            }
                            // Pass ytm:// so ResolvingDataSource adds the client-matching User-Agent (raw URL -> 403)
                            resolvedUri = android.net.Uri.parse(song.contentUri)
                            resolvedMimeType = manifest.mimeType
                        } else if (cacheManager.isTrackCached(videoId)) {
                            // Device is offline, but track is cached in SimpleCache!
                            resolvedUri = android.net.Uri.parse("https://offline.googlevideo.com/videoplayback?id=$videoId")
                            resolvedMimeType = null
                        } else {
                            val err = manifestResult.exceptionOrNull()
                            android.util.Log.e("MusicController", "Failed to resolve stream for $videoId", err)
                            _playbackStatus.value = PlaybackStatus.Error(
                                errorCode = PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                                message = err?.localizedMessage ?: "Unable to resolve YouTube Music stream",
                                isRecoverable = true
                            )
                            return@launch
                        }
                    }
                } else {
                    resolvedUri = android.net.Uri.parse(song.contentUri)
                    resolvedMimeType = null
                }

                val artBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    cacheManager.getArtworkBytes(song)
                }
                val artUri = cacheManager.resolveArtworkUri(song)

                val metadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumName)
                    .apply {
                        if (artBytes != null) {
                            setArtworkData(artBytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                        if (artUri != android.net.Uri.EMPTY) {
                            setArtworkUri(artUri)
                        }
                    }
                    .build()

                val requestMetadata = androidx.media3.common.MediaItem.RequestMetadata.Builder()
                    .setMediaUri(resolvedUri)
                    .build()

                val mediaItem = androidx.media3.common.MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(resolvedUri)
                    .setCustomCacheKey(song.cacheKey)
                    .apply {
                        if (resolvedMimeType != null) {
                            setMimeType(resolvedMimeType)
                        }
                    }
                    .setRequestMetadata(requestMetadata)
                    .setMediaMetadata(metadata)
                    .build()

                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()

                if (!song.isRemoteStream) {
                    songRepository.recordPlay(song.id)
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("MusicController", "Failed to play song ${song.id}", e)
                _playbackStatus.value = PlaybackStatus.Error(
                    errorCode = PlaybackException.ERROR_CODE_UNSPECIFIED,
                    message = e.localizedMessage ?: "Failed to play song",
                    isRecoverable = true
                )
            }
        }
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller == null) {
            val current = currentSong.value
            if (current != null) {
                pendingPlaySong = current
                connectToService()
            }
            return
        }
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
        } else {
            _isPlaying.value = false
            _currentPositionMs.value = 0L
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

    fun setShuffleEnabled(enabled: Boolean) {
        queueManager.setShuffleEnabled(enabled)
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        queueManager.cycleRepeatMode()
    }

    fun pruneOrphanedCache() {
        cacheManager.pruneOrphanedCacheEntries(currentSong.value?.remoteVideoId)
    }

    fun toggleFavorite(song: Song) {
        val newFav = !song.isFavorite
        queueManager.updateFavorite(song.id, newFav)
        scope.launch {
            if (song.isRemoteStream) {
                if (newFav) {
                    songRepository.upsertSongs(listOf(song.copy(isFavorite = true)))
                    downloadSongForOffline(song)
                } else {
                    songRepository.toggleFavorite(song.id, false)
                    songRepository.deleteNonFavoriteRemoteSongs()
                    pruneOrphanedCache()
                }
            } else {
                songRepository.toggleFavorite(song.id, newFav)
            }
        }
    }

    fun release() {
        progressTrackingJob?.cancel()
        scope.cancel()
        controllerListener?.let {
            try { mediaController?.removeListener(it) } catch (_: Exception) {}
        }
        controllerListener = null
        try { mediaController?.release() } catch (_: Exception) {}
        mediaController = null
    }
}
