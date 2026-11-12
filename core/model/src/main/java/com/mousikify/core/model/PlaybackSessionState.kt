package com.mousikify.core.model

/**
 * Consolidated playback session state emitted by MusicController.
 * Simplifies presentation ViewModels by aggregating playback state, queue context,
 * and library favorite/cache update signals into a single reactive flow.
 */
data class PlaybackSessionState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val playbackContext: PlaybackContext? = null,
    val favoriteSongIds: Set<Long> = emptySet(),
    val favoriteVideoIds: Set<String> = emptySet(),
    val cacheUpdateTick: Long = 0L
) {
    fun isFavorite(song: Song): Boolean {
        if (song.id in favoriteSongIds) return true
        val vid = song.remoteVideoId
        if (vid != null && vid in favoriteVideoIds) return true
        return song.isFavorite
    }
}
