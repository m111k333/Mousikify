package com.mousikify.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mousikify.core.database.entity.EqPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EqPresetDao {

    @Query("SELECT * FROM eq_presets ORDER BY isCustom ASC, name ASC")
    fun getAllPresets(): Flow<List<EqPresetEntity>>

    @Query("SELECT * FROM eq_presets WHERE presetId = :id")
    fun getPresetById(id: Long): Flow<EqPresetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: EqPresetEntity): Long

    @Query("DELETE FROM eq_presets WHERE presetId = :id AND isCustom = 1")
    suspend fun deleteCustomPreset(id: Long)

    @Query("SELECT COUNT(*) FROM eq_presets")
    suspend fun getPresetCount(): Int
}
