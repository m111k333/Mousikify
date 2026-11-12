package com.mousikify.feature.library.generator

import com.mousikify.core.media.recommendation.RadioRefreshConfig
import com.mousikify.core.media.recommendation.RadioSource
import com.mousikify.core.model.Artist
import com.mousikify.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistGeneratorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSong(id: Long, title: String = "Title $id", artist: String = "Artist $id"): Song = Song(
        id = id,
        contentUri = "ytm://$id",
        title = title,
        artistId = id * 10,
        artistName = artist,
        albumId = id * 100,
        albumName = "Album $id",
        durationMs = 180000L,
        trackNumber = 1,
        year = 2024,
        folderPath = "",
        dateAdded = 0L,
        dateModified = 0L
    )

    @Test
    fun initialState_hasDefaultValues() {
        val vm = PlaylistGeneratorViewModel(
            favorites = listOf(createSong(1, "Fav 1")),
            recentSongs = listOf(createSong(2, "Recent 1")),
            searcher = { emptyList() },
            generator = { _, _, _ -> emptyList() },
            saver = { _, _ -> 1L }
        )

        val state = vm.uiState.value
        assertTrue(state.seeds.isEmpty())
        assertEquals(25, state.trackCount)
        assertFalse(state.isGenerating)
        assertTrue(state.generatedTracks.isEmpty())
        assertFalse(state.isShowingGeneratedView)
        assertEquals(1, state.recentlyLikedSongs.size)
        assertEquals("Fav 1", state.recentlyLikedSongs.first().title)
        assertEquals(1, state.recentlyPlayedSongs.size)
        assertEquals("Recent 1", state.recentlyPlayedSongs.first().title)
    }

    @Test
    fun search_queriesBothSongsAndArtists() = runTest {
        val songsResult = listOf(createSong(10, "Found Song"))
        val artistsResult = listOf(Artist(name = "Found Artist", songCount = 5, albumCount = 1, id = 99L))

        val vm = PlaylistGeneratorViewModel(
            searcher = { songsResult },
            artistSearcher = { artistsResult }
        )

        vm.onSearchQueryChanged("Found")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.searchResults.size)
        assertEquals("Found Song", state.searchResults.first().title)
        assertEquals(1, state.searchArtists.size)
        assertEquals("Found Artist", state.searchArtists.first().name)
    }

    @Test
    fun addArtistSeed_addsSeedWithArtistInfo() = runTest {
        val vm = PlaylistGeneratorViewModel()

        val artist = Artist(name = "The Beatles", songCount = 10, albumCount = 3, id = 42L)
        vm.addArtistSeed(artist)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.seeds.size)
        assertEquals("The Beatles", state.seeds.first().artistName)
    }

    @Test
    fun addSeed_allowsUnlimitedSeeds_andIgnoresDuplicates() {
        val vm = PlaylistGeneratorViewModel()

        val songs = (1..7).map { createSong(it.toLong(), "Song $it") }

        // Add 6 songs -> should have 6 seeds (no 5-seed cap)
        for (i in 0..5) {
            vm.addSeed(songs[i])
        }
        assertEquals(6, vm.uiState.value.seeds.size)

        // Try adding duplicate of first song -> should still be 6
        vm.addSeed(songs[0])
        assertEquals(6, vm.uiState.value.seeds.size)

        // Remove a seed -> size becomes 5
        vm.removeSeed(songs[0].id)
        assertEquals(5, vm.uiState.value.seeds.size)
    }

    @Test
    fun onTrackCountChanged_clampsBetween10And50() {
        val vm = PlaylistGeneratorViewModel()

        vm.onTrackCountChanged(5)
        assertEquals(10, vm.uiState.value.trackCount)

        vm.onTrackCountChanged(35)
        assertEquals(35, vm.uiState.value.trackCount)

        vm.onTrackCountChanged(100)
        assertEquals(50, vm.uiState.value.trackCount)
    }

    @Test
    fun generatePlaylist_invokesGenerator_andPopulatesResults() = runTest {
        val generatedList = listOf(createSong(101, "Rec 1"), createSong(102, "Rec 2"))
        val vm = PlaylistGeneratorViewModel(
            generator = { _, _, _ -> generatedList }
        )

        vm.addSeed(createSong(1, "Seed 1"))
        vm.generatePlaylist()

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isGenerating)
        assertTrue(state.isShowingGeneratedView)
        assertEquals(2, state.generatedTracks.size)
        assertEquals("Rec 1", state.generatedTracks[0].title)
        assertNull(state.errorMessage)

        vm.dismissGeneratedView()
        assertFalse(vm.uiState.value.isShowingGeneratedView)
    }

    @Test
    fun savePlaylistToLibrary_callsSaver_andUpdatesState() = runTest {
        var savedName = ""
        var savedCount = 0
        val generatedList = listOf(createSong(101, "Rec 1"), createSong(102, "Rec 2"))

        val vm = PlaylistGeneratorViewModel(
            generator = { _, _, _ -> generatedList },
            saver = { name, tracks ->
                savedName = name
                savedCount = tracks.size
                42L
            }
        )

        vm.addSeed(createSong(1, "Seed 1"))
        vm.generatePlaylist()
        advanceUntilIdle()

        vm.savePlaylistToLibrary("My Custom Mix")
        advanceUntilIdle()

        assertEquals("My Custom Mix", savedName)
        assertEquals(2, savedCount)
        assertEquals("My Custom Mix", vm.uiState.value.savedPlaylistName)
        assertFalse(vm.uiState.value.isSaving)

        vm.clearSavedNotification()
        assertNull(vm.uiState.value.savedPlaylistName)
    }

    @Test
    fun refreshDialog_openDismiss_andRefreshWithConfig_updatesStateAndTriggersGeneration() = runTest {
        var generatedCalled = false
        var lastCount = 0
        var lastForceRefresh = false

        val vm = PlaylistGeneratorViewModel(
            generator = { _, count, forceRefresh ->
                generatedCalled = true
                lastCount = count
                lastForceRefresh = forceRefresh
                listOf(createSong(10, "Refreshed Track"))
            }
        )

        vm.addSeed(createSong(1, "Seed 1"))
        advanceUntilIdle()

        // 1. Open and dismiss dialog
        assertFalse(vm.uiState.value.isShowRefreshDialog)
        vm.openRefreshDialog()
        assertTrue(vm.uiState.value.isShowRefreshDialog)
        vm.dismissRefreshDialog()
        assertFalse(vm.uiState.value.isShowRefreshDialog)

        // 2. Refresh with custom config and count
        val customConfig = RadioRefreshConfig(source = RadioSource.DEEZER, maxPerArtist = 1)
        vm.refreshPlaylistWithConfig(customConfig, 35)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isShowRefreshDialog)
        assertEquals(customConfig, vm.uiState.value.refreshConfig)
        assertEquals(35, vm.uiState.value.trackCount)
        assertTrue(generatedCalled)
        assertEquals(35, lastCount)
        assertTrue(lastForceRefresh)
        assertEquals(1, vm.uiState.value.generatedTracks.size)
        assertEquals("Refreshed Track", vm.uiState.value.generatedTracks.first().title)
    }
}
