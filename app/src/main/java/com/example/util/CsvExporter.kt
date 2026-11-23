package com.example.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.TimeLogEntry
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    fun buildCsv(entries: List<TimeLogEntry>): String {
        val sb = StringBuilder()
        sb.append("Date,Time (HH:MM:SS),Saved Note\n")
        for (entry in entries) {
            sb.append(escapeCsv(entry.dateFormatted))
                .append(',')
                .append(escapeCsv(entry.timeFormatted))
                .append(',')
                .append(escapeCsv(entry.note))
                .append('\n')
        }
        return sb.toString()
    }

    fun writeCsvToStream(outputStream: OutputStream, entries: List<TimeLogEntry>) {
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(buildCsv(entries))
        }
    }

    fun createShareableCsvUri(context: Context, entries: List<TimeLogEntry>): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "bubblelog_$stamp.csv")
        file.outputStream().use { writeCsvToStream(it, entries) }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "bubblelog_$stamp.csv"
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
