package com.mousikify.core.media.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
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
import com.mousikify.core.media.R
import com.mousikify.core.media.cache.MediaCacheManager
import com.mousikify.core.media.diagnostics.DiagnosticsLog
import com.mousikify.core.media.dsp.AudioEffectController
import com.mousikify.core.media.player.CrossfadePlayerWrapper
import com.mousikify.core.media.player.QueueForwardingPlayer
import com.mousikify.core.media.queue.QueueManager
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.model.Song
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var playbackPreferences: PlaybackPreferences
    @Inject lateinit var cacheManager: MediaCacheManager
    @Inject lateinit var remoteMusicRepository: RemoteMusicRepository
    @Inject lateinit var diagnosticsLog: DiagnosticsLog

    private var mediaSession: MediaSession? = null
    lateinit var crossfadePlayer: CrossfadePlayerWrapper
        private set
    private var forwardingPlayer: QueueForwardingPlayer? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val CUSTOM_ACTION_TOGGLE_FAVORITE = "com.mousikify.action.TOGGLE_FAVORITE"
        const val CUSTOM_ACTION_CYCLE_REPEAT = "com.mousikify.action.CYCLE_REPEAT"
        const val CUSTOM_ACTION_TOGGLE_SHUFFLE = "com.mousikify.action.TOGGLE_SHUFFLE"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val effectController = AudioEffectController()

        crossfadePlayer = CrossfadePlayerWrapper(
            context = this,
            queueManager = queueManager,
            cacheManager = cacheManager,
            remoteMusicRepository = remoteMusicRepository,
            audioEffectController = effectController,
            diagnosticsLog = diagnosticsLog
        )

        registerNetworkHandoffCallback()

        // Continuously observe and apply EQ & DSP settings in real-time
        serviceScope.launch {
            playbackPreferences.eqEnabled.collect { enabled ->
                effectController.setEnabled(enabled)
            }
        }
        serviceScope.launch {
            playbackPreferences.eqBandLevels.collect { bands ->
                effectController.setBandLevels(bands)
            }
        }
        serviceScope.launch {
            playbackPreferences.bassBoostStrength.collect { bass ->
                effectController.setBassBoost(bass)
            }
        }
        serviceScope.launch {
            playbackPreferences.virtualizerStrength.collect { virt ->
                effectController.setVirtualizer(virt)
            }
        }
        serviceScope.launch {
            playbackPreferences.reverbPreset.collect { reverb ->
                effectController.setReverb(reverb)
            }
        }
        serviceScope.launch {
            playbackPreferences.crossfadeDurationMs.collect { crossfade ->
                crossfadePlayer.crossfadeDurationMs = crossfade
            }
        }

        forwardingPlayer = QueueForwardingPlayer(crossfadePlayer.activePlayer, queueManager) { song ->
            playSong(song)
        }

        crossfadePlayer.onPlayerSwapped = { newPlayer ->
            forwardingPlayer = QueueForwardingPlayer(newPlayer, queueManager) { song ->
                playSong(song)
            }
            mediaSession?.setPlayer(forwardingPlayer!!)
            updateCustomLayout(queueManager.currentSong.value)
        }

        crossfadePlayer.onTrackEnded = { song ->
            playSong(song)
        }

        crossfadePlayer.onSongTransition = { song ->
            if (!song.isRemoteStream) {
                serviceScope.launch {
                    songRepository.recordPlay(song.id)
                }
            }
            updateCustomLayout(song)
        }

        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_ACTION_CYCLE_REPEAT, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                    .build()
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
                return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                val resolvedItems = mediaItems.map { item ->
                    val songId = item.mediaId.toLongOrNull()
                    val matchingSong = queueManager.currentSong.value?.takeIf { it.id == songId }
                        ?: queueManager.currentQueue.value.firstOrNull { it.id == songId }

                    val targetUri = item.requestMetadata.mediaUri
                        ?: item.localConfiguration?.uri
                        ?: matchingSong?.let { s ->
                            if (s.isRemoteStream) {
                                val local = s.remoteVideoId?.let { vid -> cacheManager.getDownloadedMusicFile(vid) }
                                if (local != null && local.exists() && local.length() > 0L) {
                                    android.net.Uri.fromFile(local)
                                } else {
                                    android.net.Uri.parse(s.contentUri)
                                }
                            } else {
                                android.net.Uri.parse(s.contentUri)
                            }
                        }

                    val builder = item.buildUpon()
                    if (targetUri != null) {
                        val cacheKey = matchingSong?.cacheKey ?: item.localConfiguration?.customCacheKey ?: item.mediaId
                        builder.setUri(targetUri).setCustomCacheKey(cacheKey)
                    }
                    if (matchingSong != null && item.mediaMetadata.artworkData == null) {
                        val artBytes = cacheManager.getArtworkBytes(matchingSong)
                        val artUri = cacheManager.resolveArtworkUri(matchingSong)
                        val meta = item.mediaMetadata.buildUpon()
                            .apply {
                                if (artBytes != null) {
                                    setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                }
                                if (artUri != android.net.Uri.EMPTY) {
                                    setArtworkUri(artUri)
                                }
                            }
                            .build()
                        builder.setMediaMetadata(meta)
                    }
                    builder.build()
                }.toMutableList()
                return Futures.immediateFuture(resolvedItems)
            }

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
                            val newFav = !currentSong.isFavorite
                            queueManager.updateFavorite(currentSong.id, newFav)
                            serviceScope.launch {
                                if (currentSong.isRemoteStream) {
                                    if (newFav) {
                                        songRepository.upsertSongs(listOf(currentSong.copy(isFavorite = true)))
                                        currentSong.remoteVideoId?.let {
                                            cacheManager.downloadFullTrackForOffline(currentSong)
                                        }
                                    } else {
                                        songRepository.toggleFavorite(currentSong.id, false)
                                        songRepository.deleteNonFavoriteRemoteSongs()
                                        cacheManager.pruneOrphanedCacheEntries(currentSong.remoteVideoId)
                                    }
                                } else {
                                    songRepository.toggleFavorite(currentSong.id, newFav)
                                }
                            }
                            updateCustomLayout(currentSong.copy(isFavorite = newFav))
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

        mediaSession = MediaSession.Builder(this, forwardingPlayer!!)
            .setCallback(callback)
            .apply {
                if (sessionActivityIntent != null) {
                    setSessionActivity(sessionActivityIntent)
                }
            }
            .build()

        // Sync favorite button and notification metadata whenever current song or favorite status changes
        serviceScope.launch {
            queueManager.currentSong.collect { song ->
                updateCustomLayout(song)
            }
        }
    }

    /**
     * Proactively invalidates IP-bound googlevideo URLs when the device switches between
     * Wi-Fi and Cellular data, allowing seamless handoff from the 60s audio buffer.
     */
    private fun registerNetworkHandoffCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            connectivityManager = cm
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    remoteMusicRepository.invalidateAllUrls()
                }
            }
            networkCallback = callback
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {}
    }

    private var servicePlayJob: kotlinx.coroutines.Job? = null

    @OptIn(UnstableApi::class)
    private fun playSong(song: Song) {
        servicePlayJob?.cancel()
        if (queueManager.isCrossfading.value) {
            crossfadePlayer.cancelCrossfade()
        }
        servicePlayJob = serviceScope.launch {
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
                        val manifest = remoteMusicRepository.resolveStreamManifest(videoId).getOrNull()
                        if (manifest != null) {
                            // Pass ytm:// so ResolvingDataSource adds the client-matching User-Agent (raw URL -> 403)
                            resolvedUri = android.net.Uri.parse(song.contentUri)
                            resolvedMimeType = manifest.mimeType
                        } else if (cacheManager.isTrackCached(videoId)) {
                            resolvedUri = android.net.Uri.parse("https://offline.googlevideo.com/videoplayback?id=$videoId")
                            resolvedMimeType = null
                        } else {
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

                val metadata = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumName)
                    .apply {
                        if (artBytes != null) {
                            setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                        if (artUri != android.net.Uri.EMPTY) {
                            setArtworkUri(artUri)
                        }
                    }
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(resolvedUri)
                    .setCustomCacheKey(song.cacheKey)
                    .apply {
                        if (resolvedMimeType != null) {
                            setMimeType(resolvedMimeType)
                        }
                    }
                    .setMediaMetadata(metadata)
                    .build()

                crossfadePlayer.activePlayer.setMediaItem(mediaItem)
                crossfadePlayer.activePlayer.prepare()
                crossfadePlayer.activePlayer.play()
                updateCustomLayout(song)
                if (!song.isRemoteStream) {
                    songRepository.recordPlay(song.id)
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("MusicPlaybackService", "Error during playSong for ${song.id}", e)
            }
        }
    }

    private fun updateCustomLayout(song: Song?) {
        runCatching {
            val session = mediaSession ?: return@runCatching
            val isFav = song?.isFavorite == true
            val favIcon = if (isFav) R.drawable.ic_notification_favorite_filled else R.drawable.ic_notification_favorite_border
            val favButton = CommandButton.Builder()
                .setDisplayName(if (isFav) "Liked" else "Like")
                .setIconResId(favIcon)
                .setSessionCommand(SessionCommand(CUSTOM_ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build()
            session.setCustomLayout(listOf(favButton))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            crossfadePlayer.activePlayer.stop()
            crossfadePlayer.standbyPlayer.stop()
        } catch (_: Exception) {}
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        networkCallback?.let { cb ->
            try { connectivityManager?.unregisterNetworkCallback(cb) } catch (_: Exception) {}
        }
        networkCallback = null
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        crossfadePlayer.releasePlayers()
        super.onDestroy()
    }
}
