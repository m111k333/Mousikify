package com.mousikify.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mousikify.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class MediaIdDate(
    val id: Long,
    val dateModified: Long
)

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY date_added DESC")
    fun getSongsByDateAdded(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun getSongById(id: Long): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongByIdSync(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE last_played_at IS NOT NULL ORDER BY last_played_at DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 30): Flow<List<SongEntity>>

    // Forgotten favorites: tracks with significant play count that haven't been played recently
    @Query("SELECT * FROM songs WHERE play_count >= 3 ORDER BY last_played_at ASC LIMIT :limit")
    fun getForgottenFavorites(limit: Int = 20): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE folder_path = :folderPath ORDER BY title ASC")
    fun getSongsByFolder(folderPath: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album_id = :albumId ORDER BY trackNumber ASC, title ASC")
    fun getSongsByAlbum(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist_id = :artistId ORDER BY album_name ASC, trackNumber ASC")
    fun getSongsByArtist(artistId: Long): Flow<List<SongEntity>>

    @Query("""
        SELECT s.* FROM songs s
        JOIN songs_fts f ON s.id = f.rowid
        WHERE songs_fts MATCH :searchQuery
        ORDER BY s.play_count DESC, s.title ASC
    """)
    fun searchSongsFts(searchQuery: String): Flow<List<SongEntity>>

    @Query("SELECT id, date_modified AS dateModified FROM songs")
    suspend fun getAllMediaIdsAndModifiedDates(): List<MediaIdDate>

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>)

    @Query("UPDATE songs SET is_favorite = :isFavorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET play_count = play_count + 1, last_played_at = :timestamp WHERE id = :songId")
    suspend fun recordPlay(songId: Long, timestamp: Long)

    @Query("SELECT DISTINCT folder_path FROM songs WHERE folder_path != '' ORDER BY folder_path ASC")
    fun getAllFolderPaths(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>
}
