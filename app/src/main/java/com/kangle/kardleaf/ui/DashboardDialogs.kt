package com.kangle.kardleaf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
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

@Composable
fun NotePropertiesDialog(
    note: Note,
    onDismiss: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    val wordCount = remember(note.content) { countWords(note.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("属性") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyRow("文件名", note.file.name)
                PropertyRow("文件夹", note.folder.ifBlank { "根目录" })
                PropertyRow("路径", note.file.path)
                PropertyRow("字数", numberFormat.format(wordCount))
                PropertyRow("创建时间", dateFormat.format(note.createdAt))
                PropertyRow("修改时间", dateFormat.format(note.lastModified))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
    )
}

@Composable
private fun PropertyRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun countWords(text: String): Int {
    val cjkCount = Regex("""[\u4E00-\u9FFF]""").findAll(text).count()
    val latinCount = Regex("""[A-Za-z0-9]+(?:['_-][A-Za-z0-9]+)*""").findAll(text).count()
    return cjkCount + latinCount
}
