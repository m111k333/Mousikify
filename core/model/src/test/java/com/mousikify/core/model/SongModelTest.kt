package com.mousikify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongModelTest {

    @Test
    fun cleanArtistName_handlesVariousUploaderAttributions() {
        assertEquals("The Weeknd", cleanArtistName("The Weeknd • via PopMusicCovers"))
        assertEquals("The Weeknd", cleanArtistName("The Weeknd • via idols"))
        assertEquals("The Weeknd", cleanArtistName("The Weeknd • Via Idols"))
        assertEquals("idols", cleanArtistName("via idols"))
        assertEquals("idols", cleanArtistName("Via idols"))
        assertEquals("Taylor Swift", cleanArtistName("Taylor Swift • via FanUploads"))
        assertEquals("Coldplay", cleanArtistName("Coldplay - Topic"))
        assertEquals("Radiohead", cleanArtistName("Radiohead"))
    }

    @Test
    fun songCleanArtistName_propertyReturnsCleanedName() {
        val song = Song(
            id = 1L,
            contentUri = "ytm://test",
            title = "Starboy",
            artistId = 1L,
            artistName = "The Weeknd • via idols",
            albumId = 1L,
            albumName = "Starboy",
            durationMs = 210000L,
            trackNumber = 1,
            year = 2016,
            folderPath = "remote",
            dateAdded = 1000L,
            dateModified = 1000L,
            favoritedAt = 1700000000000L
        )

        assertEquals("The Weeknd", song.cleanArtistName)
        assertEquals(1700000000000L, song.favoritedAt)
    }

    @Test
    fun song_favoritedAtDefaultsToNull() {
        val song = Song(
            id = 2L,
            contentUri = "content://media/1",
            title = "Track",
            artistId = 1L,
            artistName = "Artist",
            albumId = 1L,
            albumName = "Album",
            durationMs = 120000L,
            trackNumber = 1,
            year = 2020,
            folderPath = "/storage",
            dateAdded = 1000L,
            dateModified = 1000L
        )

        assertNull(song.favoritedAt)
    }

    @Test
    fun playbackContext_searchTypeFormatsCorrectly() {
        val context = PlaybackContext(
            title = "Search",
            type = PlaybackContextType.SEARCH
        )
        assertEquals(PlaybackContextType.SEARCH, context.type)
        assertEquals("Search", context.title)
    }

    @Test
    fun recentlyPlayedItem_trackDisplaysSongTitleAndArtist() {
        val song = Song(
            id = -999L,
            contentUri = "ytm://stream123",
            title = "Blinding Lights",
            artistId = 1L,
            artistName = "The Weeknd",
            albumId = 0L,
            albumName = "YouTube Music",
            durationMs = 200000L,
            trackNumber = 0,
            year = 2020,
            folderPath = "YouTube Music Stream",
            dateAdded = 0L,
            dateModified = 0L
        )

        val item = RecentlyPlayedItem(
            type = RecentlyPlayedType.TRACK,
            referenceId = "track_${song.contentUri}",
            title = song.title,
            subtitle = "Song • ${song.artistName}",
            artworkUri = song.artworkUri,
            playedAt = System.currentTimeMillis(),
            song = song
        )

        assertEquals("Blinding Lights", item.title)
        assertEquals("Song • The Weeknd", item.subtitle)
        assertEquals(RecentlyPlayedType.TRACK, item.type)
    }
}

