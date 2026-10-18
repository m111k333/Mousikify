package com.mousikify.core.media.dsp

import android.media.audiofx.BassBoost
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log

class AudioEffectController {

    private val tag = "AudioEffectController"

    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null

    private var currentAudioSessionId: Int = 0
    private var bassStrength: Int = 0
    private var virtualizerStrength: Int = 0
    private var reverbPreset: Short = PresetReverb.PRESET_NONE

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId == currentAudioSessionId && bassBoost != null) return
        release()
        currentAudioSessionId = audioSessionId
        if (audioSessionId <= 0) return

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(bassStrength.toShort())
                    enabled = bassStrength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "BassBoost unsupported or failed on session $audioSessionId: ${e.message}")
        }

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(virtualizerStrength.toShort())
                    enabled = virtualizerStrength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Virtualizer unsupported or failed on session $audioSessionId: ${e.message}")
        }

        try {
            presetReverb = PresetReverb(0, audioSessionId).apply {
                preset = reverbPreset
                enabled = reverbPreset != PresetReverb.PRESET_NONE
            }
        } catch (e: Exception) {
            Log.w(tag, "PresetReverb unsupported or failed on session $audioSessionId: ${e.message}")
        }
    }

    fun setBassBoost(strength: Int) {
        this.bassStrength = strength.coerceIn(0, 1000)
        try {
            bassBoost?.apply {
                if (strengthSupported) {
                    setStrength(bassStrength.toShort())
                    enabled = bassStrength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to set bass boost: ${e.message}")
        }
    }

    fun setVirtualizer(strength: Int) {
        this.virtualizerStrength = strength.coerceIn(0, 1000)
        try {
            virtualizer?.apply {
                if (strengthSupported) {
                    setStrength(virtualizerStrength.toShort())
                    enabled = virtualizerStrength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to set virtualizer: ${e.message}")
        }
    }

    fun setReverb(preset: Short) {
        this.reverbPreset = preset
        try {
            presetReverb?.apply {
                this.preset = preset
                enabled = preset != PresetReverb.PRESET_NONE
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to set reverb: ${e.message}")
        }
    }

    fun release() {
        try {
            bassBoost?.release()
            bassBoost = null
        } catch (_: Exception) {}

        try {
            virtualizer?.release()
            virtualizer = null
        } catch (_: Exception) {}

        try {
            presetReverb?.release()
            presetReverb = null
        } catch (_: Exception) {}
    }
}
