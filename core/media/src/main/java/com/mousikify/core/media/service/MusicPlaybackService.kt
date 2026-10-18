package com.mousikify.core.media.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.dsp.AudioEffectController
import com.mousikify.core.media.dsp.EqualizerAudioProcessor
import com.mousikify.core.media.player.CrossfadePlayerWrapper
import com.mousikify.core.media.queue.QueueManager
import com.mousikify.core.model.Song
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var playbackPreferences: PlaybackPreferences

    private var mediaSession: MediaSession? = null
    lateinit var crossfadePlayer: CrossfadePlayerWrapper
        private set

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    companion object {
        const val CUSTOM_ACTION_TOGGLE_FAVORITE = "com.mousikify.action.TOGGLE_FAVORITE"
        const val CUSTOM_ACTION_CYCLE_REPEAT = "com.mousikify.action.CYCLE_REPEAT"
        const val CUSTOM_ACTION_TOGGLE_SHUFFLE = "com.mousikify.action.TOGGLE_SHUFFLE"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val processorA = EqualizerAudioProcessor()
        val processorB = EqualizerAudioProcessor()
        val effectController = AudioEffectController()

        crossfadePlayer = CrossfadePlayerWrapper(
            context = this,
            queueManager = queueManager,
            audioProcessorA = processorA,
            audioProcessorB = processorB,
            audioEffectController = effectController
        )

        // Load EQ settings from preferences
        serviceScope.launch {
            val enabled = playbackPreferences.eqEnabled.first()
            processorA.setEnabled(enabled)
            processorB.setEnabled(enabled)

            val bass = playbackPreferences.bassBoostStrength.first()
            val virt = playbackPreferences.virtualizerStrength.first()
            val reverb = playbackPreferences.reverbPreset.first()
            effectController.setBassBoost(bass)
            effectController.setVirtualizer(virt)
            effectController.setReverb(reverb)

            val crossfade = playbackPreferences.crossfadeDurationMs.first()
            crossfadePlayer.crossfadeDurationMs = crossfade
        }

        crossfadePlayer.onSongTransition = { song ->
            updateMediaMetadata(song)
            serviceScope.launch {
                songRepository.recordPlay(song.id)
            }
        }

        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val callback = object : MediaSession.Callback {
            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    CUSTOM_ACTION_TOGGLE_FAVORITE -> {
                        val currentSong = queueManager.currentSong.value
                        if (currentSong != null) {
                            serviceScope.launch {
                                songRepository.toggleFavorite(currentSong.id, !currentSong.isFavorite)
                            }
                        }
                    }
                    CUSTOM_ACTION_CYCLE_REPEAT -> {
                        queueManager.cycleRepeatMode()
                    }
                    CUSTOM_ACTION_TOGGLE_SHUFFLE -> {
                        queueManager.toggleShuffle()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, crossfadePlayer)
            .setCallback(callback)
            .apply {
                if (sessionActivityIntent != null) {
                    setSessionActivity(sessionActivityIntent)
                }
            }
            .build()
    }

    private fun updateMediaMetadata(song: Song) {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artistName)
            .setAlbumTitle(song.albumName)
            .setArtworkUri(android.net.Uri.parse("content://media/external/audio/albumart/${song.albumId}"))
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.contentUri)
            .setMediaMetadata(metadata)
            .build()

        crossfadePlayer.setMediaItem(mediaItem)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        crossfadePlayer.releasePlayers()
        super.onDestroy()
    }
}
