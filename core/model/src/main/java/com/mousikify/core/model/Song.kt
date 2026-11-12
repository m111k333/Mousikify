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
    val artworkUri: String? = null,
    val isExplicit: Boolean = false,
    val isAlbumSaved: Boolean = false,
    val viewCount: Long? = null,
    val favoritedAt: Long? = null
) {
    val cleanArtistName: String
        get() = cleanArtistName(artistName)

    val isRemoteStream: Boolean

        get() = contentUri.startsWith("ytm://", ignoreCase = true)

    val isLocal: Boolean
        get() = !isRemoteStream

    val remoteVideoId: String?
        get() = if (isRemoteStream) contentUri.removePrefix("ytm://").substringBefore("?") else null

    val cacheKey: String
        get() = remoteVideoId ?: id.toString()

    val displayCount: String?
        get() = formatPlayOrViewCount()

    fun formatPlayOrViewCount(): String? {
        if (!isRemoteStream) return null
        val count = viewCount ?: return null
        if (count <= 0) return null
        val raw = when {
            count >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.1fB views", count / 1_000_000_000.0)
            count >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM views", count / 1_000_000.0)
            count >= 1_000L -> String.format(java.util.Locale.US, "%.1fK views", count / 1_000.0)
            else -> "$count views"
        }
        return raw.replace(".0", "")
    }
}

/**
 * Strips uploader attribution (" • via <uploader>", "via <uploader>") and topic suffixes (" - Topic")
 * to recover the canonical recording artist name.
 */
fun cleanArtistName(raw: String): String {
    val trimmed = raw.trim()
    val withoutVia = if (trimmed.contains(" • via ", ignoreCase = true)) {
        trimmed.split(Regex(" • via ", RegexOption.IGNORE_CASE), limit = 2)[0].trim()
    } else if (trimmed.startsWith("via ", ignoreCase = true)) {
        trimmed.substring(4).trim()
    } else {
        trimmed
    }
    return withoutVia.replace(" - Topic", "", ignoreCase = true).trim()
}
