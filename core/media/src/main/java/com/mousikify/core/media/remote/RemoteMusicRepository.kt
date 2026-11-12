package com.mousikify.core.media.remote

import android.net.Uri
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.mousikify.core.media.diagnostics.DiagnosticsLog
import com.mousikify.core.media.recommendation.SimilarArtistDiscoveryService
import com.mousikify.core.media.remote.YouTubeMusicSearchPagingSource.Companion.toSong
import com.mousikify.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class RemotePlaylist(
    val url: String,
    val name: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val streamCount: Long
)

data class ResolvedStreamManifest(
    val videoId: String,
    val streamUrl: String,
    val mimeType: String,
    val bitrate: Int,
    val durationMs: Long,
    val highResArtworkUrl: String?,
    val expiryEpochMs: Long,
    val userAgent: String = OkHttpNewPipeDownloader.USER_AGENT
)

data class ResolverProbeResult(
    val resolverName: String,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val bitrate: Int? = null,
    val format: String? = null,
    val errorMessage: String? = null
)

data class StreamingDiagnosticReport(
    val timestampMs: Long = System.currentTimeMillis(),
    val sampleVideoId: String,
    val searchOk: Boolean,
    val searchLatencyMs: Long,
    val searchItemCount: Int,
    val searchError: String? = null,
    val resolverResults: List<ResolverProbeResult>,
    val overallVerdict: String,
    val isOperational: Boolean,
    val isDegraded: Boolean,
    val cachedUrlCount: Int
)

/**
 * Keyless repository resolving YouTube Music search queries, track metadata, and optimal
 * audio-only streams (prioritizing WebM/Opus ~160kbps or M4A/AAC ~128kbps).
 */
