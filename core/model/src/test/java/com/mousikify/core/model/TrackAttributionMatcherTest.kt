package com.mousikify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackAttributionMatcherTest {

    private fun dummySong(title: String, artist: String): Song {
        return Song(
            id = 1L,
            contentUri = "ytm://test1234",
            title = title,
            artistId = 1L,
            artistName = artist,
            albumId = 1L,
            albumName = "Album",
            durationMs = 200_000L,
            trackNumber = 1,
            year = 2024,
            folderPath = "Stream",
            dateAdded = 1000L,
            dateModified = 1000L
        )
    }

    @Test
    fun normalizeArtistTokens_splitsMultipleArtists() {
        val tokens = TrackAttributionMatcher.normalizeArtistTokens("The Weeknd feat. Daft Punk")
        assertTrue(tokens.contains("the weeknd"))
        assertTrue(tokens.contains("daft punk"))
    }

    @Test
    fun normalizeArtistTokens_handlesAmpersandsAndCommas() {
        val tokens = TrackAttributionMatcher.normalizeArtistTokens("Calvin Harris, Dua Lipa & Sam Smith")
        assertTrue(tokens.contains("calvin harris"))
        assertTrue(tokens.contains("dua lipa"))
        assertTrue(tokens.contains("sam smith"))
    }

    @Test
    fun normalizeArtistTokens_ignoresUnknownAndYouTubeMusic() {
        assertEquals(emptySet<String>(), TrackAttributionMatcher.normalizeArtistTokens("<unknown>"))
        assertEquals(emptySet<String>(), TrackAttributionMatcher.normalizeArtistTokens("YouTube Music"))
        assertEquals(emptySet<String>(), TrackAttributionMatcher.normalizeArtistTokens("   "))
    }

    @Test
    fun isSongMatchingArtist_exactAndCaseInsensitive() {
        val song = dummySong(title = "Starboy", artist = "The Weeknd")
        assertTrue(TrackAttributionMatcher.isSongMatchingArtist(song, "the weeknd"))
        assertTrue(TrackAttributionMatcher.isSongMatchingArtist(song, "THE WEEKND"))
    }

    @Test
    fun isSongMatchingArtist_stripsViaAndTopic() {
        val song = dummySong(title = "Save Your Tears", artist = "The Weeknd - Topic • via Vevo")
        assertTrue(TrackAttributionMatcher.isSongMatchingArtist(song, "The Weeknd"))
    }

    @Test
    fun isSongMatchingArtist_rejectsArtistNameAsTitleFalsePositive() {
        // e.g. Fall Out Boy singing "Phoenix" must NOT match targetArtist "Phoenix"
        val song = dummySong(title = "Phoenix", artist = "Fall Out Boy")
        assertFalse(TrackAttributionMatcher.isSongMatchingArtist(song, "Phoenix"))
    }

    @Test
    fun isSongMatchingArtist_matchesCollaborations() {
        val song = dummySong(title = "Cold Heart", artist = "Elton John, Dua Lipa (PNAU Remix)")
        assertTrue(TrackAttributionMatcher.isSongMatchingArtist(song, "Dua Lipa"))
        assertTrue(TrackAttributionMatcher.isSongMatchingArtist(song, "Elton John"))
    }
}
