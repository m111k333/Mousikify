package com.mousikify.core.scanner.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.mousikify.core.scanner.classifier.MetadataClassifier
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AudioAnalysisResult(
    val title: String,
    val artist: String,
    val album: String,
    val rawGenre: String?,
    val normalizedGenre: String,
    val year: Int,
    val decade: String?,
    val durationMs: Long,
    val rmsEnergy: Float,
    val bpm: Int,
    val vibeCategory: String
)

/**
 * FastAudioAnalyzer: A 100% offline, lightweight audio feature extractor.
 * Decodes ONLY a 30-second middle segment via Android MediaExtractor + MediaCodec
 * to compute RMS Energy and approximate BPM without external heavy ML runtimes.
 */
object FastAudioAnalyzer {

    private const val TAG = "FastAudioAnalyzer"
    private const val ANALYSIS_WINDOW_SECONDS = 30
    private const val FRAME_DURATION_MS = 50

    fun analyze(context: Context, audioUri: Uri): AudioAnalysisResult {
        // Step A: Extract metadata using MediaMetadataRetriever
        val meta = extractMetadata(context, audioUri)
        val decade = MetadataClassifier.extractDecade(meta.year)
        val normalizedGenre = MetadataClassifier.normalizeGenre(meta.rawGenre)

        // Step B: Decode 30-second middle segment to 16-bit PCM ShortArray
        val pcmData = decode30SecondSegment(
            context = context,
            audioUri = audioUri,
            durationMs = meta.durationMs
        )

        if (pcmData == null || pcmData.isEmpty()) {
            val fallbackVibe = classifyVibe(0.0f, 0)
            return AudioAnalysisResult(
                title = meta.title,
                artist = meta.artist,
                album = meta.album,
                rawGenre = meta.rawGenre,
                normalizedGenre = normalizedGenre,
                year = meta.year,
                decade = decade,
                durationMs = meta.durationMs,
                rmsEnergy = 0.0f,
                bpm = 0,
                vibeCategory = fallbackVibe
            )
        }

        // Step C: Calculate RMS Energy
        val rmsEnergy = calculateRmsEnergy(pcmData)

        // Step D: Approximate Tempo (BPM)
        val bpm = approximateBpm(
            pcmData = pcmData,
            sampleRate = 44100, // standard default if not extracted
            channels = 2
        )

        // Step E: Classify Vibe
        val vibe = classifyVibe(rmsEnergy, bpm)

        return AudioAnalysisResult(
            title = meta.title,
            artist = meta.artist,
            album = meta.album,
            rawGenre = meta.rawGenre,
            normalizedGenre = normalizedGenre,
            year = meta.year,
            decade = decade,
            durationMs = meta.durationMs,
            rmsEnergy = rmsEnergy,
            bpm = bpm,
            vibeCategory = vibe
        )
    }

    private data class TrackMeta(
        val title: String,
        val artist: String,
        val album: String,
        val rawGenre: String?,
        val year: Int,
        val durationMs: Long
    )

