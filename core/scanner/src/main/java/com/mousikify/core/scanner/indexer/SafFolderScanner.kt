package com.mousikify.core.scanner.indexer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mousikify.core.database.dao.SongDao
import com.mousikify.core.database.entity.SongEntity
import com.mousikify.core.scanner.tag.TagExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafFolderScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao
) {
    private val audioMimeTypes = setOf(
        "audio/mpeg", "audio/mp3", "audio/flac", "audio/ogg",
        "audio/x-wav", "audio/wav", "audio/aac", "audio/m4a",
        "audio/mp4", "audio/x-m4a"
    )

    suspend fun scanFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
        val songsToUpsert = mutableListOf<SongEntity>()
        traverseDirectory(root, songsToUpsert)

        if (songsToUpsert.isNotEmpty()) {
            songDao.upsertSongs(songsToUpsert)
        }
        songsToUpsert.size
    }

    private fun traverseDirectory(dir: DocumentFile, songsToUpsert: MutableList<SongEntity>) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                traverseDirectory(file, songsToUpsert)
            } else if (isAudioFile(file)) {
                val song = extractSongFromDocument(file)
                if (song != null) {
                    songsToUpsert.add(song)
                }
            }
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val mime = file.type
        if (mime != null && audioMimeTypes.contains(mime.lowercase())) return true
        val name = file.name?.lowercase() ?: return false
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") ||
                name.endsWith(".ogg") || name.endsWith(".m4a") || name.endsWith(".opus")
    }

    private fun extractSongFromDocument(file: DocumentFile): SongEntity? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, file.uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.name?.substringBeforeLast('.') ?: "Unknown"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull() ?: 0
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0

            // Unique ID generated from URI hash code to prevent collisions
            val id = (file.uri.toString().hashCode().toLong() and 0x7FFFFFFF) or 0x4000000000000000L
            val dateModified = file.lastModified()

            SongEntity(
                id = id,
                contentUri = file.uri.toString(),
                title = title,
                artistId = artist.hashCode().toLong(),
                artistName = artist,
                albumId = album.hashCode().toLong(),
                albumName = album,
                durationMs = duration,
                trackNumber = track,
                year = year,
                folderPath = file.parentFile?.name ?: "SAF Folder",
                dateAdded = System.currentTimeMillis(),
                dateModified = dateModified,
                lrcPath = null,
                replayGainDb = null
            )
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
