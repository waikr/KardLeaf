package com.kangle.kardleaf.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.kangle.kardleaf.AppUpdateCheckResult
import com.kangle.kardleaf.AppUpdateChecker
import com.kangle.kardleaf.BuildConfig
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.NoteRecordSummary
import com.kangle.kardleaf.data.ai.KardLeafAiClient
import com.kangle.kardleaf.data.ai.KardLeafAiConfig
import com.kangle.kardleaf.data.ai.KardLeafAiPreferences
import com.kangle.kardleaf.data.ai.KardLeafAiProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val SETTINGS_TRACE_TAG = "KardLeafSettingsTrace"
private const val DIAGNOSTIC_LOGCAT_MAX_CHARS = 2_000_000
private const val DIAGNOSTIC_LOGCAT_TIMEOUT_SECONDS = 5L

private data class GitChangedFileDetail(
    val path: String,
    val modifiedMs: Long,
)

private fun parseGitChangedFileDetails(raw: String): List<GitChangedFileDetail> =
    raw.lineSequence().mapNotNull { line ->
        val separator = line.lastIndexOf('\t')
        if (separator <= 0) return@mapNotNull null
        GitChangedFileDetail(
            path = line.substring(0, separator),
            modifiedMs = line.substring(separator + 1).toLongOrNull() ?: 0L,
        )
    }.toList()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun KardLeafSettingsScreen(
    onBack: () -> Unit,
    onSelectDatabase: () -> Unit,
    onSelectTaskFolder: () -> Unit = {},
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
    val aiPreferences = remember { KardLeafAiPreferences(context) }
    val aiClient = remember { KardLeafAiClient() }
    val initialAiConfig = remember { aiPreferences.load() }
    var savedAiConfig by remember { mutableStateOf(initialAiConfig) }
    var aiBaseUrl by remember { mutableStateOf(if (initialAiConfig.provider == KardLeafAiProvider.TRIAL) "" else initialAiConfig.baseUrl) }
    var aiModel by remember { mutableStateOf(if (initialAiConfig.provider == KardLeafAiProvider.TRIAL) "" else initialAiConfig.model) }
    var aiApiKey by remember { mutableStateOf(if (initialAiConfig.provider == KardLeafAiProvider.TRIAL) "" else initialAiConfig.apiKey) }
    var aiProvider by remember { mutableStateOf(initialAiConfig.provider) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var aiConnectionTesting by remember { mutableStateOf(false) }
    var aiConnectionMessage by remember { mutableStateOf<String?>(null) }
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
    var previewTheme by remember { mutableStateOf(prefsManager.getPreviewTheme()) }
    var autoCodeMirrorThresholdText by remember { mutableStateOf(prefsManager.getAutoCodeMirrorThresholdChars().toString()) }
    var codeMirrorLivePreviewEnabled by remember { mutableStateOf(prefsManager.isCodeMirrorLivePreviewEnabled()) }
    var editingImagePreviewEnabled by remember { mutableStateOf(prefsManager.isEditingImagePreviewEnabled()) }
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
    var homeWebClipActionVisible by remember { mutableStateOf(prefsManager.isHomeWebClipActionVisible()) }
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
    var appLoggingEnabled by remember { mutableStateOf(prefsManager.isAppLoggingEnabled()) }
    var autoUpdateCheckEnabled by remember { mutableStateOf(prefsManager.isAutoUpdateCheckEnabled()) }
    var updateCheckInProgress by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var isExportingDiagnosticLog by remember { mutableStateOf(false) }
    var isExportingDevStorage by remember { mutableStateOf(false) }

    fun checkForAppUpdate() {
        if (updateCheckInProgress) return
        updateCheckInProgress = true
        prefsManager.markUpdateCheckAttemptToday()
        scope.launch {
            updateCheckResult = AppUpdateChecker.checkLatestRelease(prefsManager)
            updateCheckInProgress = false
        }
    }

    if (isExportingDiagnosticLog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(settingsText(settingsEnglish, "正在导出诊断日志", "Exporting diagnostics")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(settingsText(settingsEnglish, "正在收集并生成日志文件，请稍候。", "Collecting and generating the log file."))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
        )
    }

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
        settingsText(settingsEnglish, "跟随日期格式：$dateFormat", "Uses date format: $dateFormat")
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

    fun hiddenFolderDisplayName(folder: String): String =
        when (folder) {
            PrefsManager.LEGACY_DRAFT_FOLDER_NAME -> settingsText(settingsEnglish, "速记（旧数据）", "Quick notes (legacy)")
            else -> folder
        }

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
            homeCornerRadiusDp != PrefsManager.THEME_CORNER_RADIUS_FOLLOW
        appThemeStyle = PrefsManager.AppThemeStyle.CLEAN_LIST
        appThemeMode = PrefsManager.AppThemeMode.SYSTEM
        modernThemeColorStyle = PrefsManager.ModernThemeColorStyle.CLASSIC
        cleanListFeatureIconStyle = PrefsManager.CleanListFeatureIconStyle.MODERN
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
            title = { Text(settingsText(settingsEnglish, "选择默认启动分类", "Choose startup folder")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsChoiceRow(
                        icon = Icons.Outlined.Folder,
                        title = settingsText(settingsEnglish, "不指定（全部笔记）", "None (All notes)"),
                        subtitle = settingsText(settingsEnglish, "打开软件显示全部笔记", "Show all notes when the app opens"),
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
        if (isExportingDiagnosticLog) return
        isExportingDiagnosticLog = true
        context.showToast("正在导出诊断日志...")
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    createDiagnosticLogFile(context, includeLogcat = appLoggingEnabled)
                }
            }.onSuccess { logFile ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "KardLeaf 诊断日志")
                    putExtra(Intent.EXTRA_TEXT, "KardLeaf 诊断日志")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "导出诊断日志"))
            }.onFailure {
                context.showToast("导出诊断日志失败")
            }
            isExportingDiagnosticLog = false
        }
    }

    fun exportDevStorage() {
        if (!BuildConfig.KARDLEAF_DEV_VARIANT || isExportingDevStorage) return
        isExportingDevStorage = true
        context.showToast("正在打包用户数据与缓存...")
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    createDevStorageExportFile(context)
                }
            }.onSuccess { exportFile ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_SUBJECT, "KardLeaf Dev 用户数据与缓存")
                    putExtra(Intent.EXTRA_TEXT, "ZIP 内包含 storage_report.txt，可按目录和文件大小定位空间占用。")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "导出用户数据与缓存"))
            }.onFailure { error ->
                KardLeafLog.e(SETTINGS_TRACE_TAG, "Failed to export dev storage", error)
                context.showToast("导出用户数据与缓存失败")
            }
            isExportingDevStorage = false
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
        editorKernel = PrefsManager.EditorKernel.QUILLPAD_STYLE
        autoCodeMirrorThresholdText = PrefsManager.DEFAULT_AUTO_CODEMIRROR_THRESHOLD_CHARS.toString()
        codeMirrorLivePreviewEnabled = PrefsManager.DEFAULT_CODEMIRROR_LIVE_PREVIEW_ENABLED
        editingImagePreviewEnabled = PrefsManager.DEFAULT_EDITING_IMAGE_PREVIEW_ENABLED
        editorFontSizeSp = PrefsManager.DEFAULT_EDITOR_FONT_SIZE_SP
        editorLineHeightMultiplier = PrefsManager.DEFAULT_EDITOR_LINE_HEIGHT_MULTIPLIER
        editorLetterSpacingSp = PrefsManager.DEFAULT_EDITOR_LETTER_SPACING_SP
        editorParagraphSpacingDp = PrefsManager.DEFAULT_EDITOR_PARAGRAPH_SPACING_DP
        editorFontFamily = PrefsManager.DEFAULT_EDITOR_FONT_FAMILY
        customEditorFontFamilyText = ""
        appLanguage = PrefsManager.DEFAULT_APP_LANGUAGE
        editorBottomToolbarAlwaysVisible = PrefsManager.DEFAULT_EDITOR_BOTTOM_TOOLBAR_ALWAYS_VISIBLE
        homeActionStyle = PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR
        homeWebClipActionVisible = PrefsManager.DEFAULT_HOME_WEB_CLIP_ACTION_VISIBLE
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
        showNoteDetailFileInfo = true
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
        appLoggingEnabled = PrefsManager.DEFAULT_APP_LOGGING_ENABLED
        autoUpdateCheckEnabled = PrefsManager.DEFAULT_AUTO_UPDATE_CHECK_ENABLED
        KardLeafCustomFeatures.saveUnnamedNoteDateFormat(context, dateFormat)
        KardLeafCustomFeatures.saveUnnamedNoteFileNameTemplate(context, autoFileNameTemplate)
        KardLeafCustomFeatures.saveOpenNoteMode(context, openNoteMode)
        prefsManager.saveEditorKernel(editorKernel)
        prefsManager.saveAutoCodeMirrorThresholdChars(PrefsManager.DEFAULT_AUTO_CODEMIRROR_THRESHOLD_CHARS)
        prefsManager.saveCodeMirrorLivePreviewEnabled(codeMirrorLivePreviewEnabled)
        prefsManager.saveEditingImagePreviewEnabled(editingImagePreviewEnabled)
        prefsManager.saveEditorFontSizeSp(editorFontSizeSp)
        prefsManager.saveEditorLineHeightMultiplier(editorLineHeightMultiplier)
        prefsManager.saveEditorLetterSpacingSp(editorLetterSpacingSp)
        prefsManager.saveEditorParagraphSpacingDp(editorParagraphSpacingDp)
        prefsManager.saveEditorFontFamily(editorFontFamily)
        prefsManager.saveAppLanguage(appLanguage)
        prefsManager.saveEditorBottomToolbarAlwaysVisible(editorBottomToolbarAlwaysVisible)
        prefsManager.saveHomeActionStyle(homeActionStyle)
        prefsManager.saveHomeWebClipActionVisible(homeWebClipActionVisible)
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
        prefsManager.saveDrawerEdgeWidthDp(PrefsManager.DEFAULT_DRAWER_EDGE_WIDTH_DP)
        prefsManager.saveDrawerStyle(drawerStyle)
        prefsManager.saveDrawerItemOrder(PrefsManager.DrawerItemId.DEFAULT_ORDER)
        prefsManager.saveHiddenDrawerItems(PrefsManager.DrawerItemId.DEFAULT_HIDDEN_ITEMS)
        prefsManager.saveDrawerGroupStartItems(PrefsManager.DrawerItemId.DEFAULT_GROUP_START_ITEMS)
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
        prefsManager.saveAppLoggingEnabled(appLoggingEnabled)
        prefsManager.saveAutoUpdateCheckEnabled(autoUpdateCheckEnabled)
        KardLeafLog.setUserLoggingEnabled(appLoggingEnabled)
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

    updateCheckResult?.let { result ->
        val update = result as? AppUpdateCheckResult.UpdateAvailable
        AlertDialog(
            onDismissRequest = { updateCheckResult = null },
            title = {
                Text(
                    when (result) {
                        is AppUpdateCheckResult.UpdateAvailable -> settingsText(settingsEnglish, "发现新版本", "Update available")
                        is AppUpdateCheckResult.UpToDate -> settingsText(settingsEnglish, "已是最新版本", "Up to date")
                        is AppUpdateCheckResult.Failed -> settingsText(settingsEnglish, "检查更新失败", "Update check failed")
                    },
                )
            },
            text = {
                val message =
                    when (result) {
                        is AppUpdateCheckResult.UpdateAvailable -> buildString {
                            append(settingsText(settingsEnglish, "当前版本：", "Current version: "))
                            append(BuildConfig.VERSION_NAME)
                            append("\n")
                            append(settingsText(settingsEnglish, "最新版本：", "Latest version: "))
                            append(result.release.tagName)
                            if (result.release.publishedDate.isNotBlank()) {
                                append("\n")
                                append(settingsText(settingsEnglish, "发布日期：", "Published: "))
                                append(result.release.publishedDate)
                            }
                            if (result.release.releaseNotes.isNotBlank()) {
                                append("\n\n")
                                append(result.release.releaseNotes)
                            }
                        }
                        is AppUpdateCheckResult.UpToDate -> settingsText(
                            settingsEnglish,
                            "当前 ${BuildConfig.VERSION_NAME} 已是最新正式版本。",
                            "Version ${BuildConfig.VERSION_NAME} is the latest stable release.",
                        )
                        is AppUpdateCheckResult.Failed -> result.message
                    }
                Text(
                    text = message,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateCheckResult = null
                        if (update != null) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.release.downloadUrl)))
                            }.onFailure {
                                context.showToast(settingsText(settingsEnglish, "无法打开下载链接", "Unable to open download link"))
                            }
                        }
                    },
                ) {
                    Text(
                        if (update != null) {
                            settingsText(settingsEnglish, "下载更新", "Download")
                        } else {
                            settingsText(settingsEnglish, "确定", "OK")
                        },
                    )
                }
            },
            dismissButton = if (update != null) {
                {
                    TextButton(onClick = { updateCheckResult = null }) {
                        Text(settingsText(settingsEnglish, "稍后", "Later"))
                    }
                }
            } else {
                null
            },
        )
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

    if (showAiSettingsDialog) {
        AlertDialog(
            onDismissRequest = { if (!aiConnectionTesting) showAiSettingsDialog = false },
            title = { Text(settingsText(settingsEnglish, "AI 助手", "AI assistant")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsPageText(settingsText(settingsEnglish, "接口类型", "Provider type"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KardLeafAiProvider.values().forEach { provider ->
                            TextButton(
                                onClick = {
                                    aiProvider = provider
                                    if (provider == KardLeafAiProvider.TRIAL) {
                                        aiBaseUrl = ""
                                        aiModel = ""
                                        aiApiKey = ""
                                    } else {
                                        val stored = aiPreferences.load()
                                        aiBaseUrl = stored.baseUrl
                                        aiModel = stored.model
                                        aiApiKey = stored.apiKey
                                    }
                                    aiConnectionMessage = null
                                },
                            ) {
                                Text(if (aiProvider == provider) "✓ ${provider.displayName}" else provider.displayName)
                            }
                        }
                    }
                    if (aiProvider != KardLeafAiProvider.TRIAL) {
                        OutlinedTextField(
                        value = aiBaseUrl,
                        onValueChange = { aiBaseUrl = it; aiConnectionMessage = null },
                        label = { Text("API Base URL") },
                        placeholder = {
                            Text(
                                if (aiProvider == KardLeafAiProvider.NEW_API) {
                                    "http://服务器IP:端口"
                                } else {
                                    "https://api.openai.com/v1"
                                },
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = aiModel,
                        onValueChange = { aiModel = it; aiConnectionMessage = null },
                        label = { Text(settingsText(settingsEnglish, "模型名称", "Model")) },
                        placeholder = { Text("例如：gpt-4.1-mini / deepseek-chat") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = aiApiKey,
                        onValueChange = { aiApiKey = it; aiConnectionMessage = null },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsPageText(
                        if (aiProvider == KardLeafAiProvider.NEW_API) {
                            settingsText(
                                settingsEnglish,
                                "填写 New API 面板根地址即可，应用会自动请求 /v1/chat/completions。API Key 填写 New API 令牌。",
                                "Enter the New API server root URL. KardLeaf automatically uses /v1/chat/completions; use a New API token as the API key.",
                            )
                        } else {
                            settingsText(
                                settingsEnglish,
                                "兼容 OpenAI Chat Completions 格式。API Key 使用 Android Keystore 加密保存；隐私笔记不会调用外部 AI。",
                                "Uses the OpenAI-compatible Chat Completions format. The API key is encrypted with Android Keystore; private notes are never sent to external AI.",
                            )
                        },
                    )
                    if (aiBaseUrl.trim().startsWith("http://", ignoreCase = true)) {
                        SettingsPageText(
                            settingsText(
                                settingsEnglish,
                                "安全提示：HTTP 会明文传输 API Key 和笔记内容，仅建议用于可信局域网或已受保护的自建服务。",
                                "Security warning: HTTP sends the API key and note content in cleartext. Use it only on a trusted LAN or protected self-hosted service.",
                            ),
                        )
                    }
                    TextButton(
                        enabled = !aiConnectionTesting,
                        onClick = {
                            val config = KardLeafAiConfig(
                                baseUrl = aiBaseUrl.trim(),
                                model = aiModel.trim(),
                                apiKey = aiApiKey.trim(),
                                provider = aiProvider,
                            )
                            aiConnectionTesting = true
                            aiConnectionMessage = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { aiClient.testConnection(config) }
                                }
                                aiConnectionTesting = false
                                aiConnectionMessage = result.fold(
                                    onSuccess = { "连接成功：${it.take(60)}" },
                                    onFailure = { "连接失败：${it.message ?: "未知错误"}" },
                                )
                            }
                        },
                    ) {
                        Text(if (aiConnectionTesting) "正在测试..." else "测试连接")
                    }
                        aiConnectionMessage?.let { message ->
                            SettingsPageText(message)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !aiConnectionTesting,
                    onClick = { showAiSettingsDialog = false },
                ) { Text("取消") }
            },
            confirmButton = {
                TextButton(
                    enabled = !aiConnectionTesting,
                    onClick = {
                        val config = KardLeafAiConfig(
                            baseUrl = aiBaseUrl.trim(),
                            model = aiModel.trim(),
                            apiKey = aiApiKey.trim(),
                            provider = aiProvider,
                        )
                        runCatching { aiPreferences.save(config) }
                            .onSuccess {
                                savedAiConfig = config
                                showAiSettingsDialog = false
                                context.showToast("AI 配置已保存")
                            }
                            .onFailure { context.showToast("保存失败：${it.message ?: "未知错误"}") }
                    },
                ) { Text("保存") }
            },
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
                                    subtitle = "",
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
                            listOf(
                                PrefsManager.DrawerStyle.MINIMAL_TEXT,
                                PrefsManager.DrawerStyle.DATA_CARD,
                            ).forEach { style ->
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
                        "previewTheme" -> {
                            SettingsPageText(settingsText(settingsEnglish, "预览状态下 Markdown 渲染页面的配色主题。命名主题参考 GitHub 上流行的 Markdown 样式项目。", "Color theme for the rendered Markdown preview. Named themes reference popular Markdown styles on GitHub."))
                            PrefsManager.PreviewTheme.values().forEach { theme ->
                                SettingsChoiceRow(
                                    icon = Icons.Outlined.Palette,
                                    title = previewThemeLabel(theme),
                                    subtitle = previewThemeSubtitle(theme),
                                    selected = previewTheme == theme,
                                    onClick = {
                                        previewTheme = theme
                                        prefsManager.savePreviewTheme(theme)
                                        onSettingsChanged()
                                        settingsDialog = null
                                    },
                                )
                            }
                        }
                        "editorKernel" -> listOf(
                            PrefsManager.EditorKernel.NATIVE,
                            PrefsManager.EditorKernel.QUILLPAD_STYLE,
                            PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW,
                        ).forEach { kernel ->
                            SettingsChoiceRow(
                                icon = KardLeafCustomFeatures.editorKernelIcon(kernel),
                                title = when (kernel) {
                                    PrefsManager.EditorKernel.NATIVE -> "原生Alpha内核"
                                    PrefsManager.EditorKernel.QUILLPAD_STYLE -> "原生Beta内核"
                                    PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW -> "WebView内核"
                                    PrefsManager.EditorKernel.AUTO -> "原生Beta内核"
                                },
                                subtitle = when (kernel) {
                                    PrefsManager.EditorKernel.NATIVE -> "存在已知问题，暂不推荐使用"
                                    PrefsManager.EditorKernel.QUILLPAD_STYLE -> "采用全新架构，更适合纯文本编辑"
                                    PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW -> "支持实时预览，更适合 Markdown 文本"
                                    PrefsManager.EditorKernel.AUTO -> "采用全新架构，更适合纯文本编辑"
                                },
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
                TextButton(
                    onClick = { settingsDialog = null },
                ) {
                    Text("完成")
                }
            },
        )
    }

    BackHandler {
        when {
            showAiSettingsDialog -> if (!aiConnectionTesting) showAiSettingsDialog = false
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
                            subtitle = "",
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
                                title = hiddenFolderDisplayName(folder),
                                subtitle = when (folder) {
                                    imageFolderPath -> "图片保存位置默认隐藏"
                                    PrefsManager.DEFAULT_QUICK_NOTE_FOLDER_NAME,
                                    PrefsManager.LEGACY_DRAFT_FOLDER_NAME -> "速记内容默认隐藏"
                                    else -> "隐藏该文件夹及子文件夹"
                                },
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
                    listOf(
                        PrefsManager.DrawerStyle.MINIMAL_TEXT,
                        PrefsManager.DrawerStyle.DATA_CARD,
                    ).forEach { style ->
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

                    fun saveDrawerGroupStartItems(newGroupStartItems: Set<PrefsManager.DrawerItemId>) {
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

                    val availableGroupLineTargets = visibleItems.filter { it !in drawerGroupStartItems }
                    val defaultGroupLineTarget = availableGroupLineTargets.firstOrNull {
                        it == PrefsManager.DrawerItemId.ALL_NOTES
                    } ?: availableGroupLineTargets.firstOrNull()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SettingsSectionTitle("显示")
                        }
                        TextButton(
                            enabled = defaultGroupLineTarget != null,
                            onClick = {
                                defaultGroupLineTarget?.let { target ->
                                    saveDrawerGroupStartItems(drawerGroupStartItems + target)
                                }
                            },
                        ) {
                            Text("添加分组线")
                        }
                    }
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
                            onMoveGroupStart = { oldItem, newItem ->
                                val updated = drawerGroupStartItems - oldItem
                                saveDrawerGroupStartItems(if (newItem == null) updated else updated + newItem)
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
                    SecuritySettingsPage(
                        prefsManager = prefsManager,
                        passwordInputMode = passwordInputMode,
                        onChoosePasswordMode = { settingsDialog = "passwordMode" },
                    )
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
                    SettingsSectionTitle("任务与提醒", "任务正文写入 Markdown，提醒和备注保存在 Room")
                    val taskFolder = prefsManager.getTaskFolderPath()
                    SettingsActionRow(
                        icon = Icons.Outlined.Folder,
                        title = "任务清单数据位置",
                        subtitle = if (taskFolder.isBlank()) "根目录/任务清单.md" else "根目录/$taskFolder/任务清单.md",
                        onClick = onSelectTaskFolder,
                    )
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
                                .onFailure { context.showToast("无法打开系统通知设置") }
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
                                    .onFailure { context.showToast("无法打开精确提醒权限设置") }
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
                "changedFiles" -> {
                    val changedFiles = remember {
                        parseGitChangedFileDetails(BuildConfig.KARDLEAF_GIT_CHANGED_FILE_DETAILS)
                    }
                    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
                    if (changedFiles.isEmpty()) {
                        SettingsPageText("当前没有修改文件")
                    } else {
                        SettingsListGroup {
                            changedFiles.forEach { file ->
                                val modifiedTime = file.modifiedMs.takeIf { it > 0L }
                                    ?.let { dateFormat.format(Date(it)) }
                                    ?: "unknown"
                                SettingsBaseRow(
                                    icon = Icons.Outlined.Description,
                                    title = file.path,
                                    subtitle = "",
                                    onClick = {},
                                    trailing = {
                                        Text(
                                            text = modifiedTime,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                "about" -> {
                    val packageInfo = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
                    }
                    val versionName = packageInfo?.versionName.orEmpty()
                    val installUpdateTime = remember(packageInfo?.lastUpdateTime) {
                        packageInfo?.lastUpdateTime?.let { timestamp ->
                            SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                        }.orEmpty()
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
                            painter = painterResource(id = R.mipmap.ic_app_icon_default),
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
                        subtitle = versionName.ifBlank { "1.8.0" },
                        onClick = {},
                    )
                    if (BuildConfig.KARDLEAF_DEV_VARIANT || BuildConfig.DEBUG) {
                        SettingsActionRow(
                            icon = Icons.Outlined.Update,
                            title = "更新时间",
                            subtitle = installUpdateTime.ifBlank { "unknown" },
                            onClick = {},
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.FolderOpen,
                            title = "当前工作区",
                            subtitle = BuildConfig.KARDLEAF_GIT_CHANGED_FILES.ifBlank { "unknown" },
                            onClick = { openSettingsPage("changedFiles") },
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.Folder,
                            title = "当前工作树",
                            subtitle = BuildConfig.KARDLEAF_GIT_WORKTREE.ifBlank { "unknown" },
                            onClick = {},
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.Code,
                            title = "当前分支",
                            subtitle = BuildConfig.KARDLEAF_GIT_BRANCH.ifBlank { "unknown" },
                            onClick = {},
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.Code,
                            title = "Git 节点",
                            subtitle = listOf(
                                BuildConfig.KARDLEAF_GIT_COMMIT,
                                BuildConfig.KARDLEAF_GIT_MESSAGE,
                            ).filter { it.isNotBlank() }.joinToString("\n").ifBlank { "unknown" },
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
                            subtitle = if (defaultStartLabel.isNotBlank()) defaultStartLabel else settingsText(settingsEnglish, "全部笔记", "All notes"),
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
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Language,
                            title = settingsText(settingsEnglish, "首页顶部显示保存网站", "Show Save Website on home top bar"),
                            subtitle = if (homeWebClipActionVisible) {
                                settingsText(settingsEnglish, "已显示在首页顶部工具栏", "Shown on the home top bar")
                            } else {
                                settingsText(settingsEnglish, "默认隐藏，可从新建笔记顶部使用", "Hidden by default; available in new notes")
                            },
                            checked = homeWebClipActionVisible,
                            onCheckedChange = { visible ->
                                homeWebClipActionVisible = visible
                                prefsManager.saveHomeWebClipActionVisible(visible)
                                onSettingsChanged()
                            },
                        )
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
                        SettingsActionRow(
                            Icons.Outlined.AutoAwesome,
                            settingsText(settingsEnglish, "AI 助手", "AI assistant"),
                            if (savedAiConfig.provider == KardLeafAiProvider.TRIAL) {
                                settingsText(settingsEnglish, "试用", "Trial")
                            } else if (savedAiConfig.isConfigured) {
                                settingsText(
                                    settingsEnglish,
                                    "${savedAiConfig.provider.displayName}：${savedAiConfig.model}",
                                    "${savedAiConfig.provider.displayName}: ${savedAiConfig.model}",
                                )
                            } else {
                                settingsText(settingsEnglish, "配置 OpenAI 兼容接口、模型和 API Key", "Configure an OpenAI-compatible endpoint, model and API key")
                            },
                            {
                                val config = aiPreferences.load()
                                aiBaseUrl = if (config.provider == KardLeafAiProvider.TRIAL) "" else config.baseUrl
                                aiModel = if (config.provider == KardLeafAiProvider.TRIAL) "" else config.model
                                aiApiKey = if (config.provider == KardLeafAiProvider.TRIAL) "" else config.apiKey
                                aiProvider = config.provider
                                aiConnectionMessage = null
                                showAiSettingsDialog = true
                            },
                        )
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
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Image,
                            title = settingsText(settingsEnglish, "编辑状态图片预览", "Image preview while editing"),
                            subtitle = if (editingImagePreviewEnabled) settingsText(settingsEnglish, "原生内核会在图片语法下方显示预览", "Native editors show previews below image syntax") else settingsText(settingsEnglish, "仅显示 Markdown 图片语法", "Show Markdown image syntax only"),
                            checked = editingImagePreviewEnabled,
                            onCheckedChange = { enabled ->
                                editingImagePreviewEnabled = enabled
                                prefsManager.saveEditingImagePreviewEnabled(enabled)
                                onSettingsChanged()
                            },
                        )
                        SettingsActionRow(
                            Icons.Outlined.Palette,
                            settingsText(settingsEnglish, "预览主题", "Preview theme"),
                            previewThemeLabel(previewTheme),
                            { settingsDialog = "previewTheme" },
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
                                subtitle = if (noteSidePanelOpenMode == PrefsManager.NoteSidePanelOpenMode.GESTURE) {
                                    settingsText(settingsEnglish, "手势划出", "Swipe gesture")
                                } else {
                                    settingsText(settingsEnglish, "顶部工具栏弹出", "Top toolbar button")
                                },
                                onClick = { settingsDialog = "sidePanelOpenMode" },
                            )
                        }
                    }
                    SettingsSectionDivider()
                    SettingsSectionTitle(settingsText(settingsEnglish, "附件与文件", "Files"))
                    SettingsListGroup {
                        SettingsActionRow(Icons.Outlined.Folder, settingsText(settingsEnglish, "图片保存位置", "Image folder"), imageFolder, { openSettingsPage("image") })
                        SettingsActionRow(Icons.Outlined.Image, settingsText(settingsEnglish, "图片路径格式", "Image path format"), settingsText(settingsEnglish, "根路径或相对路径", "Root or relative paths"), { settingsDialog = "imagePath" })
                        SettingsActionRow(
                            Icons.Outlined.VisibilityOff,
                            settingsText(settingsEnglish, "隐藏的文件夹", "Hidden folders"),
                            if (hiddenFolders.isEmpty()) {
                                settingsText(settingsEnglish, "未隐藏文件夹", "No hidden folders")
                            } else {
                                hiddenFolders.sorted()
                                    .joinToString(", ") { hiddenFolderDisplayName(it) }
                                    .take(32)
                            },
                            { openSettingsPage("hiddenFolders") },
                        )
                        SettingsActionRow(Icons.Outlined.Description, settingsText(settingsEnglish, "自动文件名", "Automatic file names"), autoFileNameSummary, { settingsDialog = "autoFileName" })
                        SettingsActionRow(Icons.Outlined.TextFields, settingsText(settingsEnglish, "日期格式", "Date format"), dateFormat, { settingsDialog = "date" })
                    }
                    SettingsSectionDivider()
                    SettingsSectionTitle(settingsText(settingsEnglish, "数据与安全", "Data & security"))
                    SettingsListGroup {
                        SettingsActionRow(Icons.Outlined.Backup, settingsText(settingsEnglish, "数据备份", "Data backup"), settingsText(settingsEnglish, "导入或导出用户数据 JSON", "Import or export user data JSON"), { settingsDialog = "backup" })
                        SettingsActionRow(Icons.Outlined.Backup, settingsText(settingsEnglish, "WebDAV 云同步", "WebDAV sync"), settingsText(settingsEnglish, "文件级预览、冲突处理", "File preview and conflict handling"), { openSettingsPage("webDav") })
                        SettingsActionRow(Icons.Outlined.Schedule, settingsText(settingsEnglish, "自动备份", "Automatic backup"), settingsText(settingsEnglish, "定时备份到指定目录", "Scheduled backups to a selected folder"), { openSettingsPage("autoBackup") })
                        SettingsActionRow(Icons.Outlined.Notifications, settingsText(settingsEnglish, "任务与提醒", "Tasks & reminders"), settingsText(settingsEnglish, "通知权限和精确提醒状态", "Notification and exact reminder status"), { openSettingsPage("taskReminders") })
                        SettingsActionRow(Icons.Outlined.Description, settingsText(settingsEnglish, "备注记录", "Remark records"), settingsText(settingsEnglish, "查看所有有备注的笔记", "View all notes with remarks"), { openSettingsPage("remarkRecords") })
                        SettingsActionRow(Icons.Outlined.History, settingsText(settingsEnglish, "历史版本记录", "Version history"), settingsText(settingsEnglish, "查看所有有历史版本的笔记", "View all notes with history"), { openSettingsPage("historyRecords") })
                        SettingsActionRow(
                            Icons.Outlined.History,
                            settingsText(settingsEnglish, "历史版本数量", "History limit"),
                            if (savedHistoryLimit == 0) settingsText(settingsEnglish, "已关闭历史版本记录", "Version history is off") else settingsText(settingsEnglish, "保留最新 $savedHistoryLimit 个版本", "Keep the latest $savedHistoryLimit versions"),
                            { settingsDialog = "historyLimit" },
                        )
                        SettingsActionRow(Icons.Outlined.DeleteSweep, settingsText(settingsEnglish, "清理旧历史版本", "Clean old versions"), settingsText(settingsEnglish, "清理旧版本前预览", "Preview before removing old versions"), { openCleanupHistoryDialog() })
                        SettingsActionRow(Icons.Outlined.Delete, settingsText(settingsEnglish, "回收站", "Trash"), settingsText(settingsEnglish, "目录、排序、自动清理", "Folder, sorting and automatic cleanup"), { openSettingsPage("trash") })
                        SettingsActionRow(Icons.Outlined.Lock, settingsText(settingsEnglish, "安全", "Security"), settingsText(settingsEnglish, "应用锁、隐私和指纹", "App lock, privacy and biometrics"), { openSettingsPage("security") })
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
                        if (BuildConfig.KARDLEAF_DEV_VARIANT) {
                            SettingsActionRow(
                                Icons.Outlined.Archive,
                                settingsText(settingsEnglish, "导出用户数据与缓存", "Export user data and cache"),
                                settingsText(
                                    settingsEnglish,
                                    if (isExportingDevStorage) "正在生成 ZIP..." else "导出应用私有目录并附带空间占用报告",
                                    if (isExportingDevStorage) "Generating ZIP..." else "Export private app directories with a storage report",
                                ),
                                { exportDevStorage() },
                            )
                        }
                        SettingsSwitchRow(
                            icon = Icons.Outlined.SystemUpdate,
                            title = settingsText(settingsEnglish, "自动检查更新", "Automatic update checks"),
                            subtitle = settingsText(
                                settingsEnglish,
                                "每天首次打开应用时，异步检查 GitHub 最新正式版本",
                                "Check the latest stable GitHub release once per day",
                            ),
                            checked = autoUpdateCheckEnabled,
                            onCheckedChange = { enabled ->
                                autoUpdateCheckEnabled = enabled
                                prefsManager.saveAutoUpdateCheckEnabled(enabled)
                            },
                        )
                        SettingsActionRow(
                            Icons.Outlined.SystemUpdateAlt,
                            settingsText(settingsEnglish, "检查更新", "Check for updates"),
                            settingsText(
                                settingsEnglish,
                                if (updateCheckInProgress) "正在检查 GitHub Release..." else "立即检查最新正式版本",
                                if (updateCheckInProgress) "Checking GitHub Release..." else "Check the latest stable release now",
                            ),
                            { checkForAppUpdate() },
                        )
                        SettingsSwitchRow(
                            icon = Icons.Outlined.BugReport,
                            title = settingsText(settingsEnglish, "开启日志", "Enable logs"),
                            subtitle = settingsText(
                                settingsEnglish,
                                if (appLoggingEnabled) "已开启，会额外记录详细日志" else "默认只保留警告和错误，减少正式版运行开销",
                                if (appLoggingEnabled) "Enabled; keeps extra detailed logs" else "Only warnings and errors are kept by default",
                            ),
                            checked = appLoggingEnabled,
                            onCheckedChange = { enabled ->
                                appLoggingEnabled = enabled
                                prefsManager.saveAppLoggingEnabled(enabled)
                                KardLeafLog.setUserLoggingEnabled(enabled)
                                context.showToast(if (enabled) "已开启日志" else "已关闭日志")
                            },
                        )
                        SettingsActionRow(
                            Icons.Outlined.BugReport,
                            settingsText(settingsEnglish, "导出诊断日志", "Export diagnostics"),
                            settingsText(
                                settingsEnglish,
                                when {
                                    isExportingDiagnosticLog -> "正在生成日志文件..."
                                    appLoggingEnabled -> "导出基础诊断、应用日志和系统日志"
                                    else -> "导出基础诊断和应用警告/错误；开启后包含详细日志"
                                },
                                when {
                                    isExportingDiagnosticLog -> "Generating log file..."
                                    appLoggingEnabled -> "Export diagnostics, app logs and system logs"
                                    else -> "Exports diagnostics and app warnings/errors; enable for details"
                                },
                            ),
                            { exportDiagnosticLog() },
                        )
                        SettingsActionRow(Icons.Outlined.Info, settingsText(settingsEnglish, "关于", "About"), settingsText(settingsEnglish, "版本信息和作者", "Version and author"), { openSettingsPage("about") })
                    }
                }
                    }
                }
            }
        }
    }
}

private enum class SecurityPasswordTarget(
    val title: String,
    val passwordName: String,
) {
    APP("应用锁", "应用密码"),
    PRIVACY("隐私空间", "隐私密码"),
}

@Composable
private fun SecuritySettingsPage(
    prefsManager: PrefsManager,
    passwordInputMode: PrefsManager.PasswordInputMode,
    onChoosePasswordMode: () -> Unit,
) {
    var hasAppPassword by remember { mutableStateOf(prefsManager.getAppPasswordHash() != null) }
    var hasPrivacyPassword by remember { mutableStateOf(prefsManager.getPrivacyPasswordHash() != null) }
    var appBiometricEnabled by remember { mutableStateOf(prefsManager.isAppBiometricUnlockEnabled()) }
    var privacyBiometricEnabled by remember { mutableStateOf(prefsManager.isPrivacyBiometricUnlockEnabled()) }
    var editTarget by remember { mutableStateOf<SecurityPasswordTarget?>(null) }
    var clearTarget by remember { mutableStateOf<SecurityPasswordTarget?>(null) }

    editTarget?.let { target ->
        val hasPassword = when (target) {
            SecurityPasswordTarget.APP -> hasAppPassword
            SecurityPasswordTarget.PRIVACY -> hasPrivacyPassword
        }
        SecurityPasswordEditDialog(
            target = target,
            hasPassword = hasPassword,
            passwordInputMode = passwordInputMode,
            onDismiss = { editTarget = null },
            onSave = { password ->
                when (target) {
                    SecurityPasswordTarget.APP -> {
                        prefsManager.saveAppPasswordHash(hashPassword(password))
                        hasAppPassword = true
                    }
                    SecurityPasswordTarget.PRIVACY -> {
                        prefsManager.savePrivacyPasswordHash(hashPassword(password))
                        hasPrivacyPassword = true
                    }
                }
                editTarget = null
            },
            onRemoveRequest = {
                editTarget = null
                clearTarget = target
            },
        )
    }

    clearTarget?.let { target ->
        SecurityPasswordClearDialog(
            target = target,
            passwordInputMode = passwordInputMode,
            savedPasswordHash = when (target) {
                SecurityPasswordTarget.APP -> prefsManager.getAppPasswordHash()
                SecurityPasswordTarget.PRIVACY -> prefsManager.getPrivacyPasswordHash()
            },
            onDismiss = { clearTarget = null },
            onConfirmed = {
                when (target) {
                    SecurityPasswordTarget.APP -> {
                        prefsManager.saveAppPasswordHash(null)
                        hasAppPassword = false
                        appBiometricEnabled = false
                    }
                    SecurityPasswordTarget.PRIVACY -> {
                        prefsManager.savePrivacyPasswordHash(null)
                        hasPrivacyPassword = false
                        privacyBiometricEnabled = false
                    }
                }
                clearTarget = null
            },
        )
    }

    SettingsSectionTitle("解锁方式", "密码用于后备验证，指纹用于快捷解锁")
    SettingsListGroup {
        SettingsActionRow(
            icon = Icons.Outlined.Password,
            title = "密码类型",
            subtitle = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) {
                "4 位数字密码 · 使用应用内数字键盘"
            } else {
                "复杂密码 · 使用系统键盘"
            },
            onClick = onChoosePasswordMode,
        )
    }

    SettingsSectionTitle("应用锁", "保护整个 KardLeaf")
    SettingsListGroup {
        SettingsActionRow(
            icon = Icons.Outlined.Lock,
            title = "应用密码",
            subtitle = if (hasAppPassword) "已设置 · 点击修改或移除" else "未设置 · 点击创建",
            onClick = { editTarget = SecurityPasswordTarget.APP },
        )
        SettingsToggleRow(
            icon = Icons.Outlined.Fingerprint,
            title = "指纹解锁",
            subtitle = when {
                !hasAppPassword -> "需要先设置应用密码"
                appBiometricEnabled -> "可在解锁页点击指纹按钮"
                else -> "使用系统录入的指纹快捷解锁"
            },
            checked = hasAppPassword && appBiometricEnabled,
            onCheckedChange = { enabled ->
                if (!hasAppPassword && enabled) {
                    editTarget = SecurityPasswordTarget.APP
                } else {
                    appBiometricEnabled = hasAppPassword && enabled
                    prefsManager.saveAppBiometricUnlockEnabled(appBiometricEnabled)
                }
            },
        )
    }

    SettingsSectionTitle("隐私空间", "单独保护隐私仓库中的笔记")
    SettingsListGroup {
        SettingsActionRow(
            icon = Icons.Outlined.Shield,
            title = "隐私密码",
            subtitle = if (hasPrivacyPassword) "已设置 · 点击修改或移除" else "未设置 · 首次进入时也可创建",
            onClick = { editTarget = SecurityPasswordTarget.PRIVACY },
        )
        SettingsToggleRow(
            icon = Icons.Outlined.Fingerprint,
            title = "隐私空间指纹解锁",
            subtitle = when {
                !hasPrivacyPassword -> "需要先设置隐私密码"
                privacyBiometricEnabled -> "可在隐私空间解锁页点击指纹按钮"
                else -> "使用系统录入的指纹快捷解锁"
            },
            checked = hasPrivacyPassword && privacyBiometricEnabled,
            onCheckedChange = { enabled ->
                if (!hasPrivacyPassword && enabled) {
                    editTarget = SecurityPasswordTarget.PRIVACY
                } else {
                    privacyBiometricEnabled = hasPrivacyPassword && enabled
                    prefsManager.savePrivacyBiometricUnlockEnabled(privacyBiometricEnabled)
                }
            },
        )
    }

    SettingsSectionDivider()
    SettingsPageText("指纹只作为快捷解锁方式。关闭或验证失败时，仍可使用对应密码。")
}

@Composable
private fun SecurityPasswordEditDialog(
    target: SecurityPasswordTarget,
    hasPassword: Boolean,
    passwordInputMode: PrefsManager.PasswordInputMode,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemoveRequest: () -> Unit,
) {
    var password by remember(target) { mutableStateOf("") }
    var confirmation by remember(target) { mutableStateOf("") }
    var error by remember(target) { mutableStateOf<String?>(null) }
    val simpleMode = passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE
    val keyboardOptions = KeyboardOptions(
        keyboardType = if (simpleMode) KeyboardType.NumberPassword else KeyboardType.Password,
    )
    fun normalize(value: String): String = if (simpleMode) value.filter(Char::isDigit).take(4) else value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPassword) "修改${target.passwordName}" else "设置${target.passwordName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (simpleMode) "设置 4 位数字密码" else "设置一个仅用于 ${target.title} 的密码",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = normalize(it)
                        error = null
                    },
                    label = { Text("新${target.passwordName}") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = normalize(it)
                        error = null
                    },
                    label = { Text("再次输入${target.passwordName}") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = keyboardOptions,
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error.orEmpty()) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        password.isBlank() || confirmation.isBlank() -> "请完整输入两次密码"
                        simpleMode && password.length != 4 -> "数字密码必须是 4 位"
                        password != confirmation -> "两次输入的密码不一致"
                        else -> null
                    }
                    if (error == null) onSave(password)
                },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (hasPassword) {
                    TextButton(onClick = onRemoveRequest) {
                        Text("移除密码", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun SecurityPasswordClearDialog(
    target: SecurityPasswordTarget,
    passwordInputMode: PrefsManager.PasswordInputMode,
    savedPasswordHash: String?,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    var password by remember(target) { mutableStateOf("") }
    var error by remember(target) { mutableStateOf<String?>(null) }
    val simpleMode = passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE
    val keyboardOptions = KeyboardOptions(
        keyboardType = if (simpleMode) KeyboardType.NumberPassword else KeyboardType.Password,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移除${target.passwordName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "请输入当前${target.passwordName}。移除后，对应的指纹解锁也会关闭。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = if (simpleMode) it.filter(Char::isDigit).take(4) else it
                        error = null
                    },
                    label = { Text("当前${target.passwordName}") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = keyboardOptions,
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error.orEmpty()) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        password.isBlank() -> "请输入当前密码"
                        hashPassword(password) != savedPasswordHash -> "密码错误"
                        else -> null
                    }
                    if (error == null) onConfirmed()
                },
            ) { Text("确认移除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
