package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.TimeLogEntry
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanContainer
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletContainer
import com.example.ui.theme.NeonVioletSoft
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianStroke
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh

@Composable
fun BubbleLogScreen(
    uiState: TableUiState,
    hasOverlayPermission: Boolean,
    snackbarHostState: SnackbarHostState,
    onToggleBubble: () -> Unit,
    onGrantOverlayPermission: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onQuickCaptureClick: () -> Unit,
    onDismissQuickCapture: () -> Unit,
    onSaveQuickCapture: (String) -> Unit,
    onEditEntryClick: (TimeLogEntry) -> Unit,
    onDismissEdit: () -> Unit,
    onSaveEdit: (String) -> Unit,
    onDeleteEntryClick: (TimeLogEntry) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onExportCsvSave: () -> Unit,
    onExportCsvShare: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBg,
                            ObsidianSurface.copy(alpha = 0.55f),
                            ObsidianBg,
                        ),
                    ),
                )
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 900.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                HeaderBar(
                    entryCount = uiState.allEntries.size,
                    onQuickCaptureClick = onQuickCaptureClick,
                    onExportCsvSave = onExportCsvSave,
                    onExportCsvShare = onExportCsvShare,
                )

                Spacer(modifier = Modifier.height(12.dp))

                FloatingBubbleStatusCard(
                    hasOverlayPermission = hasOverlayPermission,
                    isBubbleRunning = uiState.isBubbleRunning,
                    onActionClick = {
                        if (hasOverlayPermission) {
                            onToggleBubble()
                        } else {
                            onGrantOverlayPermission()
                        }
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                SearchInputBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                )

                Spacer(modifier = Modifier.height(12.dp))

                TimestampTableCard(
                    entries = uiState.filteredEntries,
                    hasAnyEntries = uiState.allEntries.isNotEmpty(),
                    onRowClick = onEditEntryClick,
                    onEditClick = onEditEntryClick,
                    onDeleteClick = onDeleteEntryClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    uiState.quickCaptureMillis?.let { millis ->
        QuickCaptureDialog(
            captureMillis = millis,
            onDismiss = onDismissQuickCapture,
            onSave = onSaveQuickCapture,
        )
    }

    uiState.editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = onDismissEdit,
            onSave = onSaveEdit,
        )
    }

    uiState.deletingEntry?.let { entry ->
        DeleteConfirmDialog(
            entry = entry,
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete,
        )
    }
}

