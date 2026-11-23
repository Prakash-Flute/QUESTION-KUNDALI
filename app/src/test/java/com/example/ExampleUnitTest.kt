package com.example

import com.example.data.TimeLogEntry
import com.example.data.TimestampFormatter
import com.example.util.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `timestamp formatter outputs exact HH_MM_SS and yyyy-MM-dd`() {
        val sampleMillis = 1_763_890_540_000L
        val time = TimestampFormatter.formatTime(sampleMillis)
        val date = TimestampFormatter.formatDate(sampleMillis)

        assertTrue(time.matches(Regex("^\\d{2}:\\d{2}:\\d{2}$")))
        assertTrue(date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")))
    }

    @Test
    fun `csv exporter escapes commas quotes and unlimited multiline notes`() {
        val entry = TimeLogEntry(
            id = 1L,
            capturedEpochMillis = 1_763_890_540_000L,
            dateFormatted = "2026-11-23",
            timeFormatted = "09:35:40",
            note = "Item #123, \"urgent\"\nNext line A-Z 0-9 !@#",
        )
        val csv = CsvExporter.buildCsv(listOf(entry))
        assertEquals(
            "Date,Time (HH:MM:SS),Saved Note\n2026-11-23,09:35:40,\"Item #123, \"\"urgent\"\"\nNext line A-Z 0-9 !@#\"\n",
            csv,
        )
    }
}
