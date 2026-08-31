package com.kangle.kardleaf.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.utils.NoteTextStats
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CreateLabelDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_new_label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.label_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotePropertiesDialog(
    note: Note,
    textStats: NoteTextStats? = null,
    noteCountByTag: Map<String, Int> = emptyMap(),
    onDismiss: () -> Unit,
    onTimeChange: (NoteTimeField, Long) -> Unit = { _, _ -> },
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    val pendingText = "统计中…"
    var editingField by remember(note.id) { mutableStateOf<NoteTimeField?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("属性") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PropertyRow("文件名", note.file.name)
                PropertyRow("文件夹", note.folder.ifBlank { "根目录" })
                PropertyRow("路径", note.file.path)
                PropertyRow(
                    label = "标签",
                    value = note.tags.joinToString("、").ifBlank { "无" },
                    content = {
                        if (note.tags.isEmpty()) {
                            Text(
                                text = "无",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                note.tags.distinct().forEach { tag ->
                                    FolderNavigationChip(
                                        text = tag,
                                        count = noteCountByTag[tag] ?: 0,
                                        selected = false,
                                        countBeforeText = true,
                                    )
                                }
                            }
                        }
                    },
                )
                PropertyRow("字符数", textStats?.let { numberFormat.format(it.characterCount) } ?: pendingText)
                PropertyRow("词数", textStats?.let { numberFormat.format(it.wordCountWithPunctuation) } ?: pendingText)
                PropertyRow("词数（不带标点）", textStats?.let { numberFormat.format(it.wordCountWithoutPunctuation) } ?: pendingText)
                PropertyRow("行数", textStats?.let { numberFormat.format(it.lineCount) } ?: pendingText)
                PropertyRow("段落数", textStats?.let { numberFormat.format(it.paragraphCount) } ?: pendingText)
                PropertyRow(
                    label = "创建时间",
                    value = dateFormat.format(note.createdAt),
                    onClick = { editingField = NoteTimeField.CREATED },
                )
                PropertyRow(
                    label = "修改时间",
                    value = dateFormat.format(note.updatedAt),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
    )

    editingField?.let { field ->
        NoteTimestampPickerDialog(
            initialTimestamp = if (field == NoteTimeField.CREATED) note.createdAt.time else note.updatedAt.time,
            title = if (field == NoteTimeField.CREATED) "修改创建时间" else "修改时间",
            onDismiss = { editingField = null },
            onConfirm = { timestamp ->
                onTimeChange(field, timestamp)
                editingField = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PropertyRow(
    label: String,
    value: String,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit = {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    },
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                clipboard.setText(AnnotatedString(value))
                context.showToast("已复制$value")
            },
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