    private fun extractMetadata(context: Context, audioUri: Uri): TrackMeta {
        var title = "Unknown Title"
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var rawGenre: String? = null
        var year = 0
        var durationMs = 0L

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, audioUri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let {
                title = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let {
                artist = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let {
                album = it.trim()
            }
            rawGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.takeIf { it.isNotBlank() }?.trim()

            val yearRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            year = parseYear(yearRaw)

            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract metadata for $audioUri: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return TrackMeta(
            title = title,
            artist = artist,
            album = album,
            rawGenre = rawGenre,
            year = year,
            durationMs = durationMs
        )
    }

    private fun parseYear(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val match = Regex("""\b(19\d\d|20\d\d)\b""").find(raw)
        return match?.value?.toIntOrNull() ?: 0
    }

    /**
     * Seeks to 30% of total duration and decodes 30 seconds of audio into raw 16-bit PCM ShortArray.
     */
    private fun decode30SecondSegment(
        context: Context,
        audioUri: Uri,
        durationMs: Long
    ): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val pfd = try {
            context.contentResolver.openFileDescriptor(audioUri, "r")
        } catch (_: Exception) {
            null
        }

        try {
            if (pfd != null) {
                extractor.setDataSource(pfd.fileDescriptor)
            } else {
                extractor.setDataSource(context, audioUri, null)
            }

            var audioTrackIndex = -1
            var mimeType = ""
            var sampleRate = 44100
            var channelCount = 2

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    mimeType = mime
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    break
                }
            }

            if (audioTrackIndex < 0 || mimeType.isBlank()) {
                return null
            }

            extractor.selectTrack(audioTrackIndex)

            // Calculate 30% seek point
            val seekTargetUs = if (durationMs > 30_000L) {
                (durationMs * 1000L * 0.30).toLong()
            } else {
                0L
            }
            extractor.seekTo(seekTargetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val trackFormat = extractor.getTrackFormat(audioTrackIndex)
            val decoder = MediaCodec.createDecoderByType(mimeType)
            codec = decoder
            decoder.configure(trackFormat, null, null, 0)
            decoder.start()

            val maxSamples = (sampleRate * channelCount * ANALYSIS_WINDOW_SECONDS).coerceAtLeast(44100)
            val pcmChunks = mutableListOf<ShortArray>()
            var totalSamples = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            val timeoutUs = 5000L
            var loopCount = 0
            val maxLoops = 2500

            while (!isOutputEOS && totalSamples < maxSamples && loopCount++ < maxLoops) {
                if (!isInputEOS) {
                    val inputIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isInputEOS = true
                            } else {
                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                    val outputBuffer = decoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val available = shortBuffer.remaining()
                        val take = minOf(available, maxSamples - totalSamples)
                        if (take > 0) {
                            val chunk = ShortArray(take)
                            shortBuffer.get(chunk, 0, take)
                            pcmChunks.add(chunk)
                            totalSamples += take
                        }
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFormat = decoder.outputFormat
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }

            if (totalSamples == 0) return null

            val result = ShortArray(totalSamples)
            var destPos = 0
            for (chunk in pcmChunks) {
                System.arraycopy(chunk, 0, result, destPos, chunk.size)
                destPos += chunk.size
            }
            return result
        } catch (e: Exception) {
            Log.w(TAG, "Audio decoding failed for $audioUri: ${e.message}")
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Calculates Root Mean Square (RMS) energy:
     * RMS = sqrt( (1 / N) * sum( (sample[i] / 32768.0)^2 ) )
     * Scaled by 2.2x to map commercial mastering levels [0.05..0.45] to normalized [0.0..1.0].
     */
    fun calculateRmsEnergy(pcmData: ShortArray): Float {
        if (pcmData.isEmpty()) return 0.0f

        var sumSquares = 0.0
        for (i in pcmData.indices) {
            val normalized = pcmData[i] / 32768.0
            sumSquares += normalized * normalized
        }
        val rawRms = sqrt(sumSquares / pcmData.size)
        // Scaled to map real-world mastered dynamic range into [0.0, 1.0]
        return (rawRms * 2.2).toFloat().coerceIn(0.0f, 1.0f)
    }

    /**
     * Lightweight Tempo (BPM) approximation:
     * 1. Divides the buffer into 50ms windows and computes instantaneous energy.
     * 2. Detects local peaks exceeding a dynamic threshold (beat onsets).
     * 3. Computes Inter-Onset Intervals (IOI) and maps mode to BPM clamped between 60 and 180.
     */
    fun approximateBpm(
        pcmData: ShortArray,
        sampleRate: Int = 44100,
        channels: Int = 2
    ): Int {
        if (pcmData.isEmpty()) return 0

        val samplesPerWindow = maxOf(1, ((sampleRate * channels * FRAME_DURATION_MS) / 1000))
        val numWindows = pcmData.size / samplesPerWindow
        if (numWindows < 12) return 0

        val energies = FloatArray(numWindows)
        for (w in 0 until numWindows) {
            val start = w * samplesPerWindow
            var sum = 0.0
            for (s in 0 until samplesPerWindow) {
                val norm = pcmData[start + s] / 32768.0
                sum += norm * norm
            }
            energies[w] = (sum / samplesPerWindow).toFloat()
        }

        // Detect beat onsets via dynamic moving threshold
        val contextRadius = 8
        val minIntervalWindows = 4 // ~200ms refractory period (max 300 BPM)
        var lastOnsetWindow = -minIntervalWindows
        val onsets = mutableListOf<Int>()

        for (w in 1 until numWindows - 1) {
            val startC = maxOf(0, w - contextRadius)
            val endC = minOf(numWindows - 1, w + contextRadius)
            var localSum = 0.0f
            for (c in startC..endC) {
                localSum += energies[c]
            }
            val localMean = localSum / (endC - startC + 1)
            val dynamicThreshold = localMean * 1.35f + 0.0003f

            val e = energies[w]
            if (e > dynamicThreshold && e > energies[w - 1] && e >= energies[w + 1] && (w - lastOnsetWindow) >= minIntervalWindows) {
                onsets.add(w)
                lastOnsetWindow = w
            }
        }

        if (onsets.size < 4) return 0

        // Compute Inter-Onset Intervals (IOI) in milliseconds
        val iois = mutableListOf<Int>()
        for (i in 1 until onsets.size) {
            val deltaMs = (onsets[i] - onsets[i - 1]) * FRAME_DURATION_MS
            if (deltaMs in 250..1200) {
                iois.add(deltaMs)
            }
        }

        if (iois.isEmpty()) return 0

        // Histogram binning to find mode interval
        val binSize = 25
        val histogram = mutableMapOf<Int, Int>()
        for (ioi in iois) {
            val bin = (ioi / binSize) * binSize
            histogram[bin] = (histogram[bin] ?: 0) + 1
        }

        val modeBin = histogram.maxByOrNull { it.value }?.key ?: return 0
        val peakCount = histogram[modeBin] ?: 0
        if (peakCount < 2) return 0

        val modalIntervalMs = modeBin + (binSize / 2.0)
        val rawBpm = (60_000.0 / modalIntervalMs).roundToInt()

        // Clamp / octavize BPM between 60 and 180
        var bpm = rawBpm
        while (bpm < 60 && bpm > 0) {
            bpm *= 2
        }
        while (bpm > 180) {
            bpm /= 2
        }

        return bpm.coerceIn(60, 180)
    }

    /**
     * Vibe classification based on RMS energy and estimated tempo:
     * - "High Energy / Workout" if rmsEnergy >= 0.65f || (bpm >= 125 && rmsEnergy >= 0.50f)
     * - "Focus / Ambient" if rmsEnergy <= 0.25f
     * - "Chill / Relaxed" if rmsEnergy <= 0.35f && bpm <= 100
     * - "Mid-Tempo Flow" otherwise.
     */
    fun classifyVibe(rmsEnergy: Float, bpm: Int): String {
        return when {
            rmsEnergy >= 0.65f || (bpm >= 125 && rmsEnergy >= 0.50f) -> "High Energy / Workout"
            rmsEnergy <= 0.25f -> "Focus / Ambient"
            rmsEnergy <= 0.35f && (bpm <= 100 || bpm == 0) -> "Chill / Relaxed"
            else -> "Mid-Tempo Flow"
        }
    }
}
