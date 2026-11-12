package com.mousikify.core.model

import java.util.Locale

/**
 * Domain utility for artist tokenization, attribution cleaning, and matching heuristics.
 */
object TrackAttributionMatcher {

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

    /**
     * Verifies that a song returned from online search is actually by the target artist,
     * and prevents false positives where the song's title happens to be the artist's name.
     */
    fun isSongMatchingArtist(song: Song, targetArtist: String): Boolean {
        val cleanTarget = cleanArtistName(targetArtist).trim().lowercase(Locale.ROOT)
        if (cleanTarget.isBlank()) return false

        val cleanSongArtist = cleanArtistName(song.artistName).trim().lowercase(Locale.ROOT)
        val songTitle = song.title.trim().lowercase(Locale.ROOT)

        // CRITICAL: Reject songs whose title matches the artist name while the artist is someone else
        // (e.g. Title="Phoenix", Artist="Fall Out Boy" when targetArtist="Phoenix")
        if (songTitle == cleanTarget && cleanSongArtist != cleanTarget) {
            return false
        }

        if (cleanSongArtist == cleanTarget) return true

        val songTokens = normalizeArtistTokens(song.artistName)
        val targetTokens = normalizeArtistTokens(targetArtist)

        if (songTokens.contains(cleanTarget) || targetTokens.contains(cleanSongArtist)) return true

        return songTokens.any { st ->
            targetTokens.any { tt ->
                st == tt || (tt.length >= 4 && st.contains(tt)) || (st.length >= 4 && tt.contains(st))
            }
        }
    }
}
