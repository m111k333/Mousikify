package com.mousikify.core.media.remote

import com.mousikify.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackAttributionParserTest {

    private fun createSong(
        title: String,
        artistName: String
    ): Song {
        return Song(
            id = 100L,
            contentUri = "ytm://testVid123",
            title = title,
            artistId = 1L,
            artistName = artistName,
            albumId = -1L,
            albumName = "YouTube Music",
            durationMs = 210000L,
            trackNumber = 0,
            year = 2024,
            folderPath = "YouTube Music Stream",
            dateAdded = 1000L,
            dateModified = 1000L,
            viewCount = 1_200_000L
        )
    }

    @Test
    fun parsePlaylistTrackAttribution_splitsArtistAndTitleWithUploader() {
        val input = createSong(
            title = "The Weeknd - Blinding Lights (Acoustic)",
            artistName = "PopMusicCovers"
        )
        val parsed = RemoteMusicRepository.parsePlaylistTrackAttribution(input)

        assertEquals("Blinding Lights (Acoustic)", parsed.title)
        assertEquals("The Weeknd • via PopMusicCovers", parsed.artistName)
    }

    @Test
    fun parsePlaylistTrackAttribution_handlesDifferentDashesAndColons() {
        val enDash = createSong(
            title = "Taylor Swift – Cruel Summer",
            artistName = "FanUploads"
        )
        val parsedEnDash = RemoteMusicRepository.parsePlaylistTrackAttribution(enDash)
        assertEquals("Cruel Summer", parsedEnDash.title)
        assertEquals("Taylor Swift • via FanUploads", parsedEnDash.artistName)

        val colon = createSong(
            title = "Dua Lipa: Levitating",
            artistName = "DanceMixes"
        )
        val parsedColon = RemoteMusicRepository.parsePlaylistTrackAttribution(colon)
        assertEquals("Levitating", parsedColon.title)
        assertEquals("Dua Lipa • via DanceMixes", parsedColon.artistName)
    }

    @Test
    fun parsePlaylistTrackAttribution_whenUploaderMatchesArtist_doesNotDuplicateVia() {
        val input = createSong(
            title = "Ed Sheeran - Shape of You",
            artistName = "Ed Sheeran"
        )
        val parsed = RemoteMusicRepository.parsePlaylistTrackAttribution(input)

        assertEquals("Shape of You", parsed.title)
        assertEquals("Ed Sheeran", parsed.artistName)
    }

    @Test
    fun parsePlaylistTrackAttribution_withoutDelimiter_appendsViaUploader() {
        val input = createSong(
            title = "Unreleased Studio Demo",
            artistName = "ArchiveChannels"
        )
        val parsed = RemoteMusicRepository.parsePlaylistTrackAttribution(input)

        assertEquals("Unreleased Studio Demo", parsed.title)
        assertEquals("via ArchiveChannels", parsed.artistName)
    }

    @Test
    fun extractSearchTargetTitle_extractsCorrectSubstrings() {
        assertEquals("Blinding Lights", RemoteMusicRepository.extractSearchTargetTitle("The Weeknd - Blinding Lights"))
        assertEquals("Cruel Summer", RemoteMusicRepository.extractSearchTargetTitle("Taylor Swift – Cruel Summer"))
        assertEquals("Levitating", RemoteMusicRepository.extractSearchTargetTitle("Dua Lipa: Levitating"))
        assertEquals("Single Track Query", RemoteMusicRepository.extractSearchTargetTitle("Single Track Query"))
    }
}
