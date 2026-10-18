package com.mousikify.core.scanner.indexer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mousikify.core.database.dao.PlaylistDao
import com.mousikify.core.database.dao.SongDao
import com.mousikify.core.database.dao.SongDedupRecord
import com.mousikify.core.database.entity.PlaylistEntity
import com.mousikify.core.database.entity.SongEntity
import com.mousikify.core.scanner.artwork.ArtworkExtractor
import com.mousikify.core.scanner.dedup.SongDeduplicator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SafScanResult(
    val folderName: String,
    val newCount: Int,
    val totalCount: Int
)

@Singleton
class SafFolderScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {
    private val audioMimeTypes = setOf(
        "audio/mpeg", "audio/mp3", "audio/flac", "audio/ogg",
        "audio/x-wav", "audio/wav", "audio/aac", "audio/m4a",
        "audio/mp4", "audio/x-m4a", "audio/opus"
    )

    suspend fun scanFolder(treeUri: Uri): SafScanResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext SafScanResult("Folder", 0, 0)

        val folderName = root.name?.ifBlank { "Imported Music" } ?: "Imported Music"

        // Load all existing songs' dedup signatures
        val existingRecords = songDao.getAllDedupRecords().toMutableList()

        val songsToUpsert = mutableListOf<SongEntity>()
        val allFolderSongIds = mutableListOf<Long>()
        var firstArtworkUri: String? = null

        traverseDirectory(
            dir = root,
            folderPath = folderName,
            existingRecords = existingRecords,
            songsToUpsert = songsToUpsert,
            allFolderSongIds = allFolderSongIds,
            onArtworkFound = { art ->
                if (firstArtworkUri == null) {
                    firstArtworkUri = art
                }
            }
        )

        // 1. Upsert new songs to library
        if (songsToUpsert.isNotEmpty()) {
            songDao.upsertSongs(songsToUpsert)
        }

        // 2. Automatically create or update playlist with the folder name
        if (allFolderSongIds.isNotEmpty()) {
            val existingPlaylist = playlistDao.getPlaylistByName(folderName)
            val playlistId = existingPlaylist?.playlistId ?: playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = folderName,
                    createdAt = System.currentTimeMillis(),
                    customCoverUri = firstArtworkUri
                )
            )

            // Add all songs from this folder to the playlist
            for (songId in allFolderSongIds) {
                if (playlistDao.isSongInPlaylist(playlistId, songId) == 0) {
                    playlistDao.addSongToEndOfPlaylist(playlistId, songId)
                }
            }

            // Update cover if playlist didn't have one
            if (existingPlaylist != null && existingPlaylist.customCoverUri.isNullOrBlank() && !firstArtworkUri.isNullOrBlank()) {
                playlistDao.updatePlaylist(existingPlaylist.copy(customCoverUri = firstArtworkUri))
            }
        }

        SafScanResult(
            folderName = folderName,
            newCount = songsToUpsert.size,
            totalCount = allFolderSongIds.size
        )
    }

    private fun traverseDirectory(
        dir: DocumentFile,
        folderPath: String,
        existingRecords: MutableList<SongDedupRecord>,
        songsToUpsert: MutableList<SongEntity>,
        allFolderSongIds: MutableList<Long>,
        onArtworkFound: (String) -> Unit
    ) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                val subPath = "$folderPath/${file.name ?: "subfolder"}"
                traverseDirectory(file, subPath, existingRecords, songsToUpsert, allFolderSongIds, onArtworkFound)
            } else if (isAudioFile(file)) {
                val candidateResult = extractSongFromDocument(file, folderPath, existingRecords)
                if (candidateResult != null) {
                    when (candidateResult) {
                        is CandidateSong.NewSong -> {
                            songsToUpsert.add(candidateResult.entity)
                            allFolderSongIds.add(candidateResult.entity.id)
                            candidateResult.entity.artworkUri?.let { onArtworkFound(it) }

                            // Add to existing records in memory so intra-folder duplicates are also caught
                            existingRecords.add(
                                SongDedupRecord(
                                    id = candidateResult.entity.id,
                                    contentUri = candidateResult.entity.contentUri,
                                    title = candidateResult.entity.title,
                                    artistName = candidateResult.entity.artistName,
                                    durationMs = candidateResult.entity.durationMs,
                                    folderPath = candidateResult.entity.folderPath
                                )
                            )
                        }
                        is CandidateSong.DuplicateExisting -> {
                            // Track exists in library! Skip re-inserting, but include in folder's playlist
                            allFolderSongIds.add(candidateResult.existingId)
                        }
                    }
                }
            }
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val mime = file.type
        if (mime != null && audioMimeTypes.contains(mime.lowercase())) return true
        val name = file.name?.lowercase() ?: return false
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") ||
                name.endsWith(".ogg") || name.endsWith(".m4a") || name.endsWith(".opus") ||
                name.endsWith(".aac")
    }

    private fun extractSongFromDocument(
        file: DocumentFile,
        folderPath: String,
        existingRecords: List<SongDedupRecord>
    ): CandidateSong? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, file.uri)

            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val fileName = file.name?.substringBeforeLast('.') ?: "Track"
            val title = if (rawTitle.isNullOrBlank() || rawTitle.equals("unknown", true) || rawTitle.equals("<unknown>", true)) {
                fileName
            } else rawTitle.trim()

            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val artist = if (rawArtist.isNullOrBlank() || rawArtist.equals("unknown", true) || rawArtist.equals("<unknown>", true)) {
                "Unknown Artist"
            } else rawArtist.trim()

            val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val album = if (rawAlbum.isNullOrBlank() || rawAlbum.equals("unknown", true) || rawAlbum.equals("<unknown>", true)) {
                "Unknown Album"
            } else rawAlbum.trim()

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull() ?: 0
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0

            // Check deduplication
            val duplicate = SongDeduplicator.findDuplicate(
                candidateUri = file.uri.toString(),
                candidateTitle = title,
                candidateArtist = artist,
                candidateDurationMs = duration,
                candidateFilePath = file.name,
                existingRecords = existingRecords
            )

            if (duplicate != null) {
                return CandidateSong.DuplicateExisting(duplicate.id)
            }

            // Generate stable unique 64-bit ID from URI
            val id = (file.uri.toString().hashCode().toLong() and 0x7FFFFFFF) or 0x4000000000000000L
            val dateModified = file.lastModified()
            val artistId = artist.hashCode().toLong()
            val albumId = album.hashCode().toLong()

            // Extract embedded artwork or sidecar
            val artworkUri = ArtworkExtractor.extractArtwork(
                context = context,
                songId = id,
                albumId = albumId,
                contentUri = file.uri
            )

            val entity = SongEntity(
                id = id,
                contentUri = file.uri.toString(),
                title = title,
                artistId = artistId,
                artistName = artist,
                albumId = albumId,
                albumName = album,
                durationMs = duration,
                trackNumber = track,
                year = year,
                folderPath = folderPath,
                dateAdded = System.currentTimeMillis(),
                dateModified = dateModified,
                lrcPath = null,
                replayGainDb = null,
                artworkUri = artworkUri
            )

            CandidateSong.NewSong(entity)
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private sealed interface CandidateSong {
        data class NewSong(val entity: SongEntity) : CandidateSong
        data class DuplicateExisting(val existingId: Long) : CandidateSong
    }
}
