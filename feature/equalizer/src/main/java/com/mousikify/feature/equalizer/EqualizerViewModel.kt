package com.mousikify.feature.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.EqRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.model.EqPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EqualizerUiState(
    val isEnabled: Boolean = true,
    val bandLevelsMb: List<Int> = List(10) { 0 },
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val reverbPreset: Short = 0,
    val crossfadeDurationSeconds: Float = 3f,
    val presets: List<EqPreset> = emptyList(),
    val selectedPresetId: Long = 1L
)

sealed interface EqualizerIntent {
    data class SetEnabled(val enabled: Boolean) : EqualizerIntent
    data class SetBandLevel(val bandIndex: Int, val levelMb: Int) : EqualizerIntent
    data class SetBassBoost(val strength: Int) : EqualizerIntent
    data class SetVirtualizer(val strength: Int) : EqualizerIntent
    data class SetReverb(val preset: Short) : EqualizerIntent
    data class SetCrossfadeDuration(val seconds: Float) : EqualizerIntent
    data class SelectPreset(val preset: EqPreset) : EqualizerIntent
    data class SaveCustomPreset(val name: String) : EqualizerIntent
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val eqRepository: EqRepository,
    private val preferences: PlaybackPreferences,
    private val musicController: MusicController
) : ViewModel() {

    private val _bandLevels = MutableStateFlow(List(10) { 0 })

    private val dspEffects = combine(
        preferences.bassBoostStrength,
        preferences.virtualizerStrength,
        preferences.reverbPreset,
        preferences.crossfadeDurationMs
    ) { bass, virt, reverb, crossfadeMs ->
        Triple(bass, virt, Pair(reverb, crossfadeMs))
    }

    private val presetState = combine(
        eqRepository.presets,
        preferences.activeEqPresetId
    ) { presets, activeId ->
        Pair(presets, activeId)
    }

    val uiState: StateFlow<EqualizerUiState> = combine(
        preferences.eqEnabled,
        _bandLevels,
        dspEffects,
        presetState
    ) { enabled, bands, (bass, virt, reverbAndCrossfade), (presets, activeId) ->
        EqualizerUiState(
            isEnabled = enabled,
            bandLevelsMb = bands,
            bassBoostStrength = bass,
            virtualizerStrength = virt,
            reverbPreset = reverbAndCrossfade.first,
            crossfadeDurationSeconds = reverbAndCrossfade.second / 1000f,
            presets = presets,
            selectedPresetId = activeId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EqualizerUiState()
    )

    init {
        viewModelScope.launch {
            val savedBands = preferences.eqBandLevels.first()
            if (savedBands.any { it != 0 }) {
                _bandLevels.value = savedBands
            } else {
                val presetId = preferences.activeEqPresetId.first()
                val preset = eqRepository.getPresetById(presetId).first()
                if (preset != null) {
                    _bandLevels.value = preset.bandLevelsMb
                    preferences.setEqBandLevels(preset.bandLevelsMb)
                }
            }
        }
    }

    fun onIntent(intent: EqualizerIntent) {
        when (intent) {
            is EqualizerIntent.SetEnabled -> {
                viewModelScope.launch {
                    preferences.setEqEnabled(intent.enabled)
                }
            }
            is EqualizerIntent.SetBandLevel -> {
                val list = _bandLevels.value.toMutableList()
                if (intent.bandIndex in list.indices) {
                    list[intent.bandIndex] = intent.levelMb
                    _bandLevels.value = list
                    viewModelScope.launch {
                        preferences.setEqBandLevels(list)
                    }
                }
            }
            is EqualizerIntent.SetBassBoost -> {
                viewModelScope.launch {
                    preferences.setBassBoostStrength(intent.strength)
                }
            }
            is EqualizerIntent.SetVirtualizer -> {
                viewModelScope.launch {
                    preferences.setVirtualizerStrength(intent.strength)
                }
            }
            is EqualizerIntent.SetReverb -> {
                viewModelScope.launch {
                    preferences.setReverbPreset(intent.preset)
                }
            }
            is EqualizerIntent.SetCrossfadeDuration -> {
                viewModelScope.launch {
                    preferences.setCrossfadeDurationMs((intent.seconds * 1000).toInt())
                }
            }
            is EqualizerIntent.SelectPreset -> {
                _bandLevels.value = intent.preset.bandLevelsMb
                viewModelScope.launch {
                    preferences.setEqBandLevels(intent.preset.bandLevelsMb)
                    preferences.setActiveEqPresetId(intent.preset.presetId)
                    preferences.setBassBoostStrength(intent.preset.bassBoostStrength)
                    preferences.setVirtualizerStrength(intent.preset.virtualizerStrength)
                    preferences.setReverbPreset(intent.preset.reverbPreset)
                }
            }
            is EqualizerIntent.SaveCustomPreset -> {
                viewModelScope.launch {
                    val newPreset = EqPreset(
                        name = intent.name,
                        bandLevelsMb = _bandLevels.value,
                        bassBoostStrength = uiState.value.bassBoostStrength,
                        virtualizerStrength = uiState.value.virtualizerStrength,
                        reverbPreset = uiState.value.reverbPreset,
                        isCustom = true
                    )
                    val id = eqRepository.saveCustomPreset(newPreset)
                    preferences.setActiveEqPresetId(id)
                }
            }
        }
    }
}
