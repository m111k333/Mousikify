package com.mousikify.core.media.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

@OptIn(UnstableApi::class)
class EqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        val BAND_FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    }

    private var isEnabled = true
    private val bandLevelsMb = IntArray(10) { 0 } // Millibels (-1500 to +1500)
    private var replayGainDb: Float = 0f
    private var preAmpGainLinear: Float = 1.0f

    // Biquad filter state per channel (Max 2 channels: left, right)
    private class BiquadCoeffs {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
        var a1 = 0.0; var a2 = 0.0
    }

    private class ChannelState {
        val x1 = DoubleArray(10)
        val x2 = DoubleArray(10)
        val y1 = DoubleArray(10)
        val y2 = DoubleArray(10)

        fun reset() {
            x1.fill(0.0)
            x2.fill(0.0)
            y1.fill(0.0)
            y2.fill(0.0)
        }
    }

    private val filters = Array(10) { BiquadCoeffs() }
    private val channelStates = arrayOf(ChannelState(), ChannelState())
    private var sampleRate = 44100

    private var bassBoostStrength: Int = 0 // 0 to 1000
    private var virtualizerStrength: Int = 0 // 0 to 1000

    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }

    fun setBassBoost(strength: Int) {
        this.bassBoostStrength = strength.coerceIn(0, 1000)
        recomputeFilters()
    }

    fun setVirtualizer(strength: Int) {
        this.virtualizerStrength = strength.coerceIn(0, 1000)
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        if (bandIndex in 0 until 10) {
            bandLevelsMb[bandIndex] = levelMb
            recomputeFilters()
        }
    }

    fun setAllBandLevels(levels: List<Int>) {
        for (i in 0 until minOf(10, levels.size)) {
            bandLevelsMb[i] = levels[i]
        }
        recomputeFilters()
    }

    fun setReplayGain(gainDb: Float?) {
        this.replayGainDb = gainDb ?: 0f
        this.preAmpGainLinear = 10.0.pow((replayGainDb / 20.0)).toFloat().coerceIn(0.1f, 3.0f)
    }

    private fun recomputeFilters() {
        val fs = sampleRate.toDouble()
        val q = 1.4142

        val bassExtraDb = (bassBoostStrength / 1000.0) * 12.0 // Up to +12dB low-end boost

        for (i in 0 until 10) {
            val f0 = BAND_FREQUENCIES[i].toDouble().coerceAtMost(fs * 0.45)
            val baseGainDb = bandLevelsMb[i] / 100.0 // Millibels to dB
            val boostDb = when (i) {
                0 -> bassExtraDb * 0.9  // 31Hz
                1 -> bassExtraDb * 1.0  // 62Hz
                2 -> bassExtraDb * 0.7  // 125Hz
                3 -> bassExtraDb * 0.3  // 250Hz
                else -> 0.0
            }
            val gainDb = (baseGainDb + boostDb).coerceIn(-20.0, 20.0)
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f0 / fs
            val alpha = sin(w0) / (2.0 * q)

            val a0 = 1.0 + alpha / a
            filters[i].b0 = (1.0 + alpha * a) / a0
            filters[i].b1 = (-2.0 * cos(w0)) / a0
            filters[i].b2 = (1.0 - alpha * a) / a0
            filters[i].a1 = (-2.0 * cos(w0)) / a0
            filters[i].a2 = (1.0 - alpha / a) / a0
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        recomputeFilters()
        channelStates.forEach { it.reset() }
        return inputAudioFormat
    }

    override fun onReset() {
        channelStates.forEach { it.reset() }
    }

    override fun onFlush() {
        channelStates.forEach { it.reset() }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val channelCount = inputAudioFormat.channelCount.coerceAtMost(2)
        val isStereo = channelCount == 2

        if (!isEnabled && preAmpGainLinear == 1.0f) {
            // Passthrough if EQ is bypassed and no ReplayGain
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (isStereo) {
            val widthFactor = if (isEnabled && virtualizerStrength > 0) {
                1.0 + (virtualizerStrength / 1000.0) * 1.5 // 1.0x to 2.5x spatial widening
            } else 1.0

            while (inputBuffer.remaining() >= 4) {
                val rawLeft = inputBuffer.short
                val rawRight = inputBuffer.short
                var sampleLeft = (rawLeft.toDouble() / 32768.0) * preAmpGainLinear
                var sampleRight = (rawRight.toDouble() / 32768.0) * preAmpGainLinear

                if (isEnabled) {
                    val stateL = channelStates[0]
                    for (band in 0 until 10) {
                        val filter = filters[band]
                        val out = filter.b0 * sampleLeft +
                                filter.b1 * stateL.x1[band] +
                                filter.b2 * stateL.x2[band] -
                                filter.a1 * stateL.y1[band] -
                                filter.a2 * stateL.y2[band]
                        stateL.x2[band] = stateL.x1[band]
                        stateL.x1[band] = sampleLeft
                        stateL.y2[band] = stateL.y1[band]
                        stateL.y1[band] = out
                        sampleLeft = out
                    }

                    val stateR = channelStates[1]
                    for (band in 0 until 10) {
                        val filter = filters[band]
                        val out = filter.b0 * sampleRight +
                                filter.b1 * stateR.x1[band] +
                                filter.b2 * stateR.x2[band] -
                                filter.a1 * stateR.y1[band] -
                                filter.a2 * stateR.y2[band]
                        stateR.x2[band] = stateR.x1[band]
                        stateR.x1[band] = sampleRight
                        stateR.y2[band] = stateR.y1[band]
                        stateR.y1[band] = out
                        sampleRight = out
                    }

                    if (widthFactor > 1.0) {
                        val mid = (sampleLeft + sampleRight) * 0.5
                        val side = (sampleLeft - sampleRight) * 0.5
                        sampleLeft = mid + side * widthFactor
                        sampleRight = mid - side * widthFactor
                    }
                }

                val limitedL = if (sampleLeft > 1.0 || sampleLeft < -1.0) tanh(sampleLeft) else sampleLeft
                val limitedR = if (sampleRight > 1.0 || sampleRight < -1.0) tanh(sampleRight) else sampleRight

                outputBuffer.putShort((limitedL * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
                outputBuffer.putShort((limitedR * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
            }
        } else {
            while (inputBuffer.hasRemaining()) {
                val rawSample = inputBuffer.short
                var sample = (rawSample.toDouble() / 32768.0) * preAmpGainLinear

                if (isEnabled) {
                    val state = channelStates[0]
                    for (band in 0 until 10) {
                        val filter = filters[band]
                        val out = filter.b0 * sample +
                                filter.b1 * state.x1[band] +
                                filter.b2 * state.x2[band] -
                                filter.a1 * state.y1[band] -
                                filter.a2 * state.y2[band]
                        state.x2[band] = state.x1[band]
                        state.x1[band] = sample
                        state.y2[band] = state.y1[band]
                        state.y1[band] = out
                        sample = out
                    }
                }

                val limited = if (sample > 1.0 || sample < -1.0) tanh(sample) else sample
                outputBuffer.putShort((limited * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
            }
        }

        outputBuffer.flip()
    }
}