@Singleton
class RemoteMusicRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val diagnosticsLog: DiagnosticsLog,
    private val similarArtistDiscoveryService: SimilarArtistDiscoveryService
) {
    private val urlCache = ConcurrentHashMap<String, ResolvedStreamManifest>()
    private val mutexMap = ConcurrentHashMap<String, Mutex>()

    private data class InnerTubeClientProfile(
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val clientScreen: String = "WATCH",
        val platform: String = "MOBILE",
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val androidSdkVersion: Int? = null
    )

    @Volatile
    private var cachedVisitorData: String? = null

    @Volatile
    private var visitorDataFetchedAtMs: Long = 0L

    companion object {
        private const val TAG = "RemoteMusicRepo"
        private const val INNERTUBE_PLAYER_URL =
            "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false"
        private const val INNERTUBE_VISITOR_ID_URL =
            "https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false"
        private const val CPN_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * 2026 Keyless InnerTube client profiles (led by VISIONOS 1.02 from NewPipeExtractor PR #1508)
         * that bypass YouTube SABR enforcement and bot checks without requiring user credentials or PO tokens.
         */
        private val INNERTUBE_CLIENTS = listOf(
            InnerTubeClientProfile(
                clientName = "VISIONOS",
                clientVersion = "1.02",
                userAgent = "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; GB)",
                deviceMake = "Apple",
                deviceModel = "RealityDevice14,1",
                osName = "visionOS",
                osVersion = "25.6.0.23O471"
            ),
            InnerTubeClientProfile(
                clientName = "IOS",
                clientVersion = "21.03.2",
                userAgent = "com.google.ios.youtube/21.03.2(iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; GB)",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iOS",
                osVersion = "18.7.2.22H124"
            ),
            InnerTubeClientProfile(
                clientName = "ANDROID_VR",
                clientVersion = "1.60.19",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 14; en_US; Quest 3; Build/UP1A.231005.007.A1; Cronet/132.0.6808.3)",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osName = "Android",
                osVersion = "14",
                androidSdkVersion = 34
            )
        )

        fun parsePlaylistTrackAttribution(song: Song): Song {
            val rawTitle = song.title.trim()
            val rawUploader = song.artistName.trim().removeSuffix(" - Topic")

            val delimiters = listOf(" - ", " – ", " — ", ": ")
            for (delim in delimiters) {
                if (rawTitle.contains(delim)) {
                    val parts = rawTitle.split(delim, limit = 2)
                    val potentialArtist = parts[0].trim()
                    val potentialTitle = parts[1].trim()
                    if (potentialArtist.isNotBlank() && potentialTitle.isNotBlank()) {
                        val formattedArtist = if (!rawUploader.equals(potentialArtist, ignoreCase = true) &&
                            rawUploader.isNotBlank() &&
                            !rawUploader.equals("YouTube Music", ignoreCase = true)
                        ) {
                            "$potentialArtist • via $rawUploader"
                        } else {
                            potentialArtist
                        }
                        return song.copy(
                            title = potentialTitle,
                            artistName = formattedArtist
                        )
                    }
                }
            }

            val formattedArtist = if (rawUploader.isNotBlank() && !rawUploader.equals("YouTube Music", ignoreCase = true)) {
                "via $rawUploader"
            } else {
                song.artistName
            }
            return song.copy(artistName = formattedArtist)
        }

        fun extractSearchTargetTitle(query: String): String {
            val trimmed = query.trim()
            val delimiters = listOf(" - ", " – ", " — ", ": ")
            for (delim in delimiters) {
                if (trimmed.contains(delim)) {
                    val after = trimmed.substringAfter(delim).trim()
                    if (after.isNotBlank()) return after
                }
            }
            return trimmed
        }
    }

    init {
        OkHttpNewPipeDownloader.ensureInitialized(okHttpClient)
    }

    /**
     * Returns a Paging 3 stream of YouTube Music songs for infinite scrolling in Compose.
     */
    fun searchMusicPaged(query: String): Flow<PagingData<Song>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 5,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                YouTubeMusicSearchPagingSource(
                    query = query,
                    contentFilter = YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
                )
            }
        ).flow
    }

    /**
     * One-shot search for streaming songs on YouTube Music by artist or query (capped at limit).
     */
    suspend fun searchSongsByArtist(artist: String, limit: Int = 10): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            val trimmed = artist.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
            val start = System.currentTimeMillis()
            runCatching {
                val ytService = ServiceList.YouTube
                val qh = ytService.searchQHFactory.fromQuery(
                    trimmed,
                    listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                    ""
                )
                val extractor = ytService.getSearchExtractor(qh)
                extractor.fetchPage()
                val songs = extractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { it.toSong() }
                    .take(limit)
                val elapsed = System.currentTimeMillis() - start
                diagnosticsLog.info("Search", "Found ${songs.size} songs for artist '$trimmed' in ${elapsed}ms")
                songs
            }.onFailure { e ->
                diagnosticsLog.warn("Search", "Artist search failed for '$trimmed': ${e.message}")
            }
        }

    /**
     * Resolves an online artist profile with top songs, albums, singles, and similar artists.
     */
    suspend fun getRemoteArtistProfile(
        artistName: String,
        channelUrl: String? = null,
        avatarUrl: String? = null
    ): Result<com.mousikify.core.model.ArtistProfile> =
        withContext(Dispatchers.IO) {
            val trimmed = artistName.trim()
            if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Artist name empty"))
            runCatching {
                coroutineScope {
                    val songsDeferred = async { searchSongsByArtist(trimmed, limit = 25).getOrDefault(emptyList()) }
                    val albumsDeferred = async { searchAlbums(trimmed, limit = 25).getOrDefault(emptyList()) }
                    val similarDeferred = async { similarArtistDiscoveryService.findSimilarArtistsDetailed(trimmed, limit = 8) }
                    val artistInfoDeferred = async {
                        if (channelUrl == null || avatarUrl == null) {
                            searchArtists(trimmed, limit = 1).getOrNull()?.firstOrNull()
                        } else null
                    }

                    val songs = songsDeferred.await()
                    val realAlbums = albumsDeferred.await()
                    val similar = similarDeferred.await()
                    val artistInfo = artistInfoDeferred.await()
                    val topSongs = songs.take(16)
                    val resolvedAvatar = avatarUrl ?: artistInfo?.avatarUrl ?: songs.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
                    val resolvedChannelUrl = channelUrl ?: artistInfo?.channelUrl
                    val resolvedListeners = artistInfo?.monthlyListeners

                    val filteredAlbums = realAlbums.filter { alb ->
                        val cleanArtist = alb.artist.replace(" - Topic", "", ignoreCase = true).trim()
                        val matchesArtist = cleanArtist.equals(trimmed, ignoreCase = true) ||
                            cleanArtist.contains(trimmed, ignoreCase = true) ||
                            trimmed.contains(cleanArtist, ignoreCase = true)
                        val isThirdParty = alb.artist.contains("Various Artists", ignoreCase = true) ||
                            alb.artist.contains("Tribute", ignoreCase = true) ||
                            alb.title.contains("Tribute", ignoreCase = true) ||
                            alb.artist.contains("Karaoke", ignoreCase = true) ||
                            alb.title.contains("Karaoke", ignoreCase = true)
                        matchesArtist && !isThirdParty
                    }

                    val releases = if (filteredAlbums.isNotEmpty()) {
                        filteredAlbums
                    } else {
                        songs.groupBy { it.albumName.ifBlank { "Singles & EPs" } }.map { (albName, albSongs) ->
                            val first = albSongs.first()
                            com.mousikify.core.model.Album(
                                id = first.albumId,
                                title = albName,
                                artist = trimmed,
                                artistId = first.artistId,
                                songCount = albSongs.size,
                                year = albSongs.maxOfOrNull { it.year } ?: 0,
                                artworkUri = albSongs.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                                albumType = if (albSongs.size <= 2) com.mousikify.core.model.AlbumType.SINGLE else com.mousikify.core.model.AlbumType.ALBUM,
                                totalDurationMs = albSongs.sumOf { it.durationMs }
                            )
                        }
                    }

                    val (singles, fullAlbums) = releases.partition {
                        it.songCount in 1..2 ||
                        it.title.contains("Single", ignoreCase = true) ||
                        it.title.contains(" - EP", ignoreCase = true) ||
                        it.albumType == com.mousikify.core.model.AlbumType.SINGLE ||
                        it.albumType == com.mousikify.core.model.AlbumType.EP
                    }

                    val artist = com.mousikify.core.model.Artist(
                        id = trimmed.hashCode().toLong(),
                        name = trimmed,
                        songCount = songs.size,
                        albumCount = fullAlbums.size + singles.size,
                        avatarUrl = resolvedAvatar,
                        monthlyListeners = resolvedListeners,
                        channelUrl = resolvedChannelUrl
                    )

                    com.mousikify.core.model.ArtistProfile(
                        artist = artist,
                        topSongs = topSongs,
                        albums = fullAlbums,
                        singles = singles,
                        similarArtists = similar
                    )
                }
            }
        }

    /**
     * Resolves an online album profile with numbered tracklist.
     */
    suspend fun getRemoteAlbumProfile(
        albumTitle: String,
        artistName: String,
        artworkUri: String? = null,
        playlistUrl: String? = null
    ): Result<com.mousikify.core.model.AlbumProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tracks = if (!playlistUrl.isNullOrBlank()) {
                    getPlaylistTracks(playlistUrl).getOrDefault(emptyList())
                } else {
                    searchMusicQuick("$artistName $albumTitle").getOrDefault(emptyList())
                }.mapIndexed { index, song ->
                    song.copy(trackNumber = index + 1)
                }

                val totalDuration = tracks.sumOf { it.durationMs }
                val type = when {
                    tracks.size <= 2 -> com.mousikify.core.model.AlbumType.SINGLE
                    tracks.size <= 5 -> com.mousikify.core.model.AlbumType.EP
                    else -> com.mousikify.core.model.AlbumType.ALBUM
                }
                val albumYear = tracks.maxOfOrNull { it.year } ?: 0
                val album = com.mousikify.core.model.Album(
                    id = "$artistName-$albumTitle".hashCode().toLong(),
                    title = albumTitle,
                    artist = artistName,
                    artistId = artistName.hashCode().toLong(),
                    songCount = tracks.size,
                    year = albumYear,
                    artworkUri = artworkUri ?: tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                    albumType = type,
                    totalDurationMs = totalDuration,
                    releaseDate = if (albumYear > 0) "$albumYear" else null,
                    playlistUrl = playlistUrl
                )

                com.mousikify.core.model.AlbumProfile(
                    album = album,
                    tracks = tracks
                )
            }
        }

    /**
     * Searches YouTube Music for artists matching the query.
     */
    suspend fun searchArtists(query: String, limit: Int = 10): Result<List<com.mousikify.core.model.Artist>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
            val start = System.currentTimeMillis()
            runCatching {
                val ytService = ServiceList.YouTube
                val qh = ytService.searchQHFactory.fromQuery(
                    trimmed,
                    listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS),
                    ""
                )
                val extractor = ytService.getSearchExtractor(qh)
                extractor.fetchPage()
                val artists = extractor.initialPage.items
                    .filterIsInstance<ChannelInfoItem>()
                    .map { item ->
                        val avatar = item.thumbnails.maxByOrNull { it.height * it.width }?.url
                        val subCount = item.subscriberCount
                        val subText = if (subCount > 0) {
                            formatSubscriberCount(subCount)
                        } else {
                            "Artist"
                        }
                        com.mousikify.core.model.Artist(
                            id = item.url.hashCode().toLong(),
                            name = item.name?.trim().orEmpty().ifBlank { "Unknown Artist" },
                            songCount = item.streamCount.toInt(),
                            avatarUrl = avatar,
                            monthlyListeners = subText,
                            channelUrl = item.url
                        )
                    }
                    .take(limit)
                val elapsed = System.currentTimeMillis() - start
                diagnosticsLog.info("Search", "Found ${artists.size} artists for '$trimmed' in ${elapsed}ms")
                artists
            }.onFailure { e ->
                diagnosticsLog.warn("Search", "Artist search failed for '$trimmed': ${e.message}")
            }
        }

    /**
     * Searches YouTube Music for albums matching the query.
     */
    suspend fun searchAlbums(query: String, limit: Int = 15): Result<List<com.mousikify.core.model.Album>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
            val start = System.currentTimeMillis()
            runCatching {
                val ytService = ServiceList.YouTube
                val qh = ytService.searchQHFactory.fromQuery(
                    trimmed,
                    listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
                    ""
                )
                val extractor = ytService.getSearchExtractor(qh)
                extractor.fetchPage()
                val albums = extractor.initialPage.items
                    .filterIsInstance<PlaylistInfoItem>()
                    .map { item ->
                        val cover = item.thumbnails.maxByOrNull { it.height * it.width }?.url
                        val artistName = item.uploaderName?.trim().orEmpty().ifBlank { "Unknown Artist" }
                        com.mousikify.core.model.Album(
                            id = item.url.hashCode().toLong(),
                            title = item.name?.trim().orEmpty().ifBlank { "Unknown Album" },
                            artist = artistName,
                            artistId = artistName.hashCode().toLong(),
                            songCount = item.streamCount.toInt(),
                            artworkUri = cover,
                            playlistUrl = item.url,
                            albumType = com.mousikify.core.model.AlbumType.ALBUM
                        )
                    }
                    .take(limit)
                val elapsed = System.currentTimeMillis() - start
                diagnosticsLog.info("Search", "Found ${albums.size} albums for '$trimmed' in ${elapsed}ms")
                albums
            }.onFailure { e ->
                diagnosticsLog.warn("Search", "Album search failed for '$trimmed': ${e.message}")
            }
        }

    private fun formatSubscriberCount(count: Long): String {
        return when {
            count >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1fB subscribers", count / 1_000_000_000.0)
            count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM subscribers", count / 1_000_000.0)
            count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK subscribers", count / 1_000.0)
            else -> "$count subscribers"
        }
    }

    /**
     * Performs a fast first-page search on YouTube Music (with fallback to general YouTube videos
     * if MUSIC_SONGS returns empty) for unified search states.
     */
    suspend fun searchPlaylists(query: String): Result<List<RemotePlaylist>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
            val start = System.currentTimeMillis()
            runCatching {
                val ytService = ServiceList.YouTube
                val qh = ytService.searchQHFactory.fromQuery(
                    trimmed,
                    listOf(YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS),
                    ""
                )
                val extractor = ytService.getSearchExtractor(qh)
                extractor.fetchPage()
                val playlists = extractor.initialPage.items
                    .filterIsInstance<PlaylistInfoItem>()
                    .map { item ->
                        val rawThumb = item.thumbnails.maxByOrNull { it.height * it.width }?.url
                        RemotePlaylist(
                            url = item.url,
                            name = item.name?.trim().orEmpty().ifBlank { "YouTube Music Playlist" },
                            uploader = item.uploaderName?.trim().orEmpty(),
                            thumbnailUrl = rawThumb,
                            streamCount = item.streamCount
                        )
                    }
                    .take(15)
                val elapsed = System.currentTimeMillis() - start
                diagnosticsLog.info("Search", "Found ${playlists.size} playlists for '$trimmed' in ${elapsed}ms")
                playlists
            }.onFailure { e ->
                diagnosticsLog.warn("Search", "Playlist search failed for '$trimmed': ${e.message}")
            }
        }

    /**
     * Fetches tracks from a YouTube Music playlist URL (capped at 500 items).
     */
    suspend fun getPlaylistTracks(playlistUrl: String): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                val ytService = ServiceList.YouTube
                val normalizedUrl = playlistUrl.replace("music.youtube.com", "www.youtube.com")
                val playlistInfo = PlaylistInfo.getInfo(ytService, normalizedUrl)
                val tracks = mutableListOf<Song>()
                
                playlistInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { it.toSong() }
                    .let { tracks.addAll(it) }

                var hasNext = playlistInfo.hasNextPage()
                var nextPage = if (hasNext) playlistInfo.nextPage else null

                // shortcut: cap at 500 tracks to avoid long network hangs on massive playlists, upgrade if user demands unlimited
                while (hasNext && nextPage != null && tracks.size < 500) {
                    val moreItems = PlaylistInfo.getMoreItems(ytService, normalizedUrl, nextPage)
                    moreItems.items
                        .filterIsInstance<StreamInfoItem>()
                        .mapNotNull { it.toSong() }
                        .let { tracks.addAll(it) }
                    hasNext = moreItems.hasNextPage()
                    nextPage = if (hasNext) moreItems.nextPage else null
                }

                val elapsed = System.currentTimeMillis() - start
                diagnosticsLog.info("Search", "Loaded ${tracks.size} tracks from playlist in ${elapsed}ms")
                tracks
            }.onFailure { e ->
                diagnosticsLog.warn("Search", "Failed to load playlist tracks for '$playlistUrl': ${e.message}")
            }
        }

    /**
     * Performs a fast first-page search on YouTube Music (with fallback to general YouTube videos
     * if MUSIC_SONGS returns empty) for unified search states.
     */
    suspend fun searchMusicQuick(query: String): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())

            val start = System.currentTimeMillis()
            runCatching {
                val ytService = ServiceList.YouTube
                // 1. Prioritize YouTube video search for tracks with accurate view counts & upload dates
                val videoQh = ytService.searchQHFactory.fromQuery(
                    trimmed,
                    listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                    ""
                )
                val videoExtractor = ytService.getSearchExtractor(videoQh)
                videoExtractor.fetchPage()
                val videoTracks = videoExtractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { it.toSong() }

                // Check if the searched song title appears in the returned tracks
                val targetTitle = extractSearchTargetTitle(trimmed)
                val targetWords = targetTitle.lowercase().split(" ").filter { it.isNotBlank() }
                val hasMatchingTitle = videoTracks.any { track ->
                    val titleLower = track.title.lowercase()
                    titleLower.contains(targetTitle.lowercase()) ||
                        (targetWords.size > 1 && targetWords.all { titleLower.contains(it) })
                }

                // If searched title does NOT appear in video results, search community playlists
                if (!hasMatchingTitle || videoTracks.isEmpty()) {
                    val candidatePlaylists = searchPlaylists(trimmed).getOrDefault(emptyList()).take(3)
                    if (candidatePlaylists.isNotEmpty()) {
                        val playlistCandidateTracks = mutableListOf<Song>()
                        val queryLower = targetTitle.lowercase()

                        for (pl in candidatePlaylists) {
                            val tracks = getPlaylistTracks(pl.url).getOrDefault(emptyList())
                            val matched = tracks.filter { track ->
                                val trackTitle = track.title.lowercase()
                                val trackArtist = track.artistName.lowercase()
                                trackTitle.contains(queryLower) ||
                                    (targetWords.size > 1 && targetWords.all { trackTitle.contains(it) || trackArtist.contains(it) })
                            }
                            playlistCandidateTracks.addAll(matched)
                            if (playlistCandidateTracks.size >= 10) break
                        }

                        if (playlistCandidateTracks.isNotEmpty()) {
                            val combined = (playlistCandidateTracks + videoTracks).distinctBy { it.contentUri }
                            val elapsed = System.currentTimeMillis() - start
                            diagnosticsLog.info(
                                "Search",
                                "Found ${combined.size} tracks (${playlistCandidateTracks.size} prepended from community playlists) for '$trimmed' in ${elapsed}ms"
                            )
                            return@runCatching combined
                        }
                    }
                }

                if (videoTracks.isNotEmpty()) {
                    val elapsed = System.currentTimeMillis() - start
                    diagnosticsLog.success("Search", "Found ${videoTracks.size} video tracks for '$trimmed' in ${elapsed}ms")
                    return@runCatching videoTracks
                }

                // 3. Fallback to YouTube Music Songs filter if Videos returned empty
                val musicTracks = runCatching {
                    val qh = ytService.searchQHFactory.fromQuery(
                        trimmed,
                        listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                        ""
                    )
                    val extractor = ytService.getSearchExtractor(qh)
                    extractor.fetchPage()
                    extractor.initialPage.items
                        .filterIsInstance<StreamInfoItem>()
                        .mapNotNull { it.toSong() }
                }.getOrDefault(emptyList())

                val elapsed = System.currentTimeMillis() - start
                diagnosticsLog.info("Search", "Found ${musicTracks.size} music tracks for '$trimmed' via fallback in ${elapsed}ms")
                musicTracks
            }.onFailure { e ->
                diagnosticsLog.warn("Search", "Quick search failed for '$trimmed': ${e.message}")
            }
        }

    /**
     * Resolves the audio-only stream manifest on-demand for a given YouTube videoId.
     * Uses fast keyless InnerTube player clients first, with fallback to NewPipeExtractor.
     * Caches the resolved googlevideo URL in memory until 5 minutes before its expiration.
     */
    suspend fun resolveStreamManifest(videoId: String): Result<ResolvedStreamManifest> =
        withContext(Dispatchers.IO) {
            val cleanId = videoId.removePrefix("ytm://").substringBefore("?").trim()
            val trackMutex = mutexMap.getOrPut(cleanId) { Mutex() }

            trackMutex.withLock {
                val cached = urlCache[cleanId]
                val now = System.currentTimeMillis()
                if (cached != null && now < (cached.expiryEpochMs - 300_000L)) {
                    diagnosticsLog.info("StreamResolver", "Cache hit for $cleanId (${cached.mimeType}, ${cached.bitrate} bps)")
                    return@withContext Result.success(cached)
                }

                val resolveStart = System.currentTimeMillis()
                runCatching {
                    // 1. Try fast, cipher-free direct InnerTube player clients (ANDROID_VR, IOS, ANDROID_TESTSUITE)
                    for (client in INNERTUBE_CLIENTS) {
                        val clientStart = System.currentTimeMillis()
                        val manifest = runCatching {
                            resolveViaInnerTubeClient(cleanId, client, now)
                        }.onFailure { e ->
                            val clientElapsed = System.currentTimeMillis() - clientStart
                            Log.w(TAG, "InnerTube ${client.clientName} failed for $cleanId in ${clientElapsed}ms: ${e.message}")
                            diagnosticsLog.warn("StreamResolver", "Client ${client.clientName} failed for $cleanId: ${e.message}")
                        }.getOrNull()

                        if (manifest != null) {
                            val totalElapsed = System.currentTimeMillis() - resolveStart
                            diagnosticsLog.success("StreamResolver", "Resolved $cleanId via ${client.clientName} in ${totalElapsed}ms")
                            urlCache[cleanId] = manifest
                            return@runCatching manifest
                        }
                    }

                    // 2. Fallback to NewPipeExtractor StreamInfo
                    diagnosticsLog.info("StreamResolver", "InnerTube clients exhausted for $cleanId, attempting NewPipeExtractor fallback")
                    val watchUrl = "https://www.youtube.com/watch?v=$cleanId"
                    val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)

                    val optimalAudio = selectOptimalAudioStream(info.audioStreams)
                        ?: throw IllegalStateException("No playable audio-only stream found for $cleanId")

                    val streamUrl = optimalAudio.content
                    val expiryEpochMs = parseExpiryFromUrl(streamUrl)
                        ?: (now + 5 * 3600 * 1000L) // Default 5 hours TTL

                    val bestArtwork = info.thumbnails.maxByOrNull { it.width * it.height }?.url

                    val manifest = ResolvedStreamManifest(
                        videoId = cleanId,
                        streamUrl = streamUrl,
                        mimeType = optimalAudio.format?.mimeType ?: "audio/webm",
                        bitrate = optimalAudio.averageBitrate,
                        durationMs = if (info.duration > 0) info.duration * 1000L else 0L,
                        highResArtworkUrl = bestArtwork,
                        expiryEpochMs = expiryEpochMs,
                        userAgent = inferUserAgentForStreamUrl(streamUrl)
                    )

                    val totalElapsed = System.currentTimeMillis() - resolveStart
                    diagnosticsLog.success("StreamResolver", "Resolved $cleanId via NewPipeExtractor fallback in ${totalElapsed}ms")
                    urlCache[cleanId] = manifest
                    manifest
                }.onFailure { e ->
                    diagnosticsLog.error("StreamResolver", "All resolvers failed for $cleanId: ${e.message}", e.stackTraceToString())
                }
            }
        }

    private fun generateContentPlaybackNonce(): String {
        return buildString(16) {
            repeat(16) {
                append(CPN_ALPHABET.random())
            }
        }
    }

    private fun getOrFetchVisitorData(profile: InnerTubeClientProfile, nowMs: Long): String? {
        val current = cachedVisitorData
        if (!current.isNullOrBlank() && (nowMs - visitorDataFetchedAtMs) < 1_800_000L) {
            return current
        }
        return runCatching {
            val clientJson = JSONObject().apply {
                put("clientName", profile.clientName)
                put("clientVersion", profile.clientVersion)
                put("clientScreen", profile.clientScreen)
                put("platform", profile.platform)
                put("hl", "en-GB")
                put("gl", "GB")
                profile.deviceMake?.let { put("deviceMake", it) }
                profile.deviceModel?.let { put("deviceModel", it) }
                profile.osName?.let { put("osName", it) }
                profile.osVersion?.let { put("osVersion", it) }
                profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
            }
            val contextJson = JSONObject().apply {
                put("client", clientJson)
                put("request", JSONObject().apply {
                    put("internalExperimentFlags", org.json.JSONArray())
                    put("useSsl", true)
                })
                put("user", JSONObject().put("lockedSafetyMode", false))
            }
            val body = JSONObject().put("context", contextJson)
            val req = Request.Builder()
                .url(INNERTUBE_VISITOR_ID_URL)
                .header("User-Agent", profile.userAgent)
                .header("X-Goog-Api-Format-Version", "2")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val str = resp.body?.string() ?: return@use null
                val visitorData = JSONObject(str)
                    .optJSONObject("responseContext")
                    ?.optString("visitorData")
                    ?.takeIf { it.isNotBlank() }
                if (visitorData != null) {
                    cachedVisitorData = visitorData
                    visitorDataFetchedAtMs = nowMs
                }
                visitorData
            }
        }.getOrNull()
    }

    private fun resolveViaInnerTubeClient(
        videoId: String,
        profile: InnerTubeClientProfile,
        nowMs: Long
    ): ResolvedStreamManifest? {
        val visitorData = getOrFetchVisitorData(profile, nowMs)
        val cpn = generateContentPlaybackNonce()

        val clientJson = JSONObject().apply {
            put("clientName", profile.clientName)
            put("clientVersion", profile.clientVersion)
            put("clientScreen", profile.clientScreen)
            put("platform", profile.platform)
            put("hl", "en-GB")
            put("gl", "GB")
            visitorData?.let { put("visitorData", it) }
            profile.deviceMake?.let { put("deviceMake", it) }
            profile.deviceModel?.let { put("deviceModel", it) }
            profile.osName?.let { put("osName", it) }
            profile.osVersion?.let { put("osVersion", it) }
            profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
        }
        val contextJson = JSONObject().apply {
            put("client", clientJson)
            put("request", JSONObject().apply {
                put("internalExperimentFlags", org.json.JSONArray())
                put("useSsl", true)
            })
            put("user", JSONObject().put("lockedSafetyMode", false))
        }
        val payload = JSONObject().apply {
            put("context", contextJson)
            put("videoId", videoId)
            put("cpn", cpn)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val request = Request.Builder()
            .url("$INNERTUBE_PLAYER_URL&id=$videoId")
            .header("User-Agent", profile.userAgent)
            .header("X-Goog-Api-Format-Version", "2")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} from InnerTube ${profile.clientName}")
            }
            val bodyStr = response.body?.string() ?: return null
            val root = JSONObject(bodyStr)

            val playability = root.optJSONObject("playabilityStatus")
            val status = playability?.optString("status")
            if (status != "OK") {
                val reason = playability?.optString("reason") ?: status
                throw IllegalStateException("Playability $status: $reason")
            }

            val streamingData = root.optJSONObject("streamingData") ?: return null
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return null

            data class CandidateAudio(
                val itag: Int,
                val url: String,
                val mimeType: String,
                val bitrate: Int,
                val durationMs: Long
            )

            val candidates = mutableListOf<CandidateAudio>()
            for (i in 0 until adaptiveFormats.length()) {
                val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                val mime = fmt.optString("mimeType", "")
                if (!mime.startsWith("audio/", ignoreCase = true)) continue
                val url = fmt.optString("url", "")
                if (url.isBlank()) continue

                val itag = fmt.optInt("itag", 0)
                val bitrate = fmt.optInt("averageBitrate", fmt.optInt("bitrate", 128_000))
                val approxDurationMs = fmt.optString("approxDurationMs", "0").toLongOrNull() ?: 0L
                candidates.add(
                    CandidateAudio(
                        itag = itag,
                        url = url,
                        mimeType = mime.substringBefore(";").trim().ifEmpty { "audio/webm" },
                        bitrate = bitrate,
                        durationMs = approxDurationMs
                    )
                )
            }

            if (candidates.isEmpty()) return null

            // Prioritize itag 251 (Opus ~160kbps), then any audio/webm Opus, then itag 140 (M4A ~128kbps), then highest bitrate
            val chosen = candidates.firstOrNull { it.itag == 251 }
                ?: candidates.filter { it.mimeType.contains("webm", ignoreCase = true) }.maxByOrNull { it.bitrate }
                ?: candidates.firstOrNull { it.itag == 140 }
                ?: candidates.filter { it.mimeType.contains("mp4", ignoreCase = true) }.maxByOrNull { it.bitrate }
                ?: candidates.maxByOrNull { it.bitrate }
                ?: return null

            // If the URL has an obfuscated 'n' throttling parameter, delegate to NewPipeExtractor v0.26.5
            // so YoutubeJavaScriptPlayerManager deobfuscates 'n' properly.
            val hasRawNParam = runCatching {
                !Uri.parse(chosen.url).getQueryParameter("n").isNullOrBlank()
            }.getOrDefault(false)
            if (hasRawNParam) {
                return null
            }

            val finalStreamUrl = if (chosen.url.contains("cpn=")) {
                chosen.url
            } else {
                "${chosen.url}&cpn=$cpn"
            }

            val videoDetails = root.optJSONObject("videoDetails")
            val lengthSeconds = videoDetails?.optString("lengthSeconds", "0")?.toLongOrNull() ?: 0L
            val durationMs = if (chosen.durationMs > 0L) chosen.durationMs else lengthSeconds * 1000L

            var highResArtwork: String? = null
            val thumbnails = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            if (thumbnails != null && thumbnails.length() > 0) {
                highResArtwork = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
            }

            val expiryEpochMs = parseExpiryFromUrl(finalStreamUrl) ?: (nowMs + 5 * 3600 * 1000L)

            return ResolvedStreamManifest(
                videoId = videoId,
                streamUrl = finalStreamUrl,
                mimeType = chosen.mimeType,
                bitrate = chosen.bitrate,
                durationMs = durationMs,
                highResArtworkUrl = highResArtwork,
                expiryEpochMs = expiryEpochMs,
                userAgent = profile.userAgent
            )
        }
    }

    private fun inferUserAgentForStreamUrl(streamUrl: String): String {
        val clientParam = runCatching { Uri.parse(streamUrl).getQueryParameter("c") }.getOrNull() ?: ""
        return when {
            clientParam.contains("VISIONOS", ignoreCase = true) ->
                "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; GB)"
            clientParam.contains("IOS", ignoreCase = true) ->
                "com.google.ios.youtube/21.03.2(iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; GB)"
            clientParam.contains("ANDROID_VR", ignoreCase = true) ->
                "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 14; en_US; Quest 3; Build/UP1A.231005.007.A1; Cronet/132.0.6808.3)"
            clientParam.contains("ANDROID", ignoreCase = true) ->
                "com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip"
            else -> OkHttpNewPipeDownloader.USER_AGENT
        }
    }

    /**
     * Prioritizes WebM/Opus (~160kbps, itag 251) for best audio quality and fast progressive buffer,
     * falling back to M4A/AAC (~128kbps, itag 140) or any available audio stream with URL content.
     */
    private fun selectOptimalAudioStream(streams: List<AudioStream>): AudioStream? {
        val urlStreams = streams.filter { it.isUrl && it.content.isNotBlank() }
        if (urlStreams.isEmpty()) return null

        val opusStreams = urlStreams.filter {
            it.format == MediaFormat.WEBMA_OPUS || it.format == MediaFormat.WEBM
        }.sortedByDescending { it.averageBitrate }

        if (opusStreams.isNotEmpty()) {
            return opusStreams.first()
        }

        val m4aStreams = urlStreams.filter {
            it.format == MediaFormat.M4A
        }.sortedByDescending { it.averageBitrate }

        return m4aStreams.firstOrNull() ?: urlStreams.maxByOrNull { it.averageBitrate }
    }

    private fun parseExpiryFromUrl(streamUrl: String): Long? {
        return runCatching {
            Uri.parse(streamUrl).getQueryParameter("expire")?.toLongOrNull()?.times(1000L)
        }.getOrNull()
    }

    fun invalidateStreamUrl(videoId: String) {
        val cleanId = videoId.removePrefix("ytm://").substringBefore("?").trim()
        val removed = urlCache.remove(cleanId)
        if (removed != null) {
            diagnosticsLog.info("StreamResolver", "Invalidated cached stream URL for $cleanId")
        }
    }

    fun invalidateAllUrls() {
        val count = urlCache.size
        urlCache.clear()
        diagnosticsLog.info("StreamResolver", "Cleared all $count cached stream URLs")
    }

    fun getCachedUrlCount(): Int = urlCache.size

    /**
     * Executes an end-to-end diagnostics probe across all stream resolvers and search endpoints.
     * Tests each keyless InnerTube client and NewPipeExtractor separately without reading or writing cache.
     */
    suspend fun runStreamingDiagnostics(sampleVideoId: String = "dQw4w9WgXcQ"): StreamingDiagnosticReport =
        withContext(Dispatchers.IO) {
            val cleanId = sampleVideoId.removePrefix("ytm://").substringBefore("?").trim()
            val now = System.currentTimeMillis()
            diagnosticsLog.info("DiagnosticsProbe", "Starting stream health check on videoId '$cleanId'")

            // 1. Search Probe
            val searchStart = System.currentTimeMillis()
            var searchOk = false
            var searchCount = 0
            var searchError: String? = null
            try {
                val searchRes = searchMusicQuick("test").getOrThrow()
                searchOk = searchRes.isNotEmpty()
                searchCount = searchRes.size
                if (!searchOk) {
                    searchError = "Search returned 0 results"
                }
            } catch (e: Exception) {
                searchError = e.message ?: e.javaClass.simpleName
            }
            val searchLatencyMs = System.currentTimeMillis() - searchStart

            // 2. Probe each InnerTube client directly
            val resolverResults = mutableListOf<ResolverProbeResult>()
            var innerTubeWorkingCount = 0

            for (client in INNERTUBE_CLIENTS) {
                val start = System.currentTimeMillis()
                try {
                    val manifest = resolveViaInnerTubeClient(cleanId, client, now)
                    val latency = System.currentTimeMillis() - start
                    if (manifest != null) {
                        innerTubeWorkingCount++
                        resolverResults.add(
                            ResolverProbeResult(
                                resolverName = client.clientName,
                                isSuccess = true,
                                latencyMs = latency,
                                bitrate = manifest.bitrate,
                                format = manifest.mimeType
                            )
                        )
                    } else {
                        resolverResults.add(
                            ResolverProbeResult(
                                resolverName = client.clientName,
                                isSuccess = false,
                                latencyMs = latency,
                                errorMessage = "Empty streamingData or cipher-blocked"
                            )
                        )
                    }
                } catch (e: Exception) {
                    val latency = System.currentTimeMillis() - start
                    resolverResults.add(
                        ResolverProbeResult(
                            resolverName = client.clientName,
                            isSuccess = false,
                            latencyMs = latency,
                            errorMessage = e.message ?: e.javaClass.simpleName
                        )
                    )
                }
            }

            // 3. Probe NewPipeExtractor fallback directly
            val newPipeStart = System.currentTimeMillis()
            var newPipeSuccess = false
            try {
                val watchUrl = "https://www.youtube.com/watch?v=$cleanId"
                val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)
                val optimalAudio = selectOptimalAudioStream(info.audioStreams)
                val newPipeLatency = System.currentTimeMillis() - newPipeStart
                if (optimalAudio != null) {
                    newPipeSuccess = true
                    resolverResults.add(
                        ResolverProbeResult(
                            resolverName = "NewPipeExtractor",
                            isSuccess = true,
                            latencyMs = newPipeLatency,
                            bitrate = optimalAudio.averageBitrate,
                            format = optimalAudio.format?.mimeType ?: "audio/webm"
                        )
                    )
                } else {
                    resolverResults.add(
                        ResolverProbeResult(
                            resolverName = "NewPipeExtractor",
                            isSuccess = false,
                            latencyMs = newPipeLatency,
                            errorMessage = "No playable audio stream found"
                        )
                    )
                }
            } catch (e: Exception) {
                val newPipeLatency = System.currentTimeMillis() - newPipeStart
                resolverResults.add(
                    ResolverProbeResult(
                        resolverName = "NewPipeExtractor",
                        isSuccess = false,
                        latencyMs = newPipeLatency,
                        errorMessage = e.message ?: e.javaClass.simpleName
                    )
                )
            }

            val isOperational = innerTubeWorkingCount > 0 || newPipeSuccess
            val isDegraded = innerTubeWorkingCount == 0 && newPipeSuccess
            val overallVerdict = when {
                innerTubeWorkingCount == INNERTUBE_CLIENTS.size ->
                    "Optimal: All ${innerTubeWorkingCount} InnerTube clients and NewPipeExtractor are operational."
                innerTubeWorkingCount > 0 ->
                    "Operational: ${innerTubeWorkingCount} of ${INNERTUBE_CLIENTS.size} InnerTube clients working."
                newPipeSuccess ->
                    "Degraded: InnerTube clients failed. Streams falling back to NewPipeExtractor. InnerTube signatures need updating."
                else ->
                    "Critical: All stream resolvers failed. Network issue or NewPipeExtractor update required."
            }

            diagnosticsLog.info("DiagnosticsProbe", "Completed check: $overallVerdict")

            StreamingDiagnosticReport(
                sampleVideoId = cleanId,
                searchOk = searchOk,
                searchLatencyMs = searchLatencyMs,
                searchItemCount = searchCount,
                searchError = searchError,
                resolverResults = resolverResults,
                overallVerdict = overallVerdict,
                isOperational = isOperational,
                isDegraded = isDegraded,
                cachedUrlCount = urlCache.size
            )
        }
}
