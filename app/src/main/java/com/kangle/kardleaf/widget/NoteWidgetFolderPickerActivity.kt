package com.kangle.kardleaf.widget

import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.theme.KardLeafTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class NoteWidgetFolderPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)

        setContent {
            KardLeafTheme(styleSystemBars = false) {
                if (openSettings) {
                    var hideTitle by remember(appWidgetId) {
                        mutableStateOf(NoteListWidgetProvider.isTitleHidden(this, appWidgetId))
                    }
                    var previewLines by remember(appWidgetId) {
                        mutableStateOf(NoteListWidgetProvider.previewLineCount(this, appWidgetId))
                    }
                    NoteWidgetSettingsDialog(
                        hideTitle = hideTitle,
                        previewLines = previewLines,
                        onHideTitleChange = { hideTitle = it },
                        onPreviewLinesChange = { previewLines = it },
                        onConfirm = {
                            NoteListWidgetProvider.setDisplayOptions(
                                context = this,
                                appWidgetId = appWidgetId,
                                hideTitle = hideTitle,
                                previewLines = previewLines,
                            )
                            finish()
                        },
                        onDismiss = { finish() },
                    )
                    return@KardLeafTheme
                }

                var folderPaths by remember { mutableStateOf<List<String>?>(null) }
                val selectedFolder = remember(appWidgetId) {
                    NoteListWidgetProvider.selectedFolder(this, appWidgetId)
                }

                LaunchedEffect(appWidgetId) {
                    folderPaths = withContext(Dispatchers.IO) {
                        runCatching {
                            val appContext = applicationContext
                            val hiddenFolders = PrefsManager(appContext).getHiddenFolderPaths()
                            AppDatabase.getDatabase(appContext)
                                .labelDao()
                                .getAllLabels()
                                .first()
                                .filterNot { path ->
                                    hiddenFolders.any { hidden ->
                                        path == hidden || path.startsWith("$hidden/")
                                    }
                                }
                        }.getOrDefault(emptyList())
                    }
                }

                NoteWidgetFolderPickerDialog(
                    folderChoices = folderPaths?.let(::buildExpandedFolderChoices),
                    selectedFolder = selectedFolder,
                    onSelect = { folder ->
                        NoteListWidgetProvider.selectFolder(this, appWidgetId, folder)
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        internal const val EXTRA_OPEN_SETTINGS = "kardleaf_widget_open_settings"
    }
}

@Composable
private fun NoteWidgetSettingsDialog(
    hideTitle: Boolean,
    previewLines: Int,
    onHideTitleChange: (Boolean) -> Unit,
    onPreviewLinesChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("笔记列表设置") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHideTitleChange(!hideTitle) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("隐藏标题", modifier = Modifier.weight(1f))
                    Switch(
                        checked = hideTitle,
                        onCheckedChange = onHideTitleChange,
                    )
                }

                Text(
                    text = "正文显示行数",
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                (1..3).forEach { lineCount ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPreviewLinesChange(lineCount) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = previewLines == lineCount,
                            onClick = null,
                        )
                        Text(text = "${lineCount} 行")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

private data class FolderChoice(
    val folder: String?,
    val name: String,
    val depth: Int,
)

@Composable
private fun NoteWidgetFolderPickerDialog(
    folderChoices: List<FolderChoice>?,
    selectedFolder: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.widget_folder_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (folderChoices == null) {
                    Text(text = stringResource(R.string.widget_folder_picker_loading))
                }
                folderChoices?.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice.folder) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.width((choice.depth * 20).dp))
                        RadioButton(
                            selected = selectedFolder == choice.folder,
                            onClick = null,
                        )
                        Text(
                            text = when (choice.folder) {
                                null -> stringResource(R.string.widget_folder_all_notes)
                                "" -> stringResource(R.string.widget_folder_root)
                                else -> choice.name
                            },
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun buildExpandedFolderChoices(paths: List<String>): List<FolderChoice> {
    val normalizedPaths = paths
        .asSequence()
        .map { it.trim().trim('/') }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

    val result = mutableListOf(
        FolderChoice(folder = null, name = "", depth = 0),
        FolderChoice(folder = "", name = "", depth = 0),
    )

    fun appendChildren(prefix: String, depth: Int) {
        val prefixWithSlash = prefix.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
        normalizedPaths
            .asSequence()
            .filter { it.startsWith(prefixWithSlash) && it != prefix }
            .map { it.removePrefix(prefixWithSlash).substringBefore('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .forEach { name ->
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                result += FolderChoice(folder = path, name = name, depth = depth)
                appendChildren(path, depth + 1)
            }
    }

    appendChildren(prefix = "", depth = 0)
    return result
}
