package com.mousikify.core.scanner.artwork

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import java.io.File

object ArtworkExtractor {

    private val SIDECAR_NAMES = listOf(
        "cover.jpg", "cover.jpeg", "cover.png",
        "folder.jpg", "folder.jpeg", "folder.png",
        "album.jpg", "album.jpeg", "album.png",
        "front.jpg", "front.png", "artwork.jpg"
    )

    fun extractArtwork(
        context: Context,
        songId: Long,
        albumId: Long,
        contentUri: Uri,
        filePath: String? = null
    ): String? {
        val artDir = File(context.filesDir, "album_art")
        if (!artDir.exists()) {
            artDir.mkdirs()
        }

        // 1. If we already saved artwork for this song, return it
        val songArtFile = File(artDir, "song_$songId.jpg")
        if (songArtFile.exists() && songArtFile.length() > 0) {
            return songArtFile.absolutePath
        }

        // 2. If we already saved artwork for this album, return it
        if (albumId != 0L) {
            val albumArtFile = File(artDir, "album_$albumId.jpg")
            if (albumArtFile.exists() && albumArtFile.length() > 0) {
                return albumArtFile.absolutePath
            }
        }

        // 3. Extract embedded picture via MediaMetadataRetriever
        var pictureBytes: ByteArray? = null
        val retriever = MediaMetadataRetriever()
        try {
            if (!filePath.isNullOrBlank() && File(filePath).exists()) {
                retriever.setDataSource(filePath)
            } else {
                retriever.setDataSource(context, contentUri)
            }
            pictureBytes = retriever.embeddedPicture
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // 4. Try JAudioTagger as secondary fallback if local file exists
        if (pictureBytes == null && !filePath.isNullOrBlank()) {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                try {
                    val audioFile = AudioFileIO.read(file)
                    val tag = audioFile.tag
                    val art = tag?.firstArtwork
                    if (art != null && art.binaryData != null && art.binaryData.isNotEmpty()) {
                        pictureBytes = art.binaryData
                    }
                } catch (_: Exception) {}
            }
        }

        // Save picture if found
        if (pictureBytes != null && pictureBytes.isNotEmpty()) {
            try {
                songArtFile.writeBytes(pictureBytes)
                if (albumId != 0L) {
                    val albumArtFile = File(artDir, "album_$albumId.jpg")
                    if (!albumArtFile.exists() || albumArtFile.length() == 0L) {
                        albumArtFile.writeBytes(pictureBytes)
                    }
                }
                return songArtFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 5. Look for sidecar images in the directory
        if (!filePath.isNullOrBlank()) {
            val parent = File(filePath).parentFile
            if (parent != null && parent.exists() && parent.isDirectory) {
                for (sidecarName in SIDECAR_NAMES) {
                    val candidate = File(parent, sidecarName)
                    if (candidate.exists() && candidate.length() > 0) {
                        return candidate.absolutePath
                    }
                }
            }
        }

        return null
    }
}
