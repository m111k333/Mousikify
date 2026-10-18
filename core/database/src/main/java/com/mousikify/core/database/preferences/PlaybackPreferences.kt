package com.mousikify.core.database.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mousikify.core.model.RepeatMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_preferences")

@Singleton
class PlaybackPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val LAST_PLAYED_SONG_ID = longPreferencesKey("last_played_song_id")
        val LAST_PLAYED_POSITION_MS = longPreferencesKey("last_played_position_ms")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val CURRENT_QUEUE_IDS = stringPreferencesKey("current_queue_ids")
        val CURRENT_QUEUE_INDEX = intPreferencesKey("current_queue_index")
        val CROSSFADE_DURATION_MS = intPreferencesKey("crossfade_duration_ms")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val ACTIVE_EQ_PRESET_ID = longPreferencesKey("active_eq_preset_id")
        val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val REVERB_PRESET = intPreferencesKey("reverb_preset")
        val EQ_BAND_LEVELS = stringPreferencesKey("eq_band_levels")
        val CUSTOM_FOLDER_URIS = stringSetPreferencesKey("custom_folder_uris")
    }

    val customFolderUris: Flow<Set<String>> = dataStore.data.map { it[PreferencesKeys.CUSTOM_FOLDER_URIS] ?: emptySet() }

    val lastPlayedSongId: Flow<Long?> = dataStore.data.map { it[PreferencesKeys.LAST_PLAYED_SONG_ID] }
    val lastPlayedPositionMs: Flow<Long> = dataStore.data.map { it[PreferencesKeys.LAST_PLAYED_POSITION_MS] ?: 0L }
    val repeatMode: Flow<RepeatMode> = dataStore.data.map {
        val name = it[PreferencesKeys.REPEAT_MODE] ?: RepeatMode.OFF.name
        try { RepeatMode.valueOf(name) } catch (e: Exception) { RepeatMode.OFF }
    }
    val shuffleEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.SHUFFLE_ENABLED] ?: false }
    val currentQueueIds: Flow<List<Long>> = dataStore.data.map { prefs ->
        val str = prefs[PreferencesKeys.CURRENT_QUEUE_IDS] ?: ""
        if (str.isEmpty()) emptyList()
        else str.split(",").mapNotNull { it.trim().toLongOrNull() }
    }
    val currentQueueIndex: Flow<Int> = dataStore.data.map { it[PreferencesKeys.CURRENT_QUEUE_INDEX] ?: 0 }
    val crossfadeDurationMs: Flow<Int> = dataStore.data.map { it[PreferencesKeys.CROSSFADE_DURATION_MS] ?: 3000 }
    val eqEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.EQ_ENABLED] ?: false }
    val activeEqPresetId: Flow<Long> = dataStore.data.map { it[PreferencesKeys.ACTIVE_EQ_PRESET_ID] ?: 1L }
    val bassBoostStrength: Flow<Int> = dataStore.data.map { it[PreferencesKeys.BASS_BOOST_STRENGTH] ?: 0 }
    val virtualizerStrength: Flow<Int> = dataStore.data.map { it[PreferencesKeys.VIRTUALIZER_STRENGTH] ?: 0 }
    val reverbPreset: Flow<Short> = dataStore.data.map { (it[PreferencesKeys.REVERB_PRESET] ?: 0).toShort() }
    val eqBandLevels: Flow<List<Int>> = dataStore.data.map { prefs ->
        val str = prefs[PreferencesKeys.EQ_BAND_LEVELS] ?: ""
        if (str.isEmpty()) List(10) { 0 }
        else str.split(",").mapNotNull { it.trim().toIntOrNull() }.take(10).let {
            if (it.size < 10) it + List(10 - it.size) { 0 } else it
        }
    }

    suspend fun setEqBandLevels(levels: List<Int>) {
        dataStore.edit {
            it[PreferencesKeys.EQ_BAND_LEVELS] = levels.take(10).joinToString(",")
        }
    }

    suspend fun saveLastPlaybackState(songId: Long, positionMs: Long) {
        dataStore.edit {
            it[PreferencesKeys.LAST_PLAYED_SONG_ID] = songId
            it[PreferencesKeys.LAST_PLAYED_POSITION_MS] = positionMs
        }
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        dataStore.edit { it[PreferencesKeys.REPEAT_MODE] = mode.name }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHUFFLE_ENABLED] = enabled }
    }

    suspend fun saveQueue(songIds: List<Long>, currentIndex: Int) {
        dataStore.edit {
            it[PreferencesKeys.CURRENT_QUEUE_IDS] = songIds.joinToString(",")
            it[PreferencesKeys.CURRENT_QUEUE_INDEX] = currentIndex
        }
    }

    suspend fun setCrossfadeDurationMs(durationMs: Int) {
        dataStore.edit { it[PreferencesKeys.CROSSFADE_DURATION_MS] = durationMs }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.EQ_ENABLED] = enabled }
    }

    suspend fun setActiveEqPresetId(presetId: Long) {
        dataStore.edit { it[PreferencesKeys.ACTIVE_EQ_PRESET_ID] = presetId }
    }

    suspend fun setBassBoostStrength(strength: Int) {
        dataStore.edit { it[PreferencesKeys.BASS_BOOST_STRENGTH] = strength }
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        dataStore.edit { it[PreferencesKeys.VIRTUALIZER_STRENGTH] = strength }
    }

    suspend fun setReverbPreset(preset: Short) {
        dataStore.edit { it[PreferencesKeys.REVERB_PRESET] = preset.toInt() }
    }

    suspend fun addCustomFolderUri(uriString: String) {
        dataStore.edit {
            val current = it[PreferencesKeys.CUSTOM_FOLDER_URIS] ?: emptySet()
            it[PreferencesKeys.CUSTOM_FOLDER_URIS] = current + uriString
        }
    }

    suspend fun removeCustomFolderUri(uriString: String) {
        dataStore.edit {
            val current = it[PreferencesKeys.CUSTOM_FOLDER_URIS] ?: emptySet()
            it[PreferencesKeys.CUSTOM_FOLDER_URIS] = current - uriString
        }
    }
}
