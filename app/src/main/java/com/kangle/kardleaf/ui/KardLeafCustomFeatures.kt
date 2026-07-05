package com.kangle.kardleaf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.kangle.kardleaf.AppIconManager
import com.kangle.kardleaf.BuildConfig
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.NoteRecordSummary
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.sync.WebDavCloudSyncManager
import com.kangle.kardleaf.data.task.TaskReminderScheduler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val SETTINGS_TRACE_TAG = "KardLeafSettingsTrace"

object KardLeafCustomFeatures {
    const val UseDynamicColor = false
    const val DefaultUnnamedNoteDateFormat = "yyyy.MM.dd.HHmmss"
    const val DefaultUnnamedNoteFileNameTemplate = ""
    val DefaultOpenNoteMode = OpenNoteMode.PREVIEW
    const val DefaultEditDoubleTapPreview = false

    fun editorKernelIcon(kernel: PrefsManager.EditorKernel): ImageVector = when (kernel) {
        PrefsManager.EditorKernel.AUTO -> Icons.Outlined.Settings
        PrefsManager.EditorKernel.NATIVE -> Icons.Outlined.Edit
        PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW -> Icons.Outlined.Code
    }

    fun editorKernelTitle(kernel: PrefsManager.EditorKernel): String = when (kernel) {
        PrefsManager.EditorKernel.AUTO -> "固定原生编辑器"
        PrefsManager.EditorKernel.NATIVE -> "固定原生编辑器"
        PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW -> "固定 WebView / CodeMirror"
    }

    fun editorKernelSubtitle(kernel: PrefsManager.EditorKernel): String = when (kernel) {
        PrefsManager.EditorKernel.AUTO -> "自动切换已取消，旧配置按原生编辑器处理"
        PrefsManager.EditorKernel.NATIVE -> "默认使用原生编辑器，不按字数自动切换"
        PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW -> "所有普通笔记都使用 WebView / CodeMirror"
    }

    private const val PrefsName = "kardleaf_custom_features"
    private const val KeyUnnamedNoteDateFormat = "unnamed_note_date_format"
    private const val KeyUnnamedNoteFileNameTemplate = "unnamed_note_file_name_template"
    private const val KeyOpenNoteMode = "open_note_mode"
    private const val KeyEditDoubleTapPreview = "edit_double_tap_preview"
    private const val KeyToolbarOrder = "toolbar_order"
    private const val MaxExternalNoteTitleChars = 120
    private const val MaxExternalNoteFolderChars = 240
    private const val MaxExternalNoteContentChars = 50_000
    private const val MaxExternalNoteUrlChars = 2_048
    private val unsafeFileNameChars = Regex("[\\\\/:*?\"<>|]")
    private val unsafeFolderSegmentChars = Regex("[:*?\"<>|]")
    private val looseDatePatternChars = setOf('y', 'Y', 'M', 'L', 'd', 'D', 'H', 'h', 'm', 's', 'S')

    enum class OpenNoteMode(val label: String) {
        PREVIEW("Preview"),
        EDIT("Edit"),
    }

    enum class ToolbarItem(val label: String) {
        PREVIEW("预览"),
        UNDO("撤销"),
        REDO("恢复"),
        IMAGE("图片"),
        DRAWING("绘图"),
        HEADING("一级标题"),
        HEADING2("二级标题"),
        HEADING3("三级标题"),
        RULE("分割线"),
        BOLD("加粗"),
        ITALIC("斜体"),
        UNDERLINE("下划线"),
        STRIKE("删除线"),
        LINK("链接"),
        CODE("行内代码"),
        CODE_BLOCK("代码块"),
        QUOTE("引用"),
        MATH("公式"),
        BULLET("无序列表"),
        NUMBERED("有序列表"),
        CHECKBOX("待办"),
        CHECKBOX_DONE("已完成待办"),
        TABLE("表格"),
    }

    val DefaultToolbarOrder =
        listOf(
            ToolbarItem.UNDO,
            ToolbarItem.REDO,
            ToolbarItem.IMAGE,
            ToolbarItem.DRAWING,
            ToolbarItem.HEADING,
            ToolbarItem.HEADING2,
            ToolbarItem.HEADING3,
            ToolbarItem.PREVIEW,
            ToolbarItem.RULE,
            ToolbarItem.BOLD,
            ToolbarItem.ITALIC,
            ToolbarItem.UNDERLINE,
            ToolbarItem.STRIKE,
            ToolbarItem.LINK,
            ToolbarItem.CODE,
            ToolbarItem.CODE_BLOCK,
            ToolbarItem.QUOTE,
            ToolbarItem.MATH,
            ToolbarItem.BULLET,
            ToolbarItem.NUMBERED,
            ToolbarItem.CHECKBOX,
            ToolbarItem.CHECKBOX_DONE,
            ToolbarItem.TABLE,
        )

    data class ExternalNoteDraft(
        val title: String = "",
        val content: String = "",
        val folder: String = "",
        val isTemporary: Boolean = false,
        val isPinned: Boolean = false,
        val forceRootFolder: Boolean = false,
    )

    fun parseExternalCreateNoteUri(uri: Uri?): ExternalNoteDraft? {
        if (uri == null) return null
        if (uri.scheme != "kardleaf" || uri.host != "new") return null

        val title = sanitizeTitle(
            uri.getQueryParameter("title").orEmpty().take(MaxExternalNoteTitleChars),
        ).take(MaxExternalNoteTitleChars)
        val content =
            firstQueryParameter(uri, "content", "body", "text")
                .orEmpty()
                .take(MaxExternalNoteContentChars)
        val url = uri.getQueryParameter("url").orEmpty().take(MaxExternalNoteUrlChars)
        val finalContent =
            when {
                content.isNotBlank() && url.isNotBlank() -> "$content\n\n$url"
                content.isNotBlank() -> content
                else -> url
            }.take(MaxExternalNoteContentChars)
        val folder =
            sanitizeFolderPath(
                firstQueryParameter(uri, "path", "folder", "label").orEmpty().take(MaxExternalNoteFolderChars),
            ).take(MaxExternalNoteFolderChars)
        val isPinned = parseBoolean(uri.getQueryParameter("pinned"))
        val forceRootFolder = parseBoolean(uri.getQueryParameter("root"))

        return ExternalNoteDraft(
            title = title,
            content = finalContent,
            folder = folder,
            isPinned = isPinned,
            forceRootFolder = forceRootFolder,
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
                    segment != ".."
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

    fun getUnnamedNoteFileNameTemplate(context: Context): String {
        return context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .getString(KeyUnnamedNoteFileNameTemplate, DefaultUnnamedNoteFileNameTemplate)
            .orEmpty()
    }

    fun saveUnnamedNoteFileNameTemplate(
        context: Context,
        template: String,
    ): Boolean {
        val normalized = template.trim()
        if (!isAutoFileNameTemplateUsable(normalized, getUnnamedNoteDateFormat(context))) return false

        context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyUnnamedNoteFileNameTemplate, normalized)
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
        existingTitles: Set<String> = emptySet(),
    ): String {
        val dateFormat = getUnnamedNoteDateFormat(context)
        val template = getUnnamedNoteFileNameTemplate(context)
        return runCatching {
            formatAutoFileNameTitle(template, dateFormat, date, locale, existingTitles)
        }.getOrElse {
            formatDateForFileName(DefaultUnnamedNoteDateFormat, date, locale)
        }
    }

    fun previewUnnamedNoteTitle(dateFormat: String): String {
        return runCatching {
            formatDateForFileName(dateFormat.trim(), Date(), Locale.getDefault())
        }.getOrDefault("")
    }

    fun previewUnnamedNoteFileNameTemplate(
        template: String,
        dateFormat: String,
    ): String {
        return runCatching {
            formatAutoFileNameTitle(template, dateFormat, Date(), Locale.getDefault(), emptySet())
        }.getOrDefault("")
    }

    fun isDateFormatUsable(dateFormat: String): Boolean {
        if (dateFormat.isBlank()) return false
        return runCatching {
            SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date())
        }.isSuccess
    }

    fun isAutoFileNameTemplateUsable(
        template: String,
        dateFormat: String,
    ): Boolean {
        val safeDateFormat = dateFormat.trim().takeIf { isDateFormatUsable(it) } ?: DefaultUnnamedNoteDateFormat
        return runCatching {
            formatAutoFileNameTitle(template, safeDateFormat, Date(), Locale.getDefault(), emptySet()).isNotBlank()
        }.getOrDefault(false)
    }

    private fun formatAutoFileNameTitle(
        template: String,
        dateFormat: String,
        date: Date,
        locale: Locale,
        existingTitles: Set<String>,
    ): String {
        val safeDateFormat = dateFormat.trim().takeIf { isDateFormatUsable(it) } ?: DefaultUnnamedNoteDateFormat
        val rawTemplate = template.trim().ifBlank { safeDateFormat }
        val hasCounter = rawTemplate.contains("{1}")
        val existing = existingTitles.map { it.trim() }.filter { it.isNotBlank() }.toSet()

        if (!hasCounter) {
            return sanitizeAutoFileNameTitle(renderAutoFileNameTemplate(rawTemplate, date, locale, 1), date, locale)
        }

        for (counter in 1..9999) {
            val candidate = sanitizeAutoFileNameTitle(
                renderAutoFileNameTemplate(rawTemplate, date, locale, counter),
                date,
                locale,
            )
            if (candidate !in existing) return candidate
        }

        return sanitizeAutoFileNameTitle(
            renderAutoFileNameTemplate(rawTemplate, date, locale, System.currentTimeMillis().rem(100000).toInt()),
            date,
            locale,
        )
    }

    private fun renderAutoFileNameTemplate(
        template: String,
        date: Date,
        locale: Locale,
        counter: Int,
    ): String {
        val withCounter = template.replace("{1}", counter.toString())
        return runCatching {
            SimpleDateFormat(withCounter, locale).format(date)
        }.getOrElse {
            formatLooseDateTemplate(withCounter, date, locale)
        }
    }

    private fun formatLooseDateTemplate(
        template: String,
        date: Date,
        locale: Locale,
    ): String {
        val builder = StringBuilder()
        var index = 0
        while (index < template.length) {
            val ch = template[index]
            if (ch in looseDatePatternChars) {
                var end = index + 1
                while (end < template.length && template[end] == ch) end++
                val token = template.substring(index, end)
                if (token.length >= 2) {
                    builder.append(runCatching { SimpleDateFormat(token, locale).format(date) }.getOrDefault(token))
                } else {
                    builder.append(token)
                }
                index = end
            } else {
                builder.append(ch)
                index++
            }
        }
        return builder.toString()
    }

