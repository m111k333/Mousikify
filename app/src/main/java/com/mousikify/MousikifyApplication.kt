package com.mousikify

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.scanner.observer.MediaStoreObserver
import com.mousikify.core.scanner.work.MediaStoreScannerWorker
import com.mousikify.widget.SpotifyWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MousikifyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var musicController: MusicController

    private var mediaStoreObserver: MediaStoreObserver? = null
    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Register MediaStore ContentObserver for real-time external music additions
        mediaStoreObserver = MediaStoreObserver(this).also { it.register() }

        // Enqueue immediate initial library indexing
        MediaStoreScannerWorker.enqueue(this, initialDelaySeconds = 0)

        // Continuously synchronize real-time playback state with Spotify home screen widget
        applicationScope.launch {
            kotlinx.coroutines.flow.combine(
                musicController.currentSong,
                musicController.isPlaying
            ) { song, isPlaying ->
                Pair(song, isPlaying)
            }.collect { (song, isPlaying) ->
                com.mousikify.widget.SpotifyWidgetUpdater.updateWidget(
                    this@MousikifyApplication,
                    song,
                    isPlaying
                )
            }
        }
    }

    override fun onTerminate() {
        mediaStoreObserver?.unregister()
        super.onTerminate()
    }
}
