package com.mousikify

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mousikify.core.scanner.observer.MediaStoreObserver
import com.mousikify.core.scanner.work.MediaStoreScannerWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MousikifyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private var mediaStoreObserver: MediaStoreObserver? = null

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
    }

    override fun onTerminate() {
        mediaStoreObserver?.unregister()
        super.onTerminate()
    }
}
