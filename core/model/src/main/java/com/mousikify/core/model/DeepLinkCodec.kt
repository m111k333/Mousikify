package com.mousikify.core.model

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

sealed interface DeepLinkPayload {
    data class Track(val song: Song) : DeepLinkPayload
    data class Playlist(val name: String, val songs: List<Song>) : DeepLinkPayload
}

object DeepLinkCodec {
    const val BASE_WEB_URL = "https://m111k333.github.io/Mousikify/"
    const val WEB_HOST = "m111k333.github.io"
    const val WEB_PATH_PREFIX = "/Mousikify"
    const val CUSTOM_SCHEME = "mousikify"

    fun encodeTrackUrl(song: Song): String {
        val realVideoId = song.remoteVideoId?.takeIf { !it.startsWith("search_") && !it.startsWith("search:") && it.isNotBlank() }
        val params = linkedMapOf<String, String>()
        if (realVideoId != null) {
            params["v"] = realVideoId
        } else {
            val query = "${song.title} ${song.cleanArtistName}".trim().ifBlank { song.title }
            params["q"] = query
        }
        if (song.title.isNotBlank()) params["t"] = song.title
        if (song.artistName.isNotBlank()) params["a"] = song.cleanArtistName.ifBlank { song.artistName }
        if (song.albumName.isNotBlank() && !song.albumName.equals("YouTube Music", ignoreCase = true) && !song.albumName.equals("Unknown Album", ignoreCase = true)) {
            params["al"] = song.albumName
        }
        val durationSec = song.durationMs / 1000L
        if (durationSec > 0L) params["d"] = durationSec.toString()

        val queryString = params.entries.joinToString("&") { (k, v) ->
            "$k=${urlEncode(v)}"
        }
        return "$BASE_WEB_URL?$queryString"
    }

    fun encodePlaylistUrl(name: String, songs: List<Song>): String {
        val safeName = name.cleanField().ifBlank { "Shared Playlist" }
        val tsv = buildString {
            append(safeName)
            for (song in songs) {
                append('\n')
                val vid = song.remoteVideoId?.takeIf { !it.startsWith("search_") && !it.startsWith("search:") && it.isNotBlank() }.orEmpty()
                val artist = song.cleanArtistName.ifBlank { song.artistName }
                val album = song.albumName.takeIf {
                    it.isNotBlank() && !it.equals("YouTube Music", ignoreCase = true) && !it.equals("Unknown Album", ignoreCase = true)
                }.orEmpty()
                val durationSec = (song.durationMs / 1000L).coerceAtLeast(0L)
                append(vid.cleanField()).append('\t')
                append(song.title.cleanField()).append('\t')
                append(artist.cleanField()).append('\t')
                append(album.cleanField()).append('\t')
                append(durationSec)
            }
        }
        val compressed = deflateRaw(tsv.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        return "$BASE_WEB_URL?p=$encoded"
    }

    fun decode(uriString: String?): DeepLinkPayload? {
        if (uriString.isNullOrBlank()) return null
        val trimmed = uriString.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null

        val isCustomScheme = scheme == CUSTOM_SCHEME
        val isWebScheme = (scheme == "https" || scheme == "http") &&
            uri.host.equals(WEB_HOST, ignoreCase = true) &&
            (uri.path == null || uri.path.startsWith(WEB_PATH_PREFIX, ignoreCase = true))

        if (!isCustomScheme && !isWebScheme) return null

        val rawQuery = uri.rawQuery ?: return null
        val params = parseQueryParams(rawQuery)

        val playlistParam = params["p"]
        if (!playlistParam.isNullOrBlank()) {
            return decodePlaylistPayload(playlistParam)
        }

        val videoId = params["v"]?.trim().orEmpty()
        val query = params["q"]?.trim().orEmpty()
        val title = params["t"]?.trim().orEmpty()
        val artist = params["a"]?.trim().orEmpty()
        val album = params["al"]?.trim().orEmpty()
        val durationSec = params["d"]?.trim()?.toLongOrNull() ?: 0L

        if (videoId.isEmpty() && query.isEmpty() && title.isEmpty()) return null

        val resolvedTitle = title.ifBlank { query.ifBlank { "Shared Track" } }
        val resolvedArtist = artist.ifBlank { "Unknown Artist" }
        val song = buildSharedSong(
            videoId = videoId,
            title = resolvedTitle,
            artist = resolvedArtist,
            album = album,
            durationSec = durationSec
        )
        return DeepLinkPayload.Track(song)
    }

    private fun decodePlaylistPayload(encoded: String): DeepLinkPayload.Playlist? {
        return runCatching {
            val compressed = Base64.getUrlDecoder().decode(encoded)
            val decompressed = inflateRaw(compressed).toString(StandardCharsets.UTF_8)
            val lines = decompressed.split('\n')
            if (lines.isEmpty()) return null
            val playlistName = lines[0].trim().ifBlank { "Shared Playlist" }
            val songs = lines.drop(1).mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split('\t')
                val vid = parts.getOrNull(0)?.trim().orEmpty()
                val title = parts.getOrNull(1)?.trim().orEmpty()
                val artist = parts.getOrNull(2)?.trim().orEmpty()
                val album = parts.getOrNull(3)?.trim().orEmpty()
                val durationSec = parts.getOrNull(4)?.trim()?.toLongOrNull() ?: 0L
                if (vid.isEmpty() && title.isEmpty()) return@mapNotNull null
                buildSharedSong(
                    videoId = vid,
                    title = title.ifBlank { "Shared Track" },
                    artist = artist.ifBlank { "Unknown Artist" },
                    album = album,
                    durationSec = durationSec
                )
            }
            if (songs.isEmpty()) null else DeepLinkPayload.Playlist(playlistName, songs)
        }.getOrNull()
    }

