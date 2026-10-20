package com.mousikify.core.model

data class Song(
    val id: Long,
    val contentUri: String,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val albumId: Long,
    val albumName: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val folderPath: String,
    val dateAdded: Long,
    val dateModified: Long,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val isFavorite: Boolean = false,
    val lrcPath: String? = null,
    val replayGainDb: Float? = null,
    val artworkUri: String? = null
) {
    val isRemoteStream: Boolean
        get() = contentUri.startsWith("ytm://", ignoreCase = true)

    val isLocal: Boolean
        get() = !isRemoteStream

    val remoteVideoId: String?
        get() = if (isRemoteStream) contentUri.removePrefix("ytm://").substringBefore("?") else null

    val cacheKey: String
        get() = remoteVideoId ?: id.toString()
}
