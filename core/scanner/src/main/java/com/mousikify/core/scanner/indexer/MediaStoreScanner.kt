package com.mousikify.core.scanner.indexer

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mousikify.core.database.dao.SongDao
import com.mousikify.core.database.dao.SongDedupRecord
import com.mousikify.core.database.entity.SongEntity
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.scanner.artwork.ArtworkExtractor
import com.mousikify.core.scanner.dedup.SongDeduplicator
import com.mousikify.core.scanner.tag.TagExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val preferences: PlaybackPreferences,
    private val safFolderScanner: SafFolderScanner
) {

    suspend fun scanMediaStore(): ScanResult = withContext(Dispatchers.IO) {
        try {
            songDao.deleteNonFavoriteRemoteSongs()
        } catch (_: Exception) {}
        val cachedMap = songDao.getAllMediaIdsAndModifiedDates().associate { it.id to it.dateModified }
        val existingRecords = songDao.getAllDedupRecords().toMutableList()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        // Comprehensive audio selection: includes downloads, sidify, telegram, browser tracks
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' OR ${MediaStore.Audio.Media.DATA} LIKE '%.mp3' OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac' OR ${MediaStore.Audio.Media.DATA} LIKE '%.wav' OR ${MediaStore.Audio.Media.DATA} LIKE '%.ogg' OR ${MediaStore.Audio.Media.DATA} LIKE '%.opus' OR ${MediaStore.Audio.Media.DATA} LIKE '%.aac') AND (${MediaStore.Audio.Media.DURATION} >= 1000 OR ${MediaStore.Audio.Media.DURATION} IS NULL)"
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        val activeMediaIds = mutableSetOf<Long>()
        val songsToUpsert = mutableListOf<SongEntity>()

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val id = cursor.getLong(idColumn)
                    activeMediaIds.add(id)

                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val cachedDateModified = cachedMap[id]

                    // Only process if it's new or date_modified changed
                    if (cachedDateModified == null || cachedDateModified != dateModified) {
                        val rawTitle = cursor.getString(titleColumn)
                        val filePath = cursor.getString(dataColumn)
                        val fileName = if (!filePath.isNullOrBlank()) File(filePath).nameWithoutExtension else "Track $id"
                        val title = if (rawTitle.isNullOrBlank() || rawTitle.equals("Unknown Title", ignoreCase = true) || rawTitle.equals("<unknown>", ignoreCase = true)) {
                            fileName
                        } else rawTitle.trim()

                        val rawArtist = cursor.getString(artistColumn)
                        val artist = if (rawArtist.isNullOrBlank() || rawArtist.equals("<unknown>", ignoreCase = true)) "Unknown Artist" else rawArtist.trim()
                        val artistId = cursor.getLong(artistIdColumn)

                        val rawAlbum = cursor.getString(albumColumn)
                        val album = if (rawAlbum.isNullOrBlank() || rawAlbum.equals("<unknown>", ignoreCase = true)) "Unknown Album" else rawAlbum.trim()
                        val albumId = cursor.getLong(albumIdColumn)

                        var duration = cursor.getLong(durationColumn)
                        val track = cursor.getInt(trackColumn)
                        val year = cursor.getInt(yearColumn)
                        val dateAdded = cursor.getLong(dateAddedColumn)

                        val contentUri = ContentUris.withAppendedId(collection, id).toString()
                        val folderPath = if (!filePath.isNullOrEmpty()) {
                            File(filePath).parent ?: ""
                        } else ""

                        // Fallback duration extraction if MediaStore hasn't computed duration
                        if (duration <= 0L) {
                            val retriever = MediaMetadataRetriever()
                            try {
                                if (!filePath.isNullOrBlank() && File(filePath).exists()) {
                                    retriever.setDataSource(filePath)
                                } else {
                                    retriever.setDataSource(context, Uri.parse(contentUri))
                                }
                                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            } catch (_: Exception) {
                            } finally {
                                try { retriever.release() } catch (_: Exception) {}
                            }
                        }

                        // Check deduplication
                        val duplicate = SongDeduplicator.findDuplicate(
                            candidateUri = contentUri,
                            candidateTitle = title,
                            candidateArtist = artist,
                            candidateDurationMs = duration,
                            candidateFilePath = filePath,
                            existingRecords = existingRecords.filter { it.id != id }
                        )

                        if (duplicate != null) {
                            // Already exists with a different ID (e.g. from SAF scan or previous index) -> skip
                            continue
                        }

                        val extended = TagExtractor.extract(context, filePath, Uri.parse(contentUri))
                        val resolvedLrcPath = extended.lrcPath ?: extended.embeddedLyrics?.let {
                            com.mousikify.core.scanner.lyrics.OfflineLyricsExtractor.cacheEmbeddedLyricsToFile(context, id, it)
                        }

                        // Extract embedded or sidecar artwork
                        val artworkUri = ArtworkExtractor.extractArtwork(
                            context = context,
                            songId = id,
                            albumId = albumId,
                            contentUri = Uri.parse(contentUri),
                            filePath = filePath
                        )

                        val entity = SongEntity(
                            id = id,
                            contentUri = contentUri,
                            title = title,
                            artistId = artistId,
                            artistName = artist,
                            albumId = albumId,
                            albumName = album,
                            durationMs = duration,
                            trackNumber = track,
                            year = year,
                            folderPath = folderPath,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            lrcPath = resolvedLrcPath,
                            replayGainDb = extended.replayGainDb,
                            artworkUri = artworkUri
                        )

                        songsToUpsert.add(entity)

                        existingRecords.add(
                            SongDedupRecord(
                                id = id,
                                contentUri = contentUri,
                                title = title,
                                artistName = artist,
                                durationMs = duration,
                                folderPath = folderPath
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }


        // Identify deleted tracks (excluding SAF tracks which have 0x4000000000000000L flag, and remote stream tracks which have id < 0)
        val staleIds = cachedMap.keys.filter { id ->
            id > 0L && (id and 0x4000000000000000L) == 0L && !activeMediaIds.contains(id)
        }

        if (songsToUpsert.isNotEmpty()) {
            songDao.upsertSongs(songsToUpsert)
        }
        if (staleIds.isNotEmpty()) {
            songDao.deleteSongsByIds(staleIds)
        }

        // Scan any persisted user-selected custom SAF folders
        try {
            val customFolders = preferences.customFolderUris.first()
            for (folderUriStr in customFolders) {
                try {
                    val folderUri = Uri.parse(folderUriStr)
                    safFolderScanner.scanFolder(folderUri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        ScanResult(
            addedOrUpdatedCount = songsToUpsert.size,
            deletedCount = staleIds.size,
            totalScanned = activeMediaIds.size
        )
    }
}

data class ScanResult(
    val addedOrUpdatedCount: Int,
    val deletedCount: Int,
    val totalScanned: Int
)
