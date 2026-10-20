package com.mousikify.core.media.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.mousikify.core.media.cache.MediaCacheManager
import com.mousikify.core.media.dsp.AudioEffectController
import com.mousikify.core.media.queue.QueueManager
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.mousikify.core.media.diagnostics.DiagnosticsLog
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(UnstableApi::class)
class CrossfadePlayerWrapper(
    private val context: Context,
    private val queueManager: QueueManager,
    private val cacheManager: MediaCacheManager,
    private val remoteMusicRepository: RemoteMusicRepository,
    val audioEffectController: AudioEffectController,
    private val diagnosticsLog: DiagnosticsLog? = null
) {

    val playerA: ExoPlayer = createExoPlayer(context.applicationContext, cacheManager)
    val playerB: ExoPlayer = createExoPlayer(context.applicationContext, cacheManager)

    var activePlayer: ExoPlayer = playerA
        private set
    var standbyPlayer: ExoPlayer = playerB
        private set

    private val scope = CoroutineScope(Dispatchers.Main)
    private var crossfadeMonitorJob: Job? = null
    private var crossfadeTransitionJob: Job? = null
    private val playerListeners = mutableMapOf<ExoPlayer, Player.Listener>()
    private val retriedVideoIds = mutableSetOf<String>()

    var crossfadeDurationMs: Int = 3000
    var onPlayerSwapped: ((ExoPlayer) -> Unit)? = null
    var onSongTransition: ((Song) -> Unit)? = null
    var onTrackEnded: ((Song) -> Unit)? = null

    init {
        setupPlayer(playerA)
        setupPlayer(playerB)
        startPositionMonitor()
    }

    private fun setupPlayer(player: ExoPlayer) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttributes, true)
        player.setHandleAudioBecomingNoisy(true)
        // Hold both CPU and Wi-Fi low-latency wake locks during progressive streaming
        player.setWakeMode(C.WAKE_MODE_NETWORK)

        val listener = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (player == activePlayer && audioSessionId > 0) {
                    audioEffectController.attachAudioSession(audioSessionId)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player == activePlayer && playbackState == Player.STATE_READY) {
                    if (player.audioSessionId > 0) {
                        audioEffectController.attachAudioSession(player.audioSessionId)
                    }
                    // Clear retry state once playback succeeds
                    queueManager.currentSong.value?.remoteVideoId?.let { retriedVideoIds.remove(it) }
                }
                if (player == activePlayer && playbackState == Player.STATE_ENDED) {
                    if (!queueManager.isCrossfading.value) {
                        val next = queueManager.getNextSong()
                        if (next != null) {
                            onTrackEnded?.invoke(next)
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                diagnosticsLog?.error(
                    "ExoPlayer",
                    "Playback error: ${error.errorCodeName} (${error.errorCode})",
                    error.message
                )
                if (player != activePlayer) {
                    if (queueManager.isCrossfading.value) {
                        cancelCrossfade()
                    }
                    return
                }
                val currentSong = queueManager.currentSong.value ?: return
                val videoId = currentSong.remoteVideoId ?: return

                // Automatic recovery if YouTube stream URL expired or IP changed (HTTP 403 / network handoff)
                val cause = error.cause
                val isHttp403 = (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 403)
                if (isHttp403) {
                    diagnosticsLog?.warn(
                        "ExoPlayer",
                        "HTTP 403 on stream URL for $videoId. Invalidating cached URL and retrying"
                    )
                }
                if ((isHttp403 || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) &&
                    retriedVideoIds.add(videoId)
                ) {
                    remoteMusicRepository.invalidateStreamUrl(videoId)
                    val resumePosition = player.currentPosition.coerceAtLeast(0L)
                    player.seekTo(resumePosition)
                    player.prepare()
                    player.play()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player == activePlayer && reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    // User manually triggered a song change
                    if (queueManager.isCrossfading.value) {
                        cancelCrossfade()
                    }
                }
            }
        }
        playerListeners[player] = listener
        player.addListener(listener)
    }

    private fun startPositionMonitor() {
        crossfadeMonitorJob?.cancel()
        crossfadeMonitorJob = scope.launch {
            while (isActive) {
                delay(200)
                if (!activePlayer.isPlaying || queueManager.isCrossfading.value || crossfadeDurationMs <= 0) {
                    continue
                }

                val duration = activePlayer.duration
                val position = activePlayer.currentPosition
                if (duration <= 0 || position <= 0) continue

                val timeLeft = duration - position
                if (timeLeft in 1..crossfadeDurationMs) {
                    val nextSong = queueManager.peekNextSong()
                    if (nextSong != null) {
                        launchCrossfade(nextSong, timeLeft)
                    }
                }
            }
        }
    }

    private fun launchCrossfade(nextSong: Song, remainingMs: Long) {
        // ytm:// is resolved (downloaded file / stream with headers / offline cache) by the ResolvingDataSource
        val targetUri = Uri.parse(nextSong.contentUri)

        queueManager.setCrossfading(true)

        val artBytes = cacheManager.getArtworkBytes(nextSong)
        val artUri = cacheManager.resolveArtworkUri(nextSong)

        val metadata = MediaMetadata.Builder()
            .setTitle(nextSong.title)
            .setArtist(nextSong.artistName)
            .setAlbumTitle(nextSong.albumName)
            .apply {
                if (artBytes != null) {
                    setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
                if (artUri != Uri.EMPTY) {
                    setArtworkUri(artUri)
                }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(nextSong.id.toString())
            .setUri(targetUri)
            .setCustomCacheKey(nextSong.cacheKey)
            .setMediaMetadata(metadata)
            .build()

        standbyPlayer.setMediaItem(mediaItem)
        standbyPlayer.volume = 0f
        standbyPlayer.prepare()
        standbyPlayer.play()

        crossfadeTransitionJob?.cancel()
        crossfadeTransitionJob = scope.launch {
            val totalTime = remainingMs.coerceAtLeast(1000L).toFloat()
            val startTime = System.currentTimeMillis()

            while (isActive && queueManager.isCrossfading.value) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed / totalTime).coerceIn(0f, 1f)

                // Sine / equal-power fade curve
                val fadeOut = cos(progress * (PI / 2.0)).toFloat().coerceIn(0f, 1f)
                val fadeIn = sin(progress * (PI / 2.0)).toFloat().coerceIn(0f, 1f)

                activePlayer.volume = fadeOut
                standbyPlayer.volume = fadeIn

                if (progress >= 1.0f) {
                    break
                }
                delay(40)
            }

            if (!isActive) return@launch

            // Complete crossfade swap
            val outgoing = activePlayer
            val incoming = standbyPlayer

            queueManager.getNextSong() // Advance queue to nextSong

            activePlayer = incoming
            standbyPlayer = outgoing

            queueManager.setCrossfading(false)
            onPlayerSwapped?.invoke(activePlayer)
            outgoing.stop()
            outgoing.volume = 1.0f
            incoming.volume = 1.0f

            audioEffectController.attachAudioSession(activePlayer.audioSessionId)
            onSongTransition?.invoke(nextSong)
        }
    }

    fun cancelCrossfade() {
        crossfadeTransitionJob?.cancel()
        queueManager.setCrossfading(false)
        standbyPlayer.stop()
        standbyPlayer.volume = 1.0f
        activePlayer.volume = 1.0f
    }

    fun releasePlayers() {
        scope.cancel()
        crossfadeMonitorJob?.cancel()
        crossfadeTransitionJob?.cancel()
        audioEffectController.release()
        playerListeners.forEach { (player, listener) ->
            try { player.removeListener(listener) } catch (_: Exception) {}
        }
        playerListeners.clear()
        playerA.release()
        playerB.release()
    }

    companion object {
        private fun createExoPlayer(
            context: Context,
            cacheManager: MediaCacheManager
        ): ExoPlayer {
            val renderersFactory = DefaultRenderersFactory(context)

            // Progressive playback LoadControl: starts decoding after just 250ms of audio buffer!
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 2_500,
                    /* maxBufferMs = */ 60_000,
                    /* bufferForPlaybackMs = */ 250,
                    /* bufferForPlaybackAfterRebufferMs = */ 800
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheManager.resolvingDataSourceFactory)

            return ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build()
        }
    }
}
