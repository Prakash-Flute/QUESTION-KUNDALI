package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.overlay.FloatingBubbleService
import com.example.ui.BubbleLogScreen
import com.example.ui.LogTableViewModel
import com.example.ui.theme.BubbleLogTheme
import com.example.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: LogTableViewModel by viewModels {
        LogTableViewModel.Factory((application as BubbleLogApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            BubbleLogTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val coroutineScope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                var hasOverlayPermission by remember {
                    mutableStateOf(Settings.canDrawOverlays(context))
                }

                val overlaySettingsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    val granted = Settings.canDrawOverlays(context)
                    hasOverlayPermission = granted
                    if (granted) {
                        FloatingBubbleService.start(context)
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(getString(R.string.msg_overlay_denied))
                        }
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) {
                    if (Settings.canDrawOverlays(context)) {
                        FloatingBubbleService.start(context)
                    }
                }

                val createCsvLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/csv"),
                ) { uri ->
                    if (uri != null) {
                        val entries = viewModel.getExportEntries()
                        coroutineScope.launch {
                            val success = withContext(Dispatchers.IO) {
                                runCatching {
                                    contentResolver.openOutputStream(uri)?.use { stream ->
                                        CsvExporter.writeCsvToStream(stream, entries)
                                    } ?: error("OutputStream null")
                                }.isSuccess
                            }
                            snackbarHostState.showSnackbar(
                                getString(
                                    if (success) R.string.msg_csv_exported else R.string.msg_csv_export_failed,
                                ),
                            )
                        }
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasOverlayPermission = Settings.canDrawOverlays(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                BubbleLogScreen(
                    uiState = uiState,
                    hasOverlayPermission = hasOverlayPermission,
                    snackbarHostState = snackbarHostState,
                    onToggleBubble = {
                        if (uiState.isBubbleRunning) {
                            FloatingBubbleService.stop(context)
                        } else {
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            } else {
                                FloatingBubbleService.start(context)
                            }
                        }
                    },
                    onGrantOverlayPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        )
                        overlaySettingsLauncher.launch(intent)
                    },
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onQuickCaptureClick = viewModel::openQuickCapture,
                    onDismissQuickCapture = viewModel::dismissQuickCapture,
                    onSaveQuickCapture = { note ->
                        viewModel.saveQuickCapture(note)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(getString(R.string.msg_entry_saved))
                        }
                    },
                    onEditEntryClick = viewModel::openEditDialog,
                    onDismissEdit = viewModel::dismissEditDialog,
                    onSaveEdit = viewModel::saveEditedNote,
                    onDeleteEntryClick = viewModel::requestDelete,
                    onDismissDelete = viewModel::dismissDeleteDialog,
                    onConfirmDelete = {
                        viewModel.confirmDelete { deletedEntry ->
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = getString(R.string.msg_entry_deleted),
                                    actionLabel = getString(R.string.action_undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreDeletedEntry(deletedEntry)
                                }
                            }
                        }
                    },
                    onExportCsvSave = {
                        val entries = viewModel.getExportEntries()
                        if (entries.isEmpty()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    getString(R.string.msg_no_entries_export),
                                )
                            }
                        } else {
                            createCsvLauncher.launch(CsvExporter.defaultFileName())
                        }
                    },
                    onExportCsvShare = {
                        val entries = viewModel.getExportEntries()
                        if (entries.isEmpty()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    getString(R.string.msg_no_entries_export),
                                )
                            }
                        } else {
                            coroutineScope.launch {
                                val uri = withContext(Dispatchers.IO) {
                                    CsvExporter.createShareableCsvUri(context, entries)
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        getString(R.string.action_share_csv),
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
