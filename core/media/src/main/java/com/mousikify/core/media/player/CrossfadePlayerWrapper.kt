package com.mousikify.core.media.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
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
) : ForwardingPlayer(createExoPlayer(context, audioProcessorA)) {

    private var playerA: ExoPlayer = wrappedPlayer as ExoPlayer
    private var playerB: ExoPlayer = createExoPlayer(context, audioProcessorB)

    private var activePlayer: ExoPlayer = playerA
    private var standbyPlayer: ExoPlayer = playerB

    private val scope = CoroutineScope(Dispatchers.Main)
    private var crossfadeMonitorJob: Job? = null
    private var isCrossfading = false

    var crossfadeDurationMs: Int = 3000

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
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && player == activePlayer && !isCrossfading) {
                    val nextSong = queueManager.getNextSong()
                    if (nextSong != null) {
                        playSong(nextSong)
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (player == activePlayer) {
                    audioEffectController.attachAudioSession(audioSessionId)
                }
            }
        })
    }

    fun playSong(song: Song) {
        isCrossfading = false
        activePlayer.volume = 1.0f
        activePlayer.stop()
        standbyPlayer.stop()

        val mediaItem = MediaItem.fromUri(Uri.parse(song.contentUri))
        activePlayer.setMediaItem(mediaItem)
        activePlayer.prepare()
        activePlayer.play()

        // Apply ReplayGain
        if (activePlayer == playerA) {
            audioProcessorA.setReplayGain(song.replayGainDb)
        } else {
            audioProcessorB.setReplayGain(song.replayGainDb)
        }

        audioEffectController.attachAudioSession(activePlayer.audioSessionId)
        onSongTransition?.invoke(song)
    }

    private fun startPositionMonitor() {
        crossfadeMonitorJob?.cancel()
        crossfadeMonitorJob = scope.launch {
            while (isActive) {
                delay(200)
                if (!activePlayer.isPlaying || isCrossfading || crossfadeDurationMs <= 0) {
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
        isCrossfading = true

        val mediaItem = MediaItem.fromUri(Uri.parse(nextSong.contentUri))
        standbyPlayer.setMediaItem(mediaItem)
        standbyPlayer.volume = 0f
        standbyPlayer.prepare()
        standbyPlayer.play()

        // Apply ReplayGain to next player
        if (standbyPlayer == playerA) {
            audioProcessorA.setReplayGain(nextSong.replayGainDb)
        } else {
            audioProcessorB.setReplayGain(nextSong.replayGainDb)
        }

        scope.launch {
            val totalCrossfade = crossfadeDurationMs.toFloat()
            val startTime = System.currentTimeMillis()
            val totalTime = remainingMs.coerceAtLeast(1000)

            while (isActive && isCrossfading) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed / totalTime.toFloat()).coerceIn(0f, 1f)

                // Sine/equal-power fade curve
                val fadeOut = cos(progress * (PI / 2.0)).toFloat().coerceIn(0f, 1f)
                val fadeIn = sin(progress * (PI / 2.0)).toFloat().coerceIn(0f, 1f)

                activePlayer.volume = fadeOut
                standbyPlayer.volume = fadeIn

                if (progress >= 1.0f) {
                    break
                }
                delay(50)
            }

            // Complete crossfade swap
            activePlayer.stop()
            activePlayer.volume = 1.0f
            standbyPlayer.volume = 1.0f

            // Swap references
            val temp = activePlayer
            activePlayer = standbyPlayer
            standbyPlayer = temp

            isCrossfading = false
            queueManager.getNextSong() // advance queue
            audioEffectController.attachAudioSession(activePlayer.audioSessionId)
            onSongTransition?.invoke(nextSong)
        }
    }

    fun releasePlayers() {
        crossfadeMonitorJob?.cancel()
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
