package com.mousikify.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mousikify.core.database.dao.TrackDao
import com.mousikify.core.database.entity.TrackEntity
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.PlaylistRepository
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.controller.MusicController
import com.mousikify.core.model.Album
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.Song
import com.mousikify.core.scanner.classifier.MetadataClassifier
import com.mousikify.core.scanner.indexer.MediaStoreScanner
import com.mousikify.core.scanner.indexer.SafFolderScanner
import com.mousikify.core.scanner.work.AudioAnalysisWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    data class AlbumDetail(val album: Album, val songs: List<Song>) : HomeDetailView
    data class LikedSongsDetail(val songs: List<Song>) : HomeDetailView
    data class MixDetail(val mix: SmartMix, val songs: List<Song>) : HomeDetailView
}

data class HomeUiState(
    val greeting: String = "",
    val totalSongCount: Int = 0,
    val recentlyPlayed: List<Song> = emptyList(),
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
    val statusMessage: String? = null,
    val activeDetail: HomeDetailView? = null
)

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
    data object Rescan : HomeIntent
    data object DismissStatusMessage : HomeIntent
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
    private val trackDao: TrackDao
) : ViewModel() {

    init {
        // Enqueue background analysis immediately without waiting for charging/idle
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AudioAnalysisWorker.enqueue(context, requireCharging = false, requireIdle = false)
            } catch (_: Exception) {}
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
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _activeDetail = MutableStateFlow<HomeDetailView?>(null)
    private var detailJob: Job? = null

    private val playbackState = combine(
        musicController.currentSong,
        musicController.isPlaying,
        musicController.isShuffleEnabled
    ) { song, isPlaying, shuffle ->
        Triple(song, isPlaying, shuffle)
    }

    private val scanAndDetailState = combine(
        _isScanning,
        _statusMessage,
        _activeDetail
    ) { scanning, msg, detail ->
        Triple(scanning, msg, detail)
    }

    private val mediaCollections = combine(
        songRepository.allSongs,
        playlistRepository.playlists,
        songRepository.favorites,
        songRepository.albums,
        trackDao.getAllTracks()
    ) { allSongs, playlists, favorites, albums, allTracks ->
        HomeMediaData(allSongs, playlists, favorites, albums, allTracks)
    }

    private val historyData = combine(
        songRepository.recentlyPlayed,
        songRepository.forgottenFavorites
    ) { recent, forgotten ->
        Pair(recent, forgotten)
    }

    private val homeStaticContent = combine(
        mediaCollections,
        historyData
    ) { media, (recent, forgotten) ->
        val mixes = generateSmartMixes(media.songs, media.favorites, media.tracks)
        val recentlyAdded = media.songs.sortedByDescending { it.dateAdded }.take(20)
        HomeContentData(media, recent, recentlyAdded, forgotten, mixes)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        homeStaticContent,
        playbackState,
        scanAndDetailState
    ) { content, (currentSong, isPlaying, isShuffle), (isScanning, statusMsg, activeDetail) ->
        HomeUiState(
            greeting = greeting,
            totalSongCount = content.media.songs.size,
            recentlyPlayed = content.recent,
            recentlyAdded = content.recentlyAdded,
            forgottenFavorites = content.forgotten,
            playlists = content.media.playlists,
            albums = content.media.albums,
            smartMixes = content.mixes,
            likedCount = content.media.favorites.size,
            currentSongId = currentSong?.id,
            isPlaying = isPlaying,
            isShuffleEnabled = isShuffle,
            isLoading = false,
            isScanning = isScanning,
            statusMessage = statusMsg,
            activeDetail = activeDetail
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(greeting = greeting, isLoading = true)
    )

    private fun generateSmartMixes(
        allSongs: List<Song>,
        favorites: List<Song>,
        allTracks: List<TrackEntity>
    ): List<SmartMix> {
        if (allSongs.isEmpty()) return emptyList()

        val mixes = mutableListOf<SmartMix>()
        val tracksByUri = allTracks.associateBy { it.uri }

        fun getDecade(song: Song): String? {
            val track = tracksByUri[song.contentUri]
            return track?.decade ?: MetadataClassifier.extractDecade(song.year)
        }

        fun getGenre(song: Song): String {
            val track = tracksByUri[song.contentUri]
            val normGenre = track?.normalizedGenre
            if (!normGenre.isNullOrBlank() && normGenre != "Other") {
                return normGenre
            }
            val raw = track?.rawGenre
            if (!raw.isNullOrBlank()) {
                val g = MetadataClassifier.normalizeGenre(raw)
                if (g != "Other") return g
            }
            // Check folder path
            val folderGenre = MetadataClassifier.normalizeGenre(song.folderPath)
            if (folderGenre != "Other") return folderGenre

            // Check title and artist heuristic
            val textGenre = MetadataClassifier.normalizeGenre("${song.title} ${song.artistName} ${song.albumName}")
            if (textGenre != "Other") return textGenre

            return "Other"
        }

        fun getVibe(song: Song): String {
            val track = tracksByUri[song.contentUri]
            val trackVibe = track?.vibeCategory
            if (track != null && track.isAnalyzed && !trackVibe.isNullOrBlank()) {
                return trackVibe
            }
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

        // 1. GENRE MIXES (Rock Mix, Hip-Hop Mix, Pop Mix, Electronic Mix, R&B Mix, Acoustic / Chill Mix)
        val genreGroups = allSongs.groupBy { getGenre(it) }
        val genreGradients = mapOf(
            "Rock" to listOf(0xFFB71C1C, 0xFF212121),
            "Hip-Hop" to listOf(0xFFFF8F00, 0xFF3E2723),
            "Pop" to listOf(0xFFE91E63, 0xFF9C27B0),
            "Electronic" to listOf(0xFF00B0FF, 0xFF7C4DFF),
            "R&B" to listOf(0xFF6A1B9A, 0xFFE91E63),
            "Acoustic / Chill" to listOf(0xFF2E7D32, 0xFF81C784)
        )

        for ((genre, songs) in genreGroups) {
            if (genre == "Other" || songs.isEmpty()) continue
            val artists = songs.map { it.artistName }.filter { it.isNotBlank() && it != "<unknown>" }.distinct()
            val topArtists = artists.take(3).joinToString(", ")
            val subtitle = if (topArtists.isNotBlank()) "$topArtists, and more" else "Your essential $genre mix"
            val colors = genreGradients[genre] ?: listOf(0xFF1E3264, 0xFF8D67AB)
            mixes.add(
                SmartMix(
                    id = "genre_${genre.lowercase(Locale.ROOT).replace(" ", "_").replace("/", "_")}",
                    title = "$genre Mix",
                    subtitle = subtitle,
                    gradientColors = colors,
                    songs = songs
                )
            )
        }

        // 2. DECADE MIXES (60s, 70s, 80s, 90s, 2000s, 2010s, 2020s)
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

        val sortedDecades = decadeGroups.keys.sortedDescending()
        for (decade in sortedDecades) {
            val songs = decadeGroups[decade] ?: continue
            val label = decadeLabels[decade] ?: decade
            val artists = songs.map { it.artistName }.filter { it.isNotBlank() && it != "<unknown>" }.distinct()
            val topArtists = artists.take(3).joinToString(", ")
            val subtitle = if (topArtists.isNotBlank()) "$topArtists, and more" else "The greatest hits of the $label"
            val colors = decadeGradients[decade] ?: listOf(0xFF1E3264, 0xFF8D67AB)
            mixes.add(
                SmartMix(
                    id = "decade_$decade",
                    title = "$label Mix",
                    subtitle = subtitle,
                    gradientColors = colors,
                    songs = songs
                )
            )
        }

        // 3. VIBE / SOUND MIXES
        // High Energy / Workout
        val workoutSongs = allSongs.filter { song ->
            val track = tracksByUri[song.contentUri]
            (track != null && (track.rmsEnergy >= 0.60f || track.bpm >= 125 || track.vibeCategory == "High Energy / Workout")) ||
            getVibe(song) == "High Energy / Workout"
        }
        if (workoutSongs.isNotEmpty()) {
            mixes.add(
                SmartMix(
                    id = "vibe_workout",
                    title = "High Energy / Workout Mix",
                    subtitle = "Fast tempos and high-energy bangers to push your limits",
                    gradientColors = listOf(0xFFFF3D00, 0xFFFFD600),
                    songs = workoutSongs
                )
            )
        }

        // Chill / Relaxed
        val chillSongs = allSongs.filter { song ->
            val track = tracksByUri[song.contentUri]
            (track != null && (track.rmsEnergy <= 0.35f || track.vibeCategory == "Chill / Relaxed")) ||
            getVibe(song) == "Chill / Relaxed"
        }
        if (chillSongs.isNotEmpty()) {
            mixes.add(
                SmartMix(
                    id = "vibe_chill",
                    title = "Chill / Relaxed Mix",
                    subtitle = "Mellow rhythms and peaceful vibes to unwind",
                    gradientColors = listOf(0xFF00796B, 0xFF1DB954),
                    songs = chillSongs
                )
            )
        }

        // Focus / Ambient
        val focusSongs = allSongs.filter { song ->
            val track = tracksByUri[song.contentUri]
            (track != null && (track.rmsEnergy <= 0.25f || track.vibeCategory == "Focus / Ambient")) ||
            getVibe(song) == "Focus / Ambient"
        }
        if (focusSongs.isNotEmpty()) {
            mixes.add(
                SmartMix(
                    id = "vibe_focus",
                    title = "Focus / Ambient Mix",
                    subtitle = "Low-energy soundscapes and flow-state music",
                    gradientColors = listOf(0xFF1A237E, 0xFF3949AB),
                    songs = focusSongs
                )
            )
        }

        // Mid-Tempo Flow
        val flowSongs = allSongs.filter { song ->
            val track = tracksByUri[song.contentUri]
            (track != null && track.vibeCategory == "Mid-Tempo Flow") ||
            getVibe(song) == "Mid-Tempo Flow"
        }
        if (flowSongs.isNotEmpty()) {
            mixes.add(
                SmartMix(
                    id = "vibe_flow",
                    title = "Mid-Tempo Flow Mix",
                    subtitle = "Steady grooves and everyday rotation",
                    gradientColors = listOf(0xFF4A148C, 0xFF880E4F),
                    songs = flowSongs
                )
            )
        }

        // 4. DAILY MIXES & PERSONALIZED FAVORITES
        val artists = allSongs.map { it.artistName }.filter { it.isNotBlank() && it != "<unknown>" }.distinct()
        val topArtistsStr = artists.take(3).joinToString(", ")
        mixes.add(
            SmartMix(
                id = "daily_mix_1",
                title = "Daily Mix 1",
                subtitle = if (topArtistsStr.isNotBlank()) "$topArtistsStr, and more" else "Curated for your day",
                gradientColors = listOf(0xFF1E3264, 0xFF8D67AB),
                songs = allSongs.shuffled(kotlin.random.Random(42))
            )
        )

        val topPlayed = allSongs.sortedByDescending { it.playCount }.take(25)
        if (topPlayed.any { it.playCount > 0 }) {
            mixes.add(
                SmartMix(
                    id = "heavy_rotation",
                    title = "Heavy Rotation",
                    subtitle = "Your most played tracks on repeat",
                    gradientColors = listOf(0xFFBA5A31, 0xFFE8115B),
                    songs = topPlayed
                )
            )
        }

        if (favorites.isNotEmpty()) {
            mixes.add(
                SmartMix(
                    id = "favorites_mix",
                    title = "Favorites Mix",
                    subtitle = "Only the offline tracks you love",
                    gradientColors = listOf(0xFF450AF5, 0xFFC4EFD9),
                    songs = favorites
                )
            )
        }

        return mixes
            .filter { it.songs.size >= 7 }
            .sortedByDescending { it.songs.size }
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.PlaySong -> {
                musicController.playQueue(intent.queue, intent.queue.indexOfFirst { it.id == intent.song.id }.coerceAtLeast(0))
            }
            is HomeIntent.PlayAll -> {
                musicController.playQueue(intent.songs, intent.startIndex.coerceAtLeast(0))
            }
            is HomeIntent.ToggleShuffle -> {
                handleToggleShuffle(intent.songs)
            }
            is HomeIntent.OpenPlaylist -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    playlistRepository.getPlaylistSongs(intent.playlist.id).collect { songs ->
                        _activeDetail.value = HomeDetailView.PlaylistDetail(intent.playlist, songs)
                    }
                }
            }
            is HomeIntent.OpenAlbum -> {
                detailJob?.cancel()
                detailJob = viewModelScope.launch {
                    songRepository.getSongsByAlbum(intent.album.id).collect { songs ->
                        _activeDetail.value = HomeDetailView.AlbumDetail(intent.album, songs)
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
                    musicController.playQueue(intent.mix.songs, 0)
                }
            }
            is HomeIntent.PlayPlaylist -> {
                viewModelScope.launch {
                    val songs = playlistRepository.getPlaylistSongs(intent.playlist.id).first()
                    if (songs.isNotEmpty()) {
                        musicController.playQueue(songs, 0)
                    }
                }
            }
            is HomeIntent.ToggleFavorite -> {
                musicController.toggleFavorite(intent.song)
            }
            is HomeIntent.PlayLikedSongs -> {
                viewModelScope.launch {
                    val favorites = songRepository.favorites.first()
                    if (favorites.isNotEmpty()) {
                        musicController.playQueue(favorites, 0)
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
            is HomeIntent.DismissStatusMessage -> _statusMessage.value = null
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
                    musicController.playQueue(songs, startIndex)
                }
            } else {
                musicController.toggleShuffle()
            }
        }
    }
}

private data class HomeMediaData(
    val songs: List<Song>,
    val playlists: List<Playlist>,
    val favorites: List<Song>,
    val albums: List<Album>,
    val tracks: List<TrackEntity> = emptyList()
)

private data class HomeContentData(
    val media: HomeMediaData,
    val recent: List<Song>,
    val recentlyAdded: List<Song>,
    val forgotten: List<Song>,
    val mixes: List<SmartMix>
)
