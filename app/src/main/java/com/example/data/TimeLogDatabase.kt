package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TimeLogEntry::class], version = 1, exportSchema = false)
abstract class TimeLogDatabase : RoomDatabase() {
    abstract fun timeLogDao(): TimeLogDao

    companion object {
        @Volatile
        private var INSTANCE: TimeLogDatabase? = null

        fun getInstance(context: Context): TimeLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TimeLogDatabase::class.java,
                    "bubble_log_database",
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
