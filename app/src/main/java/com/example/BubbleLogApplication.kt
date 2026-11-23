package com.example

import android.app.Application
import com.example.data.TimeLogDatabase
import com.example.data.TimeLogRepository

class BubbleLogApplication : Application() {
    val database: TimeLogDatabase by lazy { TimeLogDatabase.getInstance(this) }
    val repository: TimeLogRepository by lazy { TimeLogRepository(database.timeLogDao()) }
}
