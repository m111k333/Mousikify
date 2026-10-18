package com.mousikify.core.scanner.indexer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mousikify.core.database.dao.SongDao
import com.mousikify.core.database.entity.SongEntity
import com.mousikify.core.scanner.tag.TagExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao
) {

    suspend fun scanMediaStore(): ScanResult = withContext(Dispatchers.IO) {
        val cachedMap = songDao.getAllMediaIdsAndModifiedDates().associate { it.id to it.dateModified }

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

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        val activeMediaIds = mutableSetOf<Long>()
        val songsToUpsert = mutableListOf<SongEntity>()

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
                val id = cursor.getLong(idColumn)
                activeMediaIds.add(id)

                val dateModified = cursor.getLong(dateModifiedColumn)
                val cachedDateModified = cachedMap[id]

                // Only process if it's new or date_modified changed
                if (cachedDateModified == null || cachedDateModified != dateModified) {
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val artistId = cursor.getLong(artistIdColumn)
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdColumn)
                    val duration = cursor.getLong(durationColumn)
                    val track = cursor.getInt(trackColumn)
                    val year = cursor.getInt(yearColumn)
                    val filePath = cursor.getString(dataColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    val folderPath = if (!filePath.isNullOrEmpty()) {
                        File(filePath).parent ?: ""
                    } else ""

                    val extended = TagExtractor.extract(context, filePath, Uri.parse(contentUri))

                    songsToUpsert.add(
                        SongEntity(
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
                            lrcPath = extended.lrcPath,
                            replayGainDb = extended.replayGainDb
                        )
                    )
                }
            }
        }

        // Identify deleted tracks
        val staleIds = cachedMap.keys.filter { !activeMediaIds.contains(it) }

        if (songsToUpsert.isNotEmpty()) {
            songDao.upsertSongs(songsToUpsert)
        }
        if (staleIds.isNotEmpty()) {
            songDao.deleteSongsByIds(staleIds)
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
