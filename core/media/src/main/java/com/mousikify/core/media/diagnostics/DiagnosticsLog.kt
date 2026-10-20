package com.mousikify.core.media.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

enum class DiagLogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

data class DiagEvent(
    val id: Long = System.nanoTime(),
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val level: DiagLogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
)

@Singleton
class DiagnosticsLog @Inject constructor() {

    private val lock = Any()
    private val buffer = ArrayDeque<DiagEvent>(MAX_CAPACITY)

    private val _events = MutableStateFlow<List<DiagEvent>>(emptyList())
    val events: StateFlow<List<DiagEvent>> = _events.asStateFlow()

    fun log(level: DiagLogLevel, tag: String, message: String, details: String? = null) {
        val event = DiagEvent(
            level = level,
            tag = tag,
            message = message,
            details = details
        )
        synchronized(lock) {
            if (buffer.size >= MAX_CAPACITY) {
                buffer.removeFirst()
            }
            buffer.addLast(event)
            _events.value = buffer.toList()
        }
    }

    fun info(tag: String, message: String, details: String? = null) =
        log(DiagLogLevel.INFO, tag, message, details)

    fun success(tag: String, message: String, details: String? = null) =
        log(DiagLogLevel.SUCCESS, tag, message, details)

    fun warn(tag: String, message: String, details: String? = null) =
        log(DiagLogLevel.WARN, tag, message, details)

    fun error(tag: String, message: String, details: String? = null) =
        log(DiagLogLevel.ERROR, tag, message, details)

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _events.value = emptyList()
        }
    }

    companion object {
        const val MAX_CAPACITY = 200
    }
}
