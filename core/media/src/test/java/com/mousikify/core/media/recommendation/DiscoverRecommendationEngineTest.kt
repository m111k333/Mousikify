package com.mousikify.core.media.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverRecommendationEngineTest {

    @Test
    fun normalizeArtistTokens_singleArtist_returnsSelf() {
        val tokens = DiscoverRecommendationEngine.normalizeArtistTokens("Radiohead")
        assertEquals(setOf("radiohead"), tokens)
    }

    @Test
    fun normalizeArtistTokens_featuring_splitsCorrectly() {
        val tokens = DiscoverRecommendationEngine.normalizeArtistTokens("Dua Lipa feat. DaBaby")
        assertTrue(tokens.contains("dua lipa"))
        assertTrue(tokens.contains("dababy"))
        assertTrue(tokens.contains("dua lipa feat. dababy"))
    }

    @Test
    fun normalizeArtistTokens_commaAndAmpersand_splitsCorrectly() {
        val commaTokens = DiscoverRecommendationEngine.normalizeArtistTokens("Coldplay, BTS")
        assertTrue(commaTokens.contains("coldplay"))
        assertTrue(commaTokens.contains("bts"))

        val ampTokens = DiscoverRecommendationEngine.normalizeArtistTokens("Hall & Oates")
        assertTrue(ampTokens.contains("hall"))
        assertTrue(ampTokens.contains("oates"))
    }

    @Test
    fun normalizeArtistTokens_unknownOrYouTubeMusic_returnsEmpty() {
        assertTrue(DiscoverRecommendationEngine.normalizeArtistTokens("<unknown>").isEmpty())
        assertTrue(DiscoverRecommendationEngine.normalizeArtistTokens("YouTube Music").isEmpty())
        assertTrue(DiscoverRecommendationEngine.normalizeArtistTokens("   ").isEmpty())
    }

    @Test
    fun strictExclusion_detectsLibraryArtistAcrossCollaborations() {
        // User has "Coldplay" in their library
        val libraryTokens = DiscoverRecommendationEngine.normalizeArtistTokens("Coldplay")

        // Candidate artist that collaborates with Coldplay should be excluded
        val candidate1 = "Coldplay, BTS"
        val candidateTokens1 = DiscoverRecommendationEngine.normalizeArtistTokens(candidate1)
        val isCandidate1Excluded = candidateTokens1.any { it in libraryTokens }
        assertTrue(isCandidate1Excluded)

        // An unknown artist "Keane" has no overlap and should NOT be excluded
        val candidate2 = "Keane"
        val candidateTokens2 = DiscoverRecommendationEngine.normalizeArtistTokens(candidate2)
        val isCandidate2Excluded = candidateTokens2.any { it in libraryTokens }
        assertFalse(isCandidate2Excluded)
    }

    @Test
    fun strictExclusion_excludesFeaturingArtistWhenLeadInLibrary() {
        // User has "Dua Lipa" in library
        val libraryTokens = DiscoverRecommendationEngine.normalizeArtistTokens("Dua Lipa")

        val trackArtist = "Dua Lipa feat. Angele"
        val trackTokens = DiscoverRecommendationEngine.normalizeArtistTokens(trackArtist)

        val isExcluded = trackTokens.any { it in libraryTokens }
        assertTrue(isExcluded)
    }
}
