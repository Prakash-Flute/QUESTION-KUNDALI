package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.TimeLogDatabase
import com.example.data.TimeLogEntry
import com.example.data.TimeLogRepository
import com.example.data.TimestampFormatter
import com.example.util.CsvExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: TimeLogDatabase
    private lateinit var repository: TimeLogRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TimeLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TimeLogRepository(database.timeLogDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name matches BubbleLog`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("BubbleLog", appName)
    }

    @Test
    fun `insert update and delete timestamp entry preserves exact HH MM SS`() = runTest {
        val captureMillis = 1_763_890_540_000L
        val expectedTime = TimestampFormatter.formatTime(captureMillis)
        val expectedDate = TimestampFormatter.formatDate(captureMillis)

        val unlimitedNote = "Batch #42: A-Z 0-9 !@#\$%^&*() multi-line\nLine 2 unlimited note"
        repository.insertCapture(captureMillis, unlimitedNote)

        val inserted = repository.allLogs.first()
        assertEquals(1, inserted.size)
        assertEquals(expectedTime, inserted[0].timeFormatted)
        assertEquals(expectedDate, inserted[0].dateFormatted)
        assertEquals(unlimitedNote, inserted[0].note)

        repository.updateNote(inserted[0], "Updated Note 999")
        val updated = repository.allLogs.first()
        assertEquals("Updated Note 999", updated[0].note)
        assertEquals(expectedTime, updated[0].timeFormatted)

        repository.deleteEntry(updated[0])
        assertTrue(repository.allLogs.first().isEmpty())
    }

    @Test
    fun `csv exporter properly escapes commas quotes and newlines`() {
        val entries = listOf(
            TimeLogEntry(
                id = 1L,
                capturedEpochMillis = 1000L,
                dateFormatted = "2026-11-23",
                timeFormatted = "14:05:09",
                note = "Call \"VIP\", urgent\nFollow up",
            ),
        )
        val csv = CsvExporter.buildCsv(entries)
        assertEquals(
            "Date,Time (HH:MM:SS),Saved Note\n2026-11-23,14:05:09,\"Call \"\"VIP\"\", urgent\nFollow up\"\n",
            csv,
        )
    }
}
