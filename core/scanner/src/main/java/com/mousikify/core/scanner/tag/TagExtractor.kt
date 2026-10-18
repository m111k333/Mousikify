package com.mousikify.core.scanner.tag

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

data class ExtendedMetadata(
    val replayGainDb: Float? = null,
    val lrcPath: String? = null,
    val embeddedLyrics: String? = null
)

object TagExtractor {

    fun extract(context: Context, filePath: String?, contentUri: Uri): ExtendedMetadata {
        var replayGain: Float? = null
        var lrcPath: String? = null
        var embeddedLyrics: String? = null

        // 1. Check for sidecar .lrc file in the same directory
        if (!filePath.isNullOrBlank()) {
            val file = File(filePath)
            val parent = file.parentFile
            if (parent != null && parent.exists()) {
                val baseName = file.nameWithoutExtension
                val lrcCandidate = File(parent, "$baseName.lrc")
                if (lrcCandidate.exists() && lrcCandidate.canRead()) {
                    lrcPath = lrcCandidate.absolutePath
                }
            }
        }

        // 2. Extract embedded tags via JAudioTagger if local file is accessible
        if (!filePath.isNullOrBlank()) {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                try {
                    val audioFile = AudioFileIO.read(file)
                    val tag = audioFile.tag
                    if (tag != null) {
                        // Check ReplayGain tag
                        val rgTrackGain = try {
                            tag.getFirst("REPLAYGAIN_TRACK_GAIN")
                        } catch (_: Exception) {
                            null
                        }
                        if (!rgTrackGain.isNullOrBlank()) {
                            replayGain = parseGainDb(rgTrackGain)
                        }

                        // Check embedded lyrics
                        val lyrics = tag.getFirst(FieldKey.LYRICS)
                        if (!lyrics.isNullOrBlank()) {
                            embeddedLyrics = lyrics
                        }
                    }
                } catch (_: Exception) {
                    // Fallback to MediaMetadataRetriever
                }
            }
        }

        // 3. Fallback to MediaMetadataRetriever if needed
        if (embeddedLyrics == null) {
            val retriever = MediaMetadataRetriever()
            try {
                if (!filePath.isNullOrBlank() && File(filePath).exists()) {
                    retriever.setDataSource(filePath)
                } else {
                    retriever.setDataSource(context, contentUri)
                }
                // MediaMetadataRetriever doesn't have a direct key for lyrics, but some devices report in metadata
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        return ExtendedMetadata(
            replayGainDb = replayGain,
            lrcPath = lrcPath,
            embeddedLyrics = embeddedLyrics
        )
    }

    private fun parseGainDb(gainString: String): Float? {
        return try {
            val cleaned = gainString.replace("dB", "").replace("db", "").trim()
            cleaned.toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
