package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeLogDao {
    @Query("SELECT * FROM time_logs ORDER BY capturedEpochMillis DESC, id DESC")
    fun getAllLogs(): Flow<List<TimeLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: TimeLogEntry): Long

    @Update
    suspend fun updateLog(entry: TimeLogEntry)

    @Delete
    suspend fun deleteLog(entry: TimeLogEntry)
}
