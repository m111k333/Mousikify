package com.mousikify.core.media.recommendation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.cache.MediaCacheManager
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 100% open-source, lightweight on-device recommendation engine for the Discover Mix.
 *
 * Lifecycle:
 * 1. Checks 24-hour cache validity. If valid and not forced, serves instantly.
 * 2. Checks network connectivity. If offline, serves cached mix or reports offline without polluting with known songs.
 * 3. Identifies up to 10 ranked seed artists from 7-day history, favorites, library artists, or curated cold-start fallbacks.
 * 4. Discovers similar artists via Deezer open services with 2nd-degree similarity expansion.
 * 5. Strictly excludes any artists and tracks already in the user's library (both local and downloaded).
 * 6. Resolves top streamable tracks, shuffles with dynamic or daily seed, and caches for 24 hours.
 */
@Singleton
class DiscoverRecommendationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val remoteMusicRepository: RemoteMusicRepository,
    private val similarArtistService: SimilarArtistDiscoveryService,
    private val cacheManager: MediaCacheManager
) {
    companion object {
        private const val TAG = "DiscoverRecommendation"
        private const val CACHE_FILE_NAME = "discover_mix_cache.json"
        const val TTL_MILLIS = 24L * 60L * 60L * 1000L // 24 hours
        private const val TARGET_SONG_COUNT = 15
        private const val MIN_SONG_COUNT = 5

        /**
         * Splits an artist name into individual searchable artist tokens (e.g. "Coldplay, BTS" -> ["coldplay", "bts"]).
         */
        fun normalizeArtistTokens(name: String): Set<String> {
            val lower = name.trim().lowercase(Locale.ROOT)
            if (lower.isBlank() || lower == "<unknown>" || lower == "youtube music") return emptySet()
            val parts = lower.split(Regex("[,&/]|\\b(?:feat|ft|featuring|vs|x)\\b\\.?", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { it.isNotBlank() && it.length > 1 }
            return (setOf(lower) + parts)
        }
    }

    /**
     * Checks if active internet connectivity is present.
     */
    fun isNetworkConnected(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    fun loadCachedDiscoverMix(): Pair<Long, List<Song>>? {
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        if (!cacheFile.exists() || cacheFile.length() == 0L) return null
        return runCatching {
            val json = JSONObject(cacheFile.readText())
            val timestamp = json.optLong("timestamp", 0L)
            val array = json.optJSONArray("songs") ?: return@runCatching null
            val songs = mutableListOf<Song>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                songs.add(
                    Song(
                        id = obj.optLong("id"),
                        contentUri = obj.optString("contentUri"),
                        title = obj.optString("title"),
                        artistId = obj.optLong("artistId"),
                        artistName = obj.optString("artistName"),
                        albumId = obj.optLong("albumId", -1L),
                        albumName = obj.optString("albumName"),
                        durationMs = obj.optLong("durationMs"),
                        trackNumber = obj.optInt("trackNumber", 0),
                        year = obj.optInt("year", 0),
                        folderPath = obj.optString("folderPath"),
                        dateAdded = obj.optLong("dateAdded"),
                        dateModified = obj.optLong("dateModified"),
                        playCount = obj.optInt("playCount", 0),
                        lastPlayedAt = if (obj.has("lastPlayedAt")) obj.optLong("lastPlayedAt") else null,
                        isFavorite = obj.optBoolean("isFavorite", false),
                        lrcPath = if (obj.has("lrcPath")) obj.optString("lrcPath") else null,
                        artworkUri = if (obj.has("artworkUri")) obj.optString("artworkUri") else null
                    )
                )
            }
            Pair(timestamp, songs)
        }.getOrNull()
    }

    fun saveCachedDiscoverMix(timestamp: Long, songs: List<Song>) {
        runCatching {
            val array = JSONArray()
            for (song in songs) {
                val obj = JSONObject().apply {
                    put("id", song.id)
                    put("contentUri", song.contentUri)
                    put("title", song.title)
                    put("artistId", song.artistId)
                    put("artistName", song.artistName)
                    put("albumId", song.albumId)
                    put("albumName", song.albumName)
                    put("durationMs", song.durationMs)
                    put("trackNumber", song.trackNumber)
                    put("year", song.year)
                    put("folderPath", song.folderPath)
                    put("dateAdded", song.dateAdded)
                    put("dateModified", song.dateModified)
                    put("playCount", song.playCount)
                    song.lastPlayedAt?.let { put("lastPlayedAt", it) }
                    put("isFavorite", song.isFavorite)
                    song.lrcPath?.let { put("lrcPath", it) }
                    song.artworkUri?.let { put("artworkUri", it) }
                }
                array.put(obj)
            }
            val root = JSONObject().apply {
                put("timestamp", timestamp)
                put("songs", array)
            }
            File(context.filesDir, CACHE_FILE_NAME).writeText(root.toString())
        }
    }

    /**
     * Resolves or refreshes the Discover Mix playlist.
     * Strictly excludes all library artists and tracks, with zero fallback to familiar songs.
     */
    suspend fun getOrRefreshDiscoverMix(
        playableSongs: List<Song>,
        forceRefresh: Boolean = false
    ): List<Song> = withContext(Dispatchers.IO) {
        val cached = loadCachedDiscoverMix()
        val now = System.currentTimeMillis()

        // 1. Check 24-hour cache validity
        if (!forceRefresh && cached != null && (now - cached.first) < TTL_MILLIS && cached.second.isNotEmpty()) {
            return@withContext cached.second
        }

        val rng = if (forceRefresh) {
            kotlin.random.Random(System.currentTimeMillis())
        } else {
            kotlin.random.Random(now / TTL_MILLIS)
        }

        // 2. Check network connectivity
        if (!isNetworkConnected()) {
            Log.i(TAG, "Device is offline. Preserving cached Discover mix.")
            if (cached != null && cached.second.isNotEmpty()) {
                return@withContext cached.second
            }
            return@withContext emptyList()
        }

        // 3. Online Recommendation Generation
        try {
            val allLibrarySongs = songRepository.allSongs.firstOrNull() ?: playableSongs

            // Build complete exclusion sets for artists, titles, and videoIds in library
            val libraryArtistNames = allLibrarySongs
                .flatMap { normalizeArtistTokens(it.artistName) }
                .toSet()

            val libraryTrackTitles = allLibrarySongs
                .map { it.title.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotBlank() }
                .toSet()

            val libraryVideoIds = allLibrarySongs
                .mapNotNull { it.remoteVideoId }
                .toSet()

            // 4. Extract up to 10 ranked seed artists
            val seedArtists = songRepository.getDiscoverSeedArtists(maxSeeds = 10)
            if (seedArtists.isEmpty()) {
                if (cached != null && cached.second.isNotEmpty()) return@withContext cached.second
                return@withContext emptyList()
            }

            // 5. Query similar artists in parallel for each seed artist
            val discoveredArtists = coroutineScope {
                seedArtists.map { seed ->
                    async {
                        similarArtistService.findSimilarArtists(seed, limit = 10)
                    }
                }.flatMap { it.await() }
            }

            // 6. Strictly filter out any artist matching the user's library
            val uniqueCandidateArtists = discoveredArtists
                .map { it.trim() }
                .filter { artist ->
                    val lower = artist.lowercase(Locale.ROOT)
                    lower.isNotBlank() &&
                            lower != "<unknown>" &&
                            lower != "youtube music" &&
                            normalizeArtistTokens(artist).none { it in libraryArtistNames }
                }
                .distinctBy { it.lowercase(Locale.ROOT) }

            // 7. Multi-degree expansion if candidate artists are fewer than 8
            val expandedArtists = if (uniqueCandidateArtists.size < 8) {
                val seedsForExpansion = if (uniqueCandidateArtists.isNotEmpty()) uniqueCandidateArtists else seedArtists
                similarArtistService.expandSimilarArtists(seedsForExpansion, limitPerArtist = 5, maxTotal = 20)
                    .filter { artist ->
                        val tokens = normalizeArtistTokens(artist)
                        tokens.isNotEmpty() && tokens.none { it in libraryArtistNames }
                    }
            } else {
                emptyList()
            }

            val finalTargetArtists = (uniqueCandidateArtists + expandedArtists)
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(10)

            if (finalTargetArtists.isEmpty()) {
                Log.w(TAG, "No candidate artists discovered outside user's library.")
                if (cached != null && cached.second.isNotEmpty()) return@withContext cached.second
                return@withContext emptyList()
            }

            // 8. Fetch top streamable tracks for the target artists
            val candidateTracks = coroutineScope {
                finalTargetArtists.map { artist ->
                    async {
                        val result = remoteMusicRepository.searchSongsByArtist(artist, limit = 8)
                        result.getOrNull().orEmpty()
                    }
                }.flatMap { it.await() }
            }

            // 9. Filter out any tracks matching existing library songs or artists
            val filteredDiscoveredTracks = candidateTracks.filter { song ->
                val vid = song.remoteVideoId
                val titleNorm = song.title.trim().lowercase(Locale.ROOT)
                val artistNorm = song.artistName.trim().lowercase(Locale.ROOT)
                (vid == null || vid !in libraryVideoIds) &&
                        titleNorm !in libraryTrackTitles &&
                        normalizeArtistTokens(artistNorm).none { it in libraryArtistNames }
            }.distinctBy { it.cacheKey }

            // 10. Shuffle recommendations
            val shuffled = filteredDiscoveredTracks.shuffled(rng).take(TARGET_SONG_COUNT)

            // 11. Cache and return if sufficient tracks discovered (>= MIN_SONG_COUNT)
            if (shuffled.size >= MIN_SONG_COUNT) {
                saveCachedDiscoverMix(now, shuffled)
                Log.i(TAG, "Successfully generated and cached ${shuffled.size} discovered tracks.")
                return@withContext shuffled
            } else if (cached != null && cached.second.size >= MIN_SONG_COUNT) {
                Log.w(TAG, "Only found ${shuffled.size} new tracks; keeping previous cached mix.")
                return@withContext cached.second
            } else {
                Log.w(TAG, "Discovered tracks below minimum (${shuffled.size}); returning empty list to avoid familiar songs.")
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating discover mix recommendations", e)
            if (cached != null && cached.second.isNotEmpty()) {
                return@withContext cached.second
            }
            return@withContext emptyList()
        }
    }
}