    private fun sanitizeAutoFileNameTitle(
        title: String,
        date: Date,
        locale: Locale,
    ): String {
        return title
            .replace(unsafeFileNameChars, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { SimpleDateFormat(DefaultUnnamedNoteDateFormat, locale).format(date) }
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
    onOpenRecordNote: (String) -> Unit = {},
    onCleanupHistory: () -> Unit = {},
    onWebDavVaultChanged: (List<String>) -> Unit = {},
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
    var autoFileNameTemplateFieldValue by remember {
        val initial = KardLeafCustomFeatures.getUnnamedNoteFileNameTemplate(context)
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    val autoFileNameTemplate = autoFileNameTemplateFieldValue.text
    var openNoteMode by remember { mutableStateOf(KardLeafCustomFeatures.getOpenNoteMode(context)) }
    var editorKernel by remember { mutableStateOf(prefsManager.getEditorKernel()) }
    var autoCodeMirrorThresholdText by remember { mutableStateOf(prefsManager.getAutoCodeMirrorThresholdChars().toString()) }
    var codeMirrorLivePreviewEnabled by remember { mutableStateOf(prefsManager.isCodeMirrorLivePreviewEnabled()) }
    var editorFontSizeSp by remember { mutableStateOf(prefsManager.getEditorFontSizeSp()) }
    var editorLineHeightMultiplier by remember { mutableStateOf(prefsManager.getEditorLineHeightMultiplier()) }
    var editorLetterSpacingSp by remember { mutableStateOf(prefsManager.getEditorLetterSpacingSp()) }
    var editorParagraphSpacingDp by remember { mutableStateOf(prefsManager.getEditorParagraphSpacingDp()) }
    var editorFontFamily by remember { mutableStateOf(prefsManager.getEditorFontFamily()) }
    var customEditorFontFamilyText by remember { mutableStateOf(editorFontFamily.takeUnless { it in EditorBuiltinFontFamilies.map { item -> item.value } }.orEmpty()) }
    var appLanguage by remember { mutableStateOf(prefsManager.getAppLanguage()) }
    val settingsEnglish = appLanguage == "en"
    var editorBottomToolbarAlwaysVisible by remember { mutableStateOf(prefsManager.isEditorBottomToolbarAlwaysVisible()) }
    var homeActionStyle by remember { mutableStateOf(prefsManager.getHomeActionStyle()) }
    var homeBottomToolbarOrder by remember { mutableStateOf(prefsManager.getHomeBottomToolbarItemOrder()) }
    var homeBottomToolbarHiddenItems by remember { mutableStateOf(prefsManager.getHomeBottomToolbarHiddenItems()) }
    var homeBottomToolbarButtonSizeDp by remember { mutableStateOf(prefsManager.getHomeBottomToolbarButtonSizeDp()) }
    var trashFolderName by remember { mutableStateOf(prefsManager.getTrashFolderName()) }
    var trashSortOrder by remember { mutableStateOf(prefsManager.getTrashSortOrder()) }
    var cardDensity by remember { mutableStateOf(prefsManager.getCardDensity()) }
    var viewMode by remember { mutableStateOf(prefsManager.getViewMode()) }
    var sortOrder by remember { mutableStateOf(prefsManager.getSortOrder()) }
    var sortDirection by remember { mutableStateOf(prefsManager.getSortDirection()) }
    var imageFolder by remember { mutableStateOf(prefsManager.getImageFolder()) }
    var hiddenFolders by remember { mutableStateOf(prefsManager.getHiddenFolderPaths()) }
    var relativeImageLocation by remember { mutableStateOf(prefsManager.getRelativeImageLocation()) }
    var appThemeStyle by remember { mutableStateOf(prefsManager.getAppThemeStyle()) }
    var appThemeMode by remember { mutableStateOf(prefsManager.getAppThemeMode()) }
    var modernThemeColorStyle by remember { mutableStateOf(prefsManager.getModernThemeColorStyle()) }
    var cleanListFeatureIconStyle by remember { mutableStateOf(prefsManager.getCleanListFeatureIconStyle()) }
    var appIcon by remember { mutableStateOf(AppIconManager.current(context)) }
    var themeColor by remember { mutableStateOf(prefsManager.getThemeColor()) }
    var customThemeColorArgb by remember { mutableStateOf(prefsManager.getCustomThemeColorArgb()) }
    var customThemeColorText by remember { mutableStateOf(argbToThemeHex(customThemeColorArgb)) }
    var showCustomThemeColorDialog by remember { mutableStateOf(false) }
    var themeBackgroundColor by remember { mutableStateOf(prefsManager.getThemeBackgroundColor()) }
    var customThemeBackgroundColorArgb by remember { mutableStateOf(prefsManager.getCustomThemeBackgroundColorArgb()) }
    var customThemeBackgroundColorText by remember { mutableStateOf(argbToThemeHex(customThemeBackgroundColorArgb)) }
    var showCustomThemeBackgroundColorDialog by remember { mutableStateOf(false) }
    var globalCornerRadiusDp by remember { mutableStateOf(prefsManager.getGlobalCornerRadiusDp()) }
    var homeCornerRadiusDp by remember { mutableStateOf(prefsManager.getHomeCornerRadiusDp()) }
    var drawerEdgeWidthText by remember { mutableStateOf(prefsManager.getDrawerEdgeWidthDp().toString()) }
    var drawerStyle by remember { mutableStateOf(prefsManager.getDrawerStyle()) }
    var noteSidePanelsEnabled by remember { mutableStateOf(prefsManager.isNoteSidePanelsEnabled()) }
    var noteSidePanelOpenMode by remember { mutableStateOf(prefsManager.getNoteSidePanelOpenMode()) }
    var showYamlTagsOnLooseCards by remember { mutableStateOf(prefsManager.isLooseCardYamlTagsVisible()) }
    var showModifiedDateOnCards by remember { mutableStateOf(prefsManager.isModifiedDateOnCardsVisible()) }
    var cardModifiedDateFormat by remember { mutableStateOf(prefsManager.getCardModifiedDateFormat()) }
    var showNoteTitleOnCards by remember { mutableStateOf(prefsManager.isNoteTitleOnCardsVisible()) }
    var showDateFilenameTitleOnCards by remember { mutableStateOf(prefsManager.isDateFilenameTitleOnCardsVisible()) }
    var showNoteDetailTitle by remember { mutableStateOf(prefsManager.isNoteDetailTitleVisible()) }
    var showNoteDetailFileInfo by remember { mutableStateOf(prefsManager.isNoteDetailFileInfoVisible()) }
    var customHiddenFilenamePatterns by remember { mutableStateOf(prefsManager.getCustomHiddenFilenamePatterns()) }
    var customHiddenFilenameText by remember { mutableStateOf(customHiddenFilenamePatterns.joinToString("\n")) }
    var historyLimitText by remember { mutableStateOf(prefsManager.getHistoryVersionLimit().toString()) }
    var doubleTapIntervalText by remember { mutableStateOf(prefsManager.getPreviewDoubleTapIntervalMs().toString()) }
    var trashAutoCleanDaysText by remember { mutableStateOf(prefsManager.getTrashAutoCleanDays().toString()) }
    var passwordInputMode by remember { mutableStateOf(prefsManager.getPasswordInputMode()) }
    var toolbarOrder by remember { mutableStateOf(KardLeafCustomFeatures.getToolbarOrder(context)) }
    var editorTopToolbarOrder by remember { mutableStateOf(prefsManager.getEditorTopToolbarItemOrder()) }
    var editorTopToolbarMoreItems by remember { mutableStateOf(prefsManager.getEditorTopToolbarMoreItems()) }
    var editorTopToolbarHiddenItems by remember { mutableStateOf(prefsManager.getEditorTopToolbarHiddenItems()) }
    var selectionToolbarOrder by remember { mutableStateOf(prefsManager.getSelectionToolbarItemOrder()) }
    var selectionToolbarMoreItems by remember { mutableStateOf(prefsManager.getSelectionToolbarMoreItems()) }
    var selectionToolbarHiddenItems by remember { mutableStateOf(prefsManager.getSelectionToolbarHiddenItems()) }
    var restoreLastFilter by remember { mutableStateOf(prefsManager.isRestoreLastFilterEnabled()) }
    var defaultStartLabel by remember { mutableStateOf(prefsManager.getDefaultStartLabel()) }
    var showLabelPicker by remember { mutableStateOf(false) }
    var showVaultPathDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showCleanupHistoryDialog by remember { mutableStateOf(false) }
    var showDisableHistoryConfirmDialog by remember { mutableStateOf(false) }
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
    val normalizedAutoFileNameTemplate = autoFileNameTemplate.trim()
    val normalizedTrashFolderName = trashFolderName.trim()
    val isValid = KardLeafCustomFeatures.isDateFormatUsable(normalized)
    val autoFileNameDateFormat = normalized.takeIf { isValid } ?: KardLeafCustomFeatures.DefaultUnnamedNoteDateFormat
    val isAutoFileNameTemplateValid = KardLeafCustomFeatures.isAutoFileNameTemplateUsable(
        normalizedAutoFileNameTemplate,
        autoFileNameDateFormat,
    )
    val isTrashFolderValid = normalizedTrashFolderName.isNotBlank() && !normalizedTrashFolderName.contains(Regex("[\\\\/:*?\"<>|]"))
    val drawerEdgeWidth = drawerEdgeWidthText.trim().toIntOrNull()
    val isDrawerEdgeWidthValid = drawerEdgeWidth != null && drawerEdgeWidth in 24..160
    val historyLimit = historyLimitText.trim().toIntOrNull()
    val isHistoryLimitValid = historyLimit != null &&
        historyLimit in PrefsManager.MIN_HISTORY_VERSION_LIMIT..PrefsManager.MAX_HISTORY_VERSION_LIMIT
    val doubleTapInterval = doubleTapIntervalText.trim().toIntOrNull()
    val isDoubleTapIntervalValid = doubleTapInterval != null &&
        doubleTapInterval in PrefsManager.MIN_PREVIEW_DOUBLE_TAP_INTERVAL_MS..PrefsManager.MAX_PREVIEW_DOUBLE_TAP_INTERVAL_MS
    val autoCodeMirrorThreshold = autoCodeMirrorThresholdText.trim().toIntOrNull()
    val isAutoCodeMirrorThresholdValid = autoCodeMirrorThreshold != null &&
        autoCodeMirrorThreshold in PrefsManager.MIN_AUTO_CODEMIRROR_THRESHOLD_CHARS..PrefsManager.MAX_AUTO_CODEMIRROR_THRESHOLD_CHARS
    val trashAutoCleanDays = trashAutoCleanDaysText.trim().toIntOrNull()
    val isTrashAutoCleanDaysValid = trashAutoCleanDays != null && trashAutoCleanDays in 0..365
    val savedHistoryLimit = prefsManager.getHistoryVersionLimit()
    val normalizedCardModifiedDateFormat = cardModifiedDateFormat.trim()
    val isCardModifiedDateFormatValid = prefsManager.isDateFormatUsable(normalizedCardModifiedDateFormat)
    val cardModifiedDateSample = if (isCardModifiedDateFormatValid) {
        runCatching { SimpleDateFormat(normalizedCardModifiedDateFormat, Locale.getDefault()).format(Date()) }.getOrDefault("")
    } else {
        ""
    }
    val sample = if (isValid) KardLeafCustomFeatures.previewUnnamedNoteTitle(normalized) else ""
    val autoFileNameSample = if (isAutoFileNameTemplateValid) {
        KardLeafCustomFeatures.previewUnnamedNoteFileNameTemplate(
            normalizedAutoFileNameTemplate,
            autoFileNameDateFormat,
        )
    } else {
        ""
    }
    val autoFileNameSummary = if (normalizedAutoFileNameTemplate.isBlank()) {
        "跟随日期格式：$dateFormat"
    } else {
        normalizedAutoFileNameTemplate
    }

    fun applyHistoryVersionLimitInput(limit: Int) {
        if (limit == 0 && prefsManager.getHistoryVersionLimit() != 0) {
            showDisableHistoryConfirmDialog = true
            return
        }
        prefsManager.saveHistoryVersionLimit(limit)
        onSettingsChanged()
    }

    fun confirmDisableHistoryVersions() {
        historyLimitText = "0"
        prefsManager.saveHistoryVersionLimit(0)
        showDisableHistoryConfirmDialog = false
        onSettingsChanged()
    }

    fun cancelDisableHistoryVersions() {
        historyLimitText = prefsManager.getHistoryVersionLimit().toString()
        showDisableHistoryConfirmDialog = false
    }

    fun updateAutoFileNameTemplate(value: TextFieldValue) {
        autoFileNameTemplateFieldValue = value
        val n = value.text.trim()
        if (KardLeafCustomFeatures.isAutoFileNameTemplateUsable(n, autoFileNameDateFormat)) {
            KardLeafCustomFeatures.saveUnnamedNoteFileNameTemplate(context, n)
        }
    }

    fun insertAutoFileNameToken(token: String) {
        val value = autoFileNameTemplateFieldValue
        val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
        val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
        val newText = value.text.substring(0, start) + token + value.text.substring(end)
        val cursor = start + token.length
        updateAutoFileNameTemplate(TextFieldValue(newText, selection = TextRange(cursor)))
    }

    @Composable
    fun AutoFileNameTemplateField() {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = autoFileNameTemplateFieldValue,
                onValueChange = { updateAutoFileNameTemplate(it) },
                label = { Text("自动文件名模板") },
                singleLine = true,
                isError = !isAutoFileNameTemplateValid,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(
                    onClick = { insertAutoFileNameToken(autoFileNameDateFormat) },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text("日期格式", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = { insertAutoFileNameToken("{1}") },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text("{1}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

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

    fun applyThemeStyle(style: PrefsManager.AppThemeStyle) {
        val nextStyle = if (style == PrefsManager.AppThemeStyle.NOW_IN_ANDROID) {
            PrefsManager.AppThemeStyle.MODERN
        } else {
            style
        }
        if (appThemeStyle == nextStyle && style != PrefsManager.AppThemeStyle.NOW_IN_ANDROID) return
        appThemeStyle = nextStyle
        if (style == PrefsManager.AppThemeStyle.NOW_IN_ANDROID) {
            modernThemeColorStyle = PrefsManager.ModernThemeColorStyle.MODERN
            prefsManager.saveModernThemeColorStyle(modernThemeColorStyle)
        }
        prefsManager.saveAppThemeStyle(nextStyle)
        onRestartNeeded()
    }

    fun applyModernThemeColorStyle(style: PrefsManager.ModernThemeColorStyle) {
        if (modernThemeColorStyle == style) return
        modernThemeColorStyle = style
        prefsManager.saveModernThemeColorStyle(style)
        onRestartNeeded()
    }

    fun applyCleanListFeatureIconStyle(style: PrefsManager.CleanListFeatureIconStyle) {
        if (cleanListFeatureIconStyle == style) return
        cleanListFeatureIconStyle = style
        prefsManager.saveCleanListFeatureIconStyle(style)
        onRestartNeeded()
    }

    fun applyThemeMode(mode: PrefsManager.AppThemeMode) {
        if (appThemeMode == mode) return
        appThemeMode = mode
        prefsManager.saveAppThemeMode(mode)
        onRestartNeeded()
    }

    fun applyThemeColor(color: PrefsManager.ThemeColor) {
        if (themeColor == color) return
        themeColor = color
        prefsManager.saveThemeColor(color)
        onRestartNeeded()
    }

    fun applyCustomThemeColor(argb: Int) {
        val changed = themeColor != PrefsManager.ThemeColor.CUSTOM || customThemeColorArgb != argb
        customThemeColorArgb = argb
        customThemeColorText = argbToThemeHex(argb)
        themeColor = PrefsManager.ThemeColor.CUSTOM
        prefsManager.saveCustomThemeColorArgb(argb)
        prefsManager.saveThemeColor(themeColor)
        if (changed) onRestartNeeded()
    }

    fun applyThemeBackgroundColor(color: PrefsManager.ThemeBackgroundColor) {
        if (themeBackgroundColor == color) return
        themeBackgroundColor = color
        prefsManager.saveThemeBackgroundColor(color)
        onRestartNeeded()
    }

    fun applyCustomThemeBackgroundColor(argb: Int) {
        val changed = themeBackgroundColor != PrefsManager.ThemeBackgroundColor.CUSTOM ||
            customThemeBackgroundColorArgb != argb
        customThemeBackgroundColorArgb = argb
        customThemeBackgroundColorText = argbToThemeHex(argb)
        themeBackgroundColor = PrefsManager.ThemeBackgroundColor.CUSTOM
        prefsManager.saveCustomThemeBackgroundColorArgb(argb)
        prefsManager.saveThemeBackgroundColor(themeBackgroundColor)
        if (changed) onRestartNeeded()
    }

    fun applyRecommendedThemePalette(
        color: PrefsManager.ThemeColor,
        background: PrefsManager.ThemeBackgroundColor,
    ) {
        val changed = themeColor != color || themeBackgroundColor != background
        themeColor = color
        themeBackgroundColor = background
        prefsManager.saveThemeColor(color)
        prefsManager.saveThemeBackgroundColor(background)
        if (changed) onRestartNeeded()
    }

    fun applyGlobalCornerRadiusDp(radiusDp: Int) {
        if (globalCornerRadiusDp == radiusDp) return
        globalCornerRadiusDp = radiusDp
        prefsManager.saveGlobalCornerRadiusDp(radiusDp)
        onRestartNeeded()
    }

    fun applyHomeCornerRadiusDp(radiusDp: Int) {
        if (homeCornerRadiusDp == radiusDp) return
        homeCornerRadiusDp = radiusDp
        prefsManager.saveHomeCornerRadiusDp(radiusDp)
        onRestartNeeded()
    }

    fun resetTheme() {
        val changed = appThemeStyle != PrefsManager.AppThemeStyle.CLEAN_LIST ||
            appThemeMode != PrefsManager.AppThemeMode.SYSTEM ||
            modernThemeColorStyle != PrefsManager.ModernThemeColorStyle.CLASSIC ||
            cleanListFeatureIconStyle != PrefsManager.CleanListFeatureIconStyle.MODERN ||
            themeColor != PrefsManager.ThemeColor.BLUE ||
            themeBackgroundColor != PrefsManager.ThemeBackgroundColor.WHITE ||
            globalCornerRadiusDp != PrefsManager.THEME_CORNER_RADIUS_FOLLOW ||
            homeCornerRadiusDp != PrefsManager.THEME_CORNER_RADIUS_FOLLOW ||
            appIcon != AppIconManager.AppIcon.DEFAULT
        appThemeStyle = PrefsManager.AppThemeStyle.CLEAN_LIST
        appThemeMode = PrefsManager.AppThemeMode.SYSTEM
        modernThemeColorStyle = PrefsManager.ModernThemeColorStyle.CLASSIC
        cleanListFeatureIconStyle = PrefsManager.CleanListFeatureIconStyle.MODERN
        appIcon = AppIconManager.AppIcon.DEFAULT
        themeColor = PrefsManager.ThemeColor.BLUE
        themeBackgroundColor = PrefsManager.ThemeBackgroundColor.WHITE
        globalCornerRadiusDp = PrefsManager.THEME_CORNER_RADIUS_FOLLOW
        homeCornerRadiusDp = PrefsManager.THEME_CORNER_RADIUS_FOLLOW
        prefsManager.saveAppThemeStyle(appThemeStyle)
        prefsManager.saveAppThemeMode(appThemeMode)
        prefsManager.saveModernThemeColorStyle(modernThemeColorStyle)
        prefsManager.saveCleanListFeatureIconStyle(cleanListFeatureIconStyle)
        prefsManager.saveThemeColor(themeColor)
        prefsManager.saveThemeBackgroundColor(themeBackgroundColor)
        prefsManager.saveGlobalCornerRadiusDp(globalCornerRadiusDp)
        prefsManager.saveHomeCornerRadiusDp(homeCornerRadiusDp)
        AppIconManager.apply(context, appIcon)
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
            labels.map { it.replace("\\", "/").trim() }.filter { it.isNotBlank() && it != "." }.distinct().sorted()
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
                .filter { it.isNotBlank() && it != "." }
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

    fun exportDiagnosticLog() {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            val now = Date()
            val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
            val displayTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now)
            val logText = buildString {
                appendLine("KardLeaf 诊断日志")
                appendLine("生成时间：$displayTime")
                appendLine("应用版本：${packageInfo.versionName} ($versionCode)")
                appendLine("包名：${context.packageName}")
                appendLine("Android：${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
                appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine()
                appendLine("详细日志：当前正式版未启用")
                appendLine("说明：此文件不包含笔记正文、文件名或笔记路径。")
                appendLine("如遇问题，请联系开发者获取诊断版后再次导出。")
            }
            val shareDir = File(context.cacheDir, "shared_notes").apply { mkdirs() }
            val logFile = File(shareDir, "kardleaf_diagnostic_$fileTimestamp.txt")
            logFile.writeText(logText, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "KardLeaf 诊断日志")
                putExtra(Intent.EXTRA_TEXT, "KardLeaf 诊断日志")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "导出诊断日志"))
        } catch (e: Exception) {
            Toast.makeText(context, "导出诊断日志失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun resetSettings() {
        val oldThemeStyle = prefsManager.getAppThemeStyle()
        val oldThemeMode = prefsManager.getAppThemeMode()
        val oldThemeColor = prefsManager.getThemeColor()
        val oldThemeBackgroundColor = prefsManager.getThemeBackgroundColor()
        val oldCleanListFeatureIconStyle = prefsManager.getCleanListFeatureIconStyle()
        val oldGlobalCornerRadiusDp = prefsManager.getGlobalCornerRadiusDp()
        val oldHomeCornerRadiusDp = prefsManager.getHomeCornerRadiusDp()
        dateFormat = KardLeafCustomFeatures.DefaultUnnamedNoteDateFormat
        autoFileNameTemplateFieldValue = TextFieldValue(KardLeafCustomFeatures.DefaultUnnamedNoteFileNameTemplate)
        openNoteMode = KardLeafCustomFeatures.DefaultOpenNoteMode
        editorKernel = PrefsManager.EditorKernel.NATIVE
        autoCodeMirrorThresholdText = PrefsManager.DEFAULT_AUTO_CODEMIRROR_THRESHOLD_CHARS.toString()
        codeMirrorLivePreviewEnabled = PrefsManager.DEFAULT_CODEMIRROR_LIVE_PREVIEW_ENABLED
        editorFontSizeSp = PrefsManager.DEFAULT_EDITOR_FONT_SIZE_SP
        editorLineHeightMultiplier = PrefsManager.DEFAULT_EDITOR_LINE_HEIGHT_MULTIPLIER
        editorLetterSpacingSp = PrefsManager.DEFAULT_EDITOR_LETTER_SPACING_SP
        editorParagraphSpacingDp = PrefsManager.DEFAULT_EDITOR_PARAGRAPH_SPACING_DP
        editorFontFamily = PrefsManager.DEFAULT_EDITOR_FONT_FAMILY
        customEditorFontFamilyText = ""
        appLanguage = PrefsManager.DEFAULT_APP_LANGUAGE
        editorBottomToolbarAlwaysVisible = PrefsManager.DEFAULT_EDITOR_BOTTOM_TOOLBAR_ALWAYS_VISIBLE
        homeActionStyle = PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR
        homeBottomToolbarOrder = PrefsManager.HomeBottomToolbarItemId.DEFAULT_ORDER
        homeBottomToolbarHiddenItems = PrefsManager.HomeBottomToolbarItemId.DEFAULT_HIDDEN_ITEMS
        trashFolderName = PrefsManager.DEFAULT_TRASH_FOLDER_NAME
        trashSortOrder = PrefsManager.TrashSortOrder.DELETED_TIME
        viewMode = PrefsManager.ViewMode.LIST
        sortOrder = PrefsManager.SortOrder.DATE_MODIFIED
        sortDirection = PrefsManager.SortDirection.DESCENDING
        imageFolder = PrefsManager.DEFAULT_IMAGE_FOLDER
        relativeImageLocation = PrefsManager.RelativeImageLocation.CURRENT_NOTE_FOLDER
        appThemeStyle = PrefsManager.AppThemeStyle.CLEAN_LIST
        appThemeMode = PrefsManager.AppThemeMode.SYSTEM
        modernThemeColorStyle = PrefsManager.ModernThemeColorStyle.CLASSIC
        cleanListFeatureIconStyle = PrefsManager.CleanListFeatureIconStyle.MODERN
        appIcon = AppIconManager.AppIcon.DEFAULT
        themeColor = PrefsManager.ThemeColor.BLUE
        themeBackgroundColor = PrefsManager.ThemeBackgroundColor.WHITE
        globalCornerRadiusDp = PrefsManager.THEME_CORNER_RADIUS_FOLLOW
        homeCornerRadiusDp = PrefsManager.THEME_CORNER_RADIUS_FOLLOW
        drawerEdgeWidthText = PrefsManager.DEFAULT_DRAWER_EDGE_WIDTH_DP.toString()
        drawerStyle = PrefsManager.DrawerStyle.DEFAULT
        noteSidePanelsEnabled = PrefsManager.DEFAULT_NOTE_SIDE_PANELS_ENABLED
        noteSidePanelOpenMode = PrefsManager.DEFAULT_NOTE_SIDE_PANEL_OPEN_MODE
        showYamlTagsOnLooseCards = false
        showModifiedDateOnCards = false
        cardModifiedDateFormat = PrefsManager.DEFAULT_CARD_MODIFIED_DATE_FORMAT
        showNoteTitleOnCards = true
        showDateFilenameTitleOnCards = true
        showNoteDetailTitle = true
        showNoteDetailFileInfo = false
        customHiddenFilenamePatterns = prefsManager.defaultHiddenFilenamePatterns()
        customHiddenFilenameText = customHiddenFilenamePatterns.joinToString("\n")
        historyLimitText = PrefsManager.DEFAULT_HISTORY_VERSION_LIMIT.toString()
        cardDensity = PrefsManager.CardDensity.LOOSE
        toolbarOrder = KardLeafCustomFeatures.DefaultToolbarOrder
        selectionToolbarOrder = PrefsManager.SelectionToolbarItemId.DEFAULT_ORDER
        selectionToolbarMoreItems = PrefsManager.SelectionToolbarItemId.DEFAULT_MORE_ITEMS
        selectionToolbarHiddenItems = PrefsManager.SelectionToolbarItemId.DEFAULT_HIDDEN_ITEMS
        editorTopToolbarOrder = PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER
        editorTopToolbarMoreItems = PrefsManager.EditorTopToolbarItemId.DEFAULT_MORE_ITEMS
        editorTopToolbarHiddenItems = PrefsManager.EditorTopToolbarItemId.DEFAULT_HIDDEN_ITEMS
        homeBottomToolbarButtonSizeDp = PrefsManager.DEFAULT_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP
        restoreLastFilter = true
        defaultStartLabel = ""
        KardLeafCustomFeatures.saveUnnamedNoteDateFormat(context, dateFormat)
        KardLeafCustomFeatures.saveUnnamedNoteFileNameTemplate(context, autoFileNameTemplate)
        KardLeafCustomFeatures.saveOpenNoteMode(context, openNoteMode)
        prefsManager.saveEditorKernel(editorKernel)
        prefsManager.saveAutoCodeMirrorThresholdChars(PrefsManager.DEFAULT_AUTO_CODEMIRROR_THRESHOLD_CHARS)
        prefsManager.saveCodeMirrorLivePreviewEnabled(codeMirrorLivePreviewEnabled)
        prefsManager.saveEditorFontSizeSp(editorFontSizeSp)
        prefsManager.saveEditorLineHeightMultiplier(editorLineHeightMultiplier)
        prefsManager.saveEditorLetterSpacingSp(editorLetterSpacingSp)
        prefsManager.saveEditorParagraphSpacingDp(editorParagraphSpacingDp)
        prefsManager.saveEditorFontFamily(editorFontFamily)
        prefsManager.saveAppLanguage(appLanguage)
        prefsManager.saveEditorBottomToolbarAlwaysVisible(editorBottomToolbarAlwaysVisible)
        prefsManager.saveHomeActionStyle(homeActionStyle)
        prefsManager.saveHomeBottomToolbarItemOrder(homeBottomToolbarOrder)
        prefsManager.saveHomeBottomToolbarHiddenItems(homeBottomToolbarHiddenItems)
        KardLeafCustomFeatures.saveToolbarOrder(context, toolbarOrder)
        prefsManager.saveSelectionToolbarItemOrder(selectionToolbarOrder)
        prefsManager.saveSelectionToolbarMoreItems(selectionToolbarMoreItems)
        prefsManager.saveSelectionToolbarHiddenItems(selectionToolbarHiddenItems)
        prefsManager.saveEditorTopToolbarItemOrder(editorTopToolbarOrder)
        prefsManager.saveEditorTopToolbarMoreItems(editorTopToolbarMoreItems)
        prefsManager.saveEditorTopToolbarHiddenItems(editorTopToolbarHiddenItems)
        prefsManager.saveTrashFolderName(trashFolderName)
        prefsManager.saveTrashSortOrder(trashSortOrder)
        prefsManager.saveViewMode(viewMode)
        prefsManager.saveSortOrder(sortOrder)
        prefsManager.saveSortDirection(sortDirection)
        prefsManager.saveImageFolder(imageFolder)
        prefsManager.saveImageFolderUri(null)
        prefsManager.saveRelativeImageLocation(relativeImageLocation)
        prefsManager.saveAppThemeStyle(appThemeStyle)
        prefsManager.saveAppThemeMode(appThemeMode)
        prefsManager.saveModernThemeColorStyle(modernThemeColorStyle)
        prefsManager.saveCleanListFeatureIconStyle(cleanListFeatureIconStyle)
        prefsManager.saveThemeColor(themeColor)
        prefsManager.saveThemeBackgroundColor(themeBackgroundColor)
        prefsManager.saveGlobalCornerRadiusDp(globalCornerRadiusDp)
        prefsManager.saveHomeCornerRadiusDp(homeCornerRadiusDp)
        AppIconManager.apply(context, appIcon)
        prefsManager.saveDrawerEdgeWidthDp(PrefsManager.DEFAULT_DRAWER_EDGE_WIDTH_DP)
        prefsManager.saveDrawerStyle(drawerStyle)
        prefsManager.saveNoteSidePanelsEnabled(noteSidePanelsEnabled)
        prefsManager.saveNoteSidePanelOpenMode(noteSidePanelOpenMode)
        prefsManager.saveLooseCardYamlTagsVisible(showYamlTagsOnLooseCards)
        prefsManager.saveModifiedDateOnCardsVisible(showModifiedDateOnCards)
        prefsManager.saveCardModifiedDateFormat(cardModifiedDateFormat)
        prefsManager.saveNoteTitleOnCardsVisible(showNoteTitleOnCards)
        prefsManager.saveDateFilenameTitleOnCardsVisible(showDateFilenameTitleOnCards)
        prefsManager.saveNoteDetailTitleVisible(showNoteDetailTitle)
        prefsManager.saveNoteDetailFileInfoVisible(showNoteDetailFileInfo)
        prefsManager.saveCustomHiddenFilenamePatterns(customHiddenFilenamePatterns)
        prefsManager.saveHistoryVersionLimit(PrefsManager.DEFAULT_HISTORY_VERSION_LIMIT)
        doubleTapIntervalText = PrefsManager.DEFAULT_PREVIEW_DOUBLE_TAP_INTERVAL_MS.toString()
        prefsManager.savePreviewDoubleTapIntervalMs(PrefsManager.DEFAULT_PREVIEW_DOUBLE_TAP_INTERVAL_MS)
        trashAutoCleanDaysText = PrefsManager.DEFAULT_TRASH_AUTO_CLEAN_DAYS.toString()
        prefsManager.saveTrashAutoCleanDays(PrefsManager.DEFAULT_TRASH_AUTO_CLEAN_DAYS)
        passwordInputMode = PrefsManager.PasswordInputMode.SIMPLE
        prefsManager.savePasswordInputMode(passwordInputMode)
        prefsManager.saveCardDensity(cardDensity)
        prefsManager.saveHomeBottomToolbarButtonSizeDp(homeBottomToolbarButtonSizeDp)
        prefsManager.saveRestoreLastFilterEnabled(restoreLastFilter)
        prefsManager.saveDefaultStartLabel(defaultStartLabel)
        onSettingsChanged()
        if (
            oldThemeStyle != appThemeStyle ||
            oldThemeMode != appThemeMode ||
            oldThemeColor != themeColor ||
            oldThemeBackgroundColor != themeBackgroundColor ||
            oldCleanListFeatureIconStyle != cleanListFeatureIconStyle ||
            oldGlobalCornerRadiusDp != globalCornerRadiusDp ||
            oldHomeCornerRadiusDp != homeCornerRadiusDp
        ) {
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

    if (showDisableHistoryConfirmDialog) {
        AlertDialog(
            onDismissRequest = { cancelDisableHistoryVersions() },
            title = { Text("关闭历史版本记录？") },
            text = { Text("历史版本数量设置为 0 后，将不再自动保存新的历史版本；已有历史版本不会立即删除。") },
            confirmButton = {
                TextButton(onClick = { confirmDisableHistoryVersions() }) {
                    Text("关闭记录")
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelDisableHistoryVersions() }) {
                    Text("取消")
                }
            },
        )
    }


    if (showVaultPathDialog) {
        AlertDialog(
            onDismissRequest = { showVaultPathDialog = false },
            title = { Text("当前笔记库") },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "当前路径",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = displayRootPath(prefsManager.getRootUri()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = {
                            showVaultPathDialog = false
                            onSelectDatabase()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = "选择新的笔记库",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVaultPathDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }

    if (showCustomThemeColorDialog) {
        ThemeColorPickerDialog(
            title = "自定义强调色",
            presets = ThemeCustomAccentColorPalette,
            selectedArgb = customThemeColorArgb,
            onApply = { argb ->
                applyCustomThemeColor(argb)
                showCustomThemeColorDialog = false
            },
            onDismiss = { showCustomThemeColorDialog = false },
        )
    }

    if (showCustomThemeBackgroundColorDialog) {
        ThemeColorPickerDialog(
            title = "自定义背景色",
            presets = ThemeCustomBackgroundColorPalette,
            selectedArgb = customThemeBackgroundColorArgb,
            onApply = { argb ->
                applyCustomThemeBackgroundColor(argb)
                showCustomThemeBackgroundColorDialog = false
            },
            onDismiss = { showCustomThemeBackgroundColorDialog = false },
        )
    }

    val dialogPage = settingsDialog
    if (dialogPage != null && !showDisableHistoryConfirmDialog) {
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
                        "hiddenFilenames" -> {
                            SettingsSectionTitle("自定义隐藏文件名")
                            OutlinedTextField(
                                value = customHiddenFilenameText,
                                onValueChange = { value ->
                                    customHiddenFilenameText = value
                                    customHiddenFilenamePatterns = value
                                        .lineSequence()
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                        .distinct()
                                        .toList()
                                    prefsManager.saveCustomHiddenFilenamePatterns(customHiddenFilenamePatterns)
                                    onSettingsChanged()
                                },
                                label = { Text("每行一个文件名或日期格式") },
                                minLines = 3,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        "sort" -> {
                            SettingsSectionTitle("排序字段")
                            PrefsManager.SortOrder.values().filter { it != PrefsManager.SortOrder.CUSTOM }.forEach { order ->
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
                        "homeActionStyle" -> PrefsManager.HomeActionStyle.values().forEach { style ->
                            SettingsChoiceRow(
                                icon = if (style == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR) Icons.Outlined.ViewHeadline else Icons.Outlined.Add,
                                title = if (style == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR) "底部工具栏" else "简约新建按钮",
                                subtitle = if (style == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR) "底部显示可自定义图标入口" else "保留右下角圆形新建按钮",
                                selected = homeActionStyle == style,
                                onClick = {
                                    homeActionStyle = style
                                    prefsManager.saveHomeActionStyle(style)
                                    onSettingsChanged()
                                    settingsDialog = null
                                },
                            )
                        }
                        "drawerStyle" -> {
                            SettingsPageText("这里只切换侧边栏样式和布局，不切换应用主题；切换后仍然跟随当前主题色。")
                            PrefsManager.DrawerStyle.values().forEach { style ->
                                SettingsChoiceRow(
                                    icon = drawerStyleIcon(style),
                                    title = drawerStyleLabel(style),
                                    subtitle = drawerStyleSubtitle(style),
                                    selected = drawerStyle == style,
                                    onClick = {
                                        drawerStyle = style
                                        prefsManager.saveDrawerStyle(style)
                                        onSettingsChanged()
                                        settingsDialog = null
                                    },
                                )
                            }
                        }
                        "editorKernel" -> PrefsManager.EditorKernel.values()
                            .filter { it != PrefsManager.EditorKernel.AUTO }
                            .forEach { kernel ->
                            SettingsChoiceRow(
                                icon = KardLeafCustomFeatures.editorKernelIcon(kernel),
                                title = KardLeafCustomFeatures.editorKernelTitle(kernel),
                                subtitle = KardLeafCustomFeatures.editorKernelSubtitle(kernel),
                                selected = editorKernel == kernel,
                                onClick = {
                                    editorKernel = kernel
                                    prefsManager.saveEditorKernel(kernel)
                                    onSettingsChanged()
                                    settingsDialog = null
                                },
                            )
                        }
                        "editorTypography" -> {
                            SettingsSectionTitle(settingsText(settingsEnglish, "排版", "Typography"))
                            SettingsValueSlider(
                                title = settingsText(settingsEnglish, "字体大小", "Font size"),
                                valueText = "${editorFontSizeSp.roundToInt()}sp",
                                value = editorFontSizeSp,
                                valueRange = PrefsManager.MIN_EDITOR_FONT_SIZE_SP..PrefsManager.MAX_EDITOR_FONT_SIZE_SP,
                                onValueChange = {
                                    editorFontSizeSp = it
                                    prefsManager.saveEditorFontSizeSp(it)
                                    onSettingsChanged()
                                },
                            )
                            SettingsValueSlider(
                                title = settingsText(settingsEnglish, "行高", "Line height"),
                                valueText = "${(editorLineHeightMultiplier * 100).roundToInt()}%",
                                value = editorLineHeightMultiplier,
                                valueRange = PrefsManager.MIN_EDITOR_LINE_HEIGHT_MULTIPLIER..PrefsManager.MAX_EDITOR_LINE_HEIGHT_MULTIPLIER,
                                onValueChange = {
                                    editorLineHeightMultiplier = it
                                    prefsManager.saveEditorLineHeightMultiplier(it)
                                    onSettingsChanged()
                                },
                            )
                            SettingsValueSlider(
                                title = settingsText(settingsEnglish, "字间距", "Letter spacing"),
                                valueText = String.format(Locale.ROOT, "%.1fsp", editorLetterSpacingSp),
                                value = editorLetterSpacingSp,
                                valueRange = PrefsManager.MIN_EDITOR_LETTER_SPACING_SP..PrefsManager.MAX_EDITOR_LETTER_SPACING_SP,
                                onValueChange = {
                                    editorLetterSpacingSp = it
                                    prefsManager.saveEditorLetterSpacingSp(it)
                                    onSettingsChanged()
                                },
                            )
                            SettingsValueSlider(
                                title = settingsText(settingsEnglish, "段落间距", "Paragraph spacing"),
                                valueText = "${editorParagraphSpacingDp.roundToInt()}dp",
                                value = editorParagraphSpacingDp,
                                valueRange = PrefsManager.MIN_EDITOR_PARAGRAPH_SPACING_DP..PrefsManager.MAX_EDITOR_PARAGRAPH_SPACING_DP,
                                onValueChange = {
                                    editorParagraphSpacingDp = it
                                    prefsManager.saveEditorParagraphSpacingDp(it)
                                    onSettingsChanged()
                                },
                            )
                            SettingsSectionDivider()
                            SettingsSectionTitle(settingsText(settingsEnglish, "字体样式", "Font family"))
                            EditorBuiltinFontFamilies.forEach { font ->
                                SettingsChoiceRow(
                                    icon = Icons.Outlined.FontDownload,
                                    title = editorFontLabel(font, settingsEnglish),
                                    subtitle = editorFontSubtitle(font, settingsEnglish),
                                    selected = editorFontFamily == font.value,
                                    onClick = {
                                        editorFontFamily = font.value
                                        customEditorFontFamilyText = ""
                                        prefsManager.saveEditorFontFamily(font.value)
                                        onSettingsChanged()
                                    },
                                )
                            }
                            OutlinedTextField(
                                value = customEditorFontFamilyText,
                                onValueChange = { value ->
                                    customEditorFontFamilyText = value
                                    val fontFamily = value.trim()
                                    if (fontFamily.isNotBlank()) {
                                        editorFontFamily = fontFamily
                                        prefsManager.saveEditorFontFamily(fontFamily)
                                        onSettingsChanged()
                                    }
                                },
                                label = { Text(settingsText(settingsEnglish, "自定义字体族", "Custom font family")) },
                                placeholder = { Text(settingsText(settingsEnglish, "例如 Noto Sans CJK SC", "Example: Noto Sans CJK SC")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        "appLanguage" -> AppLanguageOptions.forEach { option ->
                            SettingsChoiceRow(
                                icon = Icons.Outlined.Language,
                                title = option.label,
                                subtitle = option.subtitle,
                                selected = appLanguage == option.value,
                                onClick = {
                                    appLanguage = option.value
                                    prefsManager.saveAppLanguage(option.value)
                                    settingsDialog = null
                                    (context as? android.app.Activity)?.recreate()
                                },
                            )
                        }
                        "autoCodeMirrorThreshold" -> {
                            SettingsSectionTitle("自动切换字数")
                            OutlinedTextField(
                                value = autoCodeMirrorThresholdText,
                                onValueChange = { value ->
                                    autoCodeMirrorThresholdText = value.filter(Char::isDigit).take(7)
                                    val chars = autoCodeMirrorThresholdText.trim().toIntOrNull()
                                    if (chars != null &&
                                        chars in PrefsManager.MIN_AUTO_CODEMIRROR_THRESHOLD_CHARS..PrefsManager.MAX_AUTO_CODEMIRROR_THRESHOLD_CHARS
                                    ) {
                                        prefsManager.saveAutoCodeMirrorThresholdChars(chars)
                                        onSettingsChanged()
                                    }
                                },
                                label = { Text("超过多少字切换 CodeMirror") },
                                singleLine = true,
                                isError = !isAutoCodeMirrorThresholdValid,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SettingsPageText(
                                "范围：${PrefsManager.MIN_AUTO_CODEMIRROR_THRESHOLD_CHARS}-${PrefsManager.MAX_AUTO_CODEMIRROR_THRESHOLD_CHARS} 字；默认 ${PrefsManager.DEFAULT_AUTO_CODEMIRROR_THRESHOLD_CHARS} 字，5 万字会继续使用原生编辑器",
                            )
                        }
                        "sidePanelOpenMode" -> PrefsManager.NoteSidePanelOpenMode.values().forEach { mode ->
                            SettingsChoiceRow(
                                icon = if (mode == PrefsManager.NoteSidePanelOpenMode.GESTURE) Icons.Outlined.Swipe else Icons.Outlined.ViewHeadline,
                                title = if (mode == PrefsManager.NoteSidePanelOpenMode.GESTURE) "手势划出" else "顶部工具栏弹出",
                                subtitle = if (mode == PrefsManager.NoteSidePanelOpenMode.GESTURE) "左右滑动打开目录和属性备注" else "用顶部按钮打开，禁用左右划出",
                                selected = noteSidePanelOpenMode == mode,
                                onClick = {
                                    noteSidePanelOpenMode = mode
                                    prefsManager.saveNoteSidePanelOpenMode(mode)
                                    onSettingsChanged()
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
                                        applyHistoryVersionLimitInput(limit)
                                    }
                                },
                                label = { Text("每篇笔记保留数量") },
                                singleLine = true,
                                isError = !isHistoryLimitValid,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SettingsPageText("范围：${PrefsManager.MIN_HISTORY_VERSION_LIMIT}-${PrefsManager.MAX_HISTORY_VERSION_LIMIT}，0 表示关闭历史版本记录")
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
                        "autoFileName" -> {
                            SettingsSectionTitle("自动文件名")
                            AutoFileNameTemplateField()
                            SettingsPageText(
                                when {
                                    !isAutoFileNameTemplateValid -> "自动文件名模板无效"
                                    normalizedAutoFileNameTemplate.isBlank() -> "留空：使用日期格式，示例：$autoFileNameSample"
                                    else -> "示例：$autoFileNameSample"
                                },
                            )
                            SettingsPageText("可用写法：笔记{1}、yyyy.MM.dd 笔记{1}。{1} 会按当前目录已有文件名自动递增。")
                        }
                        "cardModifiedDateFormat" -> {
                            SettingsSectionTitle("修改日期显示格式")
                            OutlinedTextField(
                                value = cardModifiedDateFormat,
                                onValueChange = { value ->
                                    cardModifiedDateFormat = value
                                    val n = value.trim()
                                    if (prefsManager.isDateFormatUsable(n)) {
                                        prefsManager.saveCardModifiedDateFormat(n)
                                        onSettingsChanged()
                                    }
                                },
                                label = { Text("日期格式") },
                                singleLine = true,
                                isError = !isCardModifiedDateFormatValid,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SettingsPageText(if (isCardModifiedDateFormatValid) "示例：$cardModifiedDateSample" else "日期格式无效")
                            SettingsPageText("默认：${PrefsManager.DEFAULT_CARD_MODIFIED_DATE_FORMAT}")
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
            showDisableHistoryConfirmDialog -> cancelDisableHistoryVersions()
            settingsDialog != null -> settingsDialog = null
            settingsPage == "main" -> onBack()
            else -> returnToSettingsMain()
        }
    }

    val currentThemeStyle = LocalKardLeafThemeStyle.current
    val isCleanListSettings = currentThemeStyle == PrefsManager.AppThemeStyle.CLEAN_LIST
    val isModernSettings = currentThemeStyle != PrefsManager.AppThemeStyle.CLASSIC

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (isModernSettings) {
                    MaterialTheme.colorScheme.background
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            if (isCleanListSettings) {
                CenterAlignedTopAppBar(
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
                    colors = colors,
                )
            } else {
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
                    colors = colors,
                )
            }
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
                    if (isModernSettings) {
                        (
                            fadeIn(animationSpec = tween(260)) +
                                scaleIn(initialScale = 0.96f, animationSpec = tween(260))
                        ) togetherWith (
                            fadeOut(animationSpec = tween(170)) +
                                scaleOut(targetScale = 0.98f, animationSpec = tween(170))
                        )
                    } else if (targetState == "main") {
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
                    PrefsManager.SortOrder.values().filter { it != PrefsManager.SortOrder.CUSTOM }.forEach { order ->
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
                        themeStyle = appThemeStyle,
                        themeMode = appThemeMode,
                        accentColor = themeColor,
                        backgroundColor = themeBackgroundColor,
                        modernColorStyle = modernThemeColorStyle,
                        customAccentColor = Color(customThemeColorArgb),
                        customBackgroundColor = Color(customThemeBackgroundColorArgb),
                    )
                    SettingsSectionTitle("应用图标", "选择后桌面图标会刷新，部分桌面可能需要几秒")
                    AppIconChoiceGrid(
                        selectedIcon = appIcon,
                        onIconClick = { icon ->
                            appIcon = icon
                            AppIconManager.apply(context, icon)
                        },
                    )
                    SettingsSectionDivider()
                    SettingsSectionTitle(
                        text = "黑夜模式",
                        subtitle = "选择跟随系统、固定浅色或固定深色",
                    )
                    ThemeModeChoiceGrid(
                        selectedMode = appThemeMode,
                        onModeClick = { applyThemeMode(it) },
                    )
                    SettingsSectionDivider()
                    SettingsSectionTitle(
                        text = "主题风格",
                        subtitle = "经典主题、圆润主题、清爽列表、极夜主题和霓彩主题使用不同组件形态",
                    )
                    ThemeStyleChoiceGrid(
                        styles = PrefsManager.AppThemeStyle.values()
                            .filter { it != PrefsManager.AppThemeStyle.NOW_IN_ANDROID },
                        selectedStyle = appThemeStyle,
                        onStyleClick = { applyThemeStyle(it) },
                    )
                    if (appThemeStyle == PrefsManager.AppThemeStyle.MODERN) {
                        SettingsSectionDivider()
                        SettingsSectionTitle(
                            text = "色彩",
                            subtitle = "经典保留当前圆润主题色彩；现代使用 Material3 色彩体系",
                        )
                        ModernThemeColorStyleChoiceGrid(
                            selectedStyle = modernThemeColorStyle,
                            onStyleClick = { applyModernThemeColorStyle(it) },
                        )
                    }
                    if (appThemeStyle == PrefsManager.AppThemeStyle.CLEAN_LIST) {
                        SettingsSectionDivider()
                        SettingsSectionTitle(
                            text = "功能项图标",
                            subtitle = "现代保留彩色功能图标；简约跟随当前强调色",
                        )
                        CleanListFeatureIconStyleChoiceGrid(
                            selectedStyle = cleanListFeatureIconStyle,
                            onStyleClick = { applyCleanListFeatureIconStyle(it) },
                        )
                    }
                    SettingsSectionDivider()
                    SettingsSectionTitle(
                        text = "推荐配色",
                        subtitle = "一眼看强调色、背景色和卡片层次",
                    )
                    RecommendedThemePaletteGrid(
                        selectedAccentColor = themeColor,
                        selectedBackgroundColor = themeBackgroundColor,
                        onPaletteClick = { accent, background -> applyRecommendedThemePalette(accent, background) },
                    )
                    SettingsSectionDivider()
                    SettingsSectionTitle(
                        text = "强调色",
                        subtitle = "预设色块或打开颜料盘自由调色",
                    )
                    ThemeColorPaletteGrid(
                        colors = PrefsManager.ThemeColor.values().toList(),
                        selectedColor = themeColor,
                        customColor = Color(customThemeColorArgb),
                        onColorClick = { color ->
                            if (color == PrefsManager.ThemeColor.CUSTOM) {
                                customThemeColorText = argbToThemeHex(customThemeColorArgb)
                                showCustomThemeColorDialog = true
                            } else {
                                applyThemeColor(color)
                            }
                        },
                    )
                    SettingsSectionDivider()
                    SettingsSectionTitle(
                        text = "背景色",
                        subtitle = "影响页面、卡片和弹窗底色",
                    )
                    ThemeBackgroundPaletteGrid(
                        colors = PrefsManager.ThemeBackgroundColor.values().toList(),
                        selectedColor = themeBackgroundColor,
                        customColor = Color(customThemeBackgroundColorArgb),
                        onColorClick = { color ->
                            if (color == PrefsManager.ThemeBackgroundColor.CUSTOM) {
                                customThemeBackgroundColorText = argbToThemeHex(customThemeBackgroundColorArgb)
                                showCustomThemeBackgroundColorDialog = true
                            } else {
                                applyThemeBackgroundColor(color)
                            }
                        },
                    )
                    SettingsSectionDivider()
                    SettingsSectionTitle(
                        text = "圆角",
                        subtitle = "全局圆角影响主题组件；首页圆角可单独覆盖笔记卡片和首页底部工具栏",
                    )
                    CornerRadiusPaletteGrid(
                        title = "全局圆角",
                        values = ThemeCornerRadiusOptions,
                        selected = globalCornerRadiusDp,
                        label = ::globalCornerRadiusLabel,
                        onClick = { applyGlobalCornerRadiusDp(it) },
                    )
                    CornerRadiusPaletteGrid(
                        title = "首页圆角",
                        values = ThemeCornerRadiusOptions,
                        selected = homeCornerRadiusDp,
                        label = ::homeCornerRadiusLabel,
                        onClick = { applyHomeCornerRadiusDp(it) },
                    )
                    SettingsSectionDivider()
                    SettingsActionRow(
                        icon = Icons.Outlined.Restore,
                        title = "恢复默认主题",
                        subtitle = "跟随系统 + 清爽列表 + 蓝色强调色 + 白色背景",
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
                "autoFileName" -> {
                    SettingsSectionTitle("自动文件名")
                    AutoFileNameTemplateField()
                    SettingsPageText(
                        when {
                            !isAutoFileNameTemplateValid -> "自动文件名模板无效"
                            normalizedAutoFileNameTemplate.isBlank() -> "留空：使用日期格式，示例：$autoFileNameSample"
                            else -> "示例：$autoFileNameSample"
                        },
                    )
                    SettingsPageText("可用写法：笔记{1}、yyyy.MM.dd 笔记{1}。{1} 会按当前目录已有文件名自动递增。")
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
                                applyHistoryVersionLimitInput(limit)
                            }
                        },
                        label = { Text("每篇笔记保留数量") },
                        singleLine = true,
                        isError = !isHistoryLimitValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsPageText(
                        "范围：${PrefsManager.MIN_HISTORY_VERSION_LIMIT}-${PrefsManager.MAX_HISTORY_VERSION_LIMIT}，0 表示关闭历史版本记录",
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
                "editorTopToolbar" -> {
                    val availableItems = remember(noteSidePanelOpenMode, noteSidePanelsEnabled) {
                        editorTopToolbarAvailableItems(noteSidePanelsEnabled, noteSidePanelOpenMode)
                    }
                    var itemOrder by remember(settingsPage, editorTopToolbarOrder, noteSidePanelOpenMode, noteSidePanelsEnabled) {
                        mutableStateOf(normalizeEditorTopToolbarOrder(prefsManager.getEditorTopToolbarItemOrder(), availableItems))
                    }
                    var moreItems by remember(settingsPage, editorTopToolbarMoreItems, noteSidePanelOpenMode, noteSidePanelsEnabled) {
                        mutableStateOf(prefsManager.getEditorTopToolbarMoreItems().filter { it in availableItems && it != PrefsManager.EditorTopToolbarItemId.MORE }.toSet())
                    }
                    var hiddenItems by remember(settingsPage, editorTopToolbarHiddenItems, noteSidePanelOpenMode, noteSidePanelsEnabled) {
                        mutableStateOf(prefsManager.getEditorTopToolbarHiddenItems().filter { it in availableItems && it != PrefsManager.EditorTopToolbarItemId.MORE }.toSet())
                    }

                    fun saveEditorTopToolbarState(
                        newOrder: List<PrefsManager.EditorTopToolbarItemId> = itemOrder,
                        newMoreItems: Set<PrefsManager.EditorTopToolbarItemId> = moreItems,
                        newHiddenItems: Set<PrefsManager.EditorTopToolbarItemId> = hiddenItems,
                    ) {
                        val safeOrder = normalizeEditorTopToolbarOrder(newOrder, availableItems)
                        val safeHiddenItems = newHiddenItems.filter { it in safeOrder && it != PrefsManager.EditorTopToolbarItemId.MORE }.toSet()
                        val safeMoreItems = newMoreItems
                            .filter { it in safeOrder && it != PrefsManager.EditorTopToolbarItemId.MORE && it !in safeHiddenItems }
                            .toSet()
                        val unavailableItems = PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER.filter { it !in availableItems }
                        val fullOrder = (safeOrder + unavailableItems).distinct()
                        itemOrder = safeOrder
                        moreItems = safeMoreItems
                        hiddenItems = safeHiddenItems
                        editorTopToolbarOrder = fullOrder
                        editorTopToolbarMoreItems = safeMoreItems
                        editorTopToolbarHiddenItems = safeHiddenItems
                        prefsManager.saveEditorTopToolbarItemOrder(fullOrder)
                        prefsManager.saveEditorTopToolbarMoreItems(safeMoreItems)
                        prefsManager.saveEditorTopToolbarHiddenItems(safeHiddenItems)
                        onSettingsChanged()
                    }

                    fun saveEditorTopToolbarSections(
                        topItems: List<PrefsManager.EditorTopToolbarItemId>,
                        moreDisplayItems: List<PrefsManager.EditorTopToolbarItemId>,
                        hiddenDisplayItems: List<PrefsManager.EditorTopToolbarItemId>,
                    ) {
                        saveEditorTopToolbarState(topItems + moreDisplayItems + hiddenDisplayItems, moreItems, hiddenItems)
                    }

                    val topItems = itemOrder.filter { it !in moreItems && it !in hiddenItems }
                    val moreDisplayItems = itemOrder.filter { it in moreItems && it !in hiddenItems }
                    val hiddenDisplayItems = itemOrder.filter { it in hiddenItems }

                    SettingsPageText("长按拖动调整顺序，按钮可以放在顶部、更多或隐藏")
                    if (noteSidePanelsEnabled && noteSidePanelOpenMode == PrefsManager.NoteSidePanelOpenMode.GESTURE) {
                        SettingsPageText("当前为手势划出侧滑面板，大纲和属性备注不会显示在顶部栏设置里")
                    }
                    SettingsSectionTitle("顶部展示")
                    if (topItems.isEmpty()) {
                        SettingsPageText("暂无顶部按钮")
                    } else {
                        SettingsEditorTopToolbarDragList(
                            items = topItems,
                            moreItems = moreItems,
                            hiddenItems = hiddenItems,
                            onOrderChange = { newTopItems ->
                                saveEditorTopToolbarSections(newTopItems, moreDisplayItems, hiddenDisplayItems)
                            },
                            onToggleArea = { itemId ->
                                val newMoreItems = moreItems + itemId
                                val newTopItems = itemOrder.filter { it !in newMoreItems && it !in hiddenItems }
                                val newMoreDisplayItems = itemOrder.filter { it in newMoreItems && it !in hiddenItems }
                                saveEditorTopToolbarState(newTopItems + newMoreDisplayItems + hiddenDisplayItems, newMoreItems, hiddenItems)
                            },
                            onToggleHidden = { itemId ->
                                val newHiddenItems = hiddenItems + itemId
                                val newMoreItems = moreItems - itemId
                                val newTopItems = itemOrder.filter { it !in newMoreItems && it !in newHiddenItems }
                                val newMoreDisplayItems = itemOrder.filter { it in newMoreItems && it !in newHiddenItems }
                                val newHiddenDisplayItems = itemOrder.filter { it in newHiddenItems }
                                saveEditorTopToolbarState(newTopItems + newMoreDisplayItems + newHiddenDisplayItems, newMoreItems, newHiddenItems)
                            },
                        )
                    }

                    SettingsSectionDivider()
                    SettingsSectionTitle("更多选项展示")
                    if (moreDisplayItems.isEmpty()) {
                        SettingsPageText("暂无更多选项")
                    } else {
                        SettingsEditorTopToolbarDragList(
                            items = moreDisplayItems,
                            moreItems = moreItems,
                            hiddenItems = hiddenItems,
                            onOrderChange = { newMoreDisplayItems ->
                                saveEditorTopToolbarSections(topItems, newMoreDisplayItems, hiddenDisplayItems)
                            },
                            onToggleArea = { itemId ->
                                val newMoreItems = moreItems - itemId
                                val newTopItems = itemOrder.filter { it !in newMoreItems && it !in hiddenItems }
                                val newMoreDisplayItems = itemOrder.filter { it in newMoreItems && it !in hiddenItems }
                                saveEditorTopToolbarState(newTopItems + newMoreDisplayItems + hiddenDisplayItems, newMoreItems, hiddenItems)
                            },
                            onToggleHidden = { itemId ->
                                val newHiddenItems = hiddenItems + itemId
                                val newMoreItems = moreItems - itemId
                                val newTopItems = itemOrder.filter { it !in newMoreItems && it !in newHiddenItems }
                                val newMoreDisplayItems = itemOrder.filter { it in newMoreItems && it !in newHiddenItems }
                                val newHiddenDisplayItems = itemOrder.filter { it in newHiddenItems }
                                saveEditorTopToolbarState(newTopItems + newMoreDisplayItems + newHiddenDisplayItems, newMoreItems, newHiddenItems)
                            },
                        )
                    }

                    SettingsSectionDivider()
                    SettingsSectionTitle("隐藏")
                    if (hiddenDisplayItems.isEmpty()) {
                        SettingsPageText("暂无隐藏项")
                    } else {
                        SettingsEditorTopToolbarDragList(
                            items = hiddenDisplayItems,
                            moreItems = moreItems,
                            hiddenItems = hiddenItems,
                            onOrderChange = { newHiddenDisplayItems ->
                                saveEditorTopToolbarSections(topItems, moreDisplayItems, newHiddenDisplayItems)
                            },
                            onToggleArea = {},
                            onToggleHidden = { itemId ->
                                val newHiddenItems = hiddenItems - itemId
                                val newMoreItems = moreItems - itemId
                                val newTopItems = itemOrder.filter { it !in newMoreItems && it !in newHiddenItems }
                                val newMoreDisplayItems = itemOrder.filter { it in newMoreItems && it !in newHiddenItems }
                                val newHiddenDisplayItems = itemOrder.filter { it in newHiddenItems }
                                saveEditorTopToolbarState(newTopItems + newMoreDisplayItems + newHiddenDisplayItems, newMoreItems, newHiddenItems)
                            },
                        )
                    }
                }
                "selectionToolbar" -> {
                    var itemOrder by remember(settingsPage, selectionToolbarOrder) { mutableStateOf(prefsManager.getSelectionToolbarItemOrder()) }
                    var moreItems by remember(settingsPage, selectionToolbarMoreItems) { mutableStateOf(prefsManager.getSelectionToolbarMoreItems()) }
                    var hiddenItems by remember(settingsPage, selectionToolbarHiddenItems) { mutableStateOf(prefsManager.getSelectionToolbarHiddenItems()) }

                    fun saveSelectionToolbarState(
                        newOrder: List<PrefsManager.SelectionToolbarItemId> = itemOrder,
                        newMoreItems: Set<PrefsManager.SelectionToolbarItemId> = moreItems,
                        newHiddenItems: Set<PrefsManager.SelectionToolbarItemId> = hiddenItems,
                    ) {
                        val safeOrder = newOrder.distinct().toMutableList().also { order ->
                            PrefsManager.SelectionToolbarItemId.DEFAULT_ORDER.forEach { if (it !in order) order.add(it) }
                        }
                        val safeHiddenItems = newHiddenItems.filter { it in safeOrder }.toSet()
                        val safeMoreItems = newMoreItems.filter { it in safeOrder && it !in safeHiddenItems }.toSet()
                        itemOrder = safeOrder
                        moreItems = safeMoreItems
                        hiddenItems = safeHiddenItems
                        selectionToolbarOrder = safeOrder
                        selectionToolbarMoreItems = safeMoreItems
                        selectionToolbarHiddenItems = safeHiddenItems
                        prefsManager.saveSelectionToolbarItemOrder(safeOrder)
                        prefsManager.saveSelectionToolbarMoreItems(safeMoreItems)
                        prefsManager.saveSelectionToolbarHiddenItems(safeHiddenItems)
                        onSettingsChanged()
                    }

                    fun saveSelectionToolbarSections(
                        topItems: List<PrefsManager.SelectionToolbarItemId>,
                        moreDisplayItems: List<PrefsManager.SelectionToolbarItemId>,
                        hiddenDisplayItems: List<PrefsManager.SelectionToolbarItemId>,
                    ) {
                        saveSelectionToolbarState(topItems + moreDisplayItems + hiddenDisplayItems, moreItems, hiddenItems)
                    }

                    val topItems = itemOrder.filter { it !in moreItems && it !in hiddenItems }
                    val moreDisplayItems = itemOrder.filter { it in moreItems && it !in hiddenItems }
                    val hiddenDisplayItems = itemOrder.filter { it in hiddenItems }

                    SettingsPageText("长按拖动调整顺序，按钮可以放在顶部、更多或隐藏")
                    SettingsSectionTitle("顶部展示")
                    if (topItems.isEmpty()) {
                        SettingsPageText("暂无顶部按钮")
                    } else {
                        SettingsSelectionToolbarDragList(
                            items = topItems,
                            moreItems = moreItems,
                            hiddenItems = hiddenItems,
                            onOrderChange = { newTopItems ->
                                saveSelectionToolbarSections(newTopItems, moreDisplayItems, hiddenDisplayItems)
                            },
                            onToggleArea = { itemId ->
                                val newMoreItems = moreItems + itemId
                                val newTopItems = itemOrder.filter { it !in newMoreItems && it !in hiddenItems }
                                val newMoreDisplayItems = itemOrder.filter { it in newMoreItems && it !in hiddenItems }
                                saveSelectionToolbarState(newTopItems + newMoreDisplayItems + hiddenDisplayItems, newMoreItems, hiddenItems)
                            },
                            onToggleHidden = { itemId ->
                                val newHiddenItems = hiddenItems + itemId
                                val newMoreItems = moreItems - itemId
                                val newTopItems = itemOrder.filter { it !in newMoreItems && it !in newHiddenItems }
                                val newMoreDisplayItems = itemOrder.filter { it in newMoreItems && it !in newHiddenItems }
                                val newHiddenDisplayItems = itemOrder.filter { it in newHiddenItems }
                                saveSelectionToolbarState(newTopItems + newMoreDisplayItems + newHiddenDisplayItems, newMoreItems, newHiddenItems)
                            },
                        )
                    }

                    SettingsSectionDivider()
                    SettingsSectionTitle("更多选项展示")
                    if (moreDisplayItems.isEmpty()) {
                        SettingsPageText("暂无更多选项")
                    } else {
                        SettingsSelectionToolbarDragList(
                            items = moreDisplayItems,
                            moreItems = moreItems,
                            hiddenItems = hiddenItems,
                            onOrderChange = { newMoreDisplayItems ->
                                saveSelectionToolbarSections(topItems, newMoreDisplayItems, hiddenDisplayItems)
                            },
                            onToggleArea = { itemId ->
                                val newMoreItems = moreItems - itemId
                                val newTopItems = itemOrder.filter { it !in newMoreItems && it !in hiddenItems }
                                val newMoreDisplayItems = itemOrder.filter { it in newMoreItems && it !in hiddenItems }
                                saveSelectionToolbarState(newTopItems + newMoreDisplayItems + hiddenDisplayItems, newMoreItems, hiddenItems)
                            },
                            onToggleHidden = { itemId ->
                                val newHiddenItems = hiddenItems + itemId
                                val newMoreItems = moreItems - itemId
                                val newTopItems = itemOrder.filter { it !in newMoreItems && it !in newHiddenItems }
                                val newMoreDisplayItems = itemOrder.filter { it in newMoreItems && it !in newHiddenItems }
                                val newHiddenDisplayItems = itemOrder.filter { it in newHiddenItems }
                                saveSelectionToolbarState(newTopItems + newMoreDisplayItems + newHiddenDisplayItems, newMoreItems, newHiddenItems)
                            },
                        )
                    }

                    SettingsSectionDivider()
                    SettingsSectionTitle("隐藏")
                    if (hiddenDisplayItems.isEmpty()) {
                        SettingsPageText("暂无隐藏项")
                    } else {
                        SettingsSelectionToolbarDragList(
                            items = hiddenDisplayItems,
                            moreItems = moreItems,
                            hiddenItems = hiddenItems,
                            onOrderChange = { newHiddenDisplayItems ->
                                saveSelectionToolbarSections(topItems, moreDisplayItems, newHiddenDisplayItems)
                            },
                            onToggleArea = {},
                            onToggleHidden = { itemId ->
                                val newHiddenItems = hiddenItems - itemId
                                val newMoreItems = moreItems - itemId
                                val newTopItems = itemOrder.filter { it !in newMoreItems && it !in newHiddenItems }
                                val newMoreDisplayItems = itemOrder.filter { it in newMoreItems && it !in newHiddenItems }
                                val newHiddenDisplayItems = itemOrder.filter { it in newHiddenItems }
                                saveSelectionToolbarState(newTopItems + newMoreDisplayItems + newHiddenDisplayItems, newMoreItems, newHiddenItems)
                            },
                        )
                    }
                }
                "drawerSettings" -> {
                    SettingsSectionTitle("侧边栏样式切换")
                    SettingsPageText("这里只切换侧边栏样式和布局，不切换应用主题；切换后仍然跟随当前主题色。")
                    PrefsManager.DrawerStyle.values().forEach { style ->
                        SettingsChoiceRow(
                            icon = drawerStyleIcon(style),
                            title = drawerStyleLabel(style),
                            subtitle = drawerStyleSubtitle(style),
                            selected = drawerStyle == style,
                            onClick = {
                                drawerStyle = style
                                prefsManager.saveDrawerStyle(style)
                                onSettingsChanged()
                            },
                        )
                    }

                    SettingsSectionDivider()
                    SettingsSectionTitle("侧边栏设置")
                    SettingsListGroup {
                        SettingsActionRow(
                            icon = Icons.Outlined.Reorder,
                            title = "侧边栏调整",
                            subtitle = "长按拖动，显示、隐藏、改名和分组",
                            onClick = { openSettingsPage("drawerEdit") },
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.TouchApp,
                            title = "侧边栏距离",
                            subtitle = "设置左侧划出距离",
                            onClick = { settingsDialog = "drawer" },
                        )
                    }
                }
                "drawerEdit" -> {
                    var drawerOrder by remember { mutableStateOf(prefsManager.getDrawerItemOrder()) }
                    var hiddenItems by remember { mutableStateOf(prefsManager.getHiddenDrawerItems()) }
                    var drawerGroupStartItems by remember { mutableStateOf(prefsManager.getDrawerGroupStartItems()) }
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

                    fun toggleDrawerGroupStart(itemId: PrefsManager.DrawerItemId) {
                        val newGroupStartItems = if (itemId in drawerGroupStartItems) {
                            drawerGroupStartItems - itemId
                        } else {
                            drawerGroupStartItems + itemId
                        }
                        drawerGroupStartItems = newGroupStartItems
                        prefsManager.saveDrawerGroupStartItems(newGroupStartItems)
                        onSettingsChanged()
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

                    SettingsPageText("分组只影响支持分组的侧边栏样式，不显示分组名；点击“分组”会从该功能项开始新分组。")
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
                            groupStartItems = drawerGroupStartItems,
                            onToggleGroupStart = { itemId -> toggleDrawerGroupStart(itemId) },
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
                "homeBottomToolbar" -> {
                    fun saveHomeBottomToolbarState(
                        newOrder: List<PrefsManager.HomeBottomToolbarItemId> = homeBottomToolbarOrder,
                        newHiddenItems: Set<PrefsManager.HomeBottomToolbarItemId> = homeBottomToolbarHiddenItems,
                    ) {
                        homeBottomToolbarOrder = newOrder
                        homeBottomToolbarHiddenItems = newHiddenItems
                        prefsManager.saveHomeBottomToolbarItemOrder(newOrder)
                        prefsManager.saveHomeBottomToolbarHiddenItems(newHiddenItems)
                        onSettingsChanged()
                    }

                    fun saveHomeBottomToolbarSections(
                        visibleItems: List<PrefsManager.HomeBottomToolbarItemId>,
                        hiddenToolbarItems: List<PrefsManager.HomeBottomToolbarItemId>,
                    ) {
                        saveHomeBottomToolbarState(visibleItems + hiddenToolbarItems, homeBottomToolbarHiddenItems)
                    }

                    val visibleItems = homeBottomToolbarOrder.filter { it !in homeBottomToolbarHiddenItems }
                    val hiddenToolbarItems = homeBottomToolbarOrder.filter { it in homeBottomToolbarHiddenItems }

                    SettingsSectionTitle("显示方式")
                    PrefsManager.HomeActionStyle.values().forEach { style ->
                        SettingsChoiceRow(
                            icon = if (style == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR) Icons.Outlined.ViewHeadline else Icons.Outlined.Add,
                            title = if (style == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR) "首页底部工具栏" else "简约新建按钮",
                            subtitle = if (style == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR) "底部显示可自定义图标入口" else "保留右下角圆形新建按钮",
                            selected = homeActionStyle == style,
                            onClick = {
                                homeActionStyle = style
                                prefsManager.saveHomeActionStyle(style)
                                onSettingsChanged()
                            },
                        )
                    }

                    if (homeActionStyle == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR) {
                        SettingsSectionDivider()
                        SettingsSectionTitle(
                            "按钮大小",
                            "默认 ${PrefsManager.DEFAULT_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP}dp；7 个以内会根据屏幕宽度自动缩小，8 个及以上支持左右滚动",
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "当前大小：${homeBottomToolbarButtonSizeDp}dp",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Slider(
                                value = homeBottomToolbarButtonSizeDp.toFloat(),
                                onValueChange = { value ->
                                    homeBottomToolbarButtonSizeDp = value
                                        .roundToInt()
                                        .coerceIn(
                                            PrefsManager.MIN_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP,
                                            PrefsManager.MAX_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP,
                                        )
                                },
                                onValueChangeFinished = {
                                    prefsManager.saveHomeBottomToolbarButtonSizeDp(homeBottomToolbarButtonSizeDp)
                                    onSettingsChanged()
                                },
                                valueRange = PrefsManager.MIN_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP.toFloat()..PrefsManager.MAX_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP.toFloat(),
                                steps = PrefsManager.MAX_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP - PrefsManager.MIN_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP - 1,
                            )
                            TextButton(
                                onClick = {
                                    homeBottomToolbarButtonSizeDp = PrefsManager.DEFAULT_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP
                                    prefsManager.saveHomeBottomToolbarButtonSizeDp(homeBottomToolbarButtonSizeDp)
                                    onSettingsChanged()
                                },
                            ) {
                                Text("恢复默认大小")
                            }
                        }

                        SettingsSectionDivider()
                        SettingsSectionTitle("底部工具栏显示", "长按拖动调整位置；隐藏项不会显示，等于减少底部按钮数量")
                        if (visibleItems.isEmpty()) {
                            SettingsPageText("暂无显示项")
                        } else {
                            SettingsHomeBottomToolbarDragList(
                                items = visibleItems,
                                hiddenItems = homeBottomToolbarHiddenItems,
                                onOrderChange = { newVisibleItems ->
                                    saveHomeBottomToolbarSections(newVisibleItems, hiddenToolbarItems)
                                },
                                onToggleVisible = { itemId ->
                                    val newHiddenItems = homeBottomToolbarHiddenItems + itemId
                                    val newVisibleItems = homeBottomToolbarOrder.filter { it !in newHiddenItems }
                                    val newHiddenToolbarItems = homeBottomToolbarOrder.filter { it in newHiddenItems }
                                    saveHomeBottomToolbarState(newVisibleItems + newHiddenToolbarItems, newHiddenItems)
                                },
                            )
                        }

                        SettingsSectionDivider()
                        SettingsSectionTitle("可添加功能", "包含新建入口和常用功能")
                        if (hiddenToolbarItems.isEmpty()) {
                            SettingsPageText("暂无隐藏项")
                        } else {
                            SettingsHomeBottomToolbarDragList(
                                items = hiddenToolbarItems,
                                hiddenItems = homeBottomToolbarHiddenItems,
                                onOrderChange = { newHiddenToolbarItems ->
                                    saveHomeBottomToolbarSections(visibleItems, newHiddenToolbarItems)
                                },
                                onToggleVisible = { itemId ->
                                    val newHiddenItems = homeBottomToolbarHiddenItems - itemId
                                    val newVisibleItems = homeBottomToolbarOrder.filter { it !in newHiddenItems }
                                    val newHiddenToolbarItems = homeBottomToolbarOrder.filter { it in newHiddenItems }
                                    saveHomeBottomToolbarState(newVisibleItems + newHiddenToolbarItems, newHiddenItems)
                                },
                            )
                        }
                    } else {
                        SettingsSectionDivider()
                        SettingsPageText("当前使用简约新建按钮。切换为首页底部工具栏后，可在这里调整底部按钮数量和位置。")
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
                        subtitle = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) "推荐：4 位数字密码" else "复杂密码：系统键盘",
                        onClick = { settingsDialog = "passwordMode" },
                    )
                    SettingsPageText("建议先设置应用锁；忘记密码备用的安全词放在页面底部。")

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

                }
                "webDav" -> {
                    val webDavSyncManager = remember { WebDavCloudSyncManager(context, prefsManager) }
                    val savedWebDavSettings = prefsManager.getWebDavSettings()
                    var webDavServerUrl by remember { mutableStateOf(savedWebDavSettings.serverUrl) }
                    var webDavUsername by remember { mutableStateOf(savedWebDavSettings.username) }
                    var webDavPassword by remember { mutableStateOf(savedWebDavSettings.password) }
                    var webDavRemoteFolder by remember { mutableStateOf(savedWebDavSettings.remoteFolder) }
                    var webDavRealtimeEnabled by remember { mutableStateOf(prefsManager.isWebDavRealtimeSyncEnabled()) }
                    var webDavRealtimeIntervalMs by remember { mutableStateOf(prefsManager.getWebDavRealtimePollIntervalMs()) }
                    var webDavBusy by remember { mutableStateOf(false) }
                    var webDavMessage by remember { mutableStateOf<String?>(null) }
                    var webDavPreview by remember { mutableStateOf<WebDavCloudSyncManager.SyncPreview?>(null) }
                    var webDavProgress by remember { mutableStateOf<WebDavCloudSyncManager.SyncProgress?>(null) }
                    var webDavResolutions by remember {
                        mutableStateOf<Map<String, WebDavCloudSyncManager.ConflictResolution>>(emptyMap())
                    }
                    var webDavLogVersion by remember { mutableStateOf(0) }
                    val webDavLogs = remember(webDavLogVersion, webDavMessage) { prefsManager.getWebDavSyncLogs() }
                    val webDavPendingConflicts = remember(webDavLogVersion, webDavMessage, webDavPreview) {
                        prefsManager.getWebDavPendingConflicts()
                    }

                    fun saveWebDavSettings() {
                        val saved = prefsManager.saveWebDavSettings(
                            PrefsManager.WebDavSettings(
                                serverUrl = webDavServerUrl,
                                username = webDavUsername,
                                password = webDavPassword,
                                remoteFolder = webDavRemoteFolder,
                                scope = PrefsManager.WebDavSyncScope.DATABASE_AND_VAULT,
                                mode = PrefsManager.WebDavSyncMode.INCREMENTAL,
                            ),
                        )
                        if (!saved) {
                            webDavMessage = "WebDAV 密码安全保存失败，已保留原设置"
                        }
                    }

                    fun applyWebDavPreview(preview: WebDavCloudSyncManager.SyncPreview) {
                        webDavPreview = preview
                        webDavResolutions = preview.conflicts.associate {
                            it.relativePath to WebDavCloudSyncManager.ConflictResolution.SKIP
                        }
                        webDavMessage = "同步预览：${preview.summary()}"
                    }

                    fun formatWebDavTime(ms: Long): String {
                        if (ms <= 0L) return "未知"
                        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
                    }

                    fun formatPendingConflict(line: String): String {
                        val parts = line.split('\t')
                        if (parts.size < 4) return line
                        val localTime = parts[2].toLongOrNull() ?: 0L
                        val remoteTime = parts[3].toLongOrNull() ?: 0L
                        return "${parts[0]}\n原因：${parts[1]}\n本地 ${formatWebDavTime(localTime)}；远端 ${formatWebDavTime(remoteTime)}"
                    }

                    SettingsSectionTitle("WebDAV 账号", "建议使用服务商提供的应用密码")
                    OutlinedTextField(
                        value = webDavServerUrl,
                        onValueChange = { value ->
                            webDavServerUrl = value
                            saveWebDavSettings()
                        },
                        label = { Text("WebDAV 地址") },
                        placeholder = { Text("https://example.com/dav/files/用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = webDavUsername,
                        onValueChange = { value ->
                            webDavUsername = value
                            saveWebDavSettings()
                        },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = webDavPassword,
                        onValueChange = { value ->
                            webDavPassword = value
                            saveWebDavSettings()
                        },
                        label = { Text("密码 / 应用密码") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = webDavRemoteFolder,
                        onValueChange = { value ->
                            webDavRemoteFolder = value
                            saveWebDavSettings()
                        },
                        label = { Text("远程目录") },
                        placeholder = { Text("KardLeaf") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsPageText("文件会按相对路径同步到远程目录的 vault/ 下。Room 数据库不再作为云同步主对象，zip 同步包也不会参与。")
                    SettingsPageText("新版 WebDAV 已改为文件级同步，旧版 zip 同步包不会自动恢复。如需迁移，请先在旧版本恢复到本地，再使用新版文件级同步上传。")

                    SettingsSectionDivider()
                    SettingsSectionTitle("实时同步")
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Sync,
                        title = "实时同步",
                        subtitle = "本机修改后自动延迟上传；同时约每 ${webDavRealtimeIntervalMs / 1000} 秒检查远程真实文件变化，遇到冲突会跳过并记录",
                        checked = webDavRealtimeEnabled,
                        onCheckedChange = { enabled ->
                            webDavRealtimeEnabled = enabled
                            prefsManager.saveWebDavRealtimeSyncEnabled(enabled)
                            prefsManager.appendWebDavSyncLog(if (enabled) "实时同步已开启" else "实时同步已关闭")
                            if (enabled) {
                                prefsManager.markWebDavRealtimeLocalDirty()
                            }
                            webDavLogVersion++
                        },
                    )
                    SettingsSectionTitle("实时检查间隔")
                    listOf(1_000L, 2_000L, 5_000L, 15_000L).forEach { intervalMs ->
                        SettingsChoiceRow(
                            icon = Icons.Outlined.Schedule,
                            title = "每 ${intervalMs / 1000} 秒检查一次",
                            subtitle = if (intervalMs == 1_000L) {
                                "调试用，检查更频繁，网络请求也更多"
                            } else {
                                "日常使用更省电、省流量"
                            },
                            selected = webDavRealtimeIntervalMs == intervalMs,
                            onClick = {
                                webDavRealtimeIntervalMs = intervalMs
                                prefsManager.saveWebDavRealtimePollIntervalMs(intervalMs)
                                prefsManager.appendWebDavSyncLog("实时检查间隔已改为 ${intervalMs / 1000} 秒")
                                webDavLogVersion++
                            },
                        )
                    }
                    SettingsPageText("实时同步只处理笔记库文件，不会删除本地文件，也不会覆盖 Room 数据库。")

                    if (webDavPendingConflicts.isNotEmpty()) {
                        SettingsSectionDivider()
                        SettingsSectionTitle("有冲突待处理", "请生成同步预览后选择保留本地、保留远端或跳过")
                        webDavPendingConflicts.take(10).forEach { line ->
                            SettingsPageText(formatPendingConflict(line))
                        }
                        if (webDavPendingConflicts.size > 10) {
                            SettingsPageText("还有 ${webDavPendingConflicts.size - 10} 个冲突未显示。")
                        }
                    }

                    SettingsSectionDivider()
                    SettingsSectionTitle("同步预览")
                    SettingsActionRow(
                        icon = Icons.Outlined.Search,
                        title = "生成同步预览",
                        subtitle = if (webDavBusy) {
                            "扫描中..."
                        } else {
                            "扫描本地和远端文件，列出上传、下载和冲突"
                        },
                        onClick = {
                            if (!webDavBusy) {
                                webDavBusy = true
                                webDavMessage = null
                                webDavProgress = null
                                scope.launch {
                                    val result = runCatching { webDavSyncManager.previewSync() }
                                    result.onSuccess { preview ->
                                        applyWebDavPreview(preview)
                                        prefsManager.appendWebDavSyncLog("同步预览：${preview.summary()}")
                                    }
                                    result.onFailure { error ->
                                        webDavMessage = WebDavCloudSyncManager.readableError(error, "同步预览失败")
                                        prefsManager.appendWebDavSyncLog(WebDavCloudSyncManager.readableError(error, "同步预览失败"))
                                    }
                                    webDavLogVersion++
                                    webDavBusy = false
                                }
                            }
                        },
                    )
                    webDavPreview?.let { preview ->
                        SettingsPageText("预览结果：${preview.summary()}")
                        if (preview.toUpload.isNotEmpty()) {
                            SettingsPageText("本地新增待上传：${preview.toUpload.take(8).joinToString("、")}")
                        }
                        if (preview.localNewer.isNotEmpty()) {
                            SettingsPageText("本地较新待上传：${preview.localNewer.take(8).joinToString("、")}")
                        }
                        if (preview.toDownload.isNotEmpty()) {
                            SettingsPageText("远端新增待下载：${preview.toDownload.take(8).joinToString("、")}")
                        }
                        if (preview.remoteNewer.isNotEmpty()) {
                            SettingsPageText("远端较新待下载：${preview.remoteNewer.take(8).joinToString("、")}")
                        }
                        if (preview.conflicts.isNotEmpty()) {
                            SettingsSectionTitle("冲突处理", "默认跳过；逐个选择后再开始同步")
                            preview.conflicts.take(20).forEach { conflict ->
                                val selectedResolution = webDavResolutions[conflict.relativePath]
                                    ?: WebDavCloudSyncManager.ConflictResolution.SKIP
                                SettingsPageText(
                                    "${conflict.relativePath}\n原因：${conflict.reason.label}\n本地 ${formatWebDavTime(conflict.localModifiedMs)}；远端 ${formatWebDavTime(conflict.remoteModifiedMs)}",
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(
                                        WebDavCloudSyncManager.ConflictResolution.KEEP_LOCAL to "保留本地",
                                        WebDavCloudSyncManager.ConflictResolution.KEEP_REMOTE to "保留远端",
                                        WebDavCloudSyncManager.ConflictResolution.SKIP to "跳过",
                                    ).forEach { (resolution, label) ->
                                        TextButton(
                                            onClick = {
                                                webDavResolutions = webDavResolutions + (conflict.relativePath to resolution)
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(if (selectedResolution == resolution) "已选 $label" else label)
                                        }
                                    }
                                }
                            }
                            if (preview.conflicts.size > 20) {
                                SettingsPageText("还有 ${preview.conflicts.size - 20} 个冲突未展开，默认跳过。")
                            }
                        }
                    }
                    webDavProgress?.let { progress ->
                        SettingsPageText("同步中：${progress.operation.label} ${progress.processedFiles}/${progress.totalFiles}  ${progress.currentFile}")
                    }
                    SettingsActionRow(
                        icon = Icons.Outlined.Sync,
                        title = "开始文件级同步",
                        subtitle = if (webDavBusy) {
                            "同步中..."
                        } else if (webDavPreview == null) {
                            "请先生成同步预览"
                        } else {
                            "按预览执行；冲突只按你的选择处理"
                        },
                        onClick = {
                            if (!webDavBusy) {
                                val preview = webDavPreview
                                if (preview == null) {
                                    webDavMessage = "请先生成同步预览。"
                                    return@SettingsActionRow
                                }
                                if (preview.isEmpty) {
                                    webDavMessage = "没有需要同步的文件。"
                                    return@SettingsActionRow
                                }
                                webDavBusy = true
                                webDavMessage = null
                                webDavProgress = null
                                scope.launch {
                                    val result = runCatching {
                                        webDavSyncManager.sync(
                                            preview = preview,
                                            resolutions = webDavResolutions,
                                        ) { progress ->
                                            webDavProgress = progress
                                        }
                                    }
                                    result.onSuccess { syncResult ->
                                        webDavMessage = syncResult.message
                                        if (syncResult.downloadedCount > 0) {
                                            onWebDavVaultChanged(syncResult.changedPaths)
                                        }
                                        if (syncResult.skippedConflictCount > 0) {
                                            runCatching { webDavSyncManager.previewSync() }
                                                .onSuccess { applyWebDavPreview(it) }
                                        } else {
                                            webDavPreview = null
                                            webDavResolutions = emptyMap()
                                        }
                                        prefsManager.appendWebDavSyncLog(
                                            if (syncResult.skippedConflictCount > 0) {
                                                "手动同步有冲突待处理：${syncResult.message}"
                                            } else {
                                                "手动同步成功：${syncResult.message}"
                                            },
                                        )
                                    }
                                    result.onFailure { error ->
                                        webDavMessage = WebDavCloudSyncManager.readableError(error, "WebDAV 同步失败")
                                        prefsManager.appendWebDavSyncLog(WebDavCloudSyncManager.readableError(error, "手动同步失败"))
                                    }
                                    webDavProgress = null
                                    webDavLogVersion++
                                    webDavBusy = false
                                }
                            }
                        },
                    )
                    SettingsSectionDivider()
                    SettingsSectionTitle("同步记录", "用于判断实时同步是否真的执行")
                    SettingsActionRow(
                        icon = Icons.Outlined.Search,
                        title = "测试远端检查",
                        subtitle = "检查是否能识别 WebDAV/vault/ 真实文件",
                        onClick = {
                            if (!webDavBusy) {
                                webDavBusy = true
                                scope.launch {
                                    val result = runCatching { webDavSyncManager.describeRealtimeRemoteState() }
                                    webDavMessage = result.getOrElse { WebDavCloudSyncManager.readableError(it, "实时同步检查失败") }
                                    result.onSuccess { message -> prefsManager.appendWebDavSyncLog(message) }
                                    result.onFailure { error -> prefsManager.appendWebDavSyncLog(WebDavCloudSyncManager.readableError(error, "实时同步检查失败")) }
                                    webDavLogVersion++
                                    webDavBusy = false
                                }
                            }
                        },
                    )
                    SettingsActionRow(
                        icon = Icons.Outlined.Refresh,
                        title = "刷新同步记录",
                        subtitle = "查看最近 ${webDavLogs.size} 条记录",
                        onClick = { webDavLogVersion++ },
                    )
                    SettingsActionRow(
                        icon = Icons.Outlined.DeleteSweep,
                        title = "清空同步记录",
                        subtitle = "只清除调试记录，不影响同步数据",
                        onClick = {
                            prefsManager.clearWebDavSyncLogs()
                            webDavLogVersion++
                        },
                    )
                    if (webDavLogs.isEmpty()) {
                        SettingsPageText("暂无同步记录。开启实时同步后，检测、上传、下载或失败会显示在这里。")
                    } else {
                        webDavLogs.take(30).forEach { logLine ->
                            SettingsPageText(logLine)
                        }
                    }

                    webDavMessage?.let { message ->
                        SettingsPageText(message)
                    }
                    SettingsPageText("注意：同步不会删除本地文件；同一路径两端都变动时会进入冲突列表，未选择的冲突默认跳过。")
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
                "taskReminders" -> {
                    val notificationsAllowed = TaskReminderScheduler.areNotificationsEnabled(context)
                    val exactAllowed = TaskReminderScheduler.canScheduleExactAlarms(context)
                    SettingsSectionTitle("任务与提醒", "任务数据独立存储，不会改写 Markdown 文件")
                    SettingsPageText("通知权限：${if (notificationsAllowed) "已允许" else "未允许"}")
                    SettingsPageText("精确提醒权限：${if (exactAllowed) "可用" else "不可用，将使用非精确提醒降级"}")
                    SettingsActionRow(
                        icon = Icons.Outlined.Notifications,
                        title = "系统通知设置",
                        subtitle = "查看或开启 KardLeaf 通知权限",
                        onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            } else {
                                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure { Toast.makeText(context, "无法打开系统通知设置", Toast.LENGTH_SHORT).show() }
                        },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsActionRow(
                            icon = Icons.Outlined.Schedule,
                            title = "精确提醒权限",
                            subtitle = if (exactAllowed) "已允许准时提醒" else "未允许时会使用系统非精确提醒",
                            onClick = {
                                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:${context.packageName}"))
                                runCatching { context.startActivity(intent) }
                                    .onFailure { Toast.makeText(context, "无法打开精确提醒权限设置", Toast.LENGTH_SHORT).show() }
                            },
                        )
                    }
                    SettingsPageText("手机重启后会重新注册未完成且未来有效的任务提醒。")
                }
                "remarkRecords" -> {
                    NoteRecordSummarySettingsPage(
                        title = "有备注的笔记",
                        emptyText = "当前没有带备注的笔记",
                        summaries = remarkNoteSummaries,
                        isLoading = isLoadingRecordSummaries,
                        onOpenNote = onOpenRecordNote,
                    )
                }
                "historyRecords" -> {
                    NoteRecordSummarySettingsPage(
                        title = "有历史版本的笔记",
                        emptyText = "当前没有历史版本记录",
                        summaries = historyNoteSummaries,
                        isLoading = isLoadingRecordSummaries,
                        onOpenNote = onOpenRecordNote,
                    )
                }
                "about" -> {
                    val versionName = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")
                    }
                    val githubUrl = "https://github.com/waikr/KardLeaf"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(
                            painter = painterResource(id = appIcon.iconResId),
                            contentDescription = null,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp)),
                        )
                        Text(
                            text = "卡叶笔记",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "本地优先的 Markdown 笔记软件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsActionRow(
                        icon = Icons.Outlined.Info,
                        title = "版本",
                        subtitle = versionName.ifBlank { "1.5.0" },
                        onClick = {},
                    )
                    if (BuildConfig.KARDLEAF_DEV_VARIANT) {
                        SettingsActionRow(
                            icon = Icons.Outlined.Code,
                            title = "Git 节点",
                            subtitle = BuildConfig.KARDLEAF_GIT_COMMIT.ifBlank { "unknown" },
                            onClick = {},
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.Info,
                            title = "提交信息",
                            subtitle = BuildConfig.KARDLEAF_GIT_MESSAGE.ifBlank { "unknown" },
                            onClick = {},
                        )
                    }
                    SettingsActionRow(
                        icon = Icons.Outlined.Person,
                        title = "作者",
                        subtitle = "kangle",
                        onClick = {},
                    )
                    SettingsActionRow(
                        icon = Icons.Outlined.Code,
                        title = "GitHub 仓库",
                        subtitle = githubUrl,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)))
                            }
                        },
                    )
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
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Schedule,
                        title = "显示修改日期",
                        subtitle = if (showModifiedDateOnCards) "宽松卡片右下角显示修改日期" else "首页卡片不显示修改日期",
                        checked = showModifiedDateOnCards,
                        onCheckedChange = { enabled ->
                            showModifiedDateOnCards = enabled
                            prefsManager.saveModifiedDateOnCardsVisible(enabled)
                            onSettingsChanged()
                        },
                    )
                    SettingsActionRow(
                        icon = Icons.Outlined.TextFields,
                        title = "修改日期格式",
                        subtitle = cardModifiedDateFormat.ifBlank { PrefsManager.DEFAULT_CARD_MODIFIED_DATE_FORMAT },
                        onClick = { settingsDialog = "cardModifiedDateFormat" },
                    )
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Title,
                        title = "显示文件名（标题）",
                        subtitle = if (showNoteTitleOnCards) "首页卡片显示标题" else "首页卡片隐藏标题",
                        checked = showNoteTitleOnCards,
                        onCheckedChange = { enabled ->
                            showNoteTitleOnCards = enabled
                            prefsManager.saveNoteTitleOnCardsVisible(enabled)
                            onSettingsChanged()
                        },
                    )
                    if (showNoteTitleOnCards) {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.CalendarToday,
                            title = "显示纯日期文件名",
                            subtitle = if (showDateFilenameTitleOnCards) "日期格式标题正常显示" else "隐藏纯日期格式标题",
                            checked = showDateFilenameTitleOnCards,
                            onCheckedChange = { enabled ->
                                showDateFilenameTitleOnCards = enabled
                                prefsManager.saveDateFilenameTitleOnCardsVisible(enabled)
                                onSettingsChanged()
                            },
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.VisibilityOff,
                            title = "自定义隐藏文件名",
                            subtitle = if (showDateFilenameTitleOnCards) "关闭显示纯日期文件名后生效" else "已设置 ${customHiddenFilenamePatterns.size} 条隐藏规则",
                            onClick = {
                                customHiddenFilenameText = customHiddenFilenamePatterns.joinToString("\n")
                                settingsDialog = "hiddenFilenames"
                            },
                        )
                    }
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Description,
                        title = "详情页显示文件名（标题）",
                        subtitle = if (showNoteDetailTitle) "打开笔记时正常显示标题" else "匹配隐藏规则时显示标题占位",
                        checked = showNoteDetailTitle,
                        onCheckedChange = { enabled ->
                            showNoteDetailTitle = enabled
                            prefsManager.saveNoteDetailTitleVisible(enabled)
                            onSettingsChanged()
                        },
                    )
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Info,
                        title = "标题上方显示文件信息",
                        subtitle = if (showNoteDetailFileInfo) "显示时间、字数和分类" else "不在标题上方显示文件信息",
                        checked = showNoteDetailFileInfo,
                        onCheckedChange = { enabled ->
                            showNoteDetailFileInfo = enabled
                            prefsManager.saveNoteDetailFileInfoVisible(enabled)
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
                    SettingsSectionTitle(settingsText(settingsEnglish, "常规", "General"))
                    SettingsListGroup {
                        SettingsActionRow(Icons.Outlined.Folder, settingsText(settingsEnglish, "笔记库", "Vault"), displayRootPath(prefsManager.getRootUri()), { showVaultPathDialog = true })
                        SettingsActionRow(Icons.Outlined.Palette, settingsText(settingsEnglish, "主题切换", "Theme"), themeSummary(appThemeStyle, appThemeMode, modernThemeColorStyle, themeColor, themeBackgroundColor), { openSettingsPage("theme") })
                        SettingsActionRow(Icons.Outlined.Tune, settingsText(settingsEnglish, "应用界面", "Interface"), settingsText(settingsEnglish, "布局、排序、启动分类和图标", "Layout, sorting, startup folder and icons"), { openSettingsPage("interface") })
                        SettingsActionRow(
                            icon = if (homeActionStyle == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR) Icons.Outlined.ViewHeadline else Icons.Outlined.Add,
                            title = settingsText(settingsEnglish, "首页底部工具栏", "Home toolbar"),
                            subtitle = if (homeActionStyle == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR) {
                                settingsText(settingsEnglish, "已显示 ${homeBottomToolbarOrder.count { it !in homeBottomToolbarHiddenItems }} 个图标，按钮 ${homeBottomToolbarButtonSizeDp}dp", "${homeBottomToolbarOrder.count { it !in homeBottomToolbarHiddenItems }} icons, ${homeBottomToolbarButtonSizeDp}dp buttons")
                            } else {
                                settingsText(settingsEnglish, "当前使用简约新建按钮", "Using simple new button")
                            },
                            onClick = {
                                homeBottomToolbarOrder = prefsManager.getHomeBottomToolbarItemOrder()
                                homeBottomToolbarHiddenItems = prefsManager.getHomeBottomToolbarHiddenItems()
                                homeBottomToolbarButtonSizeDp = prefsManager.getHomeBottomToolbarButtonSizeDp()
                                openSettingsPage("homeBottomToolbar")
                            },
                        )
                        SettingsActionRow(Icons.Outlined.ViewAgenda, settingsText(settingsEnglish, "侧边栏", "Sidebar"), drawerStyleLabel(drawerStyle), { openSettingsPage("drawerSettings") })
                    }
                    SettingsSectionDivider()
                    SettingsSectionTitle(settingsText(settingsEnglish, "编辑器", "Editor"))
                    SettingsListGroup {
                        SettingsActionRow(Icons.Outlined.FormatListBulleted, settingsText(settingsEnglish, "字符按钮位置", "Format buttons"), settingsText(settingsEnglish, "调整工具按钮顺序", "Reorder editor tools"), { openSettingsPage("toolbar") })
                        SettingsActionRow(Icons.Outlined.ViewHeadline, settingsText(settingsEnglish, "笔记顶部栏", "Note top bar"), settingsText(settingsEnglish, "调整顶部、更多和隐藏按钮", "Top, more and hidden buttons"), {
                            noteSidePanelsEnabled = prefsManager.isNoteSidePanelsEnabled()
                            noteSidePanelOpenMode = prefsManager.getNoteSidePanelOpenMode()
                            editorTopToolbarOrder = prefsManager.getEditorTopToolbarItemOrder()
                            editorTopToolbarMoreItems = prefsManager.getEditorTopToolbarMoreItems()
                            editorTopToolbarHiddenItems = prefsManager.getEditorTopToolbarHiddenItems()
                            openSettingsPage("editorTopToolbar")
                        })
                        SettingsActionRow(Icons.Outlined.Reorder, settingsText(settingsEnglish, "长按选择栏", "Selection toolbar"), settingsText(settingsEnglish, "调整顶部、更多和隐藏按钮", "Top, more and hidden buttons"), {
                            selectionToolbarOrder = prefsManager.getSelectionToolbarItemOrder()
                            selectionToolbarMoreItems = prefsManager.getSelectionToolbarMoreItems()
                            selectionToolbarHiddenItems = prefsManager.getSelectionToolbarHiddenItems()
                            openSettingsPage("selectionToolbar")
                        })
                        SettingsActionRow(Icons.Outlined.Visibility, settingsText(settingsEnglish, "默认打开模式", "Default open mode"), if (openNoteMode == KardLeafCustomFeatures.OpenNoteMode.PREVIEW) settingsText(settingsEnglish, "查看模式", "Preview") else settingsText(settingsEnglish, "编辑模式", "Edit"), { settingsDialog = "openNote" })
                        SettingsActionRow(
                            Icons.Outlined.FontDownload,
                            settingsText(settingsEnglish, "编辑器字体", "Editor font"),
                            settingsText(settingsEnglish, "${editorFontSizeSp.roundToInt()}sp · 行高 ${(editorLineHeightMultiplier * 100).roundToInt()}%", "${editorFontSizeSp.roundToInt()}sp · line ${(editorLineHeightMultiplier * 100).roundToInt()}%"),
                            { settingsDialog = "editorTypography" },
                        )
                        SettingsSwitchRow(
                            icon = Icons.Outlined.FormatListBulleted,
                            title = settingsText(settingsEnglish, "编辑底部工具栏常驻", "Always show edit toolbar"),
                            subtitle = if (editorBottomToolbarAlwaysVisible) settingsText(settingsEnglish, "编辑状态下始终显示底部字符栏", "Always visible while editing") else settingsText(settingsEnglish, "仅输入法弹出时显示底部字符栏", "Only with keyboard"),
                            checked = editorBottomToolbarAlwaysVisible,
                            onCheckedChange = { enabled ->
                                editorBottomToolbarAlwaysVisible = enabled
                                prefsManager.saveEditorBottomToolbarAlwaysVisible(enabled)
                                onSettingsChanged()
                            },
                        )
                        SettingsActionRow(
                            KardLeafCustomFeatures.editorKernelIcon(editorKernel),
                            settingsText(settingsEnglish, "编辑器内核", "Editor engine"),
                            KardLeafCustomFeatures.editorKernelTitle(editorKernel),
                            { settingsDialog = "editorKernel" },
                        )
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Visibility,
                            title = settingsText(settingsEnglish, "CodeMirror 实时预览", "CodeMirror live preview"),
                            subtitle = if (codeMirrorLivePreviewEnabled) settingsText(settingsEnglish, "已开启：滚动和输入时会显示轻量 Markdown 样式", "On: lightweight Markdown styling while typing") else settingsText(settingsEnglish, "已关闭：优先保证 CodeMirror 编辑流畅度", "Off: prioritizes smooth editing"),
                            checked = codeMirrorLivePreviewEnabled,
                            onCheckedChange = { enabled ->
                                codeMirrorLivePreviewEnabled = enabled
                                prefsManager.saveCodeMirrorLivePreviewEnabled(enabled)
                                onSettingsChanged()
                            },
                        )
                        SettingsActionRow(
                            Icons.Outlined.TouchApp,
                            settingsText(settingsEnglish, "双击进入编辑间隔", "Double-tap edit interval"),
                            settingsText(settingsEnglish, "当前 ${prefsManager.getPreviewDoubleTapIntervalMs()}ms", "Current ${prefsManager.getPreviewDoubleTapIntervalMs()}ms"),
                            { settingsDialog = "doubleTap" },
                        )
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Info,
                            title = settingsText(settingsEnglish, "笔记详情侧滑面板", "Note side panel"),
                            subtitle = if (noteSidePanelsEnabled) settingsText(settingsEnglish, "已开启", "On") else settingsText(settingsEnglish, "已关闭", "Off"),
                            checked = noteSidePanelsEnabled,
                            onCheckedChange = { enabled ->
                                noteSidePanelsEnabled = enabled
                                prefsManager.saveNoteSidePanelsEnabled(enabled)
                                onSettingsChanged()
                            },
                        )
                        if (noteSidePanelsEnabled) {
                            SettingsActionRow(
                                icon = if (noteSidePanelOpenMode == PrefsManager.NoteSidePanelOpenMode.GESTURE) Icons.Outlined.Swipe else Icons.Outlined.ViewHeadline,
                                title = "侧滑面板弹出方式",
                                subtitle = if (noteSidePanelOpenMode == PrefsManager.NoteSidePanelOpenMode.GESTURE) "手势划出" else "顶部工具栏弹出",
                                onClick = { settingsDialog = "sidePanelOpenMode" },
                            )
                        }
                    }
                    SettingsSectionDivider()
                    SettingsSectionTitle(settingsText(settingsEnglish, "附件与文件", "Files"))
                    SettingsListGroup {
                        SettingsActionRow(Icons.Outlined.Folder, "图片保存位置", imageFolder, { openSettingsPage("image") })
                        SettingsActionRow(Icons.Outlined.Image, "图片路径格式", "根路径或相对路径", { settingsDialog = "imagePath" })
                        SettingsActionRow(
                            Icons.Outlined.VisibilityOff,
                            "隐藏的文件夹",
                            if (hiddenFolders.isEmpty()) "未隐藏文件夹" else hiddenFolders.sorted().joinToString("、").take(32),
                            { openSettingsPage("hiddenFolders") },
                        )
                        SettingsActionRow(Icons.Outlined.Description, "自动文件名", autoFileNameSummary, { settingsDialog = "autoFileName" })
                        SettingsActionRow(Icons.Outlined.TextFields, "日期格式", dateFormat, { settingsDialog = "date" })
                    }
                    SettingsSectionDivider()
                    SettingsSectionTitle(settingsText(settingsEnglish, "数据与安全", "Data & security"))
                    SettingsListGroup {
                        SettingsActionRow(Icons.Outlined.Backup, "数据备份", "导入或导出用户数据 JSON", { settingsDialog = "backup" })
                        SettingsActionRow(Icons.Outlined.Backup, "WebDAV 云同步", "文件级预览、冲突处理", { openSettingsPage("webDav") })
                        SettingsActionRow(Icons.Outlined.Schedule, "自动备份", "定时备份到指定目录", { openSettingsPage("autoBackup") })
                        SettingsActionRow(Icons.Outlined.Notifications, "任务与提醒", "通知权限和精确提醒状态", { openSettingsPage("taskReminders") })
                        SettingsActionRow(Icons.Outlined.Description, "备注记录", "查看所有有备注的笔记", { openSettingsPage("remarkRecords") })
                        SettingsActionRow(Icons.Outlined.History, "历史版本记录", "查看所有有历史版本的笔记", { openSettingsPage("historyRecords") })
                        SettingsActionRow(
                            Icons.Outlined.History,
                            "历史版本数量",
                            if (savedHistoryLimit == 0) "已关闭历史版本记录" else "保留最新 $savedHistoryLimit 个版本",
                            { settingsDialog = "historyLimit" },
                        )
                        SettingsActionRow(Icons.Outlined.DeleteSweep, "清理旧历史版本", "清理旧版本前预览", { openCleanupHistoryDialog() })
                        SettingsActionRow(Icons.Outlined.Delete, "回收站", "目录、排序、自动清理", { openSettingsPage("trash") })
                        SettingsActionRow(Icons.Outlined.Lock, "安全", "应用锁、隐私和指纹", { openSettingsPage("security") })
                    }
                    SettingsSectionDivider()
                    SettingsSectionTitle(settingsText(settingsEnglish, "其他", "Other"))
                    SettingsListGroup {
                        SettingsActionRow(
                            Icons.Outlined.Language,
                            settingsText(settingsEnglish, "语言", "Language"),
                            if (appLanguage == "en") "English" else "中文",
                            { settingsDialog = "appLanguage" },
                        )
                        SettingsActionRow(Icons.Outlined.Restore, settingsText(settingsEnglish, "恢复默认设置", "Reset settings"), settingsText(settingsEnglish, "恢复所有默认设置", "Restore default settings"), { showResetDialog = true })
                        SettingsActionRow(Icons.Outlined.BugReport, settingsText(settingsEnglish, "导出诊断日志", "Export diagnostics"), settingsText(settingsEnglish, "导出基础诊断信息", "Export basic diagnostic info"), { exportDiagnosticLog() })
                        SettingsActionRow(Icons.Outlined.Info, settingsText(settingsEnglish, "关于", "About"), settingsText(settingsEnglish, "版本信息和作者", "Version and author"), { openSettingsPage("about") })
                    }
                }
                    }
                }
            }
        }
    }
}

private data class EditorFontOption(
    val label: String,
    val value: String,
    val subtitle: String,
)

private fun settingsText(english: Boolean, zh: String, en: String): String =
    if (english) en else zh

private val EditorBuiltinFontFamilies = listOf(
    EditorFontOption("系统默认", "system", "跟随 Android 默认字体"),
    EditorFontOption("无衬线", "sans-serif", "清爽通用正文"),
    EditorFontOption("衬线", "serif", "更接近书籍排版"),
    EditorFontOption("等宽", "monospace", "适合代码和纯文本"),
)

private fun editorFontLabel(font: EditorFontOption, english: Boolean): String =
    if (!english) font.label else when (font.value) {
        "system" -> "System"
        "sans-serif" -> "Sans serif"
        "serif" -> "Serif"
        "monospace" -> "Monospace"
        else -> font.label
    }

private fun editorFontSubtitle(font: EditorFontOption, english: Boolean): String =
    if (!english) font.subtitle else when (font.value) {
        "system" -> "Use Android's default font"
        "sans-serif" -> "Clean general text"
        "serif" -> "Book-like reading"
        "monospace" -> "For code and plain text"
        else -> font.subtitle
    }

private data class LanguageOption(
    val label: String,
    val value: String,
    val subtitle: String,
)

private val AppLanguageOptions = listOf(
    LanguageOption("中文", "zh", "默认语言"),
    LanguageOption("English", "en", "Use English resources"),
)

@Composable
private fun SettingsValueSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

private val ThemeCustomAccentColorPalette = listOf(
    0xFF2563EB.toInt(),
    0xFF3B82F6.toInt(),
    0xFF0EA5E9.toInt(),
    0xFF14B8A6.toInt(),
    0xFF16A34A.toInt(),
    0xFF84CC16.toInt(),
    0xFFF59E0B.toInt(),
    0xFFF97316.toInt(),
    0xFFEF4444.toInt(),
    0xFFEC4899.toInt(),
    0xFF8B5CF6.toInt(),
    0xFF64748B.toInt(),
)

private val ThemeCustomBackgroundColorPalette = listOf(
    0xFFFFFFFF.toInt(),
    0xFFF8FAFC.toInt(),
    0xFFF1F5F9.toInt(),
    0xFFEFF6FF.toInt(),
    0xFFE0F2FE.toInt(),
    0xFFF0FDFA.toInt(),
    0xFFF0FDF4.toInt(),
    0xFFFEFCE8.toInt(),
    0xFFFFF7ED.toInt(),
    0xFFFFF1F2.toInt(),
    0xFFFDF2F8.toInt(),
    0xFFF5F3FF.toInt(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeColorPickerDialog(
    title: String,
    presets: List<Int>,
    selectedArgb: Int,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var workingArgb by remember(selectedArgb) { mutableStateOf(selectedArgb or 0xFF000000.toInt()) }
    var hexText by remember(selectedArgb) { mutableStateOf(argbToThemeHex(selectedArgb)) }
    var hexError by remember { mutableStateOf(false) }

    fun setColor(argb: Int) {
        workingArgb = argb or 0xFF000000.toInt()
        hexText = argbToThemeHex(workingArgb)
        hexError = false
    }

    val red = (workingArgb shr 16) and 0xFF
    val green = (workingArgb shr 8) and 0xFF
    val blue = workingArgb and 0xFF
    val preview = Color(workingArgb)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(preview)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { value ->
                            hexText = value
                            val parsed = parseThemeHexColor(value)
                            hexError = parsed == null
                            if (parsed != null) workingArgb = parsed
                        },
                        label = { Text("HEX") },
                        singleLine = true,
                        isError = hexError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    presets.forEach { argb ->
                        val selected = (argb and 0x00FFFFFF) == (workingArgb and 0x00FFFFFF)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(argb))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { setColor(argb) },
                        )
                    }
                }
                ColorChannelSlider("R", red, Color(0xFFEF4444)) { setColor(themeRgb(it, green, blue)) }
                ColorChannelSlider("G", green, Color(0xFF22C55E)) { setColor(themeRgb(red, it, blue)) }
                ColorChannelSlider("B", blue, Color(0xFF3B82F6)) { setColor(themeRgb(red, green, it)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(workingArgb) }, enabled = !hexError) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.width(18.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
    }
}

private fun themeRgb(red: Int, green: Int, blue: Int): Int =
    android.graphics.Color.rgb(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

private fun themeModeIcon(mode: PrefsManager.AppThemeMode): ImageVector =
    when (mode) {
        PrefsManager.AppThemeMode.SYSTEM -> Icons.Outlined.Devices
        PrefsManager.AppThemeMode.LIGHT -> Icons.Outlined.LightMode
        PrefsManager.AppThemeMode.DARK -> Icons.Outlined.DarkMode
    }

private fun argbToThemeHex(argb: Int): String =
    "#%06X".format(argb and 0x00FFFFFF)

private fun parseThemeHexColor(raw: String): Int? {
    val text = raw.trim().removePrefix("#")
    val normalized = when (text.length) {
        6 -> "FF$text"
        8 -> text
        else -> return null
    }
    return normalized.toLongOrNull(16)?.toInt()
}

private val ThemeCornerRadiusOptions =
    listOf(PrefsManager.THEME_CORNER_RADIUS_FOLLOW, 0, 8, 16, 24, 32)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppIconChoiceGrid(
    selectedIcon: AppIconManager.AppIcon,
    onIconClick: (AppIconManager.AppIcon) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppIconManager.AppIcon.values().forEach { icon ->
            AppIconChoiceBlock(
                icon = icon,
                selected = selectedIcon == icon,
                onClick = { onIconClick(icon) },
            )
        }
    }
}

@Composable
private fun AppIconChoiceBlock(
    icon: AppIconManager.AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = icon.iconResId),
                contentDescription = icon.label,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = icon.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeModeChoiceGrid(
    selectedMode: PrefsManager.AppThemeMode,
    onModeClick: (PrefsManager.AppThemeMode) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrefsManager.AppThemeMode.values().forEach { mode ->
            ThemeChoiceBlock(
                icon = themeModeIcon(mode),
                title = themeModeLabel(mode),
                subtitle = themeModeSubtitle(mode),
                selected = selectedMode == mode,
                onClick = { onModeClick(mode) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeStyleChoiceGrid(
    styles: List<PrefsManager.AppThemeStyle>,
    selectedStyle: PrefsManager.AppThemeStyle,
    onStyleClick: (PrefsManager.AppThemeStyle) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        styles.forEach { style ->
            ThemeChoiceBlock(
                icon = Icons.Outlined.Palette,
                title = themeStyleLabel(style),
                subtitle = themeStyleSubtitle(style),
                selected = selectedStyle == style,
                onClick = { onStyleClick(style) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModernThemeColorStyleChoiceGrid(
    selectedStyle: PrefsManager.ModernThemeColorStyle,
    onStyleClick: (PrefsManager.ModernThemeColorStyle) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrefsManager.ModernThemeColorStyle.values().forEach { style ->
            ThemeChoiceBlock(
                icon = Icons.Outlined.Palette,
                title = modernThemeColorStyleLabel(style),
                subtitle = modernThemeColorStyleSubtitle(style),
                selected = selectedStyle == style,
                onClick = { onStyleClick(style) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CleanListFeatureIconStyleChoiceGrid(
    selectedStyle: PrefsManager.CleanListFeatureIconStyle,
    onStyleClick: (PrefsManager.CleanListFeatureIconStyle) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrefsManager.CleanListFeatureIconStyle.values().forEach { style ->
            ThemeChoiceBlock(
                icon = Icons.Outlined.Apps,
                title = cleanListFeatureIconStyleLabel(style),
                subtitle = cleanListFeatureIconStyleSubtitle(style),
                selected = selectedStyle == style,
                onClick = { onStyleClick(style) },
            )
        }
    }
}

@Composable
private fun ThemeChoiceBlock(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendedThemePaletteGrid(
    selectedAccentColor: PrefsManager.ThemeColor,
    selectedBackgroundColor: PrefsManager.ThemeBackgroundColor,
    onPaletteClick: (PrefsManager.ThemeColor, PrefsManager.ThemeBackgroundColor) -> Unit,
) {
    val palettes = listOf(
        ThemePalettePreset("蓝白", PrefsManager.ThemeColor.BLUE, PrefsManager.ThemeBackgroundColor.WHITE),
        ThemePalettePreset("叶绿", PrefsManager.ThemeColor.GREEN, PrefsManager.ThemeBackgroundColor.GREEN),
        ThemePalettePreset("清蓝", PrefsManager.ThemeColor.BLUE, PrefsManager.ThemeBackgroundColor.BLUE),
        ThemePalettePreset("柔紫", PrefsManager.ThemeColor.PURPLE, PrefsManager.ThemeBackgroundColor.PURPLE),
        ThemePalettePreset("暖读", PrefsManager.ThemeColor.AMBER, PrefsManager.ThemeBackgroundColor.AMBER),
        ThemePalettePreset("灰粉", PrefsManager.ThemeColor.PINK, PrefsManager.ThemeBackgroundColor.GRAY),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        palettes.forEach { preset ->
            ThemePaletteComboBox(
                title = preset.title,
                accentColor = themeAccentPreviewColor(preset.accent),
                backgroundColor = themeBackgroundPreviewColor(preset.background),
                selected = selectedAccentColor == preset.accent && selectedBackgroundColor == preset.background,
                onClick = { onPaletteClick(preset.accent, preset.background) },
            )
        }
    }
}

private data class ThemePalettePreset(
    val title: String,
    val accent: PrefsManager.ThemeColor,
    val background: PrefsManager.ThemeBackgroundColor,
)

@Composable
private fun ThemePaletteComboBox(
    title: String,
    accentColor: Color,
    backgroundColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(138.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 110.dp, height = 66.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(backgroundColor),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
                    .size(width = 58.dp, height = 22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.82f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(9.dp)
                    .size(width = 42.dp, height = 8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accentColor.copy(alpha = 0.34f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(9.dp)
                    .size(width = 34.dp, height = 22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeColorPaletteGrid(
    colors: List<PrefsManager.ThemeColor>,
    selectedColor: PrefsManager.ThemeColor,
    customColor: Color,
    onColorClick: (PrefsManager.ThemeColor) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { color ->
            ThemePaletteBox(
                title = themeColorLabel(color),
                color = if (color == PrefsManager.ThemeColor.CUSTOM) customColor else themeAccentPreviewColor(color),
                selected = selectedColor == color,
                onClick = { onColorClick(color) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeBackgroundPaletteGrid(
    colors: List<PrefsManager.ThemeBackgroundColor>,
    selectedColor: PrefsManager.ThemeBackgroundColor,
    customColor: Color,
    onColorClick: (PrefsManager.ThemeBackgroundColor) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { color ->
            ThemePaletteBox(
                title = themeBackgroundColorLabel(color),
                color = if (color == PrefsManager.ThemeBackgroundColor.CUSTOM) customColor else themeBackgroundPreviewColor(color),
                selected = selectedColor == color,
                onClick = { onColorClick(color) },
            )
        }
    }
}

@Composable
private fun ThemePaletteBox(
    title: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(92.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color),
            )
            Row(modifier = Modifier.align(Alignment.BottomCenter)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .background(color.copy(alpha = 0.62f)),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .background(color.copy(alpha = 0.28f)),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CornerRadiusPaletteGrid(
    title: String,
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onClick: (Int) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        values.forEach { value ->
            val selectedValue = selected == value
            val previewRadius = if (value == PrefsManager.THEME_CORNER_RADIUS_FOLLOW) 18.dp else value.dp
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .width(92.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        if (selectedValue) 2.dp else 1.dp,
                        if (selectedValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape,
                    )
                    .clickable { onClick(value) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 34.dp)
                        .clip(RoundedCornerShape(previewRadius))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedValue) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = label(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    themeStyle: PrefsManager.AppThemeStyle,
    themeMode: PrefsManager.AppThemeMode,
    accentColor: PrefsManager.ThemeColor,
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    modernColorStyle: PrefsManager.ModernThemeColorStyle,
    customAccentColor: Color,
    customBackgroundColor: Color,
) {
    val isReference = themeStyle == PrefsManager.AppThemeStyle.NOW_IN_ANDROID ||
        (themeStyle == PrefsManager.AppThemeStyle.MODERN && modernColorStyle == PrefsManager.ModernThemeColorStyle.MODERN)
    val isCleanList = themeStyle == PrefsManager.AppThemeStyle.CLEAN_LIST
    val isGitHub = themeStyle == PrefsManager.AppThemeStyle.GITHUB_DARK
    val isDracula = themeStyle == PrefsManager.AppThemeStyle.DRACULA
    val accent = when {
        accentColor == PrefsManager.ThemeColor.CUSTOM -> customAccentColor
        isGitHub -> githubAccentPreviewColor(accentColor)
        isDracula -> draculaAccentPreviewColor(accentColor)
        else -> themeAccentPreviewColor(accentColor)
    }
    val background = when {
        isGitHub -> Color(0xFF0D1117)
        isDracula -> Color(0xFF282A36)
        isCleanList -> Color(0xFFF0F2F3)
        backgroundColor == PrefsManager.ThemeBackgroundColor.CUSTOM -> customBackgroundColor
        else -> themeBackgroundPreviewColor(backgroundColor)
    }
    val foreground = if (isGitHub) Color(0xFFC9D1D9) else if (isDracula) Color(0xFFF8F8F2) else Color(0xFF0F172A)
    val muted = if (isGitHub) Color(0xFF8B949E) else if (isDracula) Color(0xFFCFCBD8) else Color(0xFF64748B)
    val chipSurface = if (isGitHub) Color(0xFF161B22) else if (isDracula) Color(0xFF44475A) else Color.White
    val isModern = themeStyle != PrefsManager.AppThemeStyle.CLASSIC
    val shape = RoundedCornerShape(
        when {
            isGitHub -> 12.dp
            isDracula -> 14.dp
            isCleanList -> 28.dp
            isModern -> 30.dp
            else -> 22.dp
        },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isModern) 8.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(background)
            .border(1.dp, accent.copy(alpha = if (isModern) 0.22f else 0.28f), shape)
            .padding(if (isModern) 18.dp else 16.dp),
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
                    color = foreground,
                )
                Text(
                    text = if (isDracula) {
                        "${themeStyleLabel(themeStyle)} · 固定暗色 · ${themeColorLabel(accentColor)}霓虹"
                    } else if (isGitHub) {
                        "${themeStyleLabel(themeStyle)} · 固定暗色 · ${themeColorLabel(accentColor)}链接色"
                    } else {
                        if (themeStyle == PrefsManager.AppThemeStyle.MODERN) {
                            "${themeStyleLabel(themeStyle)} · ${modernThemeColorStyleLabel(modernColorStyle)}色彩 · ${themeModeLabel(themeMode)}"
                        } else {
                            "${themeStyleLabel(themeStyle)} · ${themeModeLabel(themeMode)} · ${themeColorLabel(accentColor)} · ${themeBackgroundColorLabel(backgroundColor)}"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
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
            ThemePreviewChip(text = if (isGitHub) "链接色" else if (isDracula) "霓虹按钮" else if (isCleanList) "强调按钮" else if (isModern) "柔和按钮" else "按钮", color = accent, selected = true)
            ThemePreviewChip(text = if (isGitHub) "仓库卡片" else if (isDracula) "硬边卡片" else if (isCleanList) "白色卡片" else if (isModern) "圆角卡片" else "标签", color = if (isGitHub) Color(0xFF21262D) else if (isDracula) Color(0xFF44475A) else accent.copy(alpha = 0.12f), selected = false, textColor = if (isGitHub || isDracula) foreground else Color(0xFF334155))
            ThemePreviewChip(text = if (isGitHub) "细边框" else if (isReference) "现代色彩" else if (isDracula) "暗色面板" else if (isCleanList) "分组列表" else if (isModern) "按压动效" else "卡片", color = chipSurface, selected = false, textColor = if (isGitHub || isDracula) foreground else Color(0xFF334155))
        }
    }
}

@Composable
private fun ThemePreviewChip(
    text: String,
    color: Color,
    selected: Boolean,
    textColor: Color = Color(0xFF334155),
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Color.White else textColor,
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

fun hashPassword(raw: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun themeSummary(
    style: PrefsManager.AppThemeStyle,
    mode: PrefsManager.AppThemeMode,
    modernColorStyle: PrefsManager.ModernThemeColorStyle,
    accentColor: PrefsManager.ThemeColor,
    backgroundColor: PrefsManager.ThemeBackgroundColor,
): String =
    when (style) {
        PrefsManager.AppThemeStyle.DRACULA ->
            "${themeStyleLabel(style)} · 固定暗色 · ${themeColorLabel(accentColor)}霓虹强调色"
        PrefsManager.AppThemeStyle.GITHUB_DARK ->
            "${themeStyleLabel(style)} · 固定暗色 · ${themeColorLabel(accentColor)}链接色"
        PrefsManager.AppThemeStyle.MODERN ->
            "${themeModeLabel(mode)} · ${themeStyleLabel(style)} · 色彩：${modernThemeColorStyleLabel(modernColorStyle)} · 强调色：${themeColorLabel(accentColor)} · 背景色：${themeBackgroundColorLabel(backgroundColor)}"
        else ->
            "${themeModeLabel(mode)} · ${themeStyleLabel(style)} · 强调色：${themeColorLabel(accentColor)} · 背景色：${themeBackgroundColorLabel(backgroundColor)}"
    }

private fun themeStyleLabel(style: PrefsManager.AppThemeStyle): String =
    when (style) {
        PrefsManager.AppThemeStyle.CLASSIC -> "经典主题"
        PrefsManager.AppThemeStyle.MODERN -> "圆润主题"
        PrefsManager.AppThemeStyle.NOW_IN_ANDROID -> "圆润主题 · 现代色彩"
        PrefsManager.AppThemeStyle.CLEAN_LIST -> "清爽列表"
        PrefsManager.AppThemeStyle.GITHUB_DARK -> "极夜主题"
        PrefsManager.AppThemeStyle.DRACULA -> "霓彩主题"
    }

private fun themeStyleSubtitle(style: PrefsManager.AppThemeStyle): String =
    when (style) {
        PrefsManager.AppThemeStyle.CLASSIC -> "保留 KardLeaf 原有的经典界面和视觉效果"
        PrefsManager.AppThemeStyle.MODERN -> "柔和圆角、卡片设置项和按压动画"
        PrefsManager.AppThemeStyle.NOW_IN_ANDROID -> "圆润主题下的现代色彩体系"
        PrefsManager.AppThemeStyle.CLEAN_LIST -> "浅灰背景、白色大圆角分组列表和彩色图标"
        PrefsManager.AppThemeStyle.GITHUB_DARK -> "接近代码仓库界面的极暗配色、蓝色链接和细边框"
        PrefsManager.AppThemeStyle.DRACULA -> "暗色霓虹紫粉风格，按钮和卡片更硬朗"
    }

private fun modernThemeColorStyleLabel(style: PrefsManager.ModernThemeColorStyle): String =
    when (style) {
        PrefsManager.ModernThemeColorStyle.CLASSIC -> "经典"
        PrefsManager.ModernThemeColorStyle.MODERN -> "现代"
    }

private fun modernThemeColorStyleSubtitle(style: PrefsManager.ModernThemeColorStyle): String =
    when (style) {
        PrefsManager.ModernThemeColorStyle.CLASSIC -> "使用当前圆润主题的强调色和背景色效果"
        PrefsManager.ModernThemeColorStyle.MODERN -> "使用现代 Material3 色彩效果"
    }

private fun cleanListFeatureIconStyleLabel(style: PrefsManager.CleanListFeatureIconStyle): String =
    when (style) {
        PrefsManager.CleanListFeatureIconStyle.MODERN -> "现代"
        PrefsManager.CleanListFeatureIconStyle.SIMPLE -> "简约"
    }

private fun cleanListFeatureIconStyleSubtitle(style: PrefsManager.CleanListFeatureIconStyle): String =
    when (style) {
        PrefsManager.CleanListFeatureIconStyle.MODERN -> "保留不同颜色的功能项图标"
        PrefsManager.CleanListFeatureIconStyle.SIMPLE -> "图标统一跟随当前强调色"
    }

private fun globalCornerRadiusLabel(radiusDp: Int): String =
    if (radiusDp == PrefsManager.THEME_CORNER_RADIUS_FOLLOW) "跟随主题" else "${radiusDp}dp"

private fun homeCornerRadiusLabel(radiusDp: Int): String =
    if (radiusDp == PrefsManager.THEME_CORNER_RADIUS_FOLLOW) "跟随全局" else "${radiusDp}dp"

private fun themeModeLabel(mode: PrefsManager.AppThemeMode): String =
    when (mode) {
        PrefsManager.AppThemeMode.SYSTEM -> "跟随系统"
        PrefsManager.AppThemeMode.LIGHT -> "浅色模式"
        PrefsManager.AppThemeMode.DARK -> "黑夜模式"
    }

private fun themeModeSubtitle(mode: PrefsManager.AppThemeMode): String =
    when (mode) {
        PrefsManager.AppThemeMode.SYSTEM -> "使用系统当前的浅色/深色设置"
        PrefsManager.AppThemeMode.LIGHT -> "始终使用浅色界面"
        PrefsManager.AppThemeMode.DARK -> "始终使用深色界面"
    }

private fun themeColorLabel(color: PrefsManager.ThemeColor): String =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> "蓝色"
        PrefsManager.ThemeColor.GREEN -> "青绿色"
        PrefsManager.ThemeColor.PURPLE -> "紫色"
        PrefsManager.ThemeColor.PINK -> "粉色"
        PrefsManager.ThemeColor.AMBER -> "琥珀色"
        PrefsManager.ThemeColor.RED -> "红色"
        PrefsManager.ThemeColor.CUSTOM -> "自定义"
    }

private fun themeColorSubtitle(color: PrefsManager.ThemeColor): String =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> "默认蓝色强调色"
        PrefsManager.ThemeColor.GREEN -> "自然叶子风格"
        PrefsManager.ThemeColor.PURPLE -> "柔和效率风格"
        PrefsManager.ThemeColor.PINK -> "轻柔生活风格"
        PrefsManager.ThemeColor.AMBER -> "温暖阅读风格"
        PrefsManager.ThemeColor.RED -> "醒目强调风格"
        PrefsManager.ThemeColor.CUSTOM -> "自定义强调色"
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
        PrefsManager.ThemeBackgroundColor.CUSTOM -> "自定义"
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
        PrefsManager.ThemeBackgroundColor.CUSTOM -> "自定义背景色"
    }

private fun themeAccentPreviewColor(color: PrefsManager.ThemeColor): Color =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> Color(0xFF3B82F6)
        PrefsManager.ThemeColor.GREEN -> Color(0xFF00856F)
        PrefsManager.ThemeColor.PURPLE -> Color(0xFF7C3AED)
        PrefsManager.ThemeColor.PINK -> Color(0xFFB83263)
        PrefsManager.ThemeColor.AMBER -> Color(0xFF956300)
        PrefsManager.ThemeColor.RED -> Color(0xFFDC2626)
        PrefsManager.ThemeColor.CUSTOM -> Color(0xFF3B82F6)
    }

private fun draculaAccentPreviewColor(color: PrefsManager.ThemeColor): Color =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> Color(0xFF8BE9FD)
        PrefsManager.ThemeColor.GREEN -> Color(0xFF50FA7B)
        PrefsManager.ThemeColor.PURPLE -> Color(0xFFBD93F9)
        PrefsManager.ThemeColor.PINK -> Color(0xFFFF79C6)
        PrefsManager.ThemeColor.AMBER -> Color(0xFFFFB86C)
        PrefsManager.ThemeColor.RED -> Color(0xFFFF5555)
        PrefsManager.ThemeColor.CUSTOM -> Color(0xFFBD93F9)
    }

private fun githubAccentPreviewColor(color: PrefsManager.ThemeColor): Color =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> Color(0xFF58A6FF)
        PrefsManager.ThemeColor.GREEN -> Color(0xFF7EE787)
        PrefsManager.ThemeColor.PURPLE -> Color(0xFFD2A8FF)
        PrefsManager.ThemeColor.PINK -> Color(0xFFFFA6D1)
        PrefsManager.ThemeColor.AMBER -> Color(0xFFE3B341)
        PrefsManager.ThemeColor.RED -> Color(0xFFFF7B72)
        PrefsManager.ThemeColor.CUSTOM -> Color(0xFF58A6FF)
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
        PrefsManager.ThemeBackgroundColor.CUSTOM -> Color.White
    }
