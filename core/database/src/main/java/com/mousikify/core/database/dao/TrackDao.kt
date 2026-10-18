package com.mousikify.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.mousikify.core.database.entity.TrackEntity
import com.mousikify.core.model.PlaylistSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks WHERE decade = :decade ORDER BY title ASC")
    fun getDecadePlaylist(decade: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE normalizedGenre = :normalizedGenre ORDER BY title ASC")
    fun getGenrePlaylist(normalizedGenre: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE rmsEnergy >= 0.60 OR bpm >= 125 ORDER BY rmsEnergy DESC, bpm DESC")
    fun getWorkoutMix(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE rmsEnergy <= 0.35 AND isAnalyzed = 1 ORDER BY rmsEnergy ASC")
    fun getChillMix(): Flow<List<TrackEntity>>

    @Query("""
        SELECT 
            t.decade AS name,
            'DECADE' AS type,
            COUNT(*) AS trackCount,
            COALESCE(MAX(s.artwork_uri), MIN(t.uri)) AS coverUri
        FROM tracks t
        LEFT JOIN songs s ON t.uri = s.content_uri
        WHERE t.decade IS NOT NULL AND t.decade != ''
        GROUP BY t.decade
        HAVING COUNT(*) >= 5

        UNION ALL

        SELECT 
            (t.normalizedGenre || ' Mix') AS name,
            'GENRE' AS type,
            COUNT(*) AS trackCount,
            COALESCE(MAX(s.artwork_uri), MIN(t.uri)) AS coverUri
        FROM tracks t
        LEFT JOIN songs s ON t.uri = s.content_uri
        WHERE t.normalizedGenre IS NOT NULL AND t.normalizedGenre != '' AND t.normalizedGenre != 'Other'
        GROUP BY t.normalizedGenre
        HAVING COUNT(*) >= 5

        UNION ALL

        SELECT 
            t.vibeCategory AS name,
            'VIBE' AS type,
            COUNT(*) AS trackCount,
            COALESCE(MAX(s.artwork_uri), MIN(t.uri)) AS coverUri
        FROM tracks t
        LEFT JOIN songs s ON t.uri = s.content_uri
        WHERE t.vibeCategory IS NOT NULL AND t.vibeCategory != ''
        GROUP BY t.vibeCategory
        HAVING COUNT(*) >= 5

        ORDER BY trackCount DESC
    """)
    fun getDiscoveredAutoPlaylists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM tracks WHERE isAnalyzed = 0 ORDER BY id ASC LIMIT :limit")
    suspend fun getUnanalyzedTracks(limit: Int = 50): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun getTrackCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE isAnalyzed = 1")
    fun getAnalyzedTrackCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>): List<Long>

    @Upsert
    suspend fun upsertTrack(track: TrackEntity)

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE uri = :uri")
    suspend fun deleteTrackByUri(uri: String)

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()
}
