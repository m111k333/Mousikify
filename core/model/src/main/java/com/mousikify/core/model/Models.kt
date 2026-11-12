package com.mousikify.core.model

enum class AlbumType {
    ALBUM,
    EP,
    SINGLE
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val songCount: Int = 0,
    val year: Int = 0,
    val artworkUri: String? = null,
    val albumType: AlbumType = AlbumType.ALBUM,
    val totalDurationMs: Long = 0L,
    val releaseDate: String? = null,
    val recordLabel: String? = null,
    val playlistUrl: String? = null
)

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val avatarUrl: String? = null,
    val monthlyListeners: String? = null,
    val bio: String? = null,
    val channelUrl: String? = null
)

data class ArtistProfile(
    val artist: Artist,
    val topSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val singles: List<Album> = emptyList(),
    val featuredIn: List<Song> = emptyList(),
    val similarArtists: List<Artist> = emptyList(),
    val isLoading: Boolean = false
)

data class AlbumProfile(
    val album: Album,
    val tracks: List<Song> = emptyList(),
    val otherVersions: List<Album> = emptyList(),
    val isLoading: Boolean = false
)

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val customCoverUri: String? = null,
    val songCount: Int = 0
)

data class LyricWord(
    val timestampMs: Long,
    val text: String
)

data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val isSynced: Boolean = true
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class EqPreset(
    val presetId: Long = 0,
    val name: String,
    val bandLevelsMb: List<Int>, // 10 bands in millibels (-1500 to +1500)
    val bassBoostStrength: Int = 0,  // 0 to 1000
    val virtualizerStrength: Int = 0, // 0 to 1000
    val reverbPreset: Short = 0, // PresetReverb presets
    val isCustom: Boolean = false
)

data class EqBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Int
)

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int
)

/**
 * Generates a deterministic negative Long ID for remote YouTube videoIds so they never collide
 * with positive MediaStore IDs or SAF bit-flagged IDs in Room and QueueManager.
 */
fun String.toRemoteSongId(): Long {
    var h = 1125899906842597L
    for (i in indices) {
        h = 31 * h + this[i].code
    }
    val positiveHash = h and 0x3FFFFFFFFFFFFFFFL
    return -(positiveHash.coerceAtLeast(1L))
}

/**
 * Playback State Machine for local and remote streaming.
 */
sealed interface PlaybackStatus {
    data object Idle : PlaybackStatus
    data class Preparing(val isResolvingStream: Boolean) : PlaybackStatus
    data class Buffering(val bufferedPercentage: Int) : PlaybackStatus
    data object Playing : PlaybackStatus
    data object Paused : PlaybackStatus
    data class Error(
        val errorCode: Int,
        val message: String,
        val isRecoverable: Boolean
    ) : PlaybackStatus
}

enum class PlaybackContextType {
    PLAYLIST,
    ALBUM,
    ARTIST,
    MIX,
    LIBRARY,
    SEARCH
}

data class PlaybackContext(
    val title: String,
    val type: PlaybackContextType,
    val id: String? = null,
    val albumId: Long? = null,
    val playlistId: Long? = null,
    val artistName: String? = null,
    val isLocalOrSaved: Boolean = false,
    val artworkUri: String? = null,
    val remotePlaylistUrl: String? = null
)

enum class RecentlyPlayedType {
    TRACK,
    ALBUM,
    PLAYLIST
}

data class RecentlyPlayedItem(
    val id: Long = 0L,
    val type: RecentlyPlayedType,
    val referenceId: String,
    val title: String,
    val subtitle: String,
    val artworkUri: String? = null,
    val playedAt: Long,
    val song: Song? = null,
    val album: Album? = null,
    val playlist: Playlist? = null,
    val remotePlaylistUrl: String? = null
)

data class GenreCategory(
    val id: String,
    val name: String,
    val queryKeyword: String,
    val gradientColors: List<Long>,
    val iconName: String? = null
)

data class SearchDiscoveryData(
    val recommendedSongs: List<Song> = emptyList(),
    val exploreArtists: List<Artist> = emptyList(),
    val exploreAlbums: List<Album> = emptyList(),
    val genres: List<GenreCategory> = emptyList(),
    val isLoading: Boolean = false
)

val CURATED_SEED_ARTISTS: List<String> = listOf(
    "The Weeknd",
    "Daft Punk",
    "Coldplay",
    "Radiohead",
    "Kendrick Lamar",
    "Fleetwood Mac",
    "Billie Eilish",
    "Tame Impala",
    "Gorillaz",
    "Taylor Swift",
    "Queen"
)



