package com.example.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow

object TimestampFormatter {
    fun formatDate(epochMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }

    fun formatTime(epochMillis: Long): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }
}

class TimeLogRepository(private val timeLogDao: TimeLogDao) {
    val allLogs: Flow<List<TimeLogEntry>> = timeLogDao.getAllLogs()

    suspend fun insertCapture(epochMillis: Long, note: String): Long {
        val entry = TimeLogEntry(
            capturedEpochMillis = epochMillis,
            dateFormatted = TimestampFormatter.formatDate(epochMillis),
            timeFormatted = TimestampFormatter.formatTime(epochMillis),
            note = note,
        )
        return timeLogDao.insertLog(entry)
    }

    suspend fun restoreEntry(entry: TimeLogEntry): Long {
        return timeLogDao.insertLog(entry.copy(id = 0L))
    }

    suspend fun updateNote(entry: TimeLogEntry, updatedNote: String) {
        timeLogDao.updateLog(entry.copy(note = updatedNote))
    }

    suspend fun deleteEntry(entry: TimeLogEntry) {
        timeLogDao.deleteLog(entry)
    }
}
