package com.mousikify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongCountFormatterTest {

    private fun createSong(
        isRemote: Boolean,
        viewCount: Long? = null,
        playCount: Int = 0
    ): Song {
        return Song(
            id = 1L,
            contentUri = if (isRemote) "ytm://abc12345" else "content://media/external/audio/media/1",
            title = "Test Song",
            artistId = 1L,
            artistName = "Test Artist",
            albumId = 1L,
            albumName = "Test Album",
            durationMs = 180000L,
            trackNumber = 1,
            year = 2024,
            folderPath = "/music",
            dateAdded = 1000L,
            dateModified = 1000L,
            playCount = playCount,
            viewCount = viewCount
        )
    }

    @Test
    fun remoteSong_formatsViewCountsCorrectly() {
        assertEquals("1.5B views", createSong(isRemote = true, viewCount = 1_500_000_000L).displayCount)
        assertEquals("1B views", createSong(isRemote = true, viewCount = 1_000_000_000L).displayCount)
        assertEquals("24.5M views", createSong(isRemote = true, viewCount = 24_500_000L).displayCount)
        assertEquals("2M views", createSong(isRemote = true, viewCount = 2_000_000L).displayCount)
        assertEquals("125K views", createSong(isRemote = true, viewCount = 125_000L).displayCount)
        assertEquals("950 views", createSong(isRemote = true, viewCount = 950L).displayCount)
    }

    @Test
    fun remoteSong_zeroOrNullViewCount_returnsNull() {
        assertNull(createSong(isRemote = true, viewCount = null).displayCount)
        assertNull(createSong(isRemote = true, viewCount = 0L).displayCount)
        assertNull(createSong(isRemote = true, viewCount = -1L).displayCount)
    }

    @Test
    fun localSong_alwaysReturnsNullForDisplayCount() {
        assertNull(createSong(isRemote = false, playCount = 0).displayCount)
        assertNull(createSong(isRemote = false, playCount = 1).displayCount)
        assertNull(createSong(isRemote = false, playCount = 15).displayCount)
        assertNull(createSong(isRemote = false, viewCount = 100_000L).displayCount)
    }
}
