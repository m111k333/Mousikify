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
            .crossfade(250)
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

        // Capture uncaught exceptions to internal storage so friends can share crash logs
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                val report = "Crash at $timestamp on thread ${thread.name} " +
                        "(Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android SDK ${android.os.Build.VERSION.SDK_INT}):\n" +
                        throwable.stackTraceToString()
                crashFile.writeText(report)
            }
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }

        // Disable file URI exposure check to prevent FileUriExposedException across any IPC or external notification components
        runCatching {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        }

        // Register MediaStore ContentObserver for real-time external music additions
        mediaStoreObserver = MediaStoreObserver(this).also { it.register() }

        // Enqueue immediate initial library indexing only if storage permission is already granted
        val hasAudioPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (hasAudioPermission) {
            MediaStoreScannerWorker.enqueue(this, initialDelaySeconds = 0)
        }

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
