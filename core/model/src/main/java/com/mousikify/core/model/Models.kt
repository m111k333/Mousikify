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

data class LyricLine(
    val timestampMs: Long,
    val text: String
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
