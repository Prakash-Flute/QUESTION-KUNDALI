package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_logs")
data class TimeLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val capturedEpochMillis: Long,
    val dateFormatted: String,
    val timeFormatted: String,
    val note: String,
)
