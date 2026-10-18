package com.mousikify.core.media.dsp

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log

class AudioEffectController {

    private val tag = "AudioEffectController"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null

    private var currentAudioSessionId: Int = 0
    private var isEqEnabled: Boolean = false
    private var bandLevels: List<Int> = List(10) { 0 }
    private var bassStrength: Int = 0
    private var virtualizerStrength: Int = 0
    private var reverbPreset: Short = PresetReverb.PRESET_NONE

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId == currentAudioSessionId && equalizer != null) return
        release()
        currentAudioSessionId = audioSessionId
        if (audioSessionId <= 0) return

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEqEnabled
                applyBandLevelsToEqualizer(this)
            }
        } catch (e: Exception) {
            Log.w(tag, "Hardware Equalizer unsupported on session $audioSessionId: ${e.message}")
        }

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

    fun setEnabled(enabled: Boolean) {
        this.isEqEnabled = enabled
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            Log.w(tag, "Failed to toggle equalizer enabled: ${e.message}")
        }
    }

    fun setBandLevels(levels: List<Int>) {
        this.bandLevels = levels
        try {
            equalizer?.let { applyBandLevelsToEqualizer(it) }
        } catch (e: Exception) {
            Log.w(tag, "Failed to set band levels: ${e.message}")
        }
    }

    private fun applyBandLevelsToEqualizer(eq: Equalizer) {
        try {
            val numBands = eq.numberOfBands.toInt()
            val minRange = eq.bandLevelRange[0]
            val maxRange = eq.bandLevelRange[1]
            for (band in 0 until numBands) {
                val centerFreqHz = eq.getCenterFreq(band.toShort()) / 1000
                val closestIndex = EqualizerAudioProcessor.BAND_FREQUENCIES.indices.minByOrNull {
                    kotlin.math.abs(EqualizerAudioProcessor.BAND_FREQUENCIES[it] - centerFreqHz)
                } ?: 0
                val targetMb = bandLevels.getOrElse(closestIndex) { 0 }
                val clamped = targetMb.coerceIn(minRange.toInt(), maxRange.toInt()).toShort()
                eq.setBandLevel(band.toShort(), clamped)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to apply band levels to hardware equalizer: ${e.message}")
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
            equalizer?.release()
            equalizer = null
        } catch (_: Exception) {}

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
