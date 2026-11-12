package com.mousikify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkCodecTest {

    private fun sampleRemoteSong(
        videoId: String = "dQw4w9WgXcQ",
        title: String = "Never Gonna Give You Up",
        artist: String = "Rick Astley • via Vevo",
        album: String = "Whenever You Need Somebody",
        durationMs: Long = 213_000L
    ) = Song(
        id = videoId.toRemoteSongId(),
        contentUri = "ytm://$videoId",
        title = title,
        artistId = 1L,
        artistName = artist,
        albumId = 2L,
        albumName = album,
        durationMs = durationMs,
        trackNumber = 1,
        year = 1987,
        folderPath = "YouTube Music Stream",
        dateAdded = 1000L,
        dateModified = 1000L,
        artworkUri = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    )

    private fun sampleLocalSong(
        title: String = "Bohemian Rhapsody",
        artist: String = "Queen",
        album: String = "A Night at the Opera",
        durationMs: Long = 354_000L
    ) = Song(
        id = 42L,
        contentUri = "content://media/external/audio/media/42",
        title = title,
        artistId = 10L,
        artistName = artist,
        albumId = 20L,
        albumName = album,
        durationMs = durationMs,
        trackNumber = 11,
        year = 1975,
        folderPath = "/storage/emulated/0/Music",
        dateAdded = 1000L,
        dateModified = 1000L
    )

    @Test
    fun encodeAndDecodeRemoteTrack_preservesVideoIdAndMetadata() {
        val original = sampleRemoteSong()
        val url = DeepLinkCodec.encodeTrackUrl(original)
        assertTrue(url.startsWith(DeepLinkCodec.BASE_WEB_URL))

        val decoded = DeepLinkCodec.decode(url) as? DeepLinkPayload.Track
        assertNotNull(decoded)
        val song = decoded!!.song
        assertEquals("dQw4w9WgXcQ", song.remoteVideoId)
        assertEquals("Never Gonna Give You Up", song.title)
        assertEquals("Rick Astley", song.artistName)
        assertEquals("Whenever You Need Somebody", song.albumName)
        assertEquals(213_000L, song.durationMs)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", song.artworkUri)
    }

    @Test
    fun encodeAndDecodeLocalTrack_createsSearchFallbackStream() {
        val original = sampleLocalSong()
        val url = DeepLinkCodec.encodeTrackUrl(original)

        val decoded = DeepLinkCodec.decode(url) as? DeepLinkPayload.Track
        assertNotNull(decoded)
        val song = decoded!!.song
        assertTrue(song.contentUri.startsWith("ytm://search_"))
        assertEquals("Bohemian Rhapsody", song.title)
        assertEquals("Queen", song.artistName)
        assertEquals("A Night at the Opera", song.albumName)
        assertEquals(354_000L, song.durationMs)
    }

    @Test
    fun encodeAndDecodePlaylist_preservesMixedRemoteAndLocalTracks() {
        val songs = listOf(
            sampleRemoteSong(),
            sampleLocalSong()
        )
        val url = DeepLinkCodec.encodePlaylistUrl("Road Trip Mix 2026", songs)
        assertTrue(url.startsWith("${DeepLinkCodec.BASE_WEB_URL}?p="))

        val decoded = DeepLinkCodec.decode(url) as? DeepLinkPayload.Playlist
        assertNotNull(decoded)
        assertEquals("Road Trip Mix 2026", decoded!!.name)
        assertEquals(2, decoded.songs.size)

        val first = decoded.songs[0]
        assertEquals("dQw4w9WgXcQ", first.remoteVideoId)
        assertEquals("Never Gonna Give You Up", first.title)
        assertEquals("Rick Astley", first.artistName)

        val second = decoded.songs[1]
        assertTrue(second.contentUri.startsWith("ytm://search_"))
        assertEquals("Bohemian Rhapsody", second.title)
        assertEquals("Queen", second.artistName)
    }

    @Test
    fun decode_supportsCustomSchemeFromWebRedirect() {
        val webUrl = DeepLinkCodec.encodeTrackUrl(sampleRemoteSong())
        val query = webUrl.substringAfter('?')
        val customUri = "mousikify://open?$query"

        val decoded = DeepLinkCodec.decode(customUri) as? DeepLinkPayload.Track
        assertNotNull(decoded)
        assertEquals("dQw4w9WgXcQ", decoded!!.song.remoteVideoId)
        assertEquals("Never Gonna Give You Up", decoded.song.title)
    }

    @Test
    fun decode_rejectsUnrelatedOrMalformedUrls() {
        assertNull(DeepLinkCodec.decode(null))
        assertNull(DeepLinkCodec.decode(""))
        assertNull(DeepLinkCodec.decode("https://example.com/Mousikify/?v=dQw4w9WgXcQ"))
        assertNull(DeepLinkCodec.decode("https://m111k333.github.io/Mousikify/?p=!!!invalid_base64!!!"))
    }
}
