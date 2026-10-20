package com.mousikify

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.scanner.observer.MediaStoreObserver
import com.mousikify.core.scanner.work.MediaStoreScannerWorker
import com.mousikify.widget.SpotifyWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class MousikifyApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var musicController: MusicController

    private var mediaStoreObserver: MediaStoreObserver? = null
    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.12)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_artwork_cache").toOkioPath())
                    .maxSizeBytes(32L * 1024L * 1024L) // 32 MB artwork disk cache (~1,000 covers)
                    .build()
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Disable file URI exposure check to prevent FileUriExposedException across any IPC or external notification components
        runCatching {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        }

        // Register MediaStore ContentObserver for real-time external music additions
        mediaStoreObserver = MediaStoreObserver(this).also { it.register() }

        // Enqueue immediate initial library indexing
        MediaStoreScannerWorker.enqueue(this, initialDelaySeconds = 0)

        // Continuously synchronize real-time playback state with Spotify home screen widget
        applicationScope.launch {
            kotlinx.coroutines.flow.combine(
                musicController.currentSong,
                musicController.isPlaying,
                musicController.cacheUpdateTick
            ) { song, isPlaying, _ ->
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
        applicationScope.cancel()
        musicController.release()
        mediaStoreObserver?.unregister()
        super.onTerminate()
    }
}
