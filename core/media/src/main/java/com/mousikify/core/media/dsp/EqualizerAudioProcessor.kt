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

    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
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

        for (i in 0 until 10) {
            val f0 = BAND_FREQUENCIES[i].toDouble().coerceAtMost(fs * 0.45)
            val gainDb = bandLevelsMb[i] / 100.0 // Millibels to dB
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

        while (inputBuffer.hasRemaining()) {
            for (ch in 0 until channelCount) {
                if (!inputBuffer.hasRemaining()) break
                val rawSample = inputBuffer.short
                var sample = (rawSample.toDouble() / 32768.0) * preAmpGainLinear

                if (isEnabled) {
                    val state = channelStates[ch]
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

                // Soft-knee limiter / saturation to avoid harsh digital clipping
                val limited = if (sample > 1.0 || sample < -1.0) {
                    tanh(sample)
                } else {
                    sample
                }

                val outShort = (limited * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                outputBuffer.putShort(outShort)
            }
        }

        outputBuffer.flip()
    }
}