    internal fun buildSharedSong(
        videoId: String,
        title: String,
        artist: String,
        album: String,
        durationSec: Long
    ): Song {
        val cleanVid = videoId.trim()
        val hasDirectVideoId = cleanVid.isNotBlank() && !cleanVid.startsWith("search_") && !cleanVid.startsWith("search:")
        val contentKey = if (hasDirectVideoId) {
            cleanVid
        } else {
            val q = "$title $artist".trim().ifBlank { title }
            val encodedQuery = Base64.getUrlEncoder().withoutPadding().encodeToString(q.toByteArray(StandardCharsets.UTF_8))
            "search_$encodedQuery"
        }
        val artwork = if (hasDirectVideoId) {
            "https://i.ytimg.com/vi/$cleanVid/hqdefault.jpg"
        } else {
            null
        }
        val now = System.currentTimeMillis()
        return Song(
            id = contentKey.toRemoteSongId(),
            contentUri = "ytm://$contentKey",
            title = title,
            artistId = artist.lowercase().hashCode().toLong(),
            artistName = artist,
            albumId = if (album.isNotBlank()) "${artist.lowercase()}_${album.lowercase()}".hashCode().toLong() else 0L,
            albumName = album.ifBlank { "YouTube Music" },
            durationMs = (durationSec * 1000L).coerceAtLeast(0L),
            trackNumber = 0,
            year = 0,
            folderPath = "YouTube Music Stream",
            dateAdded = now,
            dateModified = now,
            artworkUri = artwork
        )
    }

    private fun String.cleanField(): String =
        replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            if (idx < 0) {
                val k = URLDecoder.decode(pair, StandardCharsets.UTF_8.name())
                map[k] = ""
            } else {
                val k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                val v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                map[k] = v
            }
        }
        return map
    }

    private fun deflateRaw(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        return try {
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream(input.size)
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count <= 0) break
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflateRaw(input: ByteArray): ByteArray {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(input)
            val out = ByteArrayOutputStream(input.size * 3)
            val buffer = ByteArray(1024)
            var totalBytes = 0
            // shortcut: cap decompressed payload at 256KB to prevent zip-bomb OOM on crafted URLs
            while (!inflater.finished() && totalBytes < 262_144) {
                val count = inflater.inflate(buffer)
                if (count <= 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                    break
                }
                out.write(buffer, 0, count)
                totalBytes += count
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
