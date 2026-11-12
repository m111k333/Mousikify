package com.mousikify.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.media.recommendation.DiscoverRecommendationEngine
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.model.Album
import com.mousikify.core.model.AlbumProfile
import com.mousikify.core.model.Artist
import com.mousikify.core.model.ArtistProfile
import com.mousikify.core.model.PlaybackContext
import com.mousikify.core.model.PlaybackContextType
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.RecentlyPlayedItem
import com.mousikify.core.model.RecentlyPlayedType
import com.mousikify.core.model.Song
import com.mousikify.core.scanner.classifier.MetadataClassifier
import com.mousikify.core.scanner.indexer.MediaStoreScanner
import com.mousikify.core.scanner.indexer.SafFolderScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class SmartMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val gradientColors: List<Long>,
    val songs: List<Song>
)

sealed interface HomeDetailView {
    data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>) : HomeDetailView
    data class AlbumDetail(val album: Album, val songs: List<Song>, val albumProfile: AlbumProfile? = null) : HomeDetailView
    data class ArtistDetail(val artist: Artist, val songs: List<Song>, val artistProfile: ArtistProfile? = null) : HomeDetailView
    data class LikedSongsDetail(val songs: List<Song>) : HomeDetailView
    data class MixDetail(val mix: SmartMix, val songs: List<Song>) : HomeDetailView
}

data class HomeUiState(
    val greeting: String = "",
    val totalSongCount: Int = 0,
    val allSongs: List<Song> = emptyList(),
    val recentlyHeard: List<Song> = emptyList(),
    val recentlyPlayed: List<RecentlyPlayedItem> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),


    val forgottenFavorites: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val smartMixes: List<SmartMix> = emptyList(),
    val likedCount: Int = 0,
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val isDiscoverRefreshing: Boolean = false,
    val statusMessage: String? = null,
    val activeDetail: HomeDetailView? = null,
    val pinnedVideoIds: Set<String> = emptySet(),
    val cacheUpdateTick: Long = 0L,
    val canUndo: Boolean = false,
    val favoriteSongIds: Set<Long> = emptySet(),
    val favoriteVideoIds: Set<String> = emptySet()
) {
    fun isFavorite(song: Song): Boolean {
        if (song.id in favoriteSongIds) return true
        val vid = song.remoteVideoId
        if (vid != null && vid in favoriteVideoIds) return true
        return song.isFavorite
    }

    fun isAlbumSaved(albumProfile: AlbumProfile): Boolean =
        SongRepository.isStreamingAlbumSaved(
            album = albumProfile.album,
            tracks = albumProfile.tracks,
            librarySongs = allSongs
        )
}

