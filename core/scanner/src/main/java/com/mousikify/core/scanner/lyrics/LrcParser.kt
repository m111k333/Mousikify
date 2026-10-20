package com.mousikify.core.scanner.lyrics

import com.mousikify.core.model.LyricLine
import com.mousikify.core.model.LyricWord
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

object LrcParser {

    private val TIME_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val WORD_TIME_PATTERN = Pattern.compile("<(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?>")
    private val OFFSET_PATTERN = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE)
    private val METADATA_TAG_PATTERN = Pattern.compile("^\\[(ti|ar|al|by|re|ve|length|au|offset):.*]$", Pattern.CASE_INSENSITIVE)

    fun parse(lrcContent: String, trackDurationMs: Long = 0L): List<LyricLine> {
        val normalized = lrcContent
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) return emptyList()

        val rawLines = normalized.lines()
        val syncedResult = mutableListOf<LyricLine>()
        var globalOffsetMs = 0L

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val offsetMatcher = OFFSET_PATTERN.matcher(trimmed)
            if (offsetMatcher.find()) {
                globalOffsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
                continue
            }

            if (METADATA_TAG_PATTERN.matcher(trimmed).matches()) {
                continue
            }

            val matcher = TIME_PATTERN.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val totalMs = parseTimeMatch(
                    matcher.group(1),
                    matcher.group(2),
                    matcher.group(3)
                )
                timestamps.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val rawBody = trimmed.substring(lastMatchEnd).trim()
                val (cleanText, explicitWords) = parseInlineWords(rawBody, globalOffsetMs)
                if (cleanText.isNotEmpty()) {
                    for (timestamp in timestamps) {
                        val adjustedTs = (timestamp - globalOffsetMs).coerceAtLeast(0L)
                        syncedResult.add(
                            LyricLine(
                                timestampMs = adjustedTs,
                                text = cleanText,
                                words = explicitWords,
                                isSynced = true
                            )
                        )
                    }
                }
            }
        }

        if (syncedResult.isNotEmpty()) {
            return syncedResult.sortedBy { it.timestampMs }
        }

        // Plain text fallback for unsynced lyrics
        return rawLines.mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || METADATA_TAG_PATTERN.matcher(trimmed).matches()) null
            else LyricLine(timestampMs = 0L, text = trimmed, isSynced = false)
        }
    }

    private fun parseTimeMatch(minStr: String?, secStr: String?, fracStr: String?): Long {
        val minutes = minStr?.toLongOrNull() ?: 0L
        val seconds = secStr?.toLongOrNull() ?: 0L
        val millis = when {
            fracStr == null -> 0L
            fracStr.length == 1 -> fracStr.toLong() * 100L
            fracStr.length == 2 -> fracStr.toLong() * 10L
            fracStr.length == 3 -> fracStr.toLong()
            else -> fracStr.take(3).padEnd(3, '0').toLong()
        }
        return (minutes * 60L + seconds) * 1000L + millis
    }

    private fun parseInlineWords(rawBody: String, globalOffsetMs: Long): Pair<String, List<LyricWord>> {
        val matcher = WORD_TIME_PATTERN.matcher(rawBody)
        if (!matcher.find()) {
            return rawBody to emptyList()
        }
        matcher.reset()

        val words = mutableListOf<LyricWord>()
        val cleanBuilder = StringBuilder()
        val tagStarts = mutableListOf<Int>()
        val tagEnds = mutableListOf<Int>()
        val tagTimes = mutableListOf<Long>()

        while (matcher.find()) {
            tagStarts.add(matcher.start())
            tagEnds.add(matcher.end())
            val t = (parseTimeMatch(matcher.group(1), matcher.group(2), matcher.group(3)) - globalOffsetMs).coerceAtLeast(0L)
            tagTimes.add(t)
        }

        for (i in tagTimes.indices) {
            val startText = tagEnds[i]
            val endText = if (i + 1 < tagStarts.size) tagStarts[i + 1] else rawBody.length
            val token = rawBody.substring(startText, endText).trim()
            if (token.isNotEmpty()) {
                if (cleanBuilder.isNotEmpty()) cleanBuilder.append(' ')
                cleanBuilder.append(token)
                words.add(LyricWord(timestampMs = tagTimes[i], text = token))
            }
        }

        val finalClean = cleanBuilder.toString().ifEmpty {
            rawBody.replace(WORD_TIME_PATTERN.toRegex(), "").trim()
        }
        return finalClean to words
    }


    fun parseFile(file: File, trackDurationMs: Long = 0L): List<LyricLine> {
        if (!file.exists() || !file.canRead()) return emptyList()
        return try {
            parse(file.readText(Charsets.UTF_8), trackDurationMs)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseStream(stream: InputStream, trackDurationMs: Long = 0L): List<LyricLine> {
        return try {
            parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() }, trackDurationMs)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

