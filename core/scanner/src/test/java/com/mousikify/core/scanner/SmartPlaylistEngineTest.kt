package com.mousikify.core.scanner

import com.mousikify.core.scanner.classifier.MetadataClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
