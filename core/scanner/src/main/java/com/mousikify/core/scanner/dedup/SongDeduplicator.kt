package com.mousikify.core.scanner.dedup

import com.mousikify.core.database.dao.SongDedupRecord
import java.io.File
import kotlin.math.abs

object SongDeduplicator {

    fun findDuplicate(
        candidateUri: String,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationMs: Long,
        candidateFilePath: String?,
        existingRecords: List<SongDedupRecord>
    ): SongDedupRecord? {
        val normCandidateTitle = normalize(candidateTitle)
        val normCandidateArtist = normalize(candidateArtist)
        val candidateFileName = if (!candidateFilePath.isNullOrBlank()) {
            File(candidateFilePath).nameWithoutExtension.lowercase().trim()
        } else null

        for (record in existingRecords) {
            // 1. Exact Content URI match
            if (record.contentUri.equals(candidateUri, ignoreCase = true)) {
                return record
            }

            // 2. Title + Artist + Duration match
            val normExistingTitle = normalize(record.title)
            val normExistingArtist = normalize(record.artistName)

            val titlesMatch = normCandidateTitle.isNotBlank() &&
                    normCandidateTitle == normExistingTitle

            val artistsMatch = normCandidateArtist.isNotBlank() &&
                    (normCandidateArtist == normExistingArtist ||
                            normCandidateArtist == "unknown artist" ||
                            normExistingArtist == "unknown artist")

            val durationsMatch = candidateDurationMs > 0 && record.durationMs > 0 &&
                    abs(candidateDurationMs - record.durationMs) <= 3000L

            if (titlesMatch && artistsMatch && durationsMatch) {
                return record
            }

            // 3. Filename match if titles are generic or unknown
            if (candidateFileName != null && candidateFileName.length > 3) {
                val recordTitleNorm = normalize(record.title)
                if (recordTitleNorm == candidateFileName && durationsMatch) {
                    return record
                }
            }
        }

        return null
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("""\s*\[.*?\]|\s*\(.*?\)"""), "") // Remove bracketed tags like (Official Video), [Remastered]
            .replace(Regex("""[-_]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
