package com.kangle.kardleaf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.NoteRecordSummary
import com.kangle.kardleaf.data.repository.PrefsManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object KardLeafCustomFeatures {
    const val UseDynamicColor = false
    const val DefaultUnnamedNoteDateFormat = "yyyy.MM.dd.HHmmss"
    val DefaultOpenNoteMode = OpenNoteMode.PREVIEW
    const val DefaultEditDoubleTapPreview = false

    private const val PrefsName = "kardleaf_custom_features"
    private const val KeyUnnamedNoteDateFormat = "unnamed_note_date_format"
    private const val KeyOpenNoteMode = "open_note_mode"
    private const val KeyEditDoubleTapPreview = "edit_double_tap_preview"
    private const val KeyToolbarOrder = "toolbar_order"
    private val unsafeFileNameChars = Regex("[\\\\/:*?\"<>|]")
    private val unsafeFolderSegmentChars = Regex("[:*?\"<>|]")

    enum class OpenNoteMode(val label: String) {
        PREVIEW("Preview"),
        EDIT("Edit"),
    }

    enum class ToolbarItem(val label: String) {
        PREVIEW("预览"),
        UNDO("撤销"),
        REDO("恢复"),
        IMAGE("图片"),
        HEADING("标题"),
        RULE("分割线"),
        BOLD("加粗"),
        ITALIC("斜体"),
        UNDERLINE("下划线"),
        STRIKE("删除线"),
        LINK("链接"),
        CODE("代码"),
        QUOTE("引用"),
        MATH("公式"),
        BULLET("无序列表"),
        NUMBERED("有序列表"),
        CHECKBOX("待办"),
    }

    val DefaultToolbarOrder =
        listOf(
            ToolbarItem.PREVIEW,
            ToolbarItem.UNDO,
            ToolbarItem.REDO,
            ToolbarItem.IMAGE,
            ToolbarItem.HEADING,
            ToolbarItem.RULE,
            ToolbarItem.BOLD,
            ToolbarItem.ITALIC,
            ToolbarItem.UNDERLINE,
            ToolbarItem.STRIKE,
            ToolbarItem.LINK,
            ToolbarItem.CODE,
            ToolbarItem.QUOTE,
            ToolbarItem.MATH,
            ToolbarItem.BULLET,
            ToolbarItem.NUMBERED,
            ToolbarItem.CHECKBOX,
        )

    data class ExternalNoteDraft(
        val title: String = "",
        val content: String = "",
        val folder: String = "",
        val isTemporary: Boolean = false,
        val isPinned: Boolean = false,
    )

    fun parseExternalCreateNoteUri(uri: Uri?): ExternalNoteDraft? {
        if (uri == null) return null
        if (uri.scheme != "kardleaf" || uri.host != "new") return null

        val title = sanitizeTitle(uri.getQueryParameter("title").orEmpty())
        val content =
            firstQueryParameter(uri, "content", "body", "text")
                .orEmpty()
        val url = uri.getQueryParameter("url").orEmpty()
        val finalContent =
            when {
                content.isNotBlank() && url.isNotBlank() -> "$content\n\n$url"
                content.isNotBlank() -> content
                else -> url
            }
        val folder =
            sanitizeFolderPath(
                firstQueryParameter(uri, "path", "folder", "label").orEmpty(),
            )
        val isPinned = parseBoolean(uri.getQueryParameter("pinned"))

        return ExternalNoteDraft(
            title = title,
            content = finalContent,
            folder = folder,
            isPinned = isPinned,
        )
    }

    private fun firstQueryParameter(
        uri: Uri,
        vararg names: String,
    ): String? = names.firstNotNullOfOrNull { uri.getQueryParameter(it) }

    private fun sanitizeFolderPath(path: String): String {
        return path
            .trim()
            .replace("\\", "/")
            .split("/")
            .map { segment ->
                segment
                    .trim()
                    .replace(unsafeFolderSegmentChars, " - ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            .filter { segment ->
                segment.isNotBlank() &&
                    segment != "." &&
                    segment != ".." &&
                    segment != "Unknown"
            }
            .joinToString("/")
    }

    private fun sanitizeTitle(title: String): String {
        return title
            .trim()
            .replace(unsafeFileNameChars, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseBoolean(value: String?): Boolean {
        return when (value?.trim()?.lowercase(Locale.ROOT)) {
            "1", "true", "yes", "y", "on" -> true
            else -> false
        }
    }

    fun getUnnamedNoteDateFormat(context: Context): String {
        val configured =
            context
                .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                .getString(KeyUnnamedNoteDateFormat, DefaultUnnamedNoteDateFormat)
                .orEmpty()
                .trim()

        return configured.takeIf { isDateFormatUsable(it) } ?: DefaultUnnamedNoteDateFormat
    }

    fun saveUnnamedNoteDateFormat(
        context: Context,
        dateFormat: String,
    ): Boolean {
        val normalized = dateFormat.trim()
        if (!isDateFormatUsable(normalized)) return false

        context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyUnnamedNoteDateFormat, normalized)
            .apply()
        return true
    }

    fun getOpenNoteMode(context: Context): OpenNoteMode {
        val configured =
            context
                .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                .getString(KeyOpenNoteMode, DefaultOpenNoteMode.name)

        return runCatching {
            OpenNoteMode.valueOf(configured ?: DefaultOpenNoteMode.name)
        }.getOrDefault(DefaultOpenNoteMode)
    }

    fun saveOpenNoteMode(
        context: Context,
        mode: OpenNoteMode,
    ) {
        context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyOpenNoteMode, mode.name)
            .apply()
    }

    fun isEditDoubleTapPreviewEnabled(context: Context): Boolean {
        return context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .getBoolean(KeyEditDoubleTapPreview, DefaultEditDoubleTapPreview)
    }

    fun saveEditDoubleTapPreviewEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KeyEditDoubleTapPreview, enabled)
            .apply()
    }

    fun getToolbarOrder(context: Context): List<ToolbarItem> {
        val configured =
            context
                .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                .getString(KeyToolbarOrder, null)
                ?.split(",")
                ?.mapNotNull { name -> runCatching { ToolbarItem.valueOf(name) }.getOrNull() }
                .orEmpty()

        return (configured + DefaultToolbarOrder).distinct()
    }

    fun saveToolbarOrder(
        context: Context,
        order: List<ToolbarItem>,
    ) {
        context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyToolbarOrder, order.distinct().joinToString(",") { it.name })
            .apply()
    }

    fun formatUnnamedNoteTitle(
        context: Context,
        date: Date = Date(),
        locale: Locale = Locale.getDefault(),
    ): String {
        return runCatching {
            formatDateForFileName(getUnnamedNoteDateFormat(context), date, locale)
        }.getOrElse {
            formatDateForFileName(DefaultUnnamedNoteDateFormat, date, locale)
        }
    }

    fun previewUnnamedNoteTitle(dateFormat: String): String {
        return runCatching {
            formatDateForFileName(dateFormat.trim(), Date(), Locale.getDefault())
        }.getOrDefault("")
    }

    fun isDateFormatUsable(dateFormat: String): Boolean {
        if (dateFormat.isBlank()) return false
        return runCatching {
            SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date())
        }.isSuccess
    }

    private fun formatDateForFileName(
        dateFormat: String,
        date: Date,
        locale: Locale,
    ): String {
        val formatted = SimpleDateFormat(dateFormat, locale).format(date)
        return formatted
            .replace(unsafeFileNameChars, "_")
            .trim()
            .ifBlank { SimpleDateFormat(DefaultUnnamedNoteDateFormat, locale).format(date) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun KardLeafSettingsScreen(
    onBack: () -> Unit,
    onSelectDatabase: () -> Unit,
    onSettingsChanged: () -> Unit,
    onRestartNeeded: () -> Unit = {},
    onExportUserData: () -> Unit = {},
    onImportUserData: () -> Unit = {},
    onSelectImageFolder: ((Uri) -> Unit) -> Unit = {},
    onSelectBackupDir: ((Uri) -> Unit) -> Unit = {},
    onLoadHistoryCleanupPreview: suspend (Int) -> List<HistoryCleanupPreview> = { emptyList() },
    onLoadRemarkNoteSummaries: suspend () -> List<NoteRecordSummary> = { emptyList() },
    onLoadHistoryNoteSummaries: suspend () -> List<NoteRecordSummary> = { emptyList() },
    onCleanupHistory: () -> Unit = {},
    labels: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    val scope = rememberCoroutineScope()
    var settingsPage by remember { mutableStateOf("main") }
    var settingsDialog by remember { mutableStateOf<String?>(null) }
    val mainScrollState = rememberScrollState()
    val detailScrollState = rememberScrollState()
    var savedMainScrollValue by remember { mutableStateOf(0) }

    fun openSettingsPage(page: String) {
        if (settingsPage == "main") {
            savedMainScrollValue = mainScrollState.value
        }
        settingsPage = page
    }

    fun returnToSettingsMain() {
        settingsPage = "main"
    }

    LaunchedEffect(settingsPage) {
        if (settingsPage == "main") {
            mainScrollState.scrollTo(savedMainScrollValue)
        } else {
            detailScrollState.scrollTo(0)
        }
    }


    var dateFormat by remember { mutableStateOf(KardLeafCustomFeatures.getUnnamedNoteDateFormat(context)) }
    var openNoteMode by remember { mutableStateOf(KardLeafCustomFeatures.getOpenNoteMode(context)) }
    var trashFolderName by remember { mutableStateOf(prefsManager.getTrashFolderName()) }
    var trashSortOrder by remember { mutableStateOf(prefsManager.getTrashSortOrder()) }
    var cardDensity by remember { mutableStateOf(prefsManager.getCardDensity()) }
    var viewMode by remember { mutableStateOf(prefsManager.getViewMode()) }
    var sortOrder by remember { mutableStateOf(prefsManager.getSortOrder()) }
    var sortDirection by remember { mutableStateOf(prefsManager.getSortDirection()) }
    var imageFolder by remember { mutableStateOf(prefsManager.getImageFolder()) }
    var hiddenFolders by remember { mutableStateOf(prefsManager.getHiddenFolderPaths()) }
    var relativeImageLocation by remember { mutableStateOf(prefsManager.getRelativeImageLocation()) }
    var themeColor by remember { mutableStateOf(prefsManager.getThemeColor()) }
    var themeBackgroundColor by remember { mutableStateOf(prefsManager.getThemeBackgroundColor()) }
    var drawerEdgeWidthText by remember { mutableStateOf(prefsManager.getDrawerEdgeWidthDp().toString()) }
    var noteSidePanelsEnabled by remember { mutableStateOf(prefsManager.isNoteSidePanelsEnabled()) }
    var showYamlTagsOnLooseCards by remember { mutableStateOf(prefsManager.isLooseCardYamlTagsVisible()) }
    var historyLimitText by remember { mutableStateOf(prefsManager.getHistoryVersionLimit().toString()) }
    var doubleTapIntervalText by remember { mutableStateOf(prefsManager.getPreviewDoubleTapIntervalMs().toString()) }
    var trashAutoCleanDaysText by remember { mutableStateOf(prefsManager.getTrashAutoCleanDays().toString()) }
    var passwordInputMode by remember { mutableStateOf(prefsManager.getPasswordInputMode()) }
    var toolbarOrder by remember { mutableStateOf(KardLeafCustomFeatures.getToolbarOrder(context)) }
    var restoreLastFilter by remember { mutableStateOf(prefsManager.isRestoreLastFilterEnabled()) }
    var defaultStartLabel by remember { mutableStateOf(prefsManager.getDefaultStartLabel()) }
    var showLabelPicker by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showCleanupHistoryDialog by remember { mutableStateOf(false) }
    var showTrashFolderPicker by remember { mutableStateOf(false) }
    var historyCleanupPreview by remember { mutableStateOf<List<HistoryCleanupPreview>>(emptyList()) }
    var isLoadingHistoryCleanupPreview by remember { mutableStateOf(false) }
    var remarkNoteSummaries by remember { mutableStateOf<List<NoteRecordSummary>>(emptyList()) }
    var historyNoteSummaries by remember { mutableStateOf<List<NoteRecordSummary>>(emptyList()) }
    var isLoadingRecordSummaries by remember { mutableStateOf(false) }

    LaunchedEffect(settingsPage) {
        when (settingsPage) {
            "remarkRecords" -> {
                isLoadingRecordSummaries = true
                remarkNoteSummaries = runCatching { onLoadRemarkNoteSummaries() }.getOrDefault(emptyList())
                isLoadingRecordSummaries = false
            }
            "historyRecords" -> {
                isLoadingRecordSummaries = true
                historyNoteSummaries = runCatching { onLoadHistoryNoteSummaries() }.getOrDefault(emptyList())
                isLoadingRecordSummaries = false
            }
        }
    }

    val normalized = dateFormat.trim()
    val normalizedTrashFolderName = trashFolderName.trim()
    val isValid = KardLeafCustomFeatures.isDateFormatUsable(normalized)
    val isTrashFolderValid = normalizedTrashFolderName.isNotBlank() && !normalizedTrashFolderName.contains(Regex("[\\\\/:*?\"<>|]"))
    val drawerEdgeWidth = drawerEdgeWidthText.trim().toIntOrNull()
    val isDrawerEdgeWidthValid = drawerEdgeWidth != null && drawerEdgeWidth in 24..160
    val historyLimit = historyLimitText.trim().toIntOrNull()
    val isHistoryLimitValid = historyLimit != null &&
        historyLimit in PrefsManager.MIN_HISTORY_VERSION_LIMIT..PrefsManager.MAX_HISTORY_VERSION_LIMIT
    val doubleTapInterval = doubleTapIntervalText.trim().toIntOrNull()
    val isDoubleTapIntervalValid = doubleTapInterval != null &&
        doubleTapInterval in PrefsManager.MIN_PREVIEW_DOUBLE_TAP_INTERVAL_MS..PrefsManager.MAX_PREVIEW_DOUBLE_TAP_INTERVAL_MS
    val trashAutoCleanDays = trashAutoCleanDaysText.trim().toIntOrNull()
    val isTrashAutoCleanDaysValid = trashAutoCleanDays != null && trashAutoCleanDays in 0..365
    val savedHistoryLimit = prefsManager.getHistoryVersionLimit()
    val sample = if (isValid) KardLeafCustomFeatures.previewUnnamedNoteTitle(normalized) else ""
    fun normalizeSettingsFolderPath(folder: String): String =
        folder.trim().replace("\\", "/").trim('/')

    fun handleImageFolderPicked(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefsManager.saveImageFolderUri(uri.toString())
        val picked = uri.lastPathSegment?.substringAfterLast("/")?.ifBlank { imageFolder } ?: imageFolder
        imageFolder = picked
        prefsManager.saveImageFolder(picked)
    }

    fun handleBackupDirPicked(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefsManager.saveAutoBackupDirUri(uri.toString())
    }

    fun applyThemeColor(color: PrefsManager.ThemeColor) {
        if (themeColor == color) return
        themeColor = color
        prefsManager.saveThemeColor(color)
        onRestartNeeded()
    }

    fun applyThemeBackgroundColor(color: PrefsManager.ThemeBackgroundColor) {
        if (themeBackgroundColor == color) return
        themeBackgroundColor = color
        prefsManager.saveThemeBackgroundColor(color)
        onRestartNeeded()
    }

    fun resetTheme() {
        val changed = themeColor != PrefsManager.ThemeColor.BLUE ||
            themeBackgroundColor != PrefsManager.ThemeBackgroundColor.WHITE
        themeColor = PrefsManager.ThemeColor.BLUE
        themeBackgroundColor = PrefsManager.ThemeBackgroundColor.WHITE
        prefsManager.saveThemeColor(themeColor)
        prefsManager.saveThemeBackgroundColor(themeBackgroundColor)
        if (changed) onRestartNeeded()
    }

    fun openCleanupHistoryDialog() {
        val keep = historyLimitText.trim().toIntOrNull()
            ?.coerceIn(PrefsManager.MIN_HISTORY_VERSION_LIMIT, PrefsManager.MAX_HISTORY_VERSION_LIMIT)
            ?: prefsManager.getHistoryVersionLimit()
        showCleanupHistoryDialog = true
        isLoadingHistoryCleanupPreview = true
        historyCleanupPreview = emptyList()
        scope.launch {
            historyCleanupPreview = runCatching { onLoadHistoryCleanupPreview(keep) }.getOrDefault(emptyList())
            isLoadingHistoryCleanupPreview = false
        }
    }

    if (showLabelPicker) {
        val normalizedLabels = remember(labels) {
            labels.map { it.replace("\\", "/").trim() }.filter { it.isNotBlank() && it != "Unknown" && it != "." }.distinct().sorted()
        }
        AlertDialog(
            onDismissRequest = { showLabelPicker = false },
            title = { Text("选择默认启动分类") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsChoiceRow(
                        icon = Icons.Outlined.Folder,
                        title = "不指定（全部笔记）",
                        subtitle = "打开软件显示全部笔记",
                        selected = defaultStartLabel.isBlank(),
                        onClick = {
                            defaultStartLabel = ""
                            prefsManager.saveDefaultStartLabel("")
                            showLabelPicker = false
                        },
                    )
                    normalizedLabels.forEach { label ->
                        val displayName = label.substringAfterLast("/")
                        SettingsChoiceRow(
                            icon = Icons.Outlined.Folder,
                            title = displayName,
                            subtitle = label,
                            selected = defaultStartLabel == label,
                            onClick = {
                                defaultStartLabel = label
                                prefsManager.saveDefaultStartLabel(label)
                                showLabelPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLabelPicker = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showTrashFolderPicker) {
        val normalizedFolders = remember(labels, trashFolderName) {
            (labels + trashFolderName)
                .map { it.replace("\\", "/").trim() }
                .filter { it.isNotBlank() && it != "Unknown" && it != "." }
                .map { it.substringAfterLast("/").trim() }
                .filter { it.isNotBlank() && !it.contains(Regex("[\\\\/:*?\"<>|]")) }
                .distinct()
                .sorted()
        }
        AlertDialog(
            onDismissRequest = { showTrashFolderPicker = false },
            title = { Text("选择回收站文件夹") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsChoiceRow(
                        icon = Icons.Outlined.Delete,
                        title = PrefsManager.DEFAULT_TRASH_FOLDER_NAME,
                        subtitle = "使用默认回收站文件夹",
                        selected = trashFolderName == PrefsManager.DEFAULT_TRASH_FOLDER_NAME,
                        onClick = {
                            trashFolderName = PrefsManager.DEFAULT_TRASH_FOLDER_NAME
                            prefsManager.saveTrashFolderName(trashFolderName)
                            showTrashFolderPicker = false
                            onSettingsChanged()
                        },
                    )
                    normalizedFolders.forEach { folder ->
                        if (folder != PrefsManager.DEFAULT_TRASH_FOLDER_NAME) {
                            SettingsChoiceRow(
                                icon = Icons.Outlined.Folder,
                                title = folder,
                                subtitle = "设为回收站文件夹",
                                selected = trashFolderName == folder,
                                onClick = {
                                    trashFolderName = folder
                                    prefsManager.saveTrashFolderName(folder)
                                    showTrashFolderPicker = false
                                    onSettingsChanged()
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTrashFolderPicker = false }) {
                    Text("取消")
                }
            },
        )
    }

    fun resetSettings() {
        val oldThemeColor = prefsManager.getThemeColor()
        val oldThemeBackgroundColor = prefsManager.getThemeBackgroundColor()
        dateFormat = KardLeafCustomFeatures.DefaultUnnamedNoteDateFormat
        openNoteMode = KardLeafCustomFeatures.DefaultOpenNoteMode
        trashFolderName = PrefsManager.DEFAULT_TRASH_FOLDER_NAME
        trashSortOrder = PrefsManager.TrashSortOrder.DELETED_TIME
        viewMode = PrefsManager.ViewMode.LIST
        sortOrder = PrefsManager.SortOrder.DATE_MODIFIED
        sortDirection = PrefsManager.SortDirection.DESCENDING
        imageFolder = PrefsManager.DEFAULT_IMAGE_FOLDER
        relativeImageLocation = PrefsManager.RelativeImageLocation.CURRENT_NOTE_FOLDER
        themeColor = PrefsManager.ThemeColor.BLUE
        themeBackgroundColor = PrefsManager.ThemeBackgroundColor.WHITE
        drawerEdgeWidthText = PrefsManager.DEFAULT_DRAWER_EDGE_WIDTH_DP.toString()
        noteSidePanelsEnabled = PrefsManager.DEFAULT_NOTE_SIDE_PANELS_ENABLED
        showYamlTagsOnLooseCards = false
        historyLimitText = PrefsManager.DEFAULT_HISTORY_VERSION_LIMIT.toString()
        cardDensity = PrefsManager.CardDensity.LOOSE
        toolbarOrder = KardLeafCustomFeatures.DefaultToolbarOrder
        restoreLastFilter = true
        defaultStartLabel = ""
        KardLeafCustomFeatures.saveUnnamedNoteDateFormat(context, dateFormat)
        KardLeafCustomFeatures.saveOpenNoteMode(context, openNoteMode)
        KardLeafCustomFeatures.saveToolbarOrder(context, toolbarOrder)
        prefsManager.saveTrashFolderName(trashFolderName)
        prefsManager.saveTrashSortOrder(trashSortOrder)
        prefsManager.saveViewMode(viewMode)
        prefsManager.saveSortOrder(sortOrder)
        prefsManager.saveSortDirection(sortDirection)
        prefsManager.saveImageFolder(imageFolder)
        prefsManager.saveImageFolderUri(null)
        prefsManager.saveRelativeImageLocation(relativeImageLocation)
        prefsManager.saveThemeColor(themeColor)
        prefsManager.saveThemeBackgroundColor(themeBackgroundColor)
        prefsManager.saveDrawerEdgeWidthDp(PrefsManager.DEFAULT_DRAWER_EDGE_WIDTH_DP)
        prefsManager.saveNoteSidePanelsEnabled(noteSidePanelsEnabled)
        prefsManager.saveLooseCardYamlTagsVisible(showYamlTagsOnLooseCards)
        prefsManager.saveHistoryVersionLimit(PrefsManager.DEFAULT_HISTORY_VERSION_LIMIT)
        doubleTapIntervalText = PrefsManager.DEFAULT_PREVIEW_DOUBLE_TAP_INTERVAL_MS.toString()
        prefsManager.savePreviewDoubleTapIntervalMs(PrefsManager.DEFAULT_PREVIEW_DOUBLE_TAP_INTERVAL_MS)
        trashAutoCleanDaysText = PrefsManager.DEFAULT_TRASH_AUTO_CLEAN_DAYS.toString()
        prefsManager.saveTrashAutoCleanDays(PrefsManager.DEFAULT_TRASH_AUTO_CLEAN_DAYS)
        passwordInputMode = PrefsManager.PasswordInputMode.COMPLEX
        prefsManager.savePasswordInputMode(passwordInputMode)
        prefsManager.saveCardDensity(cardDensity)
        prefsManager.saveRestoreLastFilterEnabled(restoreLastFilter)
        prefsManager.saveDefaultStartLabel(defaultStartLabel)
        onSettingsChanged()
        if (oldThemeColor != themeColor || oldThemeBackgroundColor != themeBackgroundColor) {
            onRestartNeeded()
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认设置") },
            text = { Text("是否将所有设置恢复为默认值？此操作会立即生效") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    resetSettings()
                }) { Text("恢复默认") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showCleanupHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupHistoryDialog = false },
            title = { Text("清理旧历史版本？") },
            text = {
                HistoryCleanupPreviewContent(
                    keep = savedHistoryLimit,
                    preview = historyCleanupPreview,
                    isLoading = isLoadingHistoryCleanupPreview,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isLoadingHistoryCleanupPreview && historyCleanupPreview.isNotEmpty(),
                    onClick = {
                        showCleanupHistoryDialog = false
                        onCleanupHistory()
                    },
                ) { Text("确认清理") }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupHistoryDialog = false }) {
                    Text(if (historyCleanupPreview.isEmpty() && !isLoadingHistoryCleanupPreview) "知道了" else "取消")
                }
            },
        )
    }

    val dialogPage = settingsDialog
    if (dialogPage != null) {
        AlertDialog(
            onDismissRequest = { settingsDialog = null },
            title = { Text(settingsPageTitle(dialogPage)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (dialogPage) {
                        "layout" -> PrefsManager.ViewMode.values().forEach { mode ->
                            SettingsChoiceRow(
                                icon = if (mode == PrefsManager.ViewMode.LIST) Icons.Outlined.ViewAgenda else Icons.Outlined.ViewModule,
                                title = if (mode == PrefsManager.ViewMode.LIST) "列表" else "双列",
                                subtitle = if (mode == PrefsManager.ViewMode.LIST) "单列阅读更清楚" else "双列显示更多",
                                selected = viewMode == mode,
                                onClick = {
                                    viewMode = mode
                                    prefsManager.saveViewMode(mode)
                                    onSettingsChanged()
                                    settingsDialog = null
                                },
                            )
                        }
                        "density" -> PrefsManager.CardDensity.values().forEach { density ->
                            SettingsChoiceRow(
                                icon = if (density == PrefsManager.CardDensity.LOOSE) Icons.Outlined.ViewStream else Icons.Outlined.ViewCompact,
                                title = if (density == PrefsManager.CardDensity.LOOSE) "宽松" else "紧凑",
                                subtitle = if (density == PrefsManager.CardDensity.LOOSE) "间距更舒展" else "同屏更多笔记",
                                selected = cardDensity == density,
                                onClick = {
                                    cardDensity = density
                                    prefsManager.saveCardDensity(density)
                                    onSettingsChanged()
                                    settingsDialog = null
                                },
                            )
                        }
                        "sort" -> {
                            SettingsSectionTitle("排序字段")
                            PrefsManager.SortOrder.values().forEach { order ->
                                SettingsChoiceRow(
                                    icon = Icons.Outlined.SortByAlpha,
                                    title = if (order == PrefsManager.SortOrder.DATE_MODIFIED) "修改日期" else "标题",
                                    subtitle = if (order == PrefsManager.SortOrder.DATE_MODIFIED) "按修改时间排序" else "按标题排序",
                                    selected = sortOrder == order,
                                    onClick = {
                                        sortOrder = order
                                        prefsManager.saveSortOrder(order)
                                        onSettingsChanged()
                                    },
                                )
                            }
                            SettingsSectionDivider()
                            SettingsSectionTitle("排序方向")
                            PrefsManager.SortDirection.values().forEach { direction ->
                                SettingsChoiceRow(
                                    icon = Icons.Outlined.SwapVert,
                                    title = if (direction == PrefsManager.SortDirection.DESCENDING) "降序" else "升序",
                                    subtitle = if (direction == PrefsManager.SortDirection.DESCENDING) "新内容在前" else "旧内容在前",
                                    selected = sortDirection == direction,
                                    onClick = {
                                        sortDirection = direction
                                        prefsManager.saveSortDirection(direction)
                                        onSettingsChanged()
                                    },
                                )
                            }
                        }
                        "openNote" -> KardLeafCustomFeatures.OpenNoteMode.values().forEach { mode ->
                            SettingsChoiceRow(
                                icon = if (mode == KardLeafCustomFeatures.OpenNoteMode.PREVIEW) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                                title = if (mode == KardLeafCustomFeatures.OpenNoteMode.PREVIEW) "查看模式" else "编辑模式",
                                subtitle = if (mode == KardLeafCustomFeatures.OpenNoteMode.PREVIEW) "先显示预览" else "直接进入编辑",
                                selected = openNoteMode == mode,
                                onClick = {
                                    openNoteMode = mode
                                    KardLeafCustomFeatures.saveOpenNoteMode(context, mode)
                                    settingsDialog = null
                                },
                            )
                        }
                        "backup" -> {
                            SettingsActionRow(
                                icon = Icons.Outlined.FileUpload,
                                title = "导出数据备份",
                                subtitle = "导出用户数据 JSON",
                                onClick = {
                                    settingsDialog = null
                                    onExportUserData()
                                },
                            )
                            SettingsActionRow(
                                icon = Icons.Outlined.FileDownload,
                                title = "导入数据备份",
                                subtitle = "从 JSON 恢复数据",
                                onClick = {
                                    settingsDialog = null
                                    onImportUserData()
                                },
                            )
                        }
                        "drawer" -> {
                            SettingsSectionTitle("侧边栏手势区域")
                            OutlinedTextField(
                                value = drawerEdgeWidthText,
                                onValueChange = {
                                    drawerEdgeWidthText = it.filter(Char::isDigit).take(3)
                                    val w = drawerEdgeWidthText.trim().toIntOrNull()
                                    if (w != null && w in 24..160) {
                                        prefsManager.saveDrawerEdgeWidthDp(w)
                                        onSettingsChanged()
                                    }
                                },
                                label = { Text("划出距离 dp") },
                                singleLine = true,
                                isError = !isDrawerEdgeWidthValid,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SettingsPageText("范围：24-160dp，越大越易划出")
                        }
                        "historyLimit" -> {
                            SettingsSectionTitle("历史版本数量")
                            OutlinedTextField(
                                value = historyLimitText,
                                onValueChange = { value ->
                                    historyLimitText = value.filter(Char::isDigit).take(3)
                                    val limit = historyLimitText.trim().toIntOrNull()
                                    if (limit != null &&
                                        limit in PrefsManager.MIN_HISTORY_VERSION_LIMIT..PrefsManager.MAX_HISTORY_VERSION_LIMIT
                                    ) {
                                        prefsManager.saveHistoryVersionLimit(limit)
                                    }
                                },
                                label = { Text("每篇笔记保留数量") },
                                singleLine = true,
                                isError = !isHistoryLimitValid,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SettingsPageText("范围：${PrefsManager.MIN_HISTORY_VERSION_LIMIT}-${PrefsManager.MAX_HISTORY_VERSION_LIMIT}")
                        }
                        "doubleTap" -> {
                            SettingsSectionTitle("预览双击间隔")
                            OutlinedTextField(
                                value = doubleTapIntervalText,
                                onValueChange = { value ->
                                    doubleTapIntervalText = value.filter(Char::isDigit).take(3)
                                    val interval = doubleTapIntervalText.trim().toIntOrNull()
                                    if (interval != null &&
                                        interval in PrefsManager.MIN_PREVIEW_DOUBLE_TAP_INTERVAL_MS..PrefsManager.MAX_PREVIEW_DOUBLE_TAP_INTERVAL_MS
                                    ) {
                                        prefsManager.savePreviewDoubleTapIntervalMs(interval)
                                    }
                                },
                                label = { Text("毫秒") },
                                singleLine = true,
                                isError = !isDoubleTapIntervalValid,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SettingsPageText("范围：${PrefsManager.MIN_PREVIEW_DOUBLE_TAP_INTERVAL_MS}-${PrefsManager.MAX_PREVIEW_DOUBLE_TAP_INTERVAL_MS}ms，越小越防误触")
                        }
                        "trashAutoClean" -> {
                            SettingsSectionTitle("自动清理回收站")
                            listOf(0, 1, 7, 30, 90).forEach { days ->
                                SettingsChoiceRow(
                                    icon = Icons.Outlined.DeleteSweep,
                                    title = if (days == 0) "关闭" else "$days 天后",
                                    subtitle = if (days == 0) "不自动清理回收站" else "删除超过 $days 天的废弃笔记",
                                    selected = trashAutoCleanDays == days,
                                    onClick = {
                                        trashAutoCleanDaysText = days.toString()
                                        prefsManager.saveTrashAutoCleanDays(days)
                                        settingsDialog = null
                                    },
                                )
                            }
                            SettingsSectionDivider()
                            OutlinedTextField(
                                value = trashAutoCleanDaysText,
                                onValueChange = { value ->
                                    trashAutoCleanDaysText = value.filter(Char::isDigit).take(3)
                                    val days = trashAutoCleanDaysText.trim().toIntOrNull()
                                    if (days != null && days in 0..365) {
                                        prefsManager.saveTrashAutoCleanDays(days)
                                    }
                                },
                                label = { Text("自定义天数") },
                                singleLine = true,
                                isError = !isTrashAutoCleanDaysValid,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        "passwordMode" -> PrefsManager.PasswordInputMode.values().forEach { mode ->
                            SettingsChoiceRow(
                                icon = if (mode == PrefsManager.PasswordInputMode.SIMPLE) Icons.Outlined.Lock else Icons.Outlined.Lock,
                                title = if (mode == PrefsManager.PasswordInputMode.SIMPLE) "简单密码" else "复杂密码",
                                subtitle = if (mode == PrefsManager.PasswordInputMode.SIMPLE) "使用内置数字键盘" else "使用系统键盘输入",
                                selected = passwordInputMode == mode,
                                onClick = {
                                    passwordInputMode = mode
                                    prefsManager.savePasswordInputMode(mode)
                                    settingsDialog = null
                                },
                            )
                        }
                        "imagePath" -> {
                            var imagePathMode by remember { mutableStateOf(prefsManager.getImagePathMode()) }
                            SettingsSectionTitle("图片引用路径")
                            PrefsManager.ImagePathMode.values().forEach { mode ->
                                SettingsChoiceRow(
                                    icon = Icons.Outlined.Image,
                                    title = if (mode == PrefsManager.ImagePathMode.ROOT) "基于仓库根路径" else "基于当前笔记相对路径",
                                    subtitle = if (mode == PrefsManager.ImagePathMode.ROOT) "附件目录固定引用" else "按笔记位置生成引用",
                                    selected = imagePathMode == mode,
                                    onClick = { imagePathMode = mode; prefsManager.saveImagePathMode(mode) },
                                )
                            }
                            if (imagePathMode == PrefsManager.ImagePathMode.RELATIVE) {
                                SettingsSectionDivider()
                                SettingsSectionTitle("图片存放位置")
                                PrefsManager.RelativeImageLocation.values().forEach { location ->
                                    SettingsChoiceRow(
                                        icon = Icons.Outlined.Folder,
                                        title = if (location == PrefsManager.RelativeImageLocation.CURRENT_NOTE_FOLDER) "当前笔记所在文件夹" else "固定在图片保存位置",
                                        subtitle = if (location == PrefsManager.RelativeImageLocation.CURRENT_NOTE_FOLDER) "与当前笔记同目录" else "使用图片保存位置",
                                        selected = relativeImageLocation == location,
                                        onClick = {
                                            relativeImageLocation = location
                                            prefsManager.saveRelativeImageLocation(location)
                                        },
                                    )
                                }
                            }
                        }
                        "date" -> {
                            SettingsSectionTitle("未命名笔记标题格式")
                            OutlinedTextField(
                                value = dateFormat,
                                onValueChange = {
                                    dateFormat = it
                                    val n = it.trim()
                                    if (KardLeafCustomFeatures.isDateFormatUsable(n)) {
                                        KardLeafCustomFeatures.saveUnnamedNoteDateFormat(context, n)
                                    }
                                },
                                label = { Text("日期格式") },
                                singleLine = true,
                                isError = !isValid,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SettingsPageText(if (isValid) "示例：$sample" else "日期格式无效")
                            SettingsPageText("默认：${KardLeafCustomFeatures.DefaultUnnamedNoteDateFormat}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { settingsDialog = null }) {
                    Text("完成")
                }
            },
        )
    }

    BackHandler {
        when {
            showLabelPicker -> showLabelPicker = false
            showTrashFolderPicker -> showTrashFolderPicker = false
            showResetDialog -> showResetDialog = false
            showCleanupHistoryDialog -> showCleanupHistoryDialog = false
            settingsDialog != null -> settingsDialog = null
            settingsPage == "main" -> onBack()
            else -> returnToSettingsMain()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = settingsPageTitle(settingsPage),
                        label = "SettingsTitleAnimation",
                    ) { title ->
                        Text(title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (settingsPage == "main") onBack() else returnToSettingsMain() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AnimatedContent(
                targetState = settingsPage,
                transitionSpec = {
                    if (targetState == "main") {
                        (slideInHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeIn(animationSpec = tween(220))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(220)) { it } + fadeOut(animationSpec = tween(160)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn(animationSpec = tween(220))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeOut(animationSpec = tween(160)))
                    }
                },
                label = "SettingsPageAnimation",
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(if (page == "main") mainScrollState else detailScrollState),
                ) {
                    when (page) {
                "layout" -> PrefsManager.ViewMode.values().forEach { mode ->
                    SettingsChoiceRow(
                        icon = Icons.Outlined.Description,
                        title = if (mode == PrefsManager.ViewMode.LIST) "列表" else "双列",
                        subtitle = if (mode == PrefsManager.ViewMode.LIST) "单列阅读更清楚" else "双列显示更多",
                        selected = viewMode == mode,
                        onClick = {
                            viewMode = mode
                            prefsManager.saveViewMode(mode)
                            onSettingsChanged()
                        },
                    )
                }
                "sort" -> {
                    SettingsSectionTitle("排序字段")
                    PrefsManager.SortOrder.values().forEach { order ->
                        SettingsChoiceRow(
                            icon = Icons.Outlined.Description,
                            title = if (order == PrefsManager.SortOrder.DATE_MODIFIED) "修改日期" else "标题",
                            subtitle = if (order == PrefsManager.SortOrder.DATE_MODIFIED) "按修改时间排序" else "按标题排序",
                            selected = sortOrder == order,
                            onClick = {
                                sortOrder = order
                                prefsManager.saveSortOrder(order)
                                onSettingsChanged()
                            },
                        )
                    }
                    SettingsSectionDivider()
                    SettingsSectionTitle("排序方向")
                    PrefsManager.SortDirection.values().forEach { direction ->
                        SettingsChoiceRow(
                            icon = Icons.Outlined.Description,
                            title = if (direction == PrefsManager.SortDirection.DESCENDING) "降序" else "升序",
                            subtitle = if (direction == PrefsManager.SortDirection.DESCENDING) "新内容在前" else "旧内容在前",
                            selected = sortDirection == direction,
                            onClick = {
                                sortDirection = direction
                                prefsManager.saveSortDirection(direction)
                                onSettingsChanged()
                            },
                        )
                    }
                }
                "theme" -> {
                    ThemePreviewCard(
                        accentColor = themeColor,
                        backgroundColor = themeBackgroundColor,
                    )
                    SettingsSectionTitle(
                        text = "强调色",
                        subtitle = "影响按钮、高亮和主要操作",
                    )
                    PrefsManager.ThemeColor.values().forEach { color ->
                        SettingsColorChoiceRow(
                            title = themeColorLabel(color),
                            subtitle = themeColorSubtitle(color),
                            swatchColor = themeAccentPreviewColor(color),
                            selected = themeColor == color,
                            onClick = { applyThemeColor(color) },
                        )
                    }
                    SettingsSectionDivider()
                    SettingsSectionTitle(
                        text = "背景色",
                        subtitle = "影响页面、卡片和弹窗底色",
                    )
                    PrefsManager.ThemeBackgroundColor.values().forEach { color ->
                        SettingsColorChoiceRow(
                            title = themeBackgroundColorLabel(color),
                            subtitle = themeBackgroundColorSubtitle(color),
                            swatchColor = themeBackgroundPreviewColor(color),
                            selected = themeBackgroundColor == color,
                            onClick = { applyThemeBackgroundColor(color) },
                        )
                    }
                    SettingsSectionDivider()
                    SettingsActionRow(
                        icon = Icons.Outlined.Restore,
                        title = "恢复默认主题",
                        subtitle = "蓝色强调色 + 白色背景",
                        onClick = { resetTheme() },
                    )
                }
                "image" -> {
                    SettingsSectionTitle("图片保存位置")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = imageFolder,
                            onValueChange = {
                                imageFolder = it
                                prefsManager.saveImageFolderUri(null)
                                prefsManager.saveImageFolder(it)
                            },
                            label = { Text("附件目录") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onSelectImageFolder(::handleImageFolderPicked) }) {
                            Icon(Icons.Outlined.Folder, contentDescription = "选择图片目录")
                        }
                    }
                    SettingsPageText("保存插入图片并生成引用")
                }
                "hiddenFolders" -> {
                    val imageFolderPath = normalizeSettingsFolderPath(prefsManager.getImageFolder())
                    val folderChoices = (labels + hiddenFolders + imageFolderPath)
                        .map(::normalizeSettingsFolderPath)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                    SettingsSectionTitle("隐藏的文件夹", "隐藏后首页不显示该文件夹和子文件夹")
                    if (folderChoices.isEmpty()) {
                        SettingsPageText("当前没有可隐藏的文件夹")
                    } else {
                        folderChoices.forEach { folder ->
                            val checked = folder in hiddenFolders
                            SettingsToggleRow(
                                icon = Icons.Outlined.Folder,
                                title = folder,
                                subtitle = if (folder == imageFolderPath) "图片保存位置默认隐藏" else "隐藏该文件夹及子文件夹",
                                checked = checked,
                                onCheckedChange = { enabled ->
                                    hiddenFolders = if (enabled) hiddenFolders + folder else hiddenFolders - folder
                                    prefsManager.saveHiddenFolderPaths(hiddenFolders)
                                    onSettingsChanged()
                                },
                            )
                        }
                    }
                }
                "density" -> PrefsManager.CardDensity.values().forEach { density ->
                    SettingsChoiceRow(
                        icon = if (density == PrefsManager.CardDensity.LOOSE) Icons.Outlined.ViewStream else Icons.Outlined.ViewCompact,
                        title = if (density == PrefsManager.CardDensity.LOOSE) "宽松" else "紧凑",
                        subtitle = if (density == PrefsManager.CardDensity.LOOSE) "间距更舒展" else "同屏更多笔记",
                        selected = cardDensity == density,
                        onClick = {
                            cardDensity = density
                            prefsManager.saveCardDensity(density)
                            onSettingsChanged()
                        },
                    )
                }
                "date" -> {
                    SettingsSectionTitle("未命名笔记标题格式")
                    OutlinedTextField(
                        value = dateFormat,
                        onValueChange = {
                            dateFormat = it
                            val n = it.trim()
                            if (KardLeafCustomFeatures.isDateFormatUsable(n)) {
                                KardLeafCustomFeatures.saveUnnamedNoteDateFormat(context, n)
                            }
                        },
                        label = { Text("日期格式") },
                        singleLine = true,
                        isError = !isValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsPageText(if (isValid) "示例：$sample" else "日期格式无效")
                    SettingsPageText("默认：${KardLeafCustomFeatures.DefaultUnnamedNoteDateFormat}")
                }
                "openNote" -> KardLeafCustomFeatures.OpenNoteMode.values().forEach { mode ->
                    SettingsChoiceRow(
                        icon = Icons.Outlined.Edit,
                        title = if (mode == KardLeafCustomFeatures.OpenNoteMode.PREVIEW) "查看模式" else "编辑模式",
                        subtitle = if (mode == KardLeafCustomFeatures.OpenNoteMode.PREVIEW) "先显示预览" else "直接进入编辑",
                        selected = openNoteMode == mode,
                        onClick = {
                            openNoteMode = mode
                            KardLeafCustomFeatures.saveOpenNoteMode(context, mode)
                        },
                    )
                }
                "backup" -> {
                    SettingsActionRow(
                        icon = Icons.Outlined.Description,
                        title = "导出数据备份",
                        subtitle = "导出用户数据 JSON",
                        onClick = onExportUserData,
                    )
                    SettingsActionRow(
                        icon = Icons.Outlined.Description,
                        title = "导入数据备份",
                        subtitle = "从 JSON 恢复数据",
                        onClick = onImportUserData,
                    )
                }
                "drawer" -> {
                    SettingsSectionTitle("侧边栏手势区域")
                    OutlinedTextField(
                        value = drawerEdgeWidthText,
                        onValueChange = {
                            drawerEdgeWidthText = it.filter(Char::isDigit).take(3)
                            val w = drawerEdgeWidthText.trim().toIntOrNull()
                            if (w != null && w in 24..160) {
                                prefsManager.saveDrawerEdgeWidthDp(w)
                                onSettingsChanged()
                            }
                        },
                        label = { Text("侧边栏划出距离 dp") },
                        singleLine = true,
                        isError = !isDrawerEdgeWidthValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsPageText("范围：24-160dp，越大越易划出")
                }
                "historyLimit" -> {
                    SettingsSectionTitle("历史版本数量")
                    OutlinedTextField(
                        value = historyLimitText,
                        onValueChange = { value ->
                            historyLimitText = value.filter(Char::isDigit).take(3)
                            val limit = historyLimitText.trim().toIntOrNull()
                            if (limit != null &&
                                limit in PrefsManager.MIN_HISTORY_VERSION_LIMIT..PrefsManager.MAX_HISTORY_VERSION_LIMIT
                            ) {
                                prefsManager.saveHistoryVersionLimit(limit)
                            }
                        },
                        label = { Text("每篇笔记保留数量") },
                        singleLine = true,
                        isError = !isHistoryLimitValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsPageText(
                        "范围：${PrefsManager.MIN_HISTORY_VERSION_LIMIT}-${PrefsManager.MAX_HISTORY_VERSION_LIMIT}，按数量保留历史",
                    )
                }
                "trash" -> {
                    SettingsSectionTitle("回收站文件夹")
                    OutlinedTextField(
                        value = trashFolderName,
                        onValueChange = {
                            trashFolderName = it
                            val n = it.trim()
                            if (n.isNotBlank() && !n.contains(Regex("[\\\\/:*?\"<>|]"))) {
                                prefsManager.saveTrashFolderName(n)
                            }
                        },
                        placeholder = { Text("例如：${PrefsManager.DEFAULT_TRASH_FOLDER_NAME}") },
                        trailingIcon = {
                            IconButton(onClick = { showTrashFolderPicker = true }) {
                                Icon(Icons.Outlined.Folder, contentDescription = "选择文件夹")
                            }
                        },
                        singleLine = true,
                        isError = !isTrashFolderValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsSectionDivider()
                    SettingsSectionTitle("回收站排序")
                    PrefsManager.TrashSortOrder.values().forEach { order ->
                        SettingsChoiceRow(
                            icon = Icons.Outlined.Delete,
                            title = if (order == PrefsManager.TrashSortOrder.FILE_NAME) "按文件名" else "按删除时间",
                            subtitle = if (order == PrefsManager.TrashSortOrder.FILE_NAME) "按文件名排序" else "最近删除优先",
                            selected = trashSortOrder == order,
                            onClick = {
                                trashSortOrder = order
                                prefsManager.saveTrashSortOrder(order)
                                onSettingsChanged()
                            },
                        )
                    }
                    SettingsSectionDivider()
                    SettingsSectionTitle("自动清理")
                    SettingsActionRow(
                        icon = Icons.Outlined.DeleteSweep,
                        title = "自动清理时间",
                        subtitle = if (trashAutoCleanDays == 0) "关闭" else "删除超过 $trashAutoCleanDays 天的废弃笔记",
                        onClick = { settingsDialog = "trashAutoClean" },
                    )
                }
                "toolbar" -> {
                    SettingsPageText("长按方块拖动排序")
                    SettingsToolbarGrid(
                        items = toolbarOrder,
                        onOrderChange = { newOrder ->
                            toolbarOrder = newOrder
                            KardLeafCustomFeatures.saveToolbarOrder(context, toolbarOrder)
                        },
                    )
                }
                "drawerEdit" -> {
                    var drawerOrder by remember { mutableStateOf(prefsManager.getDrawerItemOrder()) }
                    var hiddenItems by remember { mutableStateOf(prefsManager.getHiddenDrawerItems()) }
                    var renameTarget by remember { mutableStateOf<PrefsManager.DrawerItemId?>(null) }
                    var renameText by remember { mutableStateOf("") }

                    fun saveDrawerState(
                        newOrder: List<PrefsManager.DrawerItemId> = drawerOrder,
                        newHiddenItems: Set<PrefsManager.DrawerItemId> = hiddenItems,
                    ) {
                        drawerOrder = newOrder
                        hiddenItems = newHiddenItems
                        prefsManager.saveDrawerItemOrder(newOrder)
                        prefsManager.saveHiddenDrawerItems(newHiddenItems)
                        onSettingsChanged()
                    }

                    fun saveDrawerSections(
                        visibleItems: List<PrefsManager.DrawerItemId>,
                        hiddenDrawerItems: List<PrefsManager.DrawerItemId>,
                    ) {
                        saveDrawerState(visibleItems + hiddenDrawerItems, hiddenItems)
                    }

                    if (renameTarget != null) {
                        AlertDialog(
                            onDismissRequest = { renameTarget = null },
                            title = { Text("重命名侧边栏功能项") },
                            text = {
                                OutlinedTextField(
                                    value = renameText,
                                    onValueChange = { renameText = it },
                                    label = { Text("名称") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val target = renameTarget
                                    if (target != null) {
                                        prefsManager.saveDrawerItemLabel(target, renameText)
                                        onSettingsChanged()
                                    }
                                    renameTarget = null
                                }) { Text("保存") }
                            },
                            dismissButton = {
                                TextButton(onClick = { renameTarget = null }) { Text("取消") }
                            },
                        )
                    }

                    val visibleItems = drawerOrder.filter { it !in hiddenItems }
                    val hiddenDrawerItems = drawerOrder.filter { it in hiddenItems }

                    SettingsSectionTitle("显示")
                    if (visibleItems.isEmpty()) {
                        SettingsPageText("暂无显示项")
                    } else {
                        SettingsDrawerDragList(
                            items = visibleItems,
                            hiddenItems = hiddenItems,
                            prefsManager = prefsManager,
                            onOrderChange = { newVisibleItems ->
                                saveDrawerSections(newVisibleItems, hiddenDrawerItems)
                            },
                            onRename = { itemId, title ->
                                renameTarget = itemId
                                renameText = title
                            },
                            onToggleVisible = { itemId ->
                                if (itemId != PrefsManager.DrawerItemId.SETTINGS) {
                                    val newHiddenItems = hiddenItems + itemId
                                    val newVisibleItems = drawerOrder.filter { it !in newHiddenItems }
                                    val newHiddenDrawerItems = drawerOrder.filter { it in newHiddenItems }
                                    saveDrawerState(newVisibleItems + newHiddenDrawerItems, newHiddenItems)
                                }
                            },
                        )
                    }

                    SettingsSectionDivider()
                    SettingsSectionTitle("隐藏")
                    if (hiddenDrawerItems.isEmpty()) {
                        SettingsPageText("暂无隐藏项")
                    } else {
                        SettingsDrawerDragList(
                            items = hiddenDrawerItems,
                            hiddenItems = hiddenItems,
                            prefsManager = prefsManager,
                            onOrderChange = { newHiddenDrawerItems ->
                                saveDrawerSections(visibleItems, newHiddenDrawerItems)
                            },
                            onRename = { itemId, title ->
                                renameTarget = itemId
                                renameText = title
                            },
                            onToggleVisible = { itemId ->
                                val newHiddenItems = hiddenItems - itemId
                                val newVisibleItems = drawerOrder.filter { it !in newHiddenItems }
                                val newHiddenDrawerItems = drawerOrder.filter { it in newHiddenItems }
                                saveDrawerState(newVisibleItems + newHiddenDrawerItems, newHiddenItems)
                            },
                        )
                    }
                }
                "imagePath" -> {
                    var imagePathMode by remember { mutableStateOf(prefsManager.getImagePathMode()) }
                    SettingsSectionTitle("图片引用路径")
                    PrefsManager.ImagePathMode.values().forEach { mode ->
                        SettingsChoiceRow(
                            icon = Icons.Outlined.Image,
                            title = if (mode == PrefsManager.ImagePathMode.ROOT) "基于仓库根路径" else "基于当前笔记相对路径",
                            subtitle = if (mode == PrefsManager.ImagePathMode.ROOT) "附件目录固定引用" else "按笔记位置生成引用",
                            selected = imagePathMode == mode,
                            onClick = { imagePathMode = mode; prefsManager.saveImagePathMode(mode) },
                        )
                    }
                    if (imagePathMode == PrefsManager.ImagePathMode.RELATIVE) {
                        SettingsSectionDivider()
                        SettingsSectionTitle("图片存放位置")
                        PrefsManager.RelativeImageLocation.values().forEach { location ->
                            SettingsChoiceRow(
                                icon = Icons.Outlined.Folder,
                                title = if (location == PrefsManager.RelativeImageLocation.CURRENT_NOTE_FOLDER) "当前笔记所在文件夹" else "固定在图片保存位置",
                                subtitle = if (location == PrefsManager.RelativeImageLocation.CURRENT_NOTE_FOLDER) "与当前笔记同目录" else "使用图片保存位置",
                                selected = relativeImageLocation == location,
                                onClick = {
                                    relativeImageLocation = location
                                    prefsManager.saveRelativeImageLocation(location)
                                },
                            )
                        }
                    }
                }
                "security" -> {
                    var appPwd by remember { mutableStateOf("") }
                    var appPwdConfirm by remember { mutableStateOf("") }
                    var appPwdError by remember { mutableStateOf<String?>(null) }
                    var privacyPwd by remember { mutableStateOf("") }
                    var privacyPwdConfirm by remember { mutableStateOf("") }
                    var privacyPwdError by remember { mutableStateOf<String?>(null) }
                    var safetyWord by remember { mutableStateOf("") }
                    var safetyWordConfirm by remember { mutableStateOf("") }
                    var safetyWordError by remember { mutableStateOf<String?>(null) }
                    var hasPwd by remember { mutableStateOf(prefsManager.getAppPasswordHash() != null) }
                    var hasPrivacyPwd by remember { mutableStateOf(prefsManager.getPrivacyPasswordHash() != null) }
                    var hasSafetyWord by remember { mutableStateOf(prefsManager.getSafetyWordHash() != null) }
                    var appBiometricEnabled by remember { mutableStateOf(prefsManager.isAppBiometricUnlockEnabled()) }
                    var privacyBiometricEnabled by remember { mutableStateOf(prefsManager.isPrivacyBiometricUnlockEnabled()) }
                    var clearPasswordTarget by remember { mutableStateOf<String?>(null) }
                    var clearPasswordInput by remember { mutableStateOf("") }
                    var clearPasswordError by remember { mutableStateOf<String?>(null) }
                    val passwordKeyboardOptions = KeyboardOptions(
                        keyboardType = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) {
                            KeyboardType.NumberPassword
                        } else {
                            KeyboardType.Password
                        },
                    )
                    val passwordKeyboardController = LocalSoftwareKeyboardController.current
                    val appPwdFocusRequester = remember { FocusRequester() }
                    val clearPasswordFocusRequester = remember { FocusRequester() }

                    LaunchedEffect(settingsPage, passwordInputMode) {
                        if (settingsPage == "security" && passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) {
                            runCatching { appPwdFocusRequester.requestFocus() }
                            passwordKeyboardController?.show()
                        }
                    }

                    LaunchedEffect(clearPasswordTarget, passwordInputMode) {
                        if (clearPasswordTarget != null && passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) {
                            runCatching { clearPasswordFocusRequester.requestFocus() }
                            passwordKeyboardController?.show()
                        }
                    }

                    if (clearPasswordTarget != null) {
                        val isAppTarget = clearPasswordTarget == "app"
                        AlertDialog(
                            onDismissRequest = {
                                clearPasswordTarget = null
                                clearPasswordInput = ""
                                clearPasswordError = null
                            },
                            title = { Text(if (isAppTarget) "清除应用密码" else "清除隐私密码") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "请输入当前密码，验证通过后才会清除",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    OutlinedTextField(
                                        value = clearPasswordInput,
                                        onValueChange = { value ->
                                            clearPasswordInput = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) value.filter(Char::isDigit).take(4) else value
                                            clearPasswordError = null
                                        },
                                        label = { Text("当前密码") },
                                        singleLine = true,
                                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                        keyboardOptions = passwordKeyboardOptions,
                                        isError = clearPasswordError != null,
                                        modifier = Modifier.fillMaxWidth().focusRequester(clearPasswordFocusRequester),
                                    )
                                    if (clearPasswordError != null) {
                                        Text(
                                            text = clearPasswordError.orEmpty(),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val savedHash = if (isAppTarget) prefsManager.getAppPasswordHash() else prefsManager.getPrivacyPasswordHash()
                                    val safetyWordHash = prefsManager.getSafetyWordHash()
                                    val inputHash = hashPassword(clearPasswordInput)
                                    when {
                                        clearPasswordInput.isBlank() -> clearPasswordError = "请输入当前密码或安全词"
                                        inputHash != savedHash && inputHash != safetyWordHash -> {
                                            clearPasswordError = "密码或安全词错误"
                                            if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) {
                                                clearPasswordInput = ""
                                            }
                                        }
                                        isAppTarget -> {
                                            prefsManager.saveAppPasswordHash(null)
                                            prefsManager.saveAppBiometricUnlockEnabled(false)
                                            hasPwd = false
                                            appBiometricEnabled = false
                                            appPwd = ""
                                            appPwdConfirm = ""
                                            appPwdError = null
                                            clearPasswordTarget = null
                                            clearPasswordInput = ""
                                            clearPasswordError = null
                                        }
                                        else -> {
                                            prefsManager.savePrivacyPasswordHash(null)
                                            prefsManager.savePrivacyBiometricUnlockEnabled(false)
                                            hasPrivacyPwd = false
                                            privacyBiometricEnabled = false
                                            privacyPwd = ""
                                            privacyPwdConfirm = ""
                                            privacyPwdError = null
                                            clearPasswordTarget = null
                                            clearPasswordInput = ""
                                            clearPasswordError = null
                                        }
                                    }
                                }) { Text("确认清除") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    clearPasswordTarget = null
                                    clearPasswordInput = ""
                                    clearPasswordError = null
                                }) { Text("取消") }
                            },
                        )
                    }

                    SettingsSectionTitle(
                        "密码输入方式",
                        if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) "数字密码仅支持 4 位" else "复杂密码使用系统键盘",
                    )
                    SettingsActionRow(
                        icon = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) Icons.Outlined.Lock else Icons.Outlined.Lock,
                        title = "密码类型",
                        subtitle = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) "简单密码：数字键盘" else "复杂密码：系统键盘",
                        onClick = { settingsDialog = "passwordMode" },
                    )

                    SettingsSectionDivider()
                    SettingsSectionTitle("安全词", if (hasSafetyWord) "已设置安全词" else "未设置安全词")
                    SettingsPageText("忘记密码时，可用安全词作为备用验证")
                    OutlinedTextField(
                        value = safetyWord,
                        onValueChange = { value ->
                            safetyWord = value
                            safetyWordError = null
                        },
                        label = { Text(if (hasSafetyWord) "输入新安全词" else "设置安全词") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = safetyWordConfirm,
                        onValueChange = { value ->
                            safetyWordConfirm = value
                            safetyWordError = null
                        },
                        label = { Text("再次输入安全词") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (safetyWordError != null) {
                        Text(safetyWordError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            enabled = safetyWord.isNotBlank() || safetyWordConfirm.isNotBlank(),
                            onClick = {
                                when {
                                    safetyWord.isBlank() || safetyWordConfirm.isBlank() -> safetyWordError = "请完整输入两次安全词"
                                    safetyWord != safetyWordConfirm -> safetyWordError = "两次输入的安全词不一致"
                                    else -> {
                                        prefsManager.saveSafetyWordHash(hashPassword(safetyWord))
                                        hasSafetyWord = true
                                        safetyWord = ""
                                        safetyWordConfirm = ""
                                        safetyWordError = null
                                    }
                                }
                            },
                        ) { Text(if (hasSafetyWord) "修改安全词" else "保存安全词") }
                        TextButton(
                            enabled = hasSafetyWord,
                            onClick = {
                                prefsManager.saveSafetyWordHash(null)
                                hasSafetyWord = false
                                safetyWord = ""
                                safetyWordConfirm = ""
                                safetyWordError = null
                            },
                        ) { Text("清除") }
                    }

                    SettingsSectionDivider()
                    SettingsSectionTitle("应用锁", if (hasPwd) "已设置应用密码" else "未设置应用密码")
                    OutlinedTextField(
                        value = appPwd,
                        onValueChange = { value ->
                            appPwd = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) value.filter(Char::isDigit).take(4) else value
                            appPwdError = null
                        },
                        label = {
                            Text(
                                when {
                                    hasPwd -> "输入新应用密码"
                                    passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE -> "设置 4 位应用数字密码"
                                    else -> "设置应用密码"
                                },
                            )
                        },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = passwordKeyboardOptions,
                        modifier = Modifier.fillMaxWidth().focusRequester(appPwdFocusRequester),
                    )
                    OutlinedTextField(
                        value = appPwdConfirm,
                        onValueChange = { value ->
                            appPwdConfirm = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) value.filter(Char::isDigit).take(4) else value
                            appPwdError = null
                        },
                        label = { Text("再次输入应用密码") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = passwordKeyboardOptions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (appPwdError != null) {
                        Text(appPwdError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            enabled = appPwd.isNotBlank() || appPwdConfirm.isNotBlank(),
                            onClick = {
                                val minSimpleOk = passwordInputMode != PrefsManager.PasswordInputMode.SIMPLE || appPwd.length == 4
                                when {
                                    appPwd.isBlank() || appPwdConfirm.isBlank() -> appPwdError = "请完整输入两次应用密码"
                                    !minSimpleOk -> appPwdError = "简单密码必须是 4 位数字"
                                    appPwd != appPwdConfirm -> appPwdError = "两次输入的应用密码不一致"
                                    else -> {
                                        prefsManager.saveAppPasswordHash(hashPassword(appPwd))
                                        hasPwd = true
                                        appPwd = ""
                                        appPwdConfirm = ""
                                        appPwdError = null
                                    }
                                }
                            },
                        ) { Text(if (hasPwd) "修改应用密码" else "保存应用密码") }
                        TextButton(
                            enabled = hasPwd,
                            onClick = {
                                clearPasswordTarget = "app"
                                clearPasswordInput = ""
                                clearPasswordError = null
                            },
                        ) { Text("清除") }
                    }
                    SettingsToggleRow(
                        icon = Icons.Outlined.Lock,
                        title = "应用指纹解锁",
                        subtitle = if (hasPwd) "自动弹出指纹验证" else "需要先设置应用密码",
                        checked = hasPwd && appBiometricEnabled,
                        onCheckedChange = { enabled ->
                            appBiometricEnabled = hasPwd && enabled
                            prefsManager.saveAppBiometricUnlockEnabled(appBiometricEnabled)
                        },
                    )

                    SettingsSectionDivider()
                    SettingsSectionTitle("隐私空间", if (hasPrivacyPwd) "已设置隐私密码" else "未设置隐私密码")
                    OutlinedTextField(
                        value = privacyPwd,
                        onValueChange = { value ->
                            privacyPwd = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) value.filter(Char::isDigit).take(4) else value
                            privacyPwdError = null
                        },
                        label = {
                            Text(
                                when {
                                    hasPrivacyPwd -> "输入新隐私密码"
                                    passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE -> "设置 4 位隐私数字密码"
                                    else -> "设置隐私密码"
                                },
                            )
                        },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = passwordKeyboardOptions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = privacyPwdConfirm,
                        onValueChange = { value ->
                            privacyPwdConfirm = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) value.filter(Char::isDigit).take(4) else value
                            privacyPwdError = null
                        },
                        label = { Text("再次输入隐私密码") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = passwordKeyboardOptions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (privacyPwdError != null) {
                        Text(privacyPwdError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            enabled = privacyPwd.isNotBlank() || privacyPwdConfirm.isNotBlank(),
                            onClick = {
                                val minSimpleOk = passwordInputMode != PrefsManager.PasswordInputMode.SIMPLE || privacyPwd.length == 4
                                when {
                                    privacyPwd.isBlank() || privacyPwdConfirm.isBlank() -> privacyPwdError = "请完整输入两次隐私密码"
                                    !minSimpleOk -> privacyPwdError = "简单密码必须是 4 位数字"
                                    privacyPwd != privacyPwdConfirm -> privacyPwdError = "两次输入的隐私密码不一致"
                                    else -> {
                                        prefsManager.savePrivacyPasswordHash(hashPassword(privacyPwd))
                                        hasPrivacyPwd = true
                                        privacyPwd = ""
                                        privacyPwdConfirm = ""
                                        privacyPwdError = null
                                    }
                                }
                            },
                        ) { Text(if (hasPrivacyPwd) "修改隐私密码" else "保存隐私密码") }
                        TextButton(
                            enabled = hasPrivacyPwd,
                            onClick = {
                                clearPasswordTarget = "privacy"
                                clearPasswordInput = ""
                                clearPasswordError = null
                            },
                        ) { Text("清除") }
                    }
                    SettingsToggleRow(
                        icon = Icons.Outlined.Lock,
                        title = "隐私指纹解锁",
                        subtitle = if (hasPrivacyPwd) "自动弹出指纹验证" else "需要先设置隐私密码",
                        checked = hasPrivacyPwd && privacyBiometricEnabled,
                        onCheckedChange = { enabled ->
                            privacyBiometricEnabled = hasPrivacyPwd && enabled
                            prefsManager.savePrivacyBiometricUnlockEnabled(privacyBiometricEnabled)
                        },
                    )
                }
                "autoBackup" -> {
                    var intervalDays by remember { mutableStateOf(prefsManager.getAutoBackupIntervalDays()) }
                    val currentDir = prefsManager.getAutoBackupDirUri()
                    SettingsActionRow(
                        icon = Icons.Outlined.Folder,
                        title = "备份目录",
                        subtitle = if (currentDir != null) "已选择目录（点击重新选择）" else "未选择目录",
                        onClick = { onSelectBackupDir(::handleBackupDirPicked) },
                    )
                    SettingsPageText("超过周期后启动时自动备份")
                    listOf(0, 1, 3, 7, 30).forEach { days ->
                        SettingsChoiceRow(
                            icon = Icons.Outlined.Schedule,
                            title = if (days == 0) "关闭" else "每 $days 天",
                            subtitle = if (days == 0) "不自动备份" else "到期启动时备份",
                            selected = intervalDays == days,
                            onClick = { intervalDays = days; prefsManager.saveAutoBackupIntervalDays(days) },
                        )
                    }
                }
                "remarkRecords" -> {
                    NoteRecordSummarySettingsPage(
                        title = "有备注的笔记",
                        emptyText = "当前没有带备注的笔记",
                        summaries = remarkNoteSummaries,
                        isLoading = isLoadingRecordSummaries,
                        countLabel = "备注",
                    )
                }
                "historyRecords" -> {
                    NoteRecordSummarySettingsPage(
                        title = "有历史版本的笔记",
                        emptyText = "当前没有历史版本记录",
                        summaries = historyNoteSummaries,
                        isLoading = isLoadingRecordSummaries,
                        countLabel = "版本",
                    )
                }
                "about" -> {
                    val versionName = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")
                    }
                    SettingsSectionTitle("卡叶笔记", "卡片式 Markdown 笔记软件")
                    SettingsActionRow(
                        icon = Icons.Outlined.Info,
                        title = "KardLeaf",
                        subtitle = "中文名：卡叶笔记",
                        onClick = {},
                    )
                    SettingsActionRow(
                        icon = Icons.Outlined.Info,
                        title = "版本",
                        subtitle = versionName.ifBlank { "4.0" },
                        onClick = {},
                    )
                    SettingsActionRow(
                        icon = Icons.Outlined.Person,
                        title = "作者",
                        subtitle = "kangle",
                        onClick = {},
                    )
                    SettingsSectionDivider()
                    SettingsSectionTitle("说明")
                    SettingsPageText("本地笔记、历史版本和隐私空间")
                }
                "interface" -> {
                    SettingsSectionTitle("首页显示")
                    SettingsActionRow(Icons.Outlined.ViewAgenda, "布局模式", if (viewMode == PrefsManager.ViewMode.LIST) "列表" else "双列", { settingsDialog = "layout" })
                    SettingsActionRow(Icons.Outlined.ViewStream, "卡片密度", if (cardDensity == PrefsManager.CardDensity.LOOSE) "宽松" else "紧凑", { settingsDialog = "density" })
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Label,
                        title = "宽松卡片显示标签",
                        subtitle = if (showYamlTagsOnLooseCards) "显示 YAML tags" else "不显示 YAML tags",
                        checked = showYamlTagsOnLooseCards,
                        onCheckedChange = { enabled ->
                            showYamlTagsOnLooseCards = enabled
                            prefsManager.saveLooseCardYamlTagsVisible(enabled)
                            onSettingsChanged()
                        },
                    )
                    SettingsActionRow(Icons.Outlined.Sort, "排序方式", sortSummary(sortOrder, sortDirection), { settingsDialog = "sort" })
                    SettingsSectionDivider()
                    SettingsSectionTitle("启动位置")
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Restore,
                        title = "恢复上次分类标签",
                        subtitle = if (restoreLastFilter) "回到上次分类" else "使用默认分类",
                        checked = restoreLastFilter,
                        onCheckedChange = { enabled ->
                            restoreLastFilter = enabled
                            prefsManager.saveRestoreLastFilterEnabled(enabled)
                        },
                    )
                    if (!restoreLastFilter) {
                        SettingsActionRow(
                            icon = Icons.Outlined.Folder,
                            title = "默认启动分类",
                            subtitle = if (defaultStartLabel.isNotBlank()) defaultStartLabel else "全部笔记",
                            onClick = { showLabelPicker = true },
                        )
                    }
                }
                else -> {
                    SettingsSectionTitle("常规")
                    SettingsActionRow(Icons.Outlined.Folder, "笔记库", displayRootPath(prefsManager.getRootUri()), onSelectDatabase)
                    SettingsActionRow(Icons.Outlined.Palette, "主题设置", themeSummary(themeColor, themeBackgroundColor), { openSettingsPage("theme") })
                    SettingsActionRow(Icons.Outlined.Tune, "应用界面", "布局、排序和启动分类", { openSettingsPage("interface") })
                    SettingsSectionDivider()
                    SettingsSectionTitle("编辑器")
                    SettingsActionRow(Icons.Outlined.FormatListBulleted, "字符按钮位置", "调整工具按钮顺序", { openSettingsPage("toolbar") })
                    SettingsActionRow(Icons.Outlined.Visibility, "默认打开模式", if (openNoteMode == KardLeafCustomFeatures.OpenNoteMode.PREVIEW) "查看模式" else "编辑模式", { settingsDialog = "openNote" })
                    SettingsActionRow(
                        Icons.Outlined.TouchApp,
                        "双击进入编辑间隔",
                        "当前 ${prefsManager.getPreviewDoubleTapIntervalMs()}ms",
                        { settingsDialog = "doubleTap" },
                    )
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Info,
                        title = "笔记详情侧滑面板",
                        subtitle = if (noteSidePanelsEnabled) "左右滑动打开侧栏" else "关闭左右侧滑",
                        checked = noteSidePanelsEnabled,
                        onCheckedChange = { enabled ->
                            noteSidePanelsEnabled = enabled
                            prefsManager.saveNoteSidePanelsEnabled(enabled)
                        },
                    )
                    SettingsSectionDivider()
                    SettingsSectionTitle("附件与文件")
                    SettingsActionRow(Icons.Outlined.Folder, "图片保存位置", imageFolder, { openSettingsPage("image") })
                    SettingsActionRow(Icons.Outlined.Image, "图片路径格式", "根路径或相对路径", { settingsDialog = "imagePath" })
                    SettingsActionRow(
                        Icons.Outlined.VisibilityOff,
                        "隐藏的文件夹",
                        if (hiddenFolders.isEmpty()) "未隐藏文件夹" else hiddenFolders.sorted().joinToString("、").take(32),
                        { openSettingsPage("hiddenFolders") },
                    )
                    SettingsActionRow(Icons.Outlined.TextFields, "日期格式", dateFormat, { settingsDialog = "date" })
                    SettingsSectionDivider()
                    SettingsSectionTitle("数据与安全")
                    SettingsActionRow(Icons.Outlined.Backup, "数据备份", "导入或导出用户数据 JSON", { settingsDialog = "backup" })
                    SettingsActionRow(Icons.Outlined.Schedule, "自动备份", "定时备份到指定目录", { openSettingsPage("autoBackup") })
                    SettingsActionRow(Icons.Outlined.Description, "备注记录", "查看所有有备注的笔记", { openSettingsPage("remarkRecords") })
                    SettingsActionRow(Icons.Outlined.History, "历史版本记录", "查看所有有历史版本的笔记", { openSettingsPage("historyRecords") })
                    SettingsActionRow(Icons.Outlined.History, "历史版本数量", "保留最新 $savedHistoryLimit 个版本", { settingsDialog = "historyLimit" })
                    SettingsActionRow(Icons.Outlined.DeleteSweep, "清理旧历史版本", "清理旧版本前预览", { openCleanupHistoryDialog() })
                    SettingsActionRow(Icons.Outlined.Delete, "回收站", "目录、排序、自动清理", { openSettingsPage("trash") })
                    SettingsActionRow(Icons.Outlined.Lock, "安全", "应用锁、隐私和指纹", { openSettingsPage("security") })
                    SettingsSectionDivider()
                    SettingsSectionTitle("侧边栏")
                    SettingsActionRow(Icons.Outlined.Reorder, "侧边栏调整", "长按拖动，显示或隐藏", { openSettingsPage("drawerEdit") })
                    SettingsActionRow(Icons.Outlined.TouchApp, "侧边栏距离", "设置左侧划出距离", { settingsDialog = "drawer" })
                    SettingsSectionDivider()
                    SettingsSectionTitle("其他")
                    SettingsActionRow(Icons.Outlined.Restore, "恢复默认设置", "恢复所有默认设置", { showResetDialog = true })
                    SettingsActionRow(Icons.Outlined.Info, "关于", "版本信息和作者", { openSettingsPage("about") })
                }
                    }
                }
            }
        }
    }
}

private fun settingsPageTitle(page: String): String =
    when (page) {
        "layout" -> "布局模式"
        "sort" -> "排序方式"
        "theme" -> "主题设置"
        "image" -> "图片保存位置"
        "hiddenFolders" -> "隐藏的文件夹"
        "density" -> "卡片密度"
        "date" -> "日期格式"
        "openNote" -> "默认编辑器模式"
        "backup" -> "数据备份"
        "drawer" -> "侧边栏距离"
        "historyLimit" -> "历史版本数量"
        "trash" -> "回收站"
        "toolbar" -> "字符按钮位置"
        "drawerEdit" -> "侧边栏调整"
        "interface" -> "应用界面"
        "imagePath" -> "图片路径格式"
        "security" -> "安全"
        "passwordMode" -> "密码类型"
        "doubleTap" -> "双击间隔"
        "trashAutoClean" -> "自动清理回收站"
        "autoBackup" -> "自动备份"
        "remarkRecords" -> "备注记录"
        "historyRecords" -> "历史版本记录"
        "about" -> "关于"
        else -> "设置"
    }

private fun sortSummary(
    order: PrefsManager.SortOrder,
    direction: PrefsManager.SortDirection,
): String {
    val orderText = if (order == PrefsManager.SortOrder.DATE_MODIFIED) "修改日期" else "标题"
    val directionText = if (direction == PrefsManager.SortDirection.DESCENDING) "降序" else "升序"
    return "$orderText（$directionText）"
}

private fun toolbarItemIcon(item: KardLeafCustomFeatures.ToolbarItem): ImageVector =
    when (item) {
        KardLeafCustomFeatures.ToolbarItem.PREVIEW -> Icons.Outlined.Visibility
        KardLeafCustomFeatures.ToolbarItem.UNDO -> Icons.Outlined.Undo
        KardLeafCustomFeatures.ToolbarItem.REDO -> Icons.Outlined.Redo
        KardLeafCustomFeatures.ToolbarItem.IMAGE -> Icons.Outlined.Image
        KardLeafCustomFeatures.ToolbarItem.HEADING -> Icons.Outlined.Title
        KardLeafCustomFeatures.ToolbarItem.RULE -> Icons.Outlined.HorizontalRule
        KardLeafCustomFeatures.ToolbarItem.BOLD -> Icons.Outlined.FormatBold
        KardLeafCustomFeatures.ToolbarItem.ITALIC -> Icons.Outlined.FormatItalic
        KardLeafCustomFeatures.ToolbarItem.UNDERLINE -> Icons.Outlined.FormatUnderlined
        KardLeafCustomFeatures.ToolbarItem.STRIKE -> Icons.Outlined.StrikethroughS
        KardLeafCustomFeatures.ToolbarItem.LINK -> Icons.Outlined.Link
        KardLeafCustomFeatures.ToolbarItem.CODE -> Icons.Outlined.Code
        KardLeafCustomFeatures.ToolbarItem.QUOTE -> Icons.Outlined.FormatQuote
        KardLeafCustomFeatures.ToolbarItem.MATH -> Icons.Outlined.Functions
        KardLeafCustomFeatures.ToolbarItem.BULLET -> Icons.Outlined.FormatListBulleted
        KardLeafCustomFeatures.ToolbarItem.NUMBERED -> Icons.Outlined.FormatListNumbered
        KardLeafCustomFeatures.ToolbarItem.CHECKBOX -> Icons.Outlined.CheckBox
    }

@Composable
private fun ThemePreviewCard(
    accentColor: PrefsManager.ThemeColor,
    backgroundColor: PrefsManager.ThemeBackgroundColor,
) {
    val accent = themeAccentPreviewColor(accentColor)
    val background = themeBackgroundPreviewColor(backgroundColor)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "主题预览",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF0F172A),
                )
                Text(
                    text = "${themeColorLabel(accentColor)} · ${themeBackgroundColorLabel(backgroundColor)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(accent),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreviewChip(text = "按钮", color = accent, selected = true)
            ThemePreviewChip(text = "标签", color = accent.copy(alpha = 0.12f), selected = false)
            ThemePreviewChip(text = "卡片", color = Color.White, selected = false)
        }
    }
}

@Composable
private fun ThemePreviewChip(
    text: String,
    color: Color,
    selected: Boolean,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Color.White else Color(0xFF334155),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .border(
                width = 1.dp,
                color = if (selected) color else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SettingsSectionTitle(
    text: String,
    subtitle: String? = null,
) {
    Column(
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSectionDivider() {
    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingsPageText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 0.dp, end = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun NoteRecordSummarySettingsPage(
    title: String,
    emptyText: String,
    summaries: List<NoteRecordSummary>,
    isLoading: Boolean,
    countLabel: String,
) {
    val formatter = androidx.compose.runtime.remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }
    SettingsSectionTitle(title, "只展示记录索引，不会修改笔记内容")
    when {
        isLoading -> SettingsPageText("正在读取记录...")
        summaries.isEmpty() -> SettingsPageText(emptyText)
        else -> {
            SettingsPageText("共 ${summaries.size} 篇")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summaries.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.title.ifBlank { item.noteId },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = item.noteId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${item.recordCount} 条$countLabel · ${formatter.format(Date(item.updatedAtMs))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCleanupPreviewContent(
    keep: Int,
    preview: List<HistoryCleanupPreview>,
    isLoading: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "每篇笔记将只保留最新 $keep 个历史版本，更早的历史版本会被删除，此操作不可撤销",
            style = MaterialTheme.typography.bodyMedium,
        )
        when {
            isLoading -> {
                Text(
                    text = "正在读取历史版本...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.isEmpty() -> {
                Text(
                    text = "当前没有需要清理的历史版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Text(
                    text = "将清理以下文件的旧历史版本：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    preview.take(50).forEach { item ->
                        Text(
                            text = "${item.noteId}：共 ${item.versionCount} 个，将删除 ${item.deleteCount} 个",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (preview.size > 50) {
                        Text(
                            text = "还有 ${preview.size - 50} 个文件未显示",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
private fun SettingsChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        selected = selected,
        onClick = onClick,
        trailing = { RadioButton(selected = selected, onClick = onClick) },
    )
}

@Composable
private fun SettingsColorChoiceRow(
    title: String,
    subtitle: String,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SettingsBaseRow(
        icon = Icons.Outlined.Palette,
        title = title,
        subtitle = subtitle,
        selected = selected,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(swatchColor)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp)),
                )
                RadioButton(selected = selected, onClick = onClick)
            }
        },
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun SettingsToolbarGrid(
    items: List<KardLeafCustomFeatures.ToolbarItem>,
    onOrderChange: (List<KardLeafCustomFeatures.ToolbarItem>) -> Unit,
) {
    val columns = 4
    val spacing = 10.dp
    val density = LocalDensity.current
    var draggingItem by remember { mutableStateOf<KardLeafCustomFeatures.ToolbarItem?>(null) }
    var draggingStartIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }

    fun clearDragState() {
        draggingItem = null
        draggingStartIndex = -1
        dragOffset = Offset.Zero
        dragTargetIndex = null
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemSize = (maxWidth - spacing * (columns - 1).toFloat()) / columns
        val itemSizePx = with(density) { itemSize.toPx() }
        val spacingPx = with(density) { spacing.toPx() }
        val rows = items.chunked(columns)

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    rowItems.forEachIndexed { columnIndex, item ->
                        val index = rowIndex * columns + columnIndex
                        val isDragging = draggingItem == item
                        val targetIndex = dragTargetIndex
                        val isDropTarget = targetIndex == index && draggingItem != null && !isDragging
                        val avoidanceOffset = if (!isDragging && targetIndex != null) {
                            calculateToolbarAvoidanceOffset(
                                index = index,
                                fromIndex = draggingStartIndex,
                                toIndex = targetIndex,
                                columns = columns,
                                cellSizePx = itemSizePx + spacingPx,
                            )
                        } else {
                            IntOffset.Zero
                        }

                        SettingsToolbarGridItem(
                            icon = toolbarItemIcon(item),
                            title = item.label,
                            isDragging = isDragging,
                            isDropTarget = isDropTarget,
                            modifier = Modifier
                                .size(itemSize)
                                .zIndex(if (isDragging) 1f else 0f)
                                .offset {
                                    if (isDragging) {
                                        IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                                    } else {
                                        avoidanceOffset
                                    }
                                }
                                .pointerInput(item, items.size, itemSizePx, spacingPx) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingItem = item
                                            draggingStartIndex = index
                                            dragOffset = Offset.Zero
                                            dragTargetIndex = index
                                        },
                                        onDragCancel = { clearDragState() },
                                        onDragEnd = {
                                            val dragged = draggingItem
                                            val fromIndex = if (dragged == null) -1 else items.indexOf(dragged)
                                            val toIndex = dragTargetIndex
                                            if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                                val newOrder = items.toMutableList().also { list ->
                                                    val moved = list.removeAt(fromIndex)
                                                    list.add(toIndex.coerceIn(0, list.size), moved)
                                                }
                                                onOrderChange(newOrder)
                                            }
                                            clearDragState()
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount
                                            dragTargetIndex = calculateToolbarDragTarget(
                                                startIndex = draggingStartIndex,
                                                dragOffset = dragOffset,
                                                columns = columns,
                                                itemSizePx = itemSizePx,
                                                spacingPx = spacingPx,
                                                itemCount = items.size,
                                            )
                                        },
                                    )
                                },
                        )
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.size(itemSize))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToolbarGridItem(
    icon: ImageVector,
    title: String,
    isDragging: Boolean,
    isDropTarget: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val active = isDragging || isDropTarget
    val borderColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val backgroundColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun calculateToolbarDragTarget(
    startIndex: Int,
    dragOffset: Offset,
    columns: Int,
    itemSizePx: Float,
    spacingPx: Float,
    itemCount: Int,
): Int {
    if (startIndex !in 0 until itemCount) return 0

    val cellSizePx = itemSizePx + spacingPx
    val startColumn = startIndex % columns
    val startRow = startIndex / columns
    val centerX = startColumn * cellSizePx + itemSizePx / 2f + dragOffset.x
    val centerY = startRow * cellSizePx + itemSizePx / 2f + dragOffset.y
    val targetColumn = (centerX / cellSizePx).toInt().coerceIn(0, columns - 1)
    val targetRow = (centerY / cellSizePx).toInt().coerceIn(0, (itemCount - 1) / columns)

    return (targetRow * columns + targetColumn).coerceIn(0, itemCount - 1)
}

private fun calculateToolbarAvoidanceOffset(
    index: Int,
    fromIndex: Int,
    toIndex: Int,
    columns: Int,
    cellSizePx: Float,
): IntOffset {
    if (fromIndex == toIndex || fromIndex < 0) return IntOffset.Zero

    val visualIndex = when {
        fromIndex < toIndex && index in (fromIndex + 1)..toIndex -> index - 1
        fromIndex > toIndex && index in toIndex until fromIndex -> index + 1
        else -> index
    }
    if (visualIndex == index) return IntOffset.Zero

    val originalColumn = index % columns
    val originalRow = index / columns
    val visualColumn = visualIndex % columns
    val visualRow = visualIndex / columns
    return IntOffset(
        x = ((visualColumn - originalColumn) * cellSizePx).roundToInt(),
        y = ((visualRow - originalRow) * cellSizePx).roundToInt(),
    )
}

@Composable
private fun SettingsDrawerDragList(
    items: List<PrefsManager.DrawerItemId>,
    hiddenItems: Set<PrefsManager.DrawerItemId>,
    prefsManager: PrefsManager,
    onOrderChange: (List<PrefsManager.DrawerItemId>) -> Unit,
    onRename: (PrefsManager.DrawerItemId, String) -> Unit,
    onToggleVisible: (PrefsManager.DrawerItemId) -> Unit,
) {
    val rowHeight = 64.dp
    val rowSpacing = 6.dp
    val rowStepPx = with(LocalDensity.current) { (rowHeight + rowSpacing).toPx() }
    var draggingItem by remember { mutableStateOf<PrefsManager.DrawerItemId?>(null) }
    var draggingStartIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }

    fun clearDragState() {
        draggingItem = null
        draggingStartIndex = -1
        dragOffset = Offset.Zero
        dragTargetIndex = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        items.forEachIndexed { index, itemId ->
            val title = prefsManager.getDrawerItemLabel(itemId, drawerItemLabel(itemId))
            val itemIsDragging = draggingItem == itemId
            val targetIndex = dragTargetIndex
            val isDropTarget = targetIndex == index && draggingItem != null && !itemIsDragging
            val avoidanceOffset = if (!itemIsDragging && targetIndex != null) {
                calculateDrawerAvoidanceOffset(
                    index = index,
                    fromIndex = draggingStartIndex,
                    toIndex = targetIndex,
                    rowStepPx = rowStepPx,
                )
            } else {
                IntOffset.Zero
            }

            SettingsDrawerEditRow(
                icon = drawerItemIcon(itemId),
                title = title,
                isHidden = itemId in hiddenItems,
                isDragging = itemIsDragging,
                isDropTarget = isDropTarget,
                onRename = { onRename(itemId, title) },
                canToggleVisible = itemId != PrefsManager.DrawerItemId.SETTINGS,
                onToggleVisible = { onToggleVisible(itemId) },
                modifier = Modifier
                    .zIndex(if (itemIsDragging) 1f else 0f)
                    .offset {
                        if (itemIsDragging) {
                            IntOffset(0, dragOffset.y.roundToInt())
                        } else {
                            avoidanceOffset
                        }
                    }
                    .pointerInput(itemId, items.size, rowStepPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingItem = itemId
                                draggingStartIndex = index
                                dragOffset = Offset.Zero
                                dragTargetIndex = index
                            },
                            onDragCancel = { clearDragState() },
                            onDragEnd = {
                                val dragged = draggingItem
                                val fromIndex = if (dragged == null) -1 else items.indexOf(dragged)
                                val toIndex = dragTargetIndex
                                if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                    val newOrder = items.toMutableList().also { list ->
                                        val moved = list.removeAt(fromIndex)
                                        list.add(toIndex.coerceIn(0, list.size), moved)
                                    }
                                    onOrderChange(newOrder)
                                }
                                clearDragState()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                                dragTargetIndex = calculateDrawerDragTarget(
                                    startIndex = draggingStartIndex,
                                    dragOffset = dragOffset,
                                    rowHeightPx = rowStepPx,
                                    itemCount = items.size,
                                )
                            },
                        )
                    },
            )
        }
    }
}
@Composable
private fun SettingsDrawerEditRow(
    icon: ImageVector,
    title: String,
    isHidden: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    onRename: () -> Unit,
    canToggleVisible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = isDragging || isDropTarget
    val shape = RoundedCornerShape(16.dp)
    val backgroundColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val rowModifier = modifier
        .clip(shape)
        .background(backgroundColor)
        .border(1.dp, borderColor, shape)

    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = "",
        onClick = {},
        modifier = rowModifier,
        contentHorizontalPadding = 14.dp,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRename) { Text("改名") }
                if (canToggleVisible) {
                    TextButton(onClick = onToggleVisible) { Text(if (isHidden) "显示" else "隐藏") }
                }
            }
        },
    )
}

private fun calculateDrawerDragTarget(
    startIndex: Int,
    dragOffset: Offset,
    rowHeightPx: Float,
    itemCount: Int,
): Int {
    if (startIndex !in 0 until itemCount) return 0

    val centerY = startIndex * rowHeightPx + rowHeightPx / 2f + dragOffset.y
    return (centerY / rowHeightPx).toInt().coerceIn(0, itemCount - 1)
}

private fun calculateDrawerAvoidanceOffset(
    index: Int,
    fromIndex: Int,
    toIndex: Int,
    rowStepPx: Float,
): IntOffset {
    if (fromIndex == toIndex || fromIndex < 0) return IntOffset.Zero

    val visualIndex = when {
        fromIndex < toIndex && index in (fromIndex + 1)..toIndex -> index - 1
        fromIndex > toIndex && index in toIndex until fromIndex -> index + 1
        else -> index
    }
    if (visualIndex == index) return IntOffset.Zero

    return IntOffset(
        x = 0,
        y = ((visualIndex - index) * rowStepPx).roundToInt(),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsBaseRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentHorizontalPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val clickModifier = if (onLongClick == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(clickModifier)
            .padding(horizontal = contentHorizontalPadding, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

private fun drawerItemLabel(itemId: PrefsManager.DrawerItemId): String =
    when (itemId) {
        PrefsManager.DrawerItemId.ALL_NOTES -> "全部笔记"
        PrefsManager.DrawerItemId.RECENT -> "最近修改"
        PrefsManager.DrawerItemId.FAVORITES -> "收藏"
        PrefsManager.DrawerItemId.DRAFTS -> "草稿"
        PrefsManager.DrawerItemId.TAGS -> "标签"
        PrefsManager.DrawerItemId.FILES -> "文件"
        PrefsManager.DrawerItemId.DATES -> "日期"
        PrefsManager.DrawerItemId.IMAGES -> "图片"
        PrefsManager.DrawerItemId.ARCHIVE -> "归档"
        PrefsManager.DrawerItemId.TRASH -> "废弃"
        PrefsManager.DrawerItemId.PRIVACY -> "隐私"
        PrefsManager.DrawerItemId.ONBOARDING -> "介绍"
        PrefsManager.DrawerItemId.SETTINGS -> "设置"
    }

private fun drawerItemIcon(itemId: PrefsManager.DrawerItemId): ImageVector =
    when (itemId) {
        PrefsManager.DrawerItemId.ALL_NOTES -> Icons.Outlined.Description
        PrefsManager.DrawerItemId.RECENT -> Icons.Outlined.History
        PrefsManager.DrawerItemId.FAVORITES -> Icons.Outlined.FavoriteBorder
        PrefsManager.DrawerItemId.DRAFTS -> Icons.Outlined.Drafts
        PrefsManager.DrawerItemId.TAGS -> Icons.Outlined.Label
        PrefsManager.DrawerItemId.FILES -> Icons.Outlined.Folder
        PrefsManager.DrawerItemId.DATES -> Icons.Outlined.CalendarToday
        PrefsManager.DrawerItemId.IMAGES -> Icons.Outlined.Image
        PrefsManager.DrawerItemId.ARCHIVE -> Icons.Outlined.Archive
        PrefsManager.DrawerItemId.TRASH -> Icons.Outlined.Delete
        PrefsManager.DrawerItemId.PRIVACY -> Icons.Outlined.Lock
        PrefsManager.DrawerItemId.ONBOARDING -> Icons.AutoMirrored.Outlined.MenuBook
        PrefsManager.DrawerItemId.SETTINGS -> Icons.Outlined.Settings
    }

private fun displayRootPath(uriString: String?): String {
    if (uriString.isNullOrBlank()) return "未选择笔记库"
    val treePath = runCatching {
        Uri.decode(Uri.parse(uriString).lastPathSegment.orEmpty())
    }.getOrNull().orEmpty()

    if (treePath.contains(":")) {
        val volume = treePath.substringBefore(":")
        val relativePath = treePath.substringAfter(":").trim('/')
        val rootPath = if (volume.equals("primary", ignoreCase = true)) {
            "/storage/emulated/0"
        } else {
            "/storage/$volume"
        }
        return listOf(rootPath, relativePath)
            .filter { it.isNotBlank() }
            .joinToString("/")
            .replace("//", "/")
    }

    return uriString
}

fun hashPassword(raw: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun themeSummary(
    accentColor: PrefsManager.ThemeColor,
    backgroundColor: PrefsManager.ThemeBackgroundColor,
): String = "强调色：${themeColorLabel(accentColor)} · 背景色：${themeBackgroundColorLabel(backgroundColor)}"

private fun themeColorLabel(color: PrefsManager.ThemeColor): String =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> "蓝色"
        PrefsManager.ThemeColor.GREEN -> "青绿色"
        PrefsManager.ThemeColor.PURPLE -> "紫色"
        PrefsManager.ThemeColor.PINK -> "粉色"
        PrefsManager.ThemeColor.AMBER -> "琥珀色"
        PrefsManager.ThemeColor.RED -> "红色"
    }

private fun themeColorSubtitle(color: PrefsManager.ThemeColor): String =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> "默认蓝色强调色"
        PrefsManager.ThemeColor.GREEN -> "自然叶子风格"
        PrefsManager.ThemeColor.PURPLE -> "柔和效率风格"
        PrefsManager.ThemeColor.PINK -> "轻柔生活风格"
        PrefsManager.ThemeColor.AMBER -> "温暖阅读风格"
        PrefsManager.ThemeColor.RED -> "醒目强调风格"
    }

private fun themeBackgroundColorLabel(color: PrefsManager.ThemeBackgroundColor): String =
    when (color) {
        PrefsManager.ThemeBackgroundColor.WHITE -> "白色"
        PrefsManager.ThemeBackgroundColor.BLUE -> "淡蓝色"
        PrefsManager.ThemeBackgroundColor.GREEN -> "淡绿色"
        PrefsManager.ThemeBackgroundColor.PURPLE -> "淡紫色"
        PrefsManager.ThemeBackgroundColor.PINK -> "淡粉色"
        PrefsManager.ThemeBackgroundColor.AMBER -> "淡琥珀色"
        PrefsManager.ThemeBackgroundColor.GRAY -> "浅灰色"
    }

private fun themeBackgroundColorSubtitle(color: PrefsManager.ThemeBackgroundColor): String =
    when (color) {
        PrefsManager.ThemeBackgroundColor.WHITE -> "默认白色背景"
        PrefsManager.ThemeBackgroundColor.BLUE -> "清爽蓝色背景"
        PrefsManager.ThemeBackgroundColor.GREEN -> "柔和自然背景"
        PrefsManager.ThemeBackgroundColor.PURPLE -> "淡紫氛围背景"
        PrefsManager.ThemeBackgroundColor.PINK -> "温柔浅粉背景"
        PrefsManager.ThemeBackgroundColor.AMBER -> "暖色阅读背景"
        PrefsManager.ThemeBackgroundColor.GRAY -> "中性浅灰背景"
    }

private fun themeAccentPreviewColor(color: PrefsManager.ThemeColor): Color =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> Color(0xFF3B82F6)
        PrefsManager.ThemeColor.GREEN -> Color(0xFF00856F)
        PrefsManager.ThemeColor.PURPLE -> Color(0xFF7C3AED)
        PrefsManager.ThemeColor.PINK -> Color(0xFFB83263)
        PrefsManager.ThemeColor.AMBER -> Color(0xFF956300)
        PrefsManager.ThemeColor.RED -> Color(0xFFDC2626)
    }

private fun themeBackgroundPreviewColor(color: PrefsManager.ThemeBackgroundColor): Color =
    when (color) {
        PrefsManager.ThemeBackgroundColor.WHITE -> Color.White
        PrefsManager.ThemeBackgroundColor.BLUE -> Color(0xFFF4FAFF)
        PrefsManager.ThemeBackgroundColor.GREEN -> Color(0xFFF2FCF8)
        PrefsManager.ThemeBackgroundColor.PURPLE -> Color(0xFFFAF7FF)
        PrefsManager.ThemeBackgroundColor.PINK -> Color(0xFFFFF7FA)
        PrefsManager.ThemeBackgroundColor.AMBER -> Color(0xFFFFFAEF)
        PrefsManager.ThemeBackgroundColor.GRAY -> Color(0xFFF8FAFC)
    }
