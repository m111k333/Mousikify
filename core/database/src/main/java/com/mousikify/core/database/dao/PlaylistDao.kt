package com.mousikify.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mousikify.core.database.entity.PlaylistEntity
import com.mousikify.core.database.entity.PlaylistSongCrossRef
import com.mousikify.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE playlistId = :id")
    fun getPlaylistById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getPlaylistByName(name: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
    """)
    fun getSongsForPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(ref: PlaylistSongCrossRef)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongCountInPlaylist(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Int

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("UPDATE playlist_songs SET position = :newPosition WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateSongPosition(playlistId: Long, songId: Long, newPosition: Int)

    @Transaction
    suspend fun reorderPlaylist(playlistId: Long, orderedSongIds: List<Long>) {
        orderedSongIds.forEachIndexed { index, songId ->
            updateSongPosition(playlistId, songId, index)
        }
    }

    @Transaction
    suspend fun addSongToEndOfPlaylist(playlistId: Long, songId: Long) {
        val count = getSongCountInPlaylist(playlistId)
        insertCrossRef(
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                position = count,
                addedAt = System.currentTimeMillis()
            )
        )
    }
}
