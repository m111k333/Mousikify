package com.mousikify.core.database.repository

import com.mousikify.core.database.converter.Converters
import com.mousikify.core.database.dao.EqPresetDao
import com.mousikify.core.database.entity.EqPresetEntity
import com.mousikify.core.model.EqPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqRepository @Inject constructor(
    private val eqPresetDao: EqPresetDao
) {
    private val converters = Converters()

    val presets: Flow<List<EqPreset>> = eqPresetDao.getAllPresets().map { entities ->
        entities.map { entity ->
            EqPreset(
                presetId = entity.presetId,
                name = entity.name,
                bandLevelsMb = entity.bandLevelsMb,
                bassBoostStrength = entity.bassBoostStrength,
                virtualizerStrength = entity.virtualizerStrength,
                reverbPreset = entity.reverbPreset,
                isCustom = entity.isCustom
            )
        }
    }

    fun getPresetById(id: Long): Flow<EqPreset?> =
        eqPresetDao.getPresetById(id).map { entity ->
            entity?.let {
                EqPreset(
                    presetId = it.presetId,
                    name = it.name,
                    bandLevelsMb = it.bandLevelsMb,
                    bassBoostStrength = it.bassBoostStrength,
                    virtualizerStrength = it.virtualizerStrength,
                    reverbPreset = it.reverbPreset,
                    isCustom = it.isCustom
                )
            }
        }

    suspend fun saveCustomPreset(preset: EqPreset): Long {
        val entity = EqPresetEntity(
            presetId = preset.presetId,
            name = preset.name,
            bandLevelsMb = preset.bandLevelsMb,
            bassBoostStrength = preset.bassBoostStrength,
            virtualizerStrength = preset.virtualizerStrength,
            reverbPreset = preset.reverbPreset,
            isCustom = true
        )
        return eqPresetDao.insertPreset(entity)
    }

    suspend fun deleteCustomPreset(id: Long) {
        eqPresetDao.deleteCustomPreset(id)
    }
}
