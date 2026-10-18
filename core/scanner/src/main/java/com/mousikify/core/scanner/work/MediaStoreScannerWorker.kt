package com.mousikify.core.scanner.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mousikify.core.scanner.indexer.MediaStoreScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MediaStoreScannerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scanner: MediaStoreScanner
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            scanner.scanMediaStore()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "media_store_sync_work"

        fun enqueue(context: Context, initialDelaySeconds: Long = 2) {
            val constraints = Constraints.Builder().build()
            val request = OneTimeWorkRequestBuilder<MediaStoreScannerWorker>()
                .setConstraints(constraints)
                .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
