package com.kangle.kardleaf.widget

import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
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
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.ThemeColorPickerDialog
import com.kangle.kardleaf.ui.ThemeCustomAccentColorPalette
import com.kangle.kardleaf.ui.ThemeCustomBackgroundColorPalette
import com.kangle.kardleaf.ui.FileTreePickerDialog
import com.kangle.kardleaf.ui.FileTreePickerNode
import com.kangle.kardleaf.ui.FileTreeSelectionMode
import com.kangle.kardleaf.ui.buildFileTreePickerFolderNodes
import com.kangle.kardleaf.ui.normalizeFolderPathForUi
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
        val widgetKind = runCatching {
            WidgetTheme.Kind.valueOf(intent.getStringExtra(EXTRA_WIDGET_KIND).orEmpty())
        }.getOrDefault(WidgetTheme.Kind.NOTE)
        val initialWidgetTheme = WidgetTheme.load(this, widgetKind, appWidgetId)
        KardLeafLog.i(
            WIDGET_THEME_LOG_TAG,
            "theme settings opened kind=${widgetKind.name} widgetId=$appWidgetId openSettings=$openSettings " +
                "initialPreset=${initialWidgetTheme.preset.name} action=${intent.action} data=${intent.data}",
        )

        setContent {
            KardLeafTheme(styleSystemBars = false) {
                if (openSettings) {
                    var hideTitle by remember(appWidgetId) {
                        mutableStateOf(NoteListWidgetProvider.isTitleHidden(this, appWidgetId))
                    }
                    var previewLines by remember(appWidgetId) {
                        mutableStateOf(NoteListWidgetProvider.previewLineCount(this, appWidgetId))
                    }
                    var widgetTheme by remember(appWidgetId, widgetKind) {
                        mutableStateOf(initialWidgetTheme)
                    }
                    var dailyFolder by remember(appWidgetId) {
                        mutableStateOf(PrefsManager(this).getDailyNoteFolder())
                    }
                    var dailyFolderPaths by remember { mutableStateOf<List<String>?>(null) }
                    var showDailyFolderPicker by remember { mutableStateOf(false) }
                    var showCustomAccentDialog by remember { mutableStateOf(false) }
                    var showCustomBackgroundDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(appWidgetId, widgetKind) {
                        if (widgetKind == WidgetTheme.Kind.DAILY) {
                            dailyFolderPaths = loadFolderPaths(dailyFolder)
                        }
                    }

                    WidgetSettingsDialog(
                        widgetKind = widgetKind,
                        hideTitle = hideTitle,
                        previewLines = previewLines,
                        dailyFolder = dailyFolder,
                        widgetTheme = widgetTheme,
                        onHideTitleChange = { hideTitle = it },
                        onPreviewLinesChange = { previewLines = it },
                        onDailyFolderChange = { dailyFolder = it },
                        onPickDailyFolder = { showDailyFolderPicker = true },
                        onThemePresetChange = { preset ->
                            KardLeafLog.i(
                                WIDGET_THEME_LOG_TAG,
                                "theme preset selected kind=${widgetKind.name} widgetId=$appWidgetId " +
                                    "from=${widgetTheme.preset.name} to=${preset.name}",
                            )
                            widgetTheme = widgetTheme.copy(preset = preset)
                        },
                        onPickCustomAccent = { showCustomAccentDialog = true },
                        onPickCustomBackground = { showCustomBackgroundDialog = true },
                        onConfirm = {
                            KardLeafLog.i(
                                WIDGET_THEME_LOG_TAG,
                                "theme settings confirm kind=${widgetKind.name} widgetId=$appWidgetId " +
                                    "preset=${widgetTheme.preset.name}",
                            )
                            WidgetTheme.save(this, widgetKind, appWidgetId, widgetTheme)
                            when (widgetKind) {
                                WidgetTheme.Kind.NOTE -> {
                                    NoteListWidgetProvider.setDisplayOptions(
                                        context = this,
                                        appWidgetId = appWidgetId,
                                        hideTitle = hideTitle,
                                        previewLines = previewLines,
                                    )
                                }
                                WidgetTheme.Kind.TASK -> TaskListWidgetProvider.refreshAllWidgets(this)
                                WidgetTheme.Kind.DAILY -> {
                                    PrefsManager(this).saveDailyNoteFolder(dailyFolder)
                                    DailyNoteWidgetProvider.refreshAllWidgets(this)
                                }
                            }
                            finish()
                        },
                        onDismiss = { finish() },
                    )
                    if (showCustomAccentDialog) {
                        ThemeColorPickerDialog(
                            title = "自定义小部件强调色",
                            presets = ThemeCustomAccentColorPalette,
                            selectedArgb = widgetTheme.customAccent,
                            onApply = { color ->
                                widgetTheme = widgetTheme.copy(customAccent = color)
                                showCustomAccentDialog = false
                            },
                            onDismiss = { showCustomAccentDialog = false },
                        )
                    }
                    if (showCustomBackgroundDialog) {
                        ThemeColorPickerDialog(
                            title = "自定义小部件背景色",
                            presets = ThemeCustomBackgroundColorPalette,
                            selectedArgb = widgetTheme.customBackground,
                            onApply = { color ->
                                widgetTheme = widgetTheme.copy(customBackground = color)
                                showCustomBackgroundDialog = false
                            },
                            onDismiss = { showCustomBackgroundDialog = false },
                        )
                    }
                    if (showDailyFolderPicker) {
                        FileTreePickerDialog(
                            title = stringResource(R.string.widget_folder_picker_title),
                            nodes = dailyFolderPaths?.let {
                                buildWidgetFolderPickerNodes(
                                    paths = it,
                                    rootLabel = stringResource(R.string.widget_folder_root),
                                )
                            },
                            selectedId = folderPickerSelectionId(dailyFolder),
                            selectionMode = FileTreeSelectionMode.FOLDER,
                            loadingText = stringResource(R.string.widget_folder_picker_loading),
                            onSelect = { node ->
                                dailyFolder = node.value
                                showDailyFolderPicker = false
                            },
                            onDismiss = { showDailyFolderPicker = false },
                        )
                    }
                    return@KardLeafTheme
                }

                var folderPaths by remember { mutableStateOf<List<String>?>(null) }
                val selectedFolder = remember(appWidgetId) {
                    NoteListWidgetProvider.selectedFolder(this, appWidgetId)
                }

                LaunchedEffect(appWidgetId) {
                    folderPaths = loadFolderPaths()
                }

                FileTreePickerDialog(
                    title = stringResource(R.string.widget_folder_picker_title),
                    nodes = folderPaths?.let {
                        buildWidgetFolderPickerNodes(
                            paths = it,
                            rootLabel = stringResource(R.string.widget_folder_root),
                        )
                    },
                    selectedId = folderPickerSelectionId(selectedFolder),
                    selectionMode = FileTreeSelectionMode.FOLDER,
                    loadingText = stringResource(R.string.widget_folder_picker_loading),
                    onSelect = { node ->
                        NoteListWidgetProvider.selectFolder(this, appWidgetId, node.value)
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

    private suspend fun loadFolderPaths(extraPath: String? = null): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = applicationContext
            val hiddenFolders = PrefsManager(appContext)
                .getHiddenFolderPaths()
                .map(::normalizeFolderPathForUi)
                .filter(String::isNotBlank)
            val paths = AppDatabase.getDatabase(appContext)
                .labelDao()
                .getAllLabels()
                .first()
            (paths + listOfNotNull(extraPath)).asSequence()
                .map(::normalizeFolderPathForUi)
                .filter(String::isNotBlank)
                .filterNot { path ->
                    hiddenFolders.any { hidden ->
                        path == hidden || path.startsWith("$hidden/")
                    }
                }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())
    }

    companion object {
        internal const val EXTRA_OPEN_SETTINGS = "kardleaf_widget_open_settings"
        internal const val EXTRA_WIDGET_KIND = "kardleaf_widget_kind"
    }
}

@Composable
private fun WidgetSettingsDialog(
    widgetKind: WidgetTheme.Kind,
    hideTitle: Boolean,
    previewLines: Int,
    dailyFolder: String,
    widgetTheme: WidgetTheme.Settings,
    onHideTitleChange: (Boolean) -> Unit,
    onPreviewLinesChange: (Int) -> Unit,
    onDailyFolderChange: (String) -> Unit,
    onPickDailyFolder: () -> Unit,
    onThemePresetChange: (WidgetTheme.Preset) -> Unit,
    onPickCustomAccent: () -> Unit,
    onPickCustomBackground: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (widgetKind) {
                    WidgetTheme.Kind.NOTE -> "笔记列表设置"
                    WidgetTheme.Kind.TASK -> "任务清单设置"
                    WidgetTheme.Kind.DAILY -> "每日笔记设置"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (widgetKind == WidgetTheme.Kind.NOTE) {
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
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                    (1..3).forEach { lineCount ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPreviewLinesChange(lineCount) }
                                .padding(vertical = 2.dp),
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

                if (widgetKind == WidgetTheme.Kind.DAILY) {
                    Text("每日笔记存放目录")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = dailyFolder,
                            onValueChange = onDailyFolderChange,
                            label = { Text("相对笔记库路径") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onPickDailyFolder) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = "选择目录",
                            )
                        }
                    }
                    Text(
                        text = "留空表示笔记库根目录，文件名使用今天的日期。",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "小部件主题",
                    modifier = Modifier.padding(top = 8.dp),
                )
                WidgetTheme.Preset.values().forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemePresetChange(preset) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = widgetTheme.preset == preset,
                            onClick = null,
                        )
                        Text(preset.label)
                    }
                }
                if (widgetTheme.preset == WidgetTheme.Preset.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onPickCustomAccent) { Text("强调色") }
                        TextButton(onClick = onPickCustomBackground) { Text("背景色") }
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

private const val ROOT_PICKER_ID = "__kardleaf_root__"

private fun folderPickerSelectionId(folder: String?): String? {
    if (folder == null) return null
    val normalized = normalizeFolderPathForUi(folder)
    return normalized.ifBlank { ROOT_PICKER_ID }
}

private fun buildWidgetFolderPickerNodes(
    paths: Collection<String>,
    rootLabel: String,
): List<FileTreePickerNode<String>> {
    val folders = buildFileTreePickerFolderNodes(paths)
    val root = FileTreePickerNode(
        id = ROOT_PICKER_ID,
        label = rootLabel,
        value = "",
        hasChildren = folders.any { it.depth == 0 },
    )
    val nestedFolders = folders.map { folder ->
        folder.copy(
            depth = folder.depth + 1,
            parentId = folder.parentId ?: ROOT_PICKER_ID,
        )
    }
    return buildList {
        add(root)
        addAll(nestedFolders)
    }
}