@Composable
private fun HeaderBar(
    entryCount: Int,
    onQuickCaptureClick: () -> Unit,
    onExportCsvSave: () -> Unit,
    onExportCsvShare: () -> Unit,
) {
    var showExportMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_neon_bubble),
                contentDescription = stringResource(R.string.cd_neon_bubble_icon),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(NeonCyan, NeonViolet)),
                        shape = CircleShape,
                    ),
                contentScale = ContentScale.Crop,
            )

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Surface(
                        color = NeonCyanContainer,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
                    ) {
                        Text(
                            text = "$entryCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.subtitle_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                onClick = onQuickCaptureClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quick_capture_button"),
                shape = RoundedCornerShape(12.dp),
                color = NeonVioletContainer,
                border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.7f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.action_quick_capture),
                        tint = NeonVioletSoft,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.action_quick_capture),
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonVioletSoft,
                    )
                }
            }

            Box {
                Surface(
                    onClick = { showExportMenu = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("export_csv_button"),
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceHigh,
                    border = BorderStroke(1.dp, ObsidianStroke),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.cd_export_csv),
                        tint = NeonCyan,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }

                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false },
                    containerColor = ObsidianSurfaceHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_export_csv)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = NeonCyan,
                            )
                        },
                        onClick = {
                            showExportMenu = false
                            onExportCsvSave()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share_csv)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                tint = NeonVioletSoft,
                            )
                        },
                        onClick = {
                            showExportMenu = false
                            onExportCsvShare()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingBubbleStatusCard(
    hasOverlayPermission: Boolean,
    isBubbleRunning: Boolean,
    onActionClick: () -> Unit,
) {
    val borderBrush = Brush.linearGradient(
        colors = if (isBubbleRunning && hasOverlayPermission) {
            listOf(NeonCyan, NeonViolet)
        } else {
            listOf(ObsidianStroke, NeonViolet.copy(alpha = 0.6f))
        },
    )

    val statusDotColor by animateColorAsState(
        targetValue = when {
            !hasOverlayPermission -> NeonCoral
            isBubbleRunning -> NeonCyan
            else -> NeonVioletSoft
        },
        label = "bubbleStatusDot",
    )

    val statusText = when {
        !hasOverlayPermission -> stringResource(R.string.bubble_status_permission_needed)
        isBubbleRunning -> stringResource(R.string.bubble_status_active)
        else -> stringResource(R.string.bubble_status_inactive)
    }

    val buttonLabel = when {
        !hasOverlayPermission -> stringResource(R.string.action_grant_permission)
        isBubbleRunning -> stringResource(R.string.action_stop_bubble)
        else -> stringResource(R.string.action_enable_bubble)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.5.dp, brush = borderBrush, shape = RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusDotColor),
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bubble_helper_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onActionClick,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("toggle_bubble_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBubbleRunning && hasOverlayPermission) {
                        NeonVioletContainer
                    } else {
                        NeonCyan
                    },
                    contentColor = if (isBubbleRunning && hasOverlayPermission) {
                        NeonVioletSoft
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                ),
                border = if (isBubbleRunning && hasOverlayPermission) {
                    BorderStroke(1.dp, NeonViolet)
                } else {
                    null
                },
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                text = stringResource(R.string.search_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = NeonCyan.copy(alpha = 0.85f),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("clear_search_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ObsidianSurface,
            unfocusedContainerColor = ObsidianSurface,
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = ObsidianStroke,
            cursorColor = NeonCyan,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun TimestampTableCard(
    entries: List<TimeLogEntry>,
    hasAnyEntries: Boolean,
    onRowClick: (TimeLogEntry) -> Unit,
    onEditClick: (TimeLogEntry) -> Unit,
    onDeleteClick: (TimeLogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianStroke, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TableHeaderRow()
            HorizontalDivider(thickness = 1.dp, color = NeonCyan.copy(alpha = 0.4f))

            if (entries.isEmpty()) {
                EmptyTableState(
                    isFilteredEmpty = hasAnyEntries,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("timestamp_table_list"),
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { _, item -> item.id },
                    ) { index, entry ->
                        TimestampTableRow(
                            entry = entry,
                            isEvenRow = index % 2 == 0,
                            onRowClick = { onRowClick(entry) },
                            onEditClick = { onEditClick(entry) },
                            onDeleteClick = { onDeleteClick(entry) },
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = ObsidianStroke.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.table_col_date),
            style = MaterialTheme.typography.labelSmall,
            color = NeonVioletSoft,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = stringResource(R.string.table_col_time),
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            modifier = Modifier.width(78.dp),
        )
        Text(
            text = stringResource(R.string.table_col_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.table_col_actions),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(84.dp),
        )
    }
}

@Composable
private fun TimestampTableRow(
    entry: TimeLogEntry,
    isEvenRow: Boolean,
    onRowClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val rowBg = if (isEvenRow) ObsidianSurface else ObsidianSurfaceHigh.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onRowClick)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
            .testTag("log_row_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.dateFormatted,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
            maxLines = 1,
        )

        Text(
            text = entry.timeFormatted,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
            ),
            color = NeonCyan,
            modifier = Modifier.width(78.dp),
            maxLines = 1,
        )

        if (entry.note.isBlank()) {
            Text(
                text = stringResource(R.string.overlay_empty_note_fallback),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
            )
        } else {
            Text(
                text = entry.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
            )
        }

        Row(
            modifier = Modifier.width(96.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("edit_entry_${entry.id}"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_entry),
                    tint = NeonVioletSoft,
                    modifier = Modifier.size(19.dp),
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_entry_${entry.id}"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_entry),
                    tint = NeonCoral,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyTableState(
    isFilteredEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = NeonCyanContainer,
            shape = CircleShape,
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(
                if (isFilteredEmpty) R.string.empty_search_title else R.string.empty_title,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(
                if (isFilteredEmpty) R.string.empty_search_description else R.string.empty_description,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
