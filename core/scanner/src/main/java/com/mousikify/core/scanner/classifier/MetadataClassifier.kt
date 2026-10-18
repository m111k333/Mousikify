package com.mousikify.core.scanner.classifier

import java.util.Locale

/**
 * MetadataClassifier provides deterministic, lightweight normalization
 * of audio release years into decades and ID3 genres into canonical playlists.
 */
object MetadataClassifier {

    private val ID3V1_GENRES = mapOf(
        0 to "Blues", 1 to "Rock", 2 to "Country", 3 to "Pop", 4 to "Pop",
        5 to "R&B", 6 to "Rock", 7 to "Hip-Hop", 8 to "Acoustic / Chill", 9 to "Rock",
        10 to "Acoustic / Chill", 11 to "Other", 12 to "Other", 13 to "Pop", 14 to "R&B",
        15 to "Hip-Hop", 16 to "Other", 17 to "Rock", 18 to "Electronic", 19 to "Electronic",
        20 to "Rock", 21 to "Other", 22 to "Rock", 26 to "Acoustic / Chill", 27 to "Electronic",
        31 to "Electronic", 32 to "Acoustic / Chill", 33 to "Acoustic / Chill", 35 to "Electronic",
        42 to "R&B", 43 to "Rock", 52 to "Electronic", 79 to "Rock", 80 to "Acoustic / Chill",
        99 to "Acoustic / Chill", 104 to "Acoustic / Chill", 106 to "Acoustic / Chill"
    )

    /**
     * Extracts canonical decade label ("60s", "70s", "80s", "90s", "00s", "10s", "20s").
     * Returns null if year < 1950 or undetermined/future.
     */
    fun extractDecade(year: Int): String? {
        return when (year) {
            in 1950..1959 -> "50s"
            in 1960..1969 -> "60s"
            in 1970..1979 -> "70s"
            in 1980..1989 -> "80s"
            in 1990..1999 -> "90s"
            in 2000..2009 -> "00s"
            in 2010..2019 -> "10s"
            in 2020..2029 -> "20s"
            in 2030..2039 -> "30s"
            else -> null
        }
    }

    /**
     * Maps raw ID3 / MediaStore genre strings to standardized Spotify-style categories:
     * - "Rock"
     * - "Hip-Hop"
     * - "Pop"
     * - "Electronic"
     * - "R&B"
     * - "Acoustic / Chill"
     * - Default: "Other"
     */
    fun normalizeGenre(raw: String?): String {
        if (raw.isNullOrBlank()) return "Other"

        val trimmed = raw.trim()

        // Handle numeric ID3v1 tags, e.g. "(17)" or "17"
        val numericMatch = Regex("""^\(?(\d{1,3})\)?$""").find(trimmed)
        if (numericMatch != null) {
            val genreCode = numericMatch.groupValues[1].toIntOrNull()
            if (genreCode != null && ID3V1_GENRES.containsKey(genreCode)) {
                return ID3V1_GENRES[genreCode] ?: "Other"
            }
        }

        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Rock & Metal
        if (lower.contains("rock") ||
            lower.contains("metal") ||
            lower.contains("grunge") ||
            lower.contains("punk") ||
            lower.contains("indie") ||
            lower.contains("alternative")
        ) {
            return "Rock"
        }

        // 2. Hip-Hop & Rap
        if (lower.contains("hip hop") ||
            lower.contains("hip-hop") ||
            lower.contains("hiphop") ||
            lower.contains("rap") ||
            lower.contains("trap") ||
            lower.contains("boom bap")
        ) {
            return "Hip-Hop"
        }

        // 3. Pop & Synth
        if (lower.contains("electropop") ||
            lower.contains("pop") ||
            lower.contains("dance") ||
            lower.contains("synth")
        ) {
            return "Pop"
        }

        // 4. Electronic & Dance sub-genres
        if (lower.contains("house") ||
            lower.contains("techno") ||
            lower.contains("edm") ||
            lower.contains("trance") ||
            lower.contains("dubstep") ||
            lower.contains("electronic") ||
            lower.contains("drum and bass") ||
            lower.contains("dnb")
        ) {
            return "Electronic"
        }

        // 5. R&B, Soul & Funk
        if (lower.contains("r&b") ||
            lower.contains("rnb") ||
            lower.contains("soul") ||
            lower.contains("funk") ||
            lower.contains("neo-soul") ||
            lower.contains("neo soul")
        ) {
            return "R&B"
        }

        // 6. Acoustic, Classical & Chill
        if (lower.contains("ambient") ||
            lower.contains("chill") ||
            lower.contains("classical") ||
            lower.contains("acoustic") ||
            lower.contains("folk") ||
            lower.contains("jazz") ||
            lower.contains("lo-fi") ||
            lower.contains("lofi")
        ) {
            return "Acoustic / Chill"
        }

        return "Other"
    }
}
