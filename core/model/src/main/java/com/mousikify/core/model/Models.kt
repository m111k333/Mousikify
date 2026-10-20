package com.mousikify.core.model

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val songCount: Int = 0,
    val year: Int = 0,
    val artworkUri: String? = null
)

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
    val albumCount: Int = 0
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
