package com.mousikify.core.media.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsLogTest {

    private lateinit var diagnosticsLog: DiagnosticsLog

    @Before
    fun setUp() {
        diagnosticsLog = DiagnosticsLog()
    }

    @Test
    fun logsAreRecordedCorrectly() {
        diagnosticsLog.info("TestTag", "Hello World", "Extra details")
        val events = diagnosticsLog.events.value
        assertEquals(1, events.size)
        val event = events.first()
        assertEquals(DiagLogLevel.INFO, event.level)
        assertEquals("TestTag", event.tag)
        assertEquals("Hello World", event.message)
        assertEquals("Extra details", event.details)
    }

    @Test
    fun bufferCapsAtMaxCapacityAndEvictsOldest() {
        for (i in 1..250) {
            diagnosticsLog.info("Tag", "Message $i")
        }

        val events = diagnosticsLog.events.value
        assertEquals(DiagnosticsLog.MAX_CAPACITY, events.size)
        assertEquals(200, events.size)
        // First event in buffer should be Message 51 (1..50 evicted)
        assertEquals("Message 51", events.first().message)
        assertEquals("Message 250", events.last().message)
    }

    @Test
    fun clearWipesAllLogs() {
        diagnosticsLog.warn("Tag", "Warning")
        diagnosticsLog.error("Tag", "Error")
        assertEquals(2, diagnosticsLog.events.value.size)

        diagnosticsLog.clear()
        assertTrue(diagnosticsLog.events.value.isEmpty())
    }
}