sealed interface HomeIntent {
    data class PlaySong(val song: Song, val queue: List<Song>) : HomeIntent
    data class PlayAll(val songs: List<Song>, val startIndex: Int = 0) : HomeIntent
    data class ToggleShuffle(val songs: List<Song>? = null) : HomeIntent
    data class OpenPlaylist(val playlist: Playlist) : HomeIntent
    data class OpenAlbum(val album: Album) : HomeIntent
    data object OpenLikedSongs : HomeIntent
    data class OpenMix(val mix: SmartMix) : HomeIntent
    data object CloseDetail : HomeIntent
    data class PlayMix(val mix: SmartMix) : HomeIntent
    data class PlayPlaylist(val playlist: Playlist) : HomeIntent
    data class ToggleFavorite(val song: Song) : HomeIntent
    data object PlayLikedSongs : HomeIntent
    data class ImportFolder(val uri: Uri) : HomeIntent
    data class DeletePlaylist(val playlistId: Long) : HomeIntent
    data class RenamePlaylist(val playlistId: Long, val newName: String) : HomeIntent
    data class AddSongToPlaylist(val playlistId: Long, val songId: Long) : HomeIntent
    data class AddSongObjectToPlaylist(val playlistId: Long, val song: Song) : HomeIntent
    data class CreatePlaylistAndAddSong(val name: String, val song: Song) : HomeIntent
    data class RemoveSongFromPlaylist(val playlistId: Long, val songId: Long) : HomeIntent
    data class ReorderPlaylist(val playlistId: Long, val orderedSongIds: List<Long>) : HomeIntent
    data object Rescan : HomeIntent
    data object RefreshDiscoverMix : HomeIntent
    data object DismissStatusMessage : HomeIntent
    data object UndoDelete : HomeIntent
    data class OpenArtist(val artist: Artist) : HomeIntent
    data class OpenArtistByName(val artistName: String) : HomeIntent
    data class OpenAlbumByName(
        val albumTitle: String,
        val artistName: String,
        val playlistUrl: String? = null,
        val artworkUri: String? = null
    ) : HomeIntent
    data class PlayNext(val song: Song) : HomeIntent
    data class AddToQueue(val song: Song) : HomeIntent
    data class StartRadio(val song: Song) : HomeIntent
    data class StartArtistRadio(val artistName: String) : HomeIntent
    data class OpenRecentlyPlayed(val item: RecentlyPlayedItem) : HomeIntent
    data class SavePlaylistToLibrary(val name: String, val songs: List<Song>) : HomeIntent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val musicController: MusicController,
    private val mediaStoreScanner: MediaStoreScanner,
    private val safFolderScanner: SafFolderScanner,
    private val preferences: PlaybackPreferences,
    private val remoteMusicRepository: RemoteMusicRepository,
    private val discoverRecommendationEngine: DiscoverRecommendationEngine
) : ViewModel() {

    init {
        // Clean up any non-favorite remote stream tracks that may have polluted the songs table
        viewModelScope.launch(Dispatchers.IO) {
            try {
                songRepository.purgeInvalidRecentlyPlayedAlbums()
                songRepository.deleteNonFavoriteRemoteSongs()
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "Failed to clean up non-favorite remote songs", e)
            }
        }
        // Asynchronously check or refresh 24-hour Discover Mix recommendations
        viewModelScope.launch(Dispatchers.IO) {
            songRepository.allPlayableSongs.collect { playable ->
                if (playable.isNotEmpty()) {
                    val songs = discoverRecommendationEngine.getOrRefreshDiscoverMix(playable)
                    if (songs.isNotEmpty() && songs != _discoverSongs.value) {
                        _discoverSongs.value = songs
                    }
                }
            }
        }
    }

    private val greeting: String
        get() {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..11 -> "Good morning"
                in 12..17 -> "Good afternoon"
                else -> "Good evening"
            }
        }

    private val _isScanning = MutableStateFlow(false)
    private val _isDiscoverRefreshing = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _activeDetail = MutableStateFlow<HomeDetailView?>(null)
    private val _canUndo = MutableStateFlow(false)
    private var lastRemovedPlaylistSong: Pair<Long, Song>? = null
    private val _discoverSongs = MutableStateFlow<List<Song>>(
        discoverRecommendationEngine.loadCachedDiscoverMix()?.second.orEmpty()
    )
    private var detailJob: Job? = null
    private var activeGenMixId: String? = null
    private val _openRadioEvent = MutableStateFlow<Song?>(null)
    val openRadioEvent: StateFlow<Song?> = _openRadioEvent.asStateFlow()

    fun consumeOpenRadioEvent() {
        _openRadioEvent.value = null
    }

    private data class ScanAndDetailTuple(
        val isScanning: Boolean,
        val statusMessage: String?,
        val activeDetail: HomeDetailView?,
        val isDiscoverRefreshing: Boolean,
        val canUndo: Boolean
    )

    private val favoriteFlow = combine(
        musicController.favoriteSongIds,
        musicController.favoriteVideoIds
    ) { songIds, videoIds -> Pair(songIds, videoIds) }

    private val playbackState = combine(
        musicController.currentSong,
        musicController.isPlaying,
        musicController.isShuffleEnabled,
        musicController.cacheUpdateTick,
        favoriteFlow
    ) { song, isPlaying, shuffle, cacheTick, (favSongIds, favVideoIds) ->
        HomePlaybackState(song, isPlaying, shuffle, cacheTick, favSongIds, favVideoIds)
    }

    private val scanAndDetailState = combine(
        _isScanning,
        _statusMessage,
        _activeDetail,
        _isDiscoverRefreshing,
        _canUndo
    ) { scanning, msg, detail, refreshing, canUndo ->
        ScanAndDetailTuple(scanning, msg, detail, refreshing, canUndo)
    }

    private val mediaCollections = combine(
        songRepository.allPlayableSongs,
        playlistRepository.playlists,
        songRepository.favorites,
        songRepository.albums,
        songRepository.allSongs
    ) { playableSongs, playlists, favorites, albums, allSongs ->
        val pinnedIds = allSongs.mapNotNull { it.remoteVideoId }.toSet()
        HomeMediaData(playableSongs, playlists, favorites, albums, pinnedIds)
    }

    private val historyData = combine(
        songRepository.recentlyHeard,
        songRepository.recentlyPlayedItems,
        songRepository.forgottenFavorites
    ) { recent, recentlyPlayed, forgotten ->
        Triple(recent, recentlyPlayed, forgotten)
    }


    private val smartMixesData = combine(
        mediaCollections,
        _discoverSongs
    ) { media, discoverSongs ->
        val mixes = generateSmartMixes(media.songs, media.favorites, discoverSongs)
        val recentlyAdded = media.songs.sortedByDescending { it.dateAdded }.take(20)
        Pair(mixes, recentlyAdded)
    }.flowOn(Dispatchers.Default)

    private val homeStaticContent = combine(
        mediaCollections,
        historyData,
        smartMixesData
    ) { media, (recent, recentlyPlayed, forgotten), (mixes, recentlyAdded) ->
        HomeContentData(media, recent, recentlyPlayed, recentlyAdded, forgotten, mixes)
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<HomeUiState> = combine(
        homeStaticContent,
        playbackState,
        scanAndDetailState
    ) { content, pbState, scanDetail ->
        HomeUiState(
            greeting = greeting,
            totalSongCount = content.media.songs.size,
            allSongs = content.media.songs,
            recentlyHeard = content.recent,
            recentlyPlayed = content.recentlyPlayed,
            recentlyAdded = content.recentlyAdded,

            forgottenFavorites = content.forgotten,
            playlists = content.media.playlists,
            albums = content.media.albums,
            smartMixes = content.mixes,
            likedCount = content.media.favorites.size,
            currentSongId = pbState.currentSong?.id,
            isPlaying = pbState.isPlaying,
            isShuffleEnabled = pbState.isShuffleEnabled,
            isLoading = false,
            isScanning = scanDetail.isScanning,
            isDiscoverRefreshing = scanDetail.isDiscoverRefreshing,
            statusMessage = scanDetail.statusMessage,
            activeDetail = scanDetail.activeDetail,
            pinnedVideoIds = content.media.pinnedVideoIds,
            cacheUpdateTick = pbState.cacheTick,
            canUndo = scanDetail.canUndo,
            favoriteSongIds = pbState.favoriteSongIds,
            favoriteVideoIds = pbState.favoriteVideoIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(greeting = greeting, isLoading = true)
    )

    fun isSongFavorite(song: Song): Boolean = uiState.value.isFavorite(song)

    fun isTrackCached(song: Song): Boolean {
        if (song.isLocal) return true
        return musicController.isSongCached(song)
    }

    private fun generateSmartMixes(
        allSongs: List<Song>,
        favorites: List<Song>,
        discoverSongs: List<Song>
    ): List<SmartMix> {
        if (allSongs.isEmpty()) return emptyList()

        val mixes = mutableListOf<SmartMix>()
        val now = System.currentTimeMillis()
        val dailyCycle = now / (24L * 60L * 60L * 1000L) // 24-hour cycle seed
        val twelveHourCycle = now / (12L * 60L * 60L * 1000L) // 12-hour cycle seed

        // 1. DAILY MIX (Exactly 15 songs, rotates every 24 hours)
        val dailyRng = kotlin.random.Random(dailyCycle)
        val dailyTracks = allSongs.shuffled(dailyRng).take(15)
        if (dailyTracks.isNotEmpty()) {
            val topDailyArtists = dailyTracks.map { it.artistName }
                .filter { it.isNotBlank() && it != "<unknown>" }
                .distinct()
                .take(3)
                .joinToString(", ")
            val dailySubtitle = if (topDailyArtists.isNotBlank()) "$topDailyArtists, and more • Refreshes every 24 hours" else "15 fresh picks • Refreshes every 24 hours"
            mixes.add(
                SmartMix(
                    id = "daily_mix_1",
                    title = "Daily Mix 1",
                    subtitle = dailySubtitle,
                    gradientColors = listOf(0xFF1E3264, 0xFF8D67AB),
                    songs = dailyTracks
                )
            )
        }

        // 2. DISCOVER MIX (Spotify Discover style, rotates every 24 hours)
        if (discoverSongs.size >= 5) {
            mixes.add(
                SmartMix(
                    id = "discover_mix",
                    title = "Discover",
                    subtitle = "New music & hidden gems • Refreshes every 24 hours",
                    gradientColors = listOf(0xFF1DB954, 0xFF004D40),
                    songs = discoverSongs
                )
            )
        }

        // 3. FAVORITES MIX (Rotates every 12 hours)
        if (favorites.size >= 7) {
            val favRng = kotlin.random.Random(twelveHourCycle + 99)
            mixes.add(
                SmartMix(
                    id = "favorites_mix",
                    title = "Favorites Mix",
                    subtitle = "Only the tracks you love • Refreshes every 12 hours",
                    gradientColors = listOf(0xFF450AF5, 0xFFC4EFD9),
                    songs = favorites.shuffled(favRng)
                )
            )
        }

        fun getDecade(song: Song): String? {
            return MetadataClassifier.extractDecade(song.year)
        }

        fun getGenre(song: Song): String {
            val folderGenre = MetadataClassifier.normalizeGenre(song.folderPath)
            if (folderGenre != "Other") return folderGenre
            val textGenre = MetadataClassifier.normalizeGenre("${song.title} ${song.artistName} ${song.albumName}")
            if (textGenre != "Other") return textGenre
            return "Other"
        }

        fun getVibe(song: Song): String {
            val genre = getGenre(song)
            return when (genre) {
                "Rock", "Electronic" -> "High Energy / Workout"
                "Hip-Hop", "Pop" -> "Mid-Tempo Flow"
                "Acoustic / Chill" -> "Chill / Relaxed"
                else -> {
                    val lower = "${song.title} ${song.artistName}".lowercase(Locale.ROOT)
                    when {
                        lower.contains("remix") || lower.contains("club") || lower.contains("workout") || lower.contains("run") || lower.contains("fast") -> "High Energy / Workout"
                        lower.contains("acoustic") || lower.contains("piano") || lower.contains("relax") || lower.contains("slow") || lower.contains("sleep") || lower.contains("ambient") -> "Chill / Relaxed"
                        lower.contains("study") || lower.contains("focus") || lower.contains("instrumental") -> "Focus / Ambient"
                        else -> "Mid-Tempo Flow"
                    }
                }
            }
        }

        // 4. GENRE MIXES (Top 2 genres with >= 7 songs, 12h rotation)
        val genreGroups = allSongs.groupBy { getGenre(it) }
        val genreGradients = mapOf(
            "Rock" to listOf(0xFFB71C1C, 0xFF212121),
            "Hip-Hop" to listOf(0xFFFF8F00, 0xFF3E2723),
            "Pop" to listOf(0xFFE91E63, 0xFF9C27B0),
            "Electronic" to listOf(0xFF00B0FF, 0xFF7C4DFF),
            "R&B" to listOf(0xFF6A1B9A, 0xFFE91E63),
            "Acoustic / Chill" to listOf(0xFF2E7D32, 0xFF81C784)
        )

        val eligibleGenres = genreGroups.entries
            .filter { it.key != "Other" && it.value.size >= 7 }
            .sortedByDescending { it.value.size }
            .take(2)

        for ((genre, songs) in eligibleGenres) {
            val genreRng = kotlin.random.Random(twelveHourCycle + genre.hashCode())
            val shuffled = songs.shuffled(genreRng).take(25)
            val artists = shuffled.map { it.artistName }.filter { it.isNotBlank() && it != "<unknown>" }.distinct()
            val topArtists = artists.take(3).joinToString(", ")
            val subtitle = if (topArtists.isNotBlank()) "$topArtists, and more • Refreshes every 12 hours" else "Your essential $genre mix • Refreshes every 12 hours"
            val colors = genreGradients[genre] ?: listOf(0xFF1E3264, 0xFF8D67AB)
            mixes.add(
                SmartMix(
                    id = "genre_${genre.lowercase(Locale.ROOT).replace(" ", "_").replace("/", "_")}",
                    title = "$genre Mix",
                    subtitle = subtitle,
                    gradientColors = colors,
                    songs = shuffled
                )
            )
        }

        // 5. VIBE / SOUND MIXES (Top 2 vibes with >= 7 songs, 12h rotation)
        val vibeConfigs = listOf(
            Triple("High Energy / Workout", "High Energy / Workout Mix", listOf(0xFFFF3D00, 0xFFFFD600)),
            Triple("Chill / Relaxed", "Chill / Relaxed Mix", listOf(0xFF00796B, 0xFF1DB954)),
            Triple("Focus / Ambient", "Focus / Ambient Mix", listOf(0xFF1A237E, 0xFF3949AB)),
            Triple("Mid-Tempo Flow", "Mid-Tempo Flow Mix", listOf(0xFF4A148C, 0xFF880E4F))
        )
        val vibeGroups = vibeConfigs.map { (vibe, title, colors) ->
            val vSongs = allSongs.filter { getVibe(it) == vibe }
            Triple(title, colors, vSongs)
        }.filter { it.third.size >= 7 }
            .sortedByDescending { it.third.size }
            .take(2)

        for ((title, colors, vSongs) in vibeGroups) {
            val vibeRng = kotlin.random.Random(twelveHourCycle + title.hashCode())
            val shuffled = vSongs.shuffled(vibeRng).take(25)
            mixes.add(
                SmartMix(
                    id = "vibe_${title.lowercase(Locale.ROOT).replace(" ", "_").replace("/", "_")}",
                    title = title,
                    subtitle = "Curated flow • Refreshes every 12 hours",
                    gradientColors = colors,
                    songs = shuffled
                )
            )
        }

        // 6. DECADE MIXES (Pruned: Top 1 prominent decade with >= 7 songs, 12h rotation)
        val decadeGroups = allSongs.filter { getDecade(it) != null }.groupBy { getDecade(it)!! }
        val decadeLabels = mapOf(
            "50s" to "50s",
            "60s" to "60s",
            "70s" to "70s",
            "80s" to "80s",
            "90s" to "90s",
            "00s" to "2000s",
            "10s" to "2010s",
            "20s" to "2020s"
        )
        val decadeGradients = mapOf(
            "80s" to listOf(0xFFE8115B, 0xFF00D2FF),
            "90s" to listOf(0xFF8D67AB, 0xFFFF6F00),
            "00s" to listOf(0xFF1E3264, 0xFF00E5FF),
            "10s" to listOf(0xFF7000FF, 0xFFFF007A),
            "20s" to listOf(0xFF00897B, 0xFF1DB954),
            "70s" to listOf(0xFFBA5A31, 0xFFFFC107),
            "60s" to listOf(0xFF450AF5, 0xFFBA5A31)
        )

        val eligibleDecades = decadeGroups.entries
            .filter { it.value.size >= 7 }
            .sortedByDescending { it.value.size }
            .take(1) // Prune bloat: show top 1 decade mix per cycle

        for ((decade, dSongs) in eligibleDecades) {
            val dRng = kotlin.random.Random(twelveHourCycle + decade.hashCode())
            val shuffled = dSongs.shuffled(dRng).take(25)
            val label = decadeLabels[decade] ?: decade
            val artists = shuffled.map { it.artistName }.filter { it.isNotBlank() && it != "<unknown>" }.distinct()
            val topArtists = artists.take(3).joinToString(", ")
            val subtitle = if (topArtists.isNotBlank()) "$topArtists, and more • Refreshes every 12 hours" else "The greatest hits of the $label • Refreshes every 12 hours"
            val colors = decadeGradients[decade] ?: listOf(0xFF1E3264, 0xFF8D67AB)
            mixes.add(
                SmartMix(
                    id = "decade_$decade",
                    title = "$label Mix",
                    subtitle = subtitle,
                    gradientColors = colors,
                    songs = shuffled
                )
            )
        }

        return mixes
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.PlaySong -> {
                val context = when (val detail = _activeDetail.value) {
                    is HomeDetailView.PlaylistDetail -> {
                        val isGenMix = detail.playlist.id < 0
                        PlaybackContext(
                            title = detail.playlist.name,
                            type = PlaybackContextType.PLAYLIST,
                            id = if (isGenMix) (activeGenMixId ?: "genmix_${System.currentTimeMillis()}") else detail.playlist.id.toString(),
                            playlistId = if (isGenMix) null else detail.playlist.id,
                            isLocalOrSaved = !isGenMix,
                            artworkUri = detail.playlist.customCoverUri
                        )
                    }
                    is HomeDetailView.AlbumDetail -> PlaybackContext(
                        title = detail.album.title,
                        type = PlaybackContextType.ALBUM,
                        id = detail.album.id.toString(),
                        albumId = detail.album.id,
                        artistName = detail.album.artist,
                        isLocalOrSaved = true
                    )
                    is HomeDetailView.ArtistDetail -> PlaybackContext(
                        title = detail.artist.name,
                        type = PlaybackContextType.ARTIST,
                        id = detail.artist.id.toString(),
                        artistName = detail.artist.name,
                        isLocalOrSaved = true
                    )
                    is HomeDetailView.MixDetail -> PlaybackContext(
                        title = detail.mix.title,
                        type = PlaybackContextType.MIX,
                        id = detail.mix.id,
                        isLocalOrSaved = false
                    )
                    is HomeDetailView.LikedSongsDetail -> PlaybackContext(
                        title = "Liked Songs",
                        type = PlaybackContextType.PLAYLIST,
                        id = "liked",
                        isLocalOrSaved = true
                    )
                    else -> PlaybackContext(
                        title = intent.song.title,
                        type = PlaybackContextType.LIBRARY,
                        id = intent.song.contentUri,
                        artworkUri = intent.song.artworkUri,
                        artistName = intent.song.artistName,
                        isLocalOrSaved = intent.song.isLocal
                    )
                }
                musicController.playQueue(
                    intent.queue,
                    intent.queue.indexOfFirst { it.id == intent.song.id || it.contentUri == intent.song.contentUri }.coerceAtLeast(0),
                    context = context
                )
            }
            is HomeIntent.PlayAll -> {
                val context = when (val detail = _activeDetail.value) {
                    is HomeDetailView.PlaylistDetail -> {
                        val isGenMix = detail.playlist.id < 0
                        PlaybackContext(
                            title = detail.playlist.name,
                            type = PlaybackContextType.PLAYLIST,
                            id = if (isGenMix) (activeGenMixId ?: "genmix_${System.currentTimeMillis()}") else detail.playlist.id.toString(),
                            playlistId = if (isGenMix) null else detail.playlist.id,
                            isLocalOrSaved = !isGenMix,
                            artworkUri = detail.playlist.customCoverUri
                        )
                    }
                    is HomeDetailView.AlbumDetail -> PlaybackContext(
                        title = detail.album.title,
                        type = PlaybackContextType.ALBUM,
                        id = detail.album.id.toString(),
                        albumId = detail.album.id,
                        artistName = detail.album.artist,
                        isLocalOrSaved = true
                    )
                    is HomeDetailView.ArtistDetail -> PlaybackContext(
                        title = detail.artist.name,
                        type = PlaybackContextType.ARTIST,
                        id = detail.artist.id.toString(),
                        artistName = detail.artist.name,
                        isLocalOrSaved = true
                    )
                    is HomeDetailView.MixDetail -> PlaybackContext(
                        title = detail.mix.title,
                        type = PlaybackContextType.MIX,
                        id = detail.mix.id,
                        isLocalOrSaved = false
                    )
                    is HomeDetailView.LikedSongsDetail -> PlaybackContext(
                        title = "Liked Songs",
                        type = PlaybackContextType.PLAYLIST,
                        id = "liked",
                        isLocalOrSaved = true
                    )
                    else -> null
                }
                musicController.playQueue(intent.songs, intent.startIndex.coerceAtLeast(0), context = context)
            }
            is HomeIntent.ToggleShuffle -> {
                handleToggleShuffle(intent.songs)
            }
            is HomeIntent.OpenPlaylist -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    combine(
                        playlistRepository.getPlaylistById(intent.playlist.id),
                        playlistRepository.getPlaylistSongs(intent.playlist.id)
                    ) { updatedPlaylist, songs ->
                        Pair(updatedPlaylist ?: intent.playlist, songs)
                    }.collect { (playlist, songs) ->
                        songs.forEach { song ->
                            if (song.isRemoteStream && !musicController.isSongCached(song)) {
                                musicController.downloadSongForOffline(song)
                            }
                        }
                        _activeDetail.value = HomeDetailView.PlaylistDetail(playlist, songs)
                    }
                }
            }
            is HomeIntent.OpenAlbum -> {
                detailJob?.cancel()
                val initialAlbum = intent.album
                _activeDetail.value = HomeDetailView.AlbumDetail(
                    album = initialAlbum,
                    songs = emptyList(),
                    albumProfile = AlbumProfile(album = initialAlbum, tracks = emptyList(), isLoading = true)
                )

                detailJob = viewModelScope.launch {
                    val isSavedLocally = SongRepository.isStreamingAlbumSaved(intent.album, emptyList(), uiState.value.allSongs)
                    val profile = if (intent.album.id != 0L && (intent.album.playlistUrl.isNullOrBlank() || isSavedLocally)) {
                        runCatching { songRepository.getAlbumProfile(intent.album.id).firstOrNull() }.getOrNull()
                    } else null

                    if (profile != null && profile.tracks.isNotEmpty() &&
                        SongRepository.isStreamingAlbumSaved(profile.album, profile.tracks, uiState.value.allSongs)
                    ) {
                        _activeDetail.value = HomeDetailView.AlbumDetail(profile.album, profile.tracks, profile.copy(isLoading = false))
                    } else {
                        val remote = remoteMusicRepository.getRemoteAlbumProfile(
                            albumTitle = intent.album.title,
                            artistName = intent.album.artist,
                            artworkUri = intent.album.artworkUri,
                            playlistUrl = intent.album.playlistUrl
                        ).getOrNull()
                        if (remote != null && remote.tracks.isNotEmpty()) {
                            _activeDetail.value = HomeDetailView.AlbumDetail(remote.album, remote.tracks, remote.copy(isLoading = false))
                        } else if (profile != null && profile.tracks.isNotEmpty()) {
                            _activeDetail.value = HomeDetailView.AlbumDetail(profile.album, profile.tracks, profile.copy(isLoading = false))
                        } else {
                            _activeDetail.value = HomeDetailView.AlbumDetail(
                                intent.album,
                                emptyList(),
                                AlbumProfile(intent.album, emptyList(), isLoading = false)
                            )
                        }
                    }
                }
            }
            is HomeIntent.OpenLikedSongs -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    songRepository.favorites.collect { favorites ->
                        _activeDetail.value = HomeDetailView.LikedSongsDetail(favorites)
                    }
                }
            }
            is HomeIntent.OpenMix -> {
                detailJob?.cancel()
                _activeDetail.value = HomeDetailView.MixDetail(intent.mix, intent.mix.songs)
            }
            is HomeIntent.CloseDetail -> {
                detailJob?.cancel()
                _activeDetail.value = null
            }
            is HomeIntent.PlayMix -> {
                if (intent.mix.songs.isNotEmpty()) {
                    val context = PlaybackContext(
                        title = intent.mix.title,
                        type = PlaybackContextType.MIX,
                        id = intent.mix.id,
                        isLocalOrSaved = false
                    )
                    musicController.playQueue(intent.mix.songs, 0, context = context)
                }
            }
            is HomeIntent.PlayPlaylist -> {
                viewModelScope.launch {
                    val songs = playlistRepository.getPlaylistSongs(intent.playlist.id).first()
                    if (songs.isNotEmpty()) {
                        val context = PlaybackContext(
                            title = intent.playlist.name,
                            type = PlaybackContextType.PLAYLIST,
                            id = intent.playlist.id.toString(),
                            playlistId = intent.playlist.id,
                            isLocalOrSaved = true
                        )
                        musicController.playQueue(songs, 0, context = context)
                    }
                }
            }
            is HomeIntent.ToggleFavorite -> {
                val song = intent.song
                val willBeFav = !isSongFavorite(song)
                musicController.toggleFavorite(song)

                _activeDetail.value?.let { detail ->
                    when (detail) {
                        is HomeDetailView.AlbumDetail -> {
                            val updatedTracks = detail.albumProfile?.tracks?.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            val updatedSongs = detail.songs.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            _activeDetail.value = detail.copy(
                                songs = updatedSongs,
                                albumProfile = detail.albumProfile?.copy(tracks = updatedTracks ?: detail.albumProfile.tracks)
                            )
                        }
                        is HomeDetailView.ArtistDetail -> {
                            val updatedTop = detail.artistProfile?.topSongs?.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            val updatedFeatured = detail.artistProfile?.featuredIn?.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            val updatedSongs = detail.songs.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            _activeDetail.value = detail.copy(
                                songs = updatedSongs,
                                artistProfile = detail.artistProfile?.copy(
                                    topSongs = updatedTop ?: detail.artistProfile.topSongs,
                                    featuredIn = updatedFeatured ?: detail.artistProfile.featuredIn
                                )
                            )
                        }
                        is HomeDetailView.PlaylistDetail -> {
                            val updatedSongs = detail.songs.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            _activeDetail.value = detail.copy(songs = updatedSongs)
                        }
                        is HomeDetailView.LikedSongsDetail -> {
                            val updatedSongs = detail.songs.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            _activeDetail.value = detail.copy(songs = updatedSongs)
                        }
                        is HomeDetailView.MixDetail -> {
                            val updatedSongs = detail.songs.map {
                                if (it.id == song.id || (song.remoteVideoId != null && it.remoteVideoId == song.remoteVideoId)) {
                                    it.copy(isFavorite = willBeFav)
                                } else it
                            }
                            _activeDetail.value = detail.copy(songs = updatedSongs)
                        }
                    }
                }
            }
            is HomeIntent.PlayLikedSongs -> {
                viewModelScope.launch {
                    val favorites = songRepository.favorites.first()
                    if (favorites.isNotEmpty()) {
                        val context = PlaybackContext(
                            title = "Liked Songs",
                            type = PlaybackContextType.PLAYLIST,
                            id = "liked",
                            isLocalOrSaved = true
                        )
                        musicController.playQueue(favorites, 0, context = context)
                    }
                }
            }
            is HomeIntent.ImportFolder -> {
                viewModelScope.launch {
                    _isScanning.value = true
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            intent.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        preferences.addCustomFolderUri(intent.uri.toString())
                        val result = safFolderScanner.scanFolder(intent.uri)
                        _statusMessage.value = if (result.newCount > 0) {
                            "Imported ${result.newCount} songs into playlist '${result.folderName}' (${result.totalCount} tracks total)!"
                        } else if (result.totalCount > 0) {
                            "Playlist '${result.folderName}' created with ${result.totalCount} tracks."
                        } else {
                            "No audio files found in selected folder."
                        }
                    } catch (e: Exception) {
                        _statusMessage.value = "Import error: ${e.localizedMessage}"
                    } finally {
                        _isScanning.value = false
                    }
                }
            }
            is HomeIntent.DeletePlaylist -> {
                viewModelScope.launch {
                    playlistRepository.deletePlaylist(intent.playlistId)
                    songRepository.deleteNonFavoriteRemoteSongs()
                    musicController.pruneOrphanedCache()
                    if (_activeDetail.value is HomeDetailView.PlaylistDetail &&
                        (_activeDetail.value as HomeDetailView.PlaylistDetail).playlist.id == intent.playlistId
                    ) {
                        _activeDetail.value = null
                    }
                    _statusMessage.value = "Playlist deleted"
                }
            }
            is HomeIntent.RenamePlaylist -> {
                viewModelScope.launch {
                    val trimmed = intent.newName.trim()
                    if (trimmed.isNotEmpty()) {
                        playlistRepository.renamePlaylist(intent.playlistId, trimmed)
                        _statusMessage.value = "Playlist renamed to '$trimmed'"
                    }
                }
            }
            is HomeIntent.AddSongToPlaylist -> {
                viewModelScope.launch {
                    playlistRepository.addSongToPlaylist(intent.playlistId, intent.songId)
                    val song = songRepository.getSongByIdSync(intent.songId)
                    if (song != null && song.isRemoteStream) {
                        musicController.downloadSongForOffline(song)
                    }
                    _statusMessage.value = "Added to playlist"
                }
            }
            is HomeIntent.AddSongObjectToPlaylist -> {
                viewModelScope.launch {
                    val resolvedSong = playlistRepository.addSongObjectToPlaylist(intent.playlistId, intent.song)
                    if (resolvedSong.isRemoteStream && !musicController.isSongCached(resolvedSong)) {
                        musicController.downloadSongForOffline(resolvedSong)
                    }
                    _statusMessage.value = "Added to playlist"
                }
            }
            is HomeIntent.CreatePlaylistAndAddSong -> {
                viewModelScope.launch {
                    val trimmed = intent.name.trim()
                    if (trimmed.isNotEmpty()) {
                        val newId = playlistRepository.createPlaylist(trimmed, intent.song.artworkUri)
                        val resolvedSong = playlistRepository.addSongObjectToPlaylist(newId, intent.song)
                        if (resolvedSong.isRemoteStream && !musicController.isSongCached(resolvedSong)) {
                            musicController.downloadSongForOffline(resolvedSong)
                        }
                        _statusMessage.value = "Created and added to '$trimmed'"
                    }
                }
            }
            is HomeIntent.RemoveSongFromPlaylist -> {
                viewModelScope.launch {
                    val song = songRepository.getSongByIdSync(intent.songId)
                    if (song != null) {
                        lastRemovedPlaylistSong = Pair(intent.playlistId, song)
                        _canUndo.value = true
                    }
                    playlistRepository.removeSongFromPlaylist(intent.playlistId, intent.songId)
                    songRepository.deleteNonFavoriteRemoteSongs()
                    musicController.pruneOrphanedCache()
                    _statusMessage.value = "Removed from playlist"
                }
            }
            is HomeIntent.ReorderPlaylist -> {
                viewModelScope.launch {
                    playlistRepository.reorderPlaylist(intent.playlistId, intent.orderedSongIds)
                }
            }
            is HomeIntent.SavePlaylistToLibrary -> {
                viewModelScope.launch {
                    val trimmed = intent.name.trim().ifEmpty { "Generated Playlist" }
                    val coverUri = intent.songs.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
                    val newId = playlistRepository.createPlaylist(trimmed, coverUri)
                    intent.songs.forEach { song ->
                        playlistRepository.addSongObjectToPlaylist(newId, song)
                    }
                    val savedPlaylist = playlistRepository.getPlaylistById(newId).firstOrNull()
                        ?: Playlist(id = newId, name = trimmed, createdAt = System.currentTimeMillis(), customCoverUri = coverUri, songCount = intent.songs.size)
                    activeGenMixId = null
                    _activeDetail.value = HomeDetailView.PlaylistDetail(savedPlaylist, intent.songs)
                    _statusMessage.value = "Saved '$trimmed' to library"
                }
            }
            is HomeIntent.Rescan -> {
                viewModelScope.launch {
                    _isScanning.value = true
                    try {
                        val res = mediaStoreScanner.scanMediaStore()
                        _statusMessage.value = "Scan complete: ${res.totalScanned} songs found."
                    } catch (e: Exception) {
                        _statusMessage.value = "Scan error: ${e.localizedMessage}"
                    } finally {
                        _isScanning.value = false
                    }
                }
            }
            is HomeIntent.RefreshDiscoverMix -> {
                if (_isDiscoverRefreshing.value) return
                viewModelScope.launch {
                    _isDiscoverRefreshing.value = true
                    try {
                        val playable = songRepository.allPlayableSongs.first()
                        val songs = discoverRecommendationEngine.getOrRefreshDiscoverMix(playable, forceRefresh = true)
                        if (songs.isNotEmpty()) {
                            _discoverSongs.value = songs
                            val currentDetail = _activeDetail.value
                            if (currentDetail is HomeDetailView.MixDetail && currentDetail.mix.id == "discover_mix") {
                                val updatedMix = currentDetail.mix.copy(songs = songs)
                                _activeDetail.value = HomeDetailView.MixDetail(updatedMix, songs)
                            }
                            _statusMessage.value = "Discover Mix refreshed with ${songs.size} new tracks"
                        } else {
                            _statusMessage.value = "Couldn't refresh Discover Mix. Check your network connection."
                        }
                    } catch (e: Exception) {
                        _statusMessage.value = "Refresh failed: ${e.localizedMessage ?: "Unknown error"}"
                    } finally {
                        _isDiscoverRefreshing.value = false
                    }
                }
            }
            is HomeIntent.DismissStatusMessage -> {
                _statusMessage.value = null
                _canUndo.value = false
                lastRemovedPlaylistSong = null
            }
            is HomeIntent.UndoDelete -> {
                viewModelScope.launch {
                    val plSong = lastRemovedPlaylistSong
                    if (plSong != null) {
                        val (playlistId, song) = plSong
                        songRepository.upsertSongs(listOf(song))
                        playlistRepository.addSongObjectToPlaylist(playlistId, song)
                        if (song.isRemoteStream && !musicController.isSongCached(song)) {
                            musicController.downloadSongForOffline(song)
                        }
                        _statusMessage.value = "Restored '${song.title}' to playlist"
                        lastRemovedPlaylistSong = null
                        _canUndo.value = false
                    }
                }
            }
            is HomeIntent.OpenArtist -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    val localProfile = runCatching { songRepository.getArtistProfile(intent.artist.id).firstOrNull() }.getOrNull()
                    if (localProfile != null) {
                        _activeDetail.value = HomeDetailView.ArtistDetail(
                            localProfile.artist,
                            localProfile.topSongs,
                            localProfile.copy(isLoading = true)
                        )
                    } else {
                        _activeDetail.value = HomeDetailView.ArtistDetail(
                            intent.artist,
                            emptyList(),
                            ArtistProfile(intent.artist, isLoading = true)
                        )
                    }
                    val remote = remoteMusicRepository.getRemoteArtistProfile(
                        artistName = intent.artist.name,
                        channelUrl = intent.artist.channelUrl,
                        avatarUrl = intent.artist.avatarUrl
                    ).getOrNull()
                    if (remote != null) {
                        _activeDetail.value = HomeDetailView.ArtistDetail(
                            remote.artist.copy(
                                id = intent.artist.id,
                                avatarUrl = remote.artist.avatarUrl ?: intent.artist.avatarUrl
                            ),
                            remote.topSongs,
                            remote.copy(
                                artist = remote.artist.copy(
                                    id = intent.artist.id,
                                    avatarUrl = remote.artist.avatarUrl ?: intent.artist.avatarUrl
                                ),
                                isLoading = false
                            )
                        )
                    } else if (localProfile != null) {
                        _activeDetail.value = HomeDetailView.ArtistDetail(
                            localProfile.artist.copy(
                                id = intent.artist.id,
                                avatarUrl = localProfile.artist.avatarUrl ?: intent.artist.avatarUrl
                            ),
                            localProfile.topSongs,
                            localProfile.copy(
                                artist = localProfile.artist.copy(
                                    id = intent.artist.id,
                                    avatarUrl = localProfile.artist.avatarUrl ?: intent.artist.avatarUrl
                                ),
                                isLoading = false
                            )
                        )
                    } else {
                        _activeDetail.value = HomeDetailView.ArtistDetail(
                            intent.artist,
                            emptyList(),
                            ArtistProfile(intent.artist, isLoading = false)
                        )
                    }
                }
            }
            is HomeIntent.OpenArtistByName -> {
                val cleanName = com.mousikify.core.model.cleanArtistName(intent.artistName)
                detailJob?.cancel()
                val fallbackArtwork = uiState.value.allSongs.firstOrNull {
                    it.cleanArtistName.equals(cleanName, ignoreCase = true) && !it.artworkUri.isNullOrBlank()
                }?.artworkUri ?: uiState.value.recentlyHeard.firstOrNull {
                    it.cleanArtistName.equals(cleanName, ignoreCase = true) && !it.artworkUri.isNullOrBlank()
                }?.artworkUri
                detailJob = viewModelScope.launch {
                    val localProfile = runCatching { songRepository.getArtistProfileByName(cleanName).firstOrNull() }.getOrNull()
                    val initialArtist = (localProfile?.artist ?: Artist(
                        id = cleanName.hashCode().toLong(),
                        name = cleanName,
                        songCount = 0
                    )).let { it.copy(avatarUrl = it.avatarUrl ?: fallbackArtwork) }

                    if (localProfile != null) {
                        _activeDetail.value = HomeDetailView.ArtistDetail(
                            initialArtist,
                            localProfile.topSongs,
                            localProfile.copy(artist = initialArtist, isLoading = true)
                        )
                    } else {
                        _activeDetail.value = HomeDetailView.ArtistDetail(
                            initialArtist,
                            emptyList(),
                            ArtistProfile(initialArtist, isLoading = true)
                        )
                    }
                    val remote = remoteMusicRepository.getRemoteArtistProfile(
                        artistName = cleanName,
                        channelUrl = initialArtist.channelUrl,
                        avatarUrl = initialArtist.avatarUrl
                    ).getOrNull()
                    if (remote != null) {
                        _activeDetail.value = HomeDetailView.ArtistDetail(
                            remote.artist.copy(
                                id = initialArtist.id,
                                avatarUrl = remote.artist.avatarUrl ?: initialArtist.avatarUrl
                            ),
                            remote.topSongs,
                            remote.copy(
                                artist = remote.artist.copy(
                                    id = initialArtist.id,
                                    avatarUrl = remote.artist.avatarUrl ?: initialArtist.avatarUrl
                                ),
                                isLoading = false
                            )
                        )
                    } else if (localProfile != null) {
                        _activeDetail.value = HomeDetailView.ArtistDetail(
                            initialArtist,
                            localProfile.topSongs,
                            localProfile.copy(artist = initialArtist, isLoading = false)
                        )
                    } else {
                        _activeDetail.value = HomeDetailView.ArtistDetail(
                            initialArtist,
                            emptyList(),
                            ArtistProfile(initialArtist, isLoading = false)
                        )
                    }
                }
            }
            is HomeIntent.OpenAlbumByName -> {
                detailJob?.cancel()
                val cleanArtist = intent.artistName.trim()
                val cleanTitle = intent.albumTitle.trim()
                val local = uiState.value.albums.firstOrNull {
                    it.title.equals(cleanTitle, ignoreCase = true) &&
                    (cleanArtist.isBlank() || it.artist.contains(cleanArtist, ignoreCase = true) || cleanArtist.contains(it.artist, ignoreCase = true))
                }
                if (local != null) {
                    onIntent(HomeIntent.OpenAlbum(local))
                } else {
                    val placeholderAlbum = Album(
                        id = "$cleanArtist-$cleanTitle".hashCode().toLong(),
                        title = cleanTitle,
                        artist = cleanArtist,
                        artistId = cleanArtist.hashCode().toLong(),
                        songCount = 0,
                        year = 0,
                        artworkUri = intent.artworkUri,
                        playlistUrl = intent.playlistUrl
                    )
                    _activeDetail.value = HomeDetailView.AlbumDetail(
                        placeholderAlbum,
                        emptyList(),
                        AlbumProfile(placeholderAlbum, emptyList(), isLoading = true)
                    )

                    detailJob = viewModelScope.launch {
                        val remote = remoteMusicRepository.getRemoteAlbumProfile(
                            albumTitle = cleanTitle,
                            artistName = cleanArtist,
                            artworkUri = intent.artworkUri,
                            playlistUrl = intent.playlistUrl
                        ).getOrNull()
                        if (remote != null) {
                            _activeDetail.value = HomeDetailView.AlbumDetail(remote.album, remote.tracks, remote.copy(isLoading = false))
                        } else {
                            _activeDetail.value = HomeDetailView.AlbumDetail(
                                placeholderAlbum,
                                emptyList(),
                                AlbumProfile(placeholderAlbum, emptyList(), isLoading = false)
                            )
                        }
                    }
                }
            }
            is HomeIntent.PlayNext -> musicController.playNext(intent.song)
            is HomeIntent.AddToQueue -> musicController.addToQueue(intent.song)
            is HomeIntent.StartRadio -> musicController.startRadio(intent.song)
            is HomeIntent.StartArtistRadio -> musicController.startArtistRadio(intent.artistName)
            is HomeIntent.OpenRecentlyPlayed -> handleOpenRecentlyPlayed(intent.item)
        }
    }

    private fun handleOpenRecentlyPlayed(item: RecentlyPlayedItem) {
        when (item.type) {
            RecentlyPlayedType.TRACK -> {
                val allSongs = uiState.value.allSongs
                val song = item.song
                    ?: allSongs.firstOrNull { it.contentUri == item.referenceId.removePrefix("track_") || it.id.toString() == item.referenceId.removePrefix("track_") }
                if (song != null) {
                    val context = PlaybackContext(
                        title = song.title,
                        type = PlaybackContextType.LIBRARY,
                        artworkUri = song.artworkUri
                    )
                    musicController.playSong(song, context)
                } else {
                    val uri = item.referenceId.removePrefix("track_")
                    val virtualSong = Song(
                        id = item.id.let { if (it > 0) -it else it },
                        contentUri = uri,
                        title = item.title,
                        artistId = 0L,
                        artistName = item.subtitle.removePrefix("Song • ").trim(),
                        albumId = 0L,
                        albumName = "",
                        durationMs = 0L,
                        trackNumber = 0,
                        year = 0,
                        folderPath = "",
                        dateAdded = 0L,
                        dateModified = 0L,
                        artworkUri = item.artworkUri
                    )
                    val context = PlaybackContext(
                        title = virtualSong.title,
                        type = PlaybackContextType.LIBRARY,
                        artworkUri = virtualSong.artworkUri
                    )
                    musicController.playSong(virtualSong, context)
                }
            }
            RecentlyPlayedType.ALBUM -> {
                val cleanArtist = item.subtitle.removePrefix("Album • ").trim()
                val local = uiState.value.albums.firstOrNull {
                    it.title.equals(item.title, ignoreCase = true) &&
                    (cleanArtist.isBlank() || it.artist.contains(cleanArtist, ignoreCase = true) || cleanArtist.contains(it.artist, ignoreCase = true))
                }
                if (local != null) {
                    onIntent(HomeIntent.OpenAlbum(local))
                } else {
                    onIntent(HomeIntent.OpenAlbumByName(
                        albumTitle = item.title,
                        artistName = cleanArtist,
                        playlistUrl = item.remotePlaylistUrl,
                        artworkUri = item.artworkUri
                    ))
                }
            }
            RecentlyPlayedType.PLAYLIST -> {
                if (item.referenceId == "playlist_liked" || item.title.equals("Liked Songs", ignoreCase = true)) {
                    onIntent(HomeIntent.OpenLikedSongs)
                    return
                }

                val refPlaylistId = item.referenceId.removePrefix("playlist_").toLongOrNull()
                val savedPlaylist = item.playlist
                    ?: uiState.value.playlists.firstOrNull {
                        (refPlaylistId != null && it.id == refPlaylistId) || it.name.equals(item.title, ignoreCase = true)
                    }
                if (savedPlaylist != null) {
                    onIntent(HomeIntent.OpenPlaylist(savedPlaylist))
                    return
                }

                val isGeneratedMix = item.referenceId.startsWith("genmix_")
                if (isGeneratedMix) {
                    detailJob?.cancel()
                    detailJob = viewModelScope.launch {
                        _isScanning.value = true
                        val cachedTracks = songRepository.getCachedGeneratedMix(item.referenceId)
                        _isScanning.value = false
                        if (!cachedTracks.isNullOrEmpty()) {
                            activeGenMixId = item.referenceId
                            val mixPlaylist = Playlist(
                                id = -kotlin.math.abs(item.referenceId.hashCode().toLong()).coerceAtLeast(1L),
                                name = item.title,
                                createdAt = item.playedAt,
                                customCoverUri = item.artworkUri ?: cachedTracks.firstOrNull()?.artworkUri,
                                songCount = cachedTracks.size
                            )
                            _activeDetail.value = HomeDetailView.PlaylistDetail(mixPlaylist, cachedTracks)
                        } else {
                            _statusMessage.value = "Generated playlist expired or not found"
                        }
                    }
                    return
                }

                val isRadio = item.referenceId.startsWith("radio_") || item.subtitle.startsWith("Song Radio")
                if (isRadio) {
                    val seed = item.song ?: Song(
                        id = item.id.let { if (it > 0) -it else it },
                        contentUri = if (item.referenceId.startsWith("radio_")) item.referenceId.removePrefix("radio_") else "",
                        title = item.title.removeSuffix(" Radio").trim(),
                        artistId = 0L,
                        artistName = item.subtitle.removePrefix("Song Radio • ").trim(),
                        albumId = 0L,
                        albumName = "",
                        durationMs = 0L,
                        trackNumber = 0,
                        year = 0,
                        folderPath = "",
                        dateAdded = 0L,
                        dateModified = 0L,
                        artworkUri = item.artworkUri
                    )
                    _openRadioEvent.value = seed
                    return
                }

                val isCommunity = item.referenceId.startsWith("remote_") ||
                    item.subtitle.contains("Community", ignoreCase = true) ||
                    !item.remotePlaylistUrl.isNullOrBlank()

                val rawUrl = item.remotePlaylistUrl
                    ?: if (item.referenceId.startsWith("remote_")) item.referenceId.removePrefix("remote_") else null

                val rUrl = rawUrl?.let {
                    if (it.startsWith("http")) it else "https://music.youtube.com/playlist?list=$it"
                }

                if (isCommunity && !rUrl.isNullOrBlank()) {
                    detailJob?.cancel()
                    detailJob = viewModelScope.launch {
                        _isScanning.value = true
                        val result = remoteMusicRepository.getPlaylistTracks(rUrl)
                        _isScanning.value = false
                        result.onSuccess { tracks ->
                            val playlist = Playlist(
                                id = -(rUrl.hashCode().toLong().let { if (it < 0) -it else it }),
                                name = item.title,
                                createdAt = item.playedAt,
                                customCoverUri = item.artworkUri,
                                songCount = tracks.size
                            )
                            _activeDetail.value = HomeDetailView.PlaylistDetail(playlist, tracks)
                        }
                    }
                } else {
                    val localPlaylist = item.playlist
                        ?: uiState.value.playlists.firstOrNull {
                            it.name.equals(item.title, ignoreCase = true)
                        }
                    if (localPlaylist != null) {
                        onIntent(HomeIntent.OpenPlaylist(localPlaylist))
                    } else if (!rUrl.isNullOrBlank()) {
                        detailJob?.cancel()
                        detailJob = viewModelScope.launch {
                            _isScanning.value = true
                            val result = remoteMusicRepository.getPlaylistTracks(rUrl)
                            _isScanning.value = false
                            result.onSuccess { tracks ->
                                val playlist = Playlist(
                                    id = -(rUrl.hashCode().toLong().let { if (it < 0) -it else it }),
                                    name = item.title,
                                    createdAt = item.playedAt,
                                    customCoverUri = item.artworkUri,
                                    songCount = tracks.size
                                )
                                _activeDetail.value = HomeDetailView.PlaylistDetail(playlist, tracks)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleToggleShuffle(songs: List<Song>?) {
        if (musicController.isShuffleEnabled.value) {
            musicController.toggleShuffle()
        } else {
            if (!songs.isNullOrEmpty()) {
                val currentQueueIds = musicController.currentQueue.value.map { it.id }
                val targetIds = songs.map { it.id }
                if (currentQueueIds.isNotEmpty() && currentQueueIds == targetIds) {
                    musicController.toggleShuffle()
                } else {
                    musicController.setShuffleEnabled(true)
                    val startIndex = songs.indices.random()
                    val context = when (val detail = _activeDetail.value) {
                        is HomeDetailView.PlaylistDetail -> {
                            val isGenMix = detail.playlist.id < 0
                            PlaybackContext(
                                title = detail.playlist.name,
                                type = PlaybackContextType.PLAYLIST,
                                id = if (isGenMix) (activeGenMixId ?: "genmix_${System.currentTimeMillis()}") else detail.playlist.id.toString(),
                                playlistId = if (isGenMix) null else detail.playlist.id,
                                isLocalOrSaved = !isGenMix,
                                artworkUri = detail.playlist.customCoverUri
                            )
                        }
                        is HomeDetailView.AlbumDetail -> PlaybackContext(
                            title = detail.album.title,
                            type = PlaybackContextType.ALBUM,
                            id = detail.album.id.toString(),
                            albumId = detail.album.id,
                            artistName = detail.album.artist,
                            isLocalOrSaved = true
                        )
                        is HomeDetailView.ArtistDetail -> PlaybackContext(
                            title = detail.artist.name,
                            type = PlaybackContextType.ARTIST,
                            id = detail.artist.id.toString(),
                            artistName = detail.artist.name,
                            isLocalOrSaved = true
                        )
                        is HomeDetailView.MixDetail -> PlaybackContext(
                            title = detail.mix.title,
                            type = PlaybackContextType.MIX,
                            id = detail.mix.id,
                            isLocalOrSaved = false
                        )
                        is HomeDetailView.LikedSongsDetail -> PlaybackContext(
                            title = "Liked Songs",
                            type = PlaybackContextType.PLAYLIST,
                            id = "liked",
                            isLocalOrSaved = true
                        )
                        else -> null
                    }
                    musicController.playQueue(songs, startIndex, context = context)
                }
            } else {
                musicController.toggleShuffle()
            }
        }
    }
}

private data class HomePlaybackState(
    val currentSong: Song?,
    val isPlaying: Boolean,
    val isShuffleEnabled: Boolean,
    val cacheTick: Long,
    val favoriteSongIds: Set<Long>,
    val favoriteVideoIds: Set<String>
)

private data class HomeMediaData(
    val songs: List<Song>,
    val playlists: List<Playlist>,
    val favorites: List<Song>,
    val albums: List<Album>,
    val pinnedVideoIds: Set<String> = emptySet()
)

private data class HomeContentData(
    val media: HomeMediaData,
    val recent: List<Song>,
    val recentlyPlayed: List<RecentlyPlayedItem>,
    val recentlyAdded: List<Song>,
    val forgotten: List<Song>,
    val mixes: List<SmartMix>
)
