package com.mousikify.core.scanner.lyrics

import android.content.Context
import android.net.Uri
import com.mousikify.core.model.LyricLine
import com.mousikify.core.model.Song
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * Lightweight, 100% offline lyrics extractor.
 *
 * Extraction priority:
 * 1. Local cached `.lrc` file in `context.filesDir/lyrics/song_<id>.lrc` (or `song.lrcPath`)
 * 2. Sidecar `.lrc` / `.txt` file in the same directory as the audio file
 * 3. JAudioTagger tag extraction on file path or content stream
 */
object OfflineLyricsExtractor {

    private const val MAX_LYRICS_FRAME_BYTES = 128 * 1024 // 128 KB cap for a single lyrics frame

    fun getCachedLyricsFile(context: Context, songId: Long): File {
        val dir = File(context.applicationContext.filesDir, "lyrics")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "song_${songId}.lrc")
    }

    fun saveCustomLyrics(context: Context, songId: Long, rawLyrics: String): File? {
        val trimmed = rawLyrics.trim()
        val targetFile = getCachedLyricsFile(context, songId)
        if (trimmed.isEmpty()) {
            if (targetFile.exists()) {
                try { targetFile.delete() } catch (_: Exception) {}
            }
            return null
        }
        return try {
            targetFile.writeText(trimmed, Charsets.UTF_8)
            targetFile
        } catch (_: Exception) {
            null
        }
    }

    fun readRawLyrics(context: Context, song: Song): String? {
        val appContext = context.applicationContext

        // 1. Check song.lrcPath if already populated
        if (!song.lrcPath.isNullOrBlank()) {
            val lrcFile = File(song.lrcPath!!)
            if (lrcFile.exists() && lrcFile.canRead() && lrcFile.length() in 1..MAX_LYRICS_FRAME_BYTES) {
                try {
                    val text = lrcFile.readText(Charsets.UTF_8).trim()
                    if (text.isNotEmpty()) return text
                } catch (_: Exception) {}
            }
        }

        // 2. Check cached lyrics file in filesDir/lyrics/song_<id>.lrc
        val cachedFile = getCachedLyricsFile(appContext, song.id)
        if (cachedFile.exists() && cachedFile.canRead() && cachedFile.length() in 1..MAX_LYRICS_FRAME_BYTES) {
            try {
                val text = cachedFile.readText(Charsets.UTF_8).trim()
                if (text.isNotEmpty()) return text
            } catch (_: Exception) {}
        }

        // 3. Extract from sidecar or embedded audio tags (only for local audio files)
        if (song.isRemoteStream) return null

        val extracted = extractEmbeddedOrSidecar(
            context = appContext,
            songId = song.id,
            contentUri = Uri.parse(song.contentUri),
            folderPath = song.folderPath,
            title = song.title
        )
        if (!extracted.isNullOrBlank()) {
            try {
                cachedFile.writeText(extracted, Charsets.UTF_8)
            } catch (_: Exception) {}
            return extracted
        }

        return null
    }

    fun extractAndParse(context: Context, song: Song): Pair<List<LyricLine>, String?> {
        val appContext = context.applicationContext
        val raw = readRawLyrics(appContext, song) ?: return Pair(emptyList(), null)
        val parsed = LrcParser.parse(raw, song.durationMs)
        val cachedPath = getCachedLyricsFile(appContext, song.id).takeIf { it.exists() }?.absolutePath ?: song.lrcPath
        return Pair(parsed, cachedPath)
    }

    fun cacheEmbeddedLyricsToFile(context: Context, songId: Long, rawLyrics: String): String? {
        val trimmed = rawLyrics.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val file = getCachedLyricsFile(context.applicationContext, songId)
            file.writeText(trimmed, Charsets.UTF_8)
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun extractEmbeddedOrSidecar(
        context: Context,
        songId: Long,
        contentUri: Uri,
        folderPath: String?,
        title: String
    ): String? {
        // A. Check sidecar .lrc in folderPath if accessible
        if (!folderPath.isNullOrBlank()) {
            val folder = File(folderPath)
            if (folder.exists() && folder.isDirectory && folder.canRead()) {
                val candidates = folder.listFiles { f ->
                    val ext = f.extension.lowercase()
                    (ext == "lrc" || ext == "txt") &&
                        f.nameWithoutExtension.equals(title, ignoreCase = true)
                }
                val match = candidates?.firstOrNull()
                if (match != null && match.canRead() && match.length() in 1..MAX_LYRICS_FRAME_BYTES) {
                    try {
                        val content = match.readText(Charsets.UTF_8).trim()
                        if (content.isNotEmpty()) return content
                    } catch (_: Exception) {}
                }
            }
        }

        // B. JAudioTagger extraction if file is accessible directly
        val filePath = if (contentUri.scheme == "file") contentUri.path else null
        if (!filePath.isNullOrBlank()) {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                try {
                    val audioFile = AudioFileIO.read(file)
                    val lyrics = audioFile.tag?.getFirst(FieldKey.LYRICS)
                    if (!lyrics.isNullOrBlank()) {
                        return lyrics.trim()
                    }
                } catch (_: Exception) {}
            }
        }

        return null
    }
}
