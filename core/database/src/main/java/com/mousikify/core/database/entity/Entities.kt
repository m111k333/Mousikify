package com.mousikify.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mousikify.core.model.Song

@Entity(
    tableName = "songs",
    indices = [
        Index("album_id"),
        Index("artist_id"),
        Index(value = ["content_uri"], unique = true)
    ]
)
data class SongEntity(
    @PrimaryKey val id: Long, // Matches MediaStore.Audio.Media._ID or generated hash for SAF files
    @ColumnInfo(name = "content_uri") val contentUri: String,
    val title: String,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "album_name") val albumName: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "lrc_path") val lrcPath: String? = null,
    @ColumnInfo(name = "replay_gain_db") val replayGainDb: Float? = null
) {
    fun toDomain(): Song = Song(
        id = id,
        contentUri = contentUri,
        title = title,
        artistId = artistId,
        artistName = artistName,
        albumId = albumId,
        albumName = albumName,
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = year,
        folderPath = folderPath,
        dateAdded = dateAdded,
        dateModified = dateModified,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        isFavorite = isFavorite,
        lrcPath = lrcPath,
        replayGainDb = replayGainDb
    )
}

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    contentUri = contentUri,
    title = title,
    artistId = artistId,
    artistName = artistName,
    albumId = albumId,
    albumName = albumName,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    folderPath = folderPath,
    dateAdded = dateAdded,
    dateModified = dateModified,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
    isFavorite = isFavorite,
    lrcPath = lrcPath,
    replayGainDb = replayGainDb
)

// Virtual FTS4 table for sub-10ms instant fuzzy search across Title, Artist, and Album
@Fts4(contentEntity = SongEntity::class)
@Entity(tableName = "songs_fts")
data class SongFtsEntity(
    val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "album_name") val albumName: String
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val createdAt: Long,
    val customCoverUri: String? = null
)

// Junction table supporting drag-and-drop custom ordering via 'position'
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("songId")]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAt: Long
)

@Entity(tableName = "eq_presets")
data class EqPresetEntity(
    @PrimaryKey(autoGenerate = true) val presetId: Long = 0,
    val name: String,
    val bandLevelsMb: List<Int>, // Stored via Room TypeConverter (millibels per frequency band)
    val bassBoostStrength: Int,  // 0 to 1000
    val virtualizerStrength: Int, // 0 to 1000
    val reverbPreset: Short,
    val isCustom: Boolean
)
