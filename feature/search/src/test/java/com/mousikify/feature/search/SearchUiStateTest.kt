package com.mousikify.feature.search

import com.mousikify.core.media.remote.RemotePlaylist
import com.mousikify.core.model.Album
import com.mousikify.core.model.AlbumType
import com.mousikify.core.model.Artist
import com.mousikify.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUiStateTest {

    @Test
    fun defaultStateHasAllTabsAndEmptyLists() {
        val state = SearchUiState()
        assertEquals(SearchTab.ALL, state.activeTab)
        assertEquals("", state.query)
        assertTrue(state.songs.isEmpty())
        assertTrue(state.artists.isEmpty())
        assertTrue(state.albums.isEmpty())
        assertTrue(state.onlineSongs.isEmpty())
        assertTrue(state.onlineArtists.isEmpty())
        assertTrue(state.onlineAlbums.isEmpty())
        assertTrue(state.onlinePlaylists.isEmpty())
    }

    @Test
    fun onlineStreamsPopulateCorrectCategories() {
        val sampleArtist = Artist(
            id = 101L,
            name = "Coldplay",
            avatarUrl = "https://example.com/coldplay.jpg",
            monthlyListeners = "25.6M subscribers",
            channelUrl = "https://www.youtube.com/channel/UCIaFw5VBEK8qaW6nRpx_qnw"
        )
        val sampleAlbum = Album(
            id = 202L,
            title = "A Rush of Blood to the Head",
            artist = "Coldplay",
            artistId = 101L,
            songCount = 10,
            artworkUri = "https://example.com/rush.jpg",
            playlistUrl = "https://music.youtube.com/playlist?list=OLAK5uy_test",
            albumType = AlbumType.ALBUM
        )
        val sampleSong = Song(
            id = -5001L,
            contentUri = "ytm://test12345",
            title = "Clocks",
            artistId = 101L,
            artistName = "Coldplay",
            albumId = 202L,
            albumName = "A Rush of Blood to the Head",
            durationMs = 307000L,
            trackNumber = 2,
            year = 2002,
            folderPath = "YouTube Music Stream",
            dateAdded = 0L,
            dateModified = 0L,
            isFavorite = false,
            artworkUri = "https://example.com/clocks.jpg"
        )
        val samplePlaylist = RemotePlaylist(
            url = "https://music.youtube.com/playlist?list=PLtest",
            name = "Coldplay Essentials",
            uploader = "YouTube Music",
            thumbnailUrl = "https://example.com/playlist.jpg",
            streamCount = 25
        )

        val state = SearchUiState(
            query = "Coldplay",
            activeTab = SearchTab.ALL,
            onlineSongs = listOf(sampleSong),
            onlineArtists = listOf(sampleArtist),
            onlineAlbums = listOf(sampleAlbum),
            onlinePlaylists = listOf(samplePlaylist)
        )

        assertEquals("Coldplay", state.query)
        assertEquals(1, state.onlineArtists.size)
        assertEquals("Coldplay", state.onlineArtists.first().name)
        assertEquals("https://www.youtube.com/channel/UCIaFw5VBEK8qaW6nRpx_qnw", state.onlineArtists.first().channelUrl)

        assertEquals(1, state.onlineAlbums.size)
        assertEquals("A Rush of Blood to the Head", state.onlineAlbums.first().title)
        assertEquals("https://music.youtube.com/playlist?list=OLAK5uy_test", state.onlineAlbums.first().playlistUrl)

        assertEquals(1, state.onlineSongs.size)
        assertEquals("Clocks", state.onlineSongs.first().title)
        assertTrue(state.onlineSongs.first().isRemoteStream)

        assertEquals(1, state.onlinePlaylists.size)
        assertEquals("Coldplay Essentials", state.onlinePlaylists.first().name)
    }

    @Test
    fun categoryTabSwitchingPreservesQueryAndOnlineData() {
        val sampleArtist = Artist(id = 1L, name = "Daft Punk")
        val state = SearchUiState(
            query = "Daft Punk",
            activeTab = SearchTab.ALL,
            onlineArtists = listOf(sampleArtist)
        )

        val switchedState = state.copy(activeTab = SearchTab.ARTISTS)
        assertEquals(SearchTab.ARTISTS, switchedState.activeTab)
        assertEquals("Daft Punk", switchedState.query)
        assertEquals(1, switchedState.onlineArtists.size)
    }

    @Test
    fun artistProfileSeparatesAlbumsAndSinglesCorrectly() {
        val studioAlbum = Album(
            id = 101L,
            title = "A Rush of Blood to the Head",
            artist = "Coldplay",
            artistId = 1L,
            songCount = 10,
            albumType = AlbumType.ALBUM,
            playlistUrl = "https://music.youtube.com/playlist?list=OLAK5uy_album"
        )
        val singleRelease = Album(
            id = 102L,
            title = "Yellow - Single",
            artist = "Coldplay",
            artistId = 1L,
            songCount = 1,
            albumType = AlbumType.SINGLE,
            playlistUrl = "https://music.youtube.com/playlist?list=OLAK5uy_single"
        )
        val profile = com.mousikify.core.model.ArtistProfile(
            artist = Artist(id = 1L, name = "Coldplay"),
            topSongs = emptyList(),
            albums = listOf(studioAlbum),
            singles = listOf(singleRelease)
        )
        assertEquals(1, profile.albums.size)
        assertEquals("A Rush of Blood to the Head", profile.albums.first().title)
        assertEquals(AlbumType.ALBUM, profile.albums.first().albumType)
        assertEquals("https://music.youtube.com/playlist?list=OLAK5uy_album", profile.albums.first().playlistUrl)

        assertEquals(1, profile.singles.size)
        assertEquals("Yellow - Single", profile.singles.first().title)
        assertEquals(AlbumType.SINGLE, profile.singles.first().albumType)
        assertEquals("https://music.youtube.com/playlist?list=OLAK5uy_single", profile.singles.first().playlistUrl)
    }

    @Test
    fun isAlbumSavedMatchesSavedAlbumInLocalCatalog() {
        val savedAlbum = Album(
            id = 202L,
            title = "A Rush of Blood to the Head",
            artist = "Coldplay",
            artistId = 101L,
            songCount = 10
        )
        val state = SearchUiState(
            query = "RandomQueryThatDoesNotMatchAlbum",
            albums = emptyList(), // Filtered by query
            allSavedAlbums = listOf(savedAlbum) // Complete unfiltered saved catalog
        )
        val isSaved = state.allSavedAlbums.any { it.title.equals("A Rush of Blood to the Head", ignoreCase = true) }
        val isNotSaved = state.allSavedAlbums.any { it.title.equals("Parachutes", ignoreCase = true) }
        assertTrue(isSaved)
        org.junit.Assert.assertFalse(isNotSaved)
    }
}
