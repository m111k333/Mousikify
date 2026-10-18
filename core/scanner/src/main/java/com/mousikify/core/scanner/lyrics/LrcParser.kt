package com.mousikify.core.scanner.lyrics

import com.mousikify.core.model.LyricLine
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

object LrcParser {

    private val TIME_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{2,3}))?]")

    fun parse(lrcContent: String): List<LyricLine> {
        val lines = lrcContent.lines()
        val result = mutableListOf<LyricLine>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val matcher = TIME_PATTERN.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val millisRaw = matcher.group(3)
                val millis = when {
                    millisRaw == null -> 0L
                    millisRaw.length == 2 -> millisRaw.toLong() * 10
                    millisRaw.length == 3 -> millisRaw.toLong()
                    else -> millisRaw.take(3).padEnd(3, '0').toLong()
                }

                val totalMs = (minutes * 60 + seconds) * 1000 + millis
                timestamps.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val text = trimmed.substring(lastMatchEnd).trim()
                for (timestamp in timestamps) {
                    result.add(LyricLine(timestampMs = timestamp, text = text))
                }
            }
        }

        return result.sortedBy { it.timestampMs }
    }

    fun parseFile(file: File): List<LyricLine> {
        if (!file.exists() || !file.canRead()) return emptyList()
        return try {
            parse(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseStream(stream: InputStream): List<LyricLine> {
        return try {
            parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) {
            emptyList()
        }
    }
}
