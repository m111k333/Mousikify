package com.mousikify.core.scanner

import com.mousikify.core.scanner.classifier.MetadataClassifier
import com.mousikify.core.scanner.dsp.FastAudioAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistEngineTest {

    @Test
    fun testExtractDecade() {
        assertEquals("50s", MetadataClassifier.extractDecade(1955))
        assertEquals("60s", MetadataClassifier.extractDecade(1968))
        assertEquals("70s", MetadataClassifier.extractDecade(1975))
        assertEquals("80s", MetadataClassifier.extractDecade(1984))
        assertEquals("90s", MetadataClassifier.extractDecade(1999))
        assertEquals("00s", MetadataClassifier.extractDecade(2005))
        assertEquals("10s", MetadataClassifier.extractDecade(2018))
        assertEquals("20s", MetadataClassifier.extractDecade(2024))

        assertNull(MetadataClassifier.extractDecade(1945))
        assertNull(MetadataClassifier.extractDecade(0))
        assertNull(MetadataClassifier.extractDecade(-1))
    }

    @Test
    fun testNormalizeGenre() {
        assertEquals("Rock", MetadataClassifier.normalizeGenre("Alternative Rock"))
        assertEquals("Rock", MetadataClassifier.normalizeGenre("Heavy Metal"))
        assertEquals("Rock", MetadataClassifier.normalizeGenre("Punk"))
        assertEquals("Rock", MetadataClassifier.normalizeGenre("Grunge"))
        assertEquals("Rock", MetadataClassifier.normalizeGenre("(17)"))

        assertEquals("Hip-Hop", MetadataClassifier.normalizeGenre("Hip-Hop / Rap"))
        assertEquals("Hip-Hop", MetadataClassifier.normalizeGenre("Trap"))
        assertEquals("Hip-Hop", MetadataClassifier.normalizeGenre("Boom Bap"))

        assertEquals("Pop", MetadataClassifier.normalizeGenre("Synth Pop"))
        assertEquals("Pop", MetadataClassifier.normalizeGenre("Dance"))
        assertEquals("Pop", MetadataClassifier.normalizeGenre("Electropop"))

        assertEquals("Electronic", MetadataClassifier.normalizeGenre("House"))
        assertEquals("Electronic", MetadataClassifier.normalizeGenre("Techno"))
        assertEquals("Electronic", MetadataClassifier.normalizeGenre("EDM"))
        assertEquals("Electronic", MetadataClassifier.normalizeGenre("Dubstep"))

        assertEquals("R&B", MetadataClassifier.normalizeGenre("R&B"))
        assertEquals("R&B", MetadataClassifier.normalizeGenre("Soul"))
        assertEquals("R&B", MetadataClassifier.normalizeGenre("Neo-Soul"))
        assertEquals("R&B", MetadataClassifier.normalizeGenre("Funk"))

        assertEquals("Acoustic / Chill", MetadataClassifier.normalizeGenre("Ambient"))
        assertEquals("Acoustic / Chill", MetadataClassifier.normalizeGenre("Acoustic"))
        assertEquals("Acoustic / Chill", MetadataClassifier.normalizeGenre("Classical"))
        assertEquals("Acoustic / Chill", MetadataClassifier.normalizeGenre("Chillout"))

        assertEquals("Other", MetadataClassifier.normalizeGenre(null))
        assertEquals("Other", MetadataClassifier.normalizeGenre(""))
        assertEquals("Other", MetadataClassifier.normalizeGenre("Unknown Sound"))
    }

    @Test
    fun testCalculateRmsEnergy() {
        // 1. Empty array
        assertEquals(0.0f, FastAudioAnalyzer.calculateRmsEnergy(ShortArray(0)), 0.001f)

        // 2. Silence (zeros)
        val silence = ShortArray(44100) { 0 }
        assertEquals(0.0f, FastAudioAnalyzer.calculateRmsEnergy(silence), 0.001f)

        // 3. Max amplitude square wave
        val maxSquareWave = ShortArray(44100) { 32767 }
        val maxRms = FastAudioAnalyzer.calculateRmsEnergy(maxSquareWave)
        assertEquals(1.0f, maxRms, 0.001f)

        // 4. Typical mastered audio sample (~10000 amplitude)
        val midSignal = ShortArray(44100) { 10000 }
        val midRms = FastAudioAnalyzer.calculateRmsEnergy(midSignal)
        assertTrue("RMS should be between 0.0 and 1.0, was $midRms", midRms in 0.4f..0.8f)
    }

    @Test
    fun testClassifyVibe() {
        assertEquals("High Energy / Workout", FastAudioAnalyzer.classifyVibe(0.70f, 130))
        assertEquals("High Energy / Workout", FastAudioAnalyzer.classifyVibe(0.52f, 128))
        assertEquals("Focus / Ambient", FastAudioAnalyzer.classifyVibe(0.20f, 90))
        assertEquals("Chill / Relaxed", FastAudioAnalyzer.classifyVibe(0.32f, 85))
        assertEquals("Mid-Tempo Flow", FastAudioAnalyzer.classifyVibe(0.45f, 110))
    }

    @Test
    fun testApproximateBpmWithSyntheticBeat() {
        val sampleRate = 44100
        val channels = 2
        val durationSeconds = 30
        val totalSamples = sampleRate * channels * durationSeconds
        val pcm = ShortArray(totalSamples)

        // Generate synthetic rhythmic pulses at 120 BPM (every 500ms = 0.5s)
        val samplesPerBeat = (sampleRate * channels * 0.5).toInt()
        val pulseLength = (sampleRate * channels * 0.02).toInt() // 20ms click

        var beatPos = 0
        while (beatPos < totalSamples) {
            for (i in 0 until pulseLength) {
                if (beatPos + i < totalSamples) {
                    pcm[beatPos + i] = 30000 // loud beat click
                }
            }
            beatPos += samplesPerBeat
        }

        val estimatedBpm = FastAudioAnalyzer.approximateBpm(pcm, sampleRate, channels)
        // 120 BPM +/- 5% or octave
        assertTrue(
            "Estimated BPM should be close to 120, got $estimatedBpm",
            estimatedBpm in 115..125 || estimatedBpm in 58..62
        )
    }
}
