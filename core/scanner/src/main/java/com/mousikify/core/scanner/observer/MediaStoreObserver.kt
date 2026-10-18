package com.mousikify.core.scanner.observer

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.mousikify.core.scanner.work.MediaStoreScannerWorker

class MediaStoreObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        // Debounce via WorkManager REPLACE policy with 2s delay
        MediaStoreScannerWorker.enqueue(context, initialDelaySeconds = 2)
    }

    fun register() {
        try {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.registerContentObserver(uri, true, this)
        } catch (_: Exception) {}
    }

    fun unregister() {
        try {
            context.contentResolver.unregisterContentObserver(this)
        } catch (_: Exception) {}
    }
}
