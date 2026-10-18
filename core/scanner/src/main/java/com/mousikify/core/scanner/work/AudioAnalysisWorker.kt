package com.mousikify.core.scanner.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mousikify.core.database.dao.SongDao
import com.mousikify.core.database.dao.TrackDao
import com.mousikify.core.database.entity.TrackEntity
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.scanner.classifier.MetadataClassifier
import com.mousikify.core.scanner.dsp.FastAudioAnalyzer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * AudioAnalysisWorker: Executes offline DSP feature extraction and classification
 * in the background via WorkManager, constrained to device charging or idle.
 * Immediately yields if music playback begins to eliminate CPU and battery contention.
 */
@HiltWorker
class AudioAnalysisWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val trackDao: TrackDao,
    private val songDao: SongDao,
    private val musicController: MusicController
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "AudioAnalysisWorker started.")

        // Synchronize any newly added songs from SongEntity to TrackEntity
        try {
            syncSongsToTracks()
        } catch (e: Exception) {
            Log.w(TAG, "Failed syncing songs to tracks: ${e.message}")
        }

        while (!isStopped) {
            // Priority check: immediately yield if user is actively playing music
            if (musicController.isPlaying.value) {
                Log.d(TAG, "Playback is currently active. Yielding AudioAnalysisWorker.")
                return@withContext Result.retry()
            }

            val unanalyzedBatch = trackDao.getUnanalyzedTracks(limit = 10)
            if (unanalyzedBatch.isEmpty()) {
                Log.d(TAG, "No unanalyzed tracks remaining. Work complete.")
                break
            }

            for (track in unanalyzedBatch) {
                if (isStopped) {
                    Log.d(TAG, "Worker stopped by system. Retrying later.")
                    return@withContext Result.retry()
                }

                // Check again before analyzing each individual track
                if (musicController.isPlaying.value) {
                    Log.d(TAG, "Music started during analysis. Pausing worker.")
                    return@withContext Result.retry()
                }

                try {
                    val analysis = FastAudioAnalyzer.analyze(appContext, Uri.parse(track.uri))
                    val updated = track.copy(
                        title = if (track.title.isNotBlank() && track.title != "Unknown Title") track.title else analysis.title,
                        artist = if (track.artist.isNotBlank() && track.artist != "Unknown Artist") track.artist else analysis.artist,
                        album = if (track.album.isNotBlank() && track.album != "Unknown Album") track.album else analysis.album,
                        rawGenre = analysis.rawGenre ?: track.rawGenre,
                        normalizedGenre = analysis.normalizedGenre,
                        year = if (track.year > 0) track.year else analysis.year,
                        decade = analysis.decade ?: track.decade,
                        durationMs = if (track.durationMs > 0) track.durationMs else analysis.durationMs,
                        rmsEnergy = analysis.rmsEnergy,
                        bpm = analysis.bpm,
                        vibeCategory = analysis.vibeCategory,
                        isAnalyzed = true
                    )
                    trackDao.updateTrack(updated)
                    Log.d(TAG, "Analyzed: '${updated.title}' -> RMS: ${updated.rmsEnergy}, BPM: ${updated.bpm}, Vibe: '${updated.vibeCategory}'")
                } catch (e: Exception) {
                    Log.w(TAG, "Error analyzing track ${track.id} (${track.uri}): ${e.message}")
                    // Mark as analyzed with default vibe to avoid endless loops on corrupt audio
                    trackDao.updateTrack(track.copy(isAnalyzed = true, vibeCategory = "Mid-Tempo Flow"))
                }

                // Small delay to keep thermals and CPU low
                delay(60)
            }
        }

        Result.success()
    }

    private suspend fun syncSongsToTracks() {
        val allSongs = songDao.getAllSongs().first()
        val newTracks = mutableListOf<TrackEntity>()

        for (song in allSongs) {
            val existing = trackDao.getTrackByUri(song.contentUri)
            if (existing == null) {
                var rawGenre: String? = null
                var year = song.year
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(appContext, Uri.parse(song.contentUri))
                    rawGenre = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)?.takeIf { it.isNotBlank() }
                    if (year <= 0) {
                        val yearRaw = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)
                            ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE)
                        if (!yearRaw.isNullOrBlank()) {
                            val match = Regex("""\b(19\d\d|20\d\d)\b""").find(yearRaw)
                            year = match?.value?.toIntOrNull() ?: 0
                        }
                    }
                } catch (_: Exception) {} finally {
                    try { retriever.release() } catch (_: Exception) {}
                }

                val decade = MetadataClassifier.extractDecade(year)
                val normalizedGenre = MetadataClassifier.normalizeGenre(rawGenre)

                newTracks.add(
                    TrackEntity(
                        uri = song.contentUri,
                        title = song.title,
                        artist = song.artistName,
                        album = song.albumName,
                        rawGenre = rawGenre,
                        normalizedGenre = if (normalizedGenre != "Other") normalizedGenre else null,
                        year = year,
                        decade = decade,
                        durationMs = song.durationMs,
                        rmsEnergy = 0.0f,
                        bpm = 0,
                        vibeCategory = null,
                        isAnalyzed = false
                    )
                )
            }
        }

        if (newTracks.isNotEmpty()) {
            trackDao.upsertTracks(newTracks)
            Log.d(TAG, "Synced ${newTracks.size} new tracks into analysis queue.")
        }
    }

    companion object {
        private const val TAG = "AudioAnalysisWorker"
        const val WORK_NAME = "audio_dsp_analysis_work"
        const val PERIODIC_WORK_NAME = "audio_dsp_analysis_periodic"

        /**
         * Enqueues a one-time analysis request with device idle or charging constraints.
         */
        fun enqueue(
            context: Context,
            requireCharging: Boolean = false,
            requireIdle: Boolean = false
        ) {
            val constraints = Constraints.Builder().apply {
                if (requireCharging) setRequiresCharging(true)
                if (requireIdle) setRequiresDeviceIdle(true)
            }.build()

            val request = OneTimeWorkRequestBuilder<AudioAnalysisWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Schedules recurring analysis when charging and idle.
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AudioAnalysisWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
