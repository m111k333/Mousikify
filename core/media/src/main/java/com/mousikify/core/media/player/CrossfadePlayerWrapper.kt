package com.mousikify.core.media.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.mousikify.core.media.dsp.AudioEffectController
import com.mousikify.core.media.dsp.EqualizerAudioProcessor
import com.mousikify.core.media.queue.QueueManager
import com.mousikify.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(UnstableApi::class)
class CrossfadePlayerWrapper(
    private val context: Context,
    private val queueManager: QueueManager,
    val audioProcessorA: EqualizerAudioProcessor,
    val audioProcessorB: EqualizerAudioProcessor,
    val audioEffectController: AudioEffectController
) {

    val playerA: ExoPlayer = createExoPlayer(context, audioProcessorA)
    val playerB: ExoPlayer = createExoPlayer(context, audioProcessorB)

    var activePlayer: ExoPlayer = playerA
        private set
    var standbyPlayer: ExoPlayer = playerB
        private set

    private val scope = CoroutineScope(Dispatchers.Main)
    private var crossfadeMonitorJob: Job? = null
    private var crossfadeTransitionJob: Job? = null

    var crossfadeDurationMs: Int = 3000
    var onPlayerSwapped: ((ExoPlayer) -> Unit)? = null
    var onSongTransition: ((Song) -> Unit)? = null

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
        player.setWakeMode(C.WAKE_MODE_LOCAL)

        player.addListener(object : Player.Listener {
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
        })
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
        queueManager.setCrossfading(true)

        val metadata = MediaMetadata.Builder()
            .setTitle(nextSong.title)
            .setArtist(nextSong.artistName)
            .setAlbumTitle(nextSong.albumName)
            .setArtworkUri(
                if (!nextSong.artworkUri.isNullOrBlank()) {
                    Uri.parse(nextSong.artworkUri)
                } else {
                    Uri.parse("content://media/external/audio/albumart/${nextSong.albumId}")
                }
            )
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(nextSong.id.toString())
            .setUri(Uri.parse(nextSong.contentUri))
            .setMediaMetadata(metadata)
            .build()

        standbyPlayer.setMediaItem(mediaItem)
        standbyPlayer.volume = 0f
        standbyPlayer.prepare()
        standbyPlayer.play()

        // Apply ReplayGain
        if (standbyPlayer == playerA) {
            audioProcessorA.setReplayGain(nextSong.replayGainDb)
        } else {
            audioProcessorB.setReplayGain(nextSong.replayGainDb)
        }

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

            outgoing.stop()
            outgoing.volume = 1.0f
            incoming.volume = 1.0f

            activePlayer = incoming
            standbyPlayer = outgoing

            queueManager.setCrossfading(false)
            onPlayerSwapped?.invoke(activePlayer)
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
        crossfadeMonitorJob?.cancel()
        crossfadeTransitionJob?.cancel()
        audioEffectController.release()
        playerA.release()
        playerB.release()
    }

    companion object {
        private fun createExoPlayer(context: Context, processor: EqualizerAudioProcessor): ExoPlayer {
            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): androidx.media3.exoplayer.audio.AudioSink {
                    return DefaultAudioSink.Builder(context)
                        .setAudioProcessors(arrayOf(processor))
                        .build()
                }
            }
            return ExoPlayer.Builder(context, renderersFactory).build()
        }
    }
}
