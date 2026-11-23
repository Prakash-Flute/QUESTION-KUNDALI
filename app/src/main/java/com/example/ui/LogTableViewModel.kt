package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.TimeLogEntry
import com.example.data.TimeLogRepository
import com.example.overlay.BubbleServiceState
import com.example.util.CsvExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TableUiState(
    val allEntries: List<TimeLogEntry> = emptyList(),
    val filteredEntries: List<TimeLogEntry> = emptyList(),
    val searchQuery: String = "",
    val isBubbleRunning: Boolean = false,
    val editingEntry: TimeLogEntry? = null,
    val deletingEntry: TimeLogEntry? = null,
    val quickCaptureMillis: Long? = null,
)

class LogTableViewModel(
    private val repository: TimeLogRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val editingEntry = MutableStateFlow<TimeLogEntry?>(null)
    private val deletingEntry = MutableStateFlow<TimeLogEntry?>(null)
    private val quickCaptureMillis = MutableStateFlow<Long?>(null)

    private val dialogState = combine(
        editingEntry,
        deletingEntry,
        quickCaptureMillis,
    ) { edit, delete, quick ->
        Triple(edit, delete, quick)
    }

    val uiState: StateFlow<TableUiState> = combine(
        repository.allLogs,
        searchQuery,
        BubbleServiceState.isRunning,
        dialogState,
    ) { logs, query, running, dialogs ->
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            logs
        } else {
            logs.filter { entry ->
                entry.note.contains(trimmed, ignoreCase = true) ||
                    entry.timeFormatted.contains(trimmed, ignoreCase = true) ||
                    entry.dateFormatted.contains(trimmed, ignoreCase = true)
            }
        }
        TableUiState(
            allEntries = logs,
            filteredEntries = filtered,
            searchQuery = query,
            isBubbleRunning = running,
            editingEntry = dialogs.first,
            deletingEntry = dialogs.second,
            quickCaptureMillis = dialogs.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TableUiState(),
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun openQuickCapture() {
        quickCaptureMillis.value = System.currentTimeMillis()
    }

    fun dismissQuickCapture() {
        quickCaptureMillis.value = null
    }

    fun saveQuickCapture(note: String) {
        val millis = quickCaptureMillis.value ?: return
        quickCaptureMillis.value = null
        viewModelScope.launch {
            repository.insertCapture(epochMillis = millis, note = note)
        }
    }

    fun openEditDialog(entry: TimeLogEntry) {
        editingEntry.value = entry
    }

    fun dismissEditDialog() {
        editingEntry.value = null
    }

    fun saveEditedNote(updatedNote: String) {
        val target = editingEntry.value ?: return
        editingEntry.value = null
        viewModelScope.launch {
            repository.updateNote(target, updatedNote)
        }
    }

    fun requestDelete(entry: TimeLogEntry) {
        deletingEntry.value = entry
    }

    fun dismissDeleteDialog() {
        deletingEntry.value = null
    }

    fun confirmDelete(onDeleted: (TimeLogEntry) -> Unit) {
        val target = deletingEntry.value ?: return
        deletingEntry.value = null
        viewModelScope.launch {
            repository.deleteEntry(target)
            onDeleted(target)
        }
    }

    fun restoreDeletedEntry(entry: TimeLogEntry) {
        viewModelScope.launch {
            repository.restoreEntry(entry)
        }
    }

    fun getExportEntries(): List<TimeLogEntry> {
        val state = uiState.value
        return if (state.searchQuery.isNotBlank()) state.filteredEntries else state.allEntries
    }

    fun buildCsvPreview(): String = CsvExporter.buildCsv(getExportEntries())

    class Factory(private val repository: TimeLogRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LogTableViewModel(repository) as T
        }
    }
}
