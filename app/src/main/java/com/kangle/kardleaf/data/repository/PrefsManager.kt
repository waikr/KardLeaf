package com.kangle.kardleaf.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.kangle.kardleaf.BuildConfig
import com.kangle.kardleaf.data.repository.prefs.DashboardPreferences
import com.kangle.kardleaf.data.repository.prefs.EditorPreferences
import com.kangle.kardleaf.data.repository.prefs.PrivacyPreferences
import com.kangle.kardleaf.data.repository.prefs.SyncPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { newPrefs ->
            val oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
            if (newPrefs.all.isEmpty() && oldPrefs.all.isNotEmpty()) {
                newPrefs.edit().apply {
                    oldPrefs.all.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> putBoolean(key, value)
                            is Float -> putFloat(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is String -> putString(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }.apply()
            }
        }
    private val syncPreferences = SyncPreferences(context, prefs)
    private val privacyPreferences = PrivacyPreferences(prefs)
    private val editorPreferences = EditorPreferences(prefs)
    private val dashboardPreferences = DashboardPreferences(prefs)

    companion object {
        private const val PREFS_NAME = "kardleaf_prefs"
        private const val OLD_PREFS_NAME = "keepnotes_prefs"
        private const val KEY_ROOT_URI = "root_uri"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_SORT_DIRECTION = "sort_direction"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_TRASH_FOLDER_NAME = "trash_folder_name"
        private const val KEY_TRASH_SORT_ORDER = "trash_sort_order"
        private const val KEY_CARD_DENSITY = "card_density"
        private const val KEY_DRAWER_EDGE_WIDTH_DP = "drawer_edge_width_dp"
        private const val KEY_IMAGE_FOLDER = "image_folder"
        private const val KEY_IMAGE_FOLDER_URI = "image_folder_uri"
        private const val KEY_HIDDEN_FOLDER_PATHS = "hidden_folder_paths"
        private const val KEY_APP_THEME_STYLE = "app_theme_style"
        private const val KEY_APP_THEME_MODE = "app_theme_mode"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_CUSTOM_THEME_COLOR_ARGB = "custom_theme_color_argb"
        private const val KEY_THEME_BACKGROUND_COLOR = "theme_background_color"
        private const val KEY_CUSTOM_THEME_BACKGROUND_COLOR_ARGB = "custom_theme_background_color_argb"
        private const val KEY_MODERN_THEME_COLOR_STYLE = "modern_theme_color_style"
        private const val KEY_CLEAN_LIST_FEATURE_ICON_STYLE = "clean_list_feature_icon_style"
        private const val KEY_GLOBAL_CORNER_RADIUS_DP = "global_corner_radius_dp"
        private const val KEY_HOME_CORNER_RADIUS_DP = "home_corner_radius_dp"
        private const val KEY_LAST_FILTER_TYPE = "last_filter_type"
        private const val KEY_LAST_FILTER_LABEL = "last_filter_label"
        private const val KEY_RESTORE_LAST_FILTER = "restore_last_filter"
        private const val KEY_DEFAULT_START_LABEL = "default_start_label"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_DRAWER_ITEM_ORDER = "drawer_item_order"
        private const val KEY_DRAWER_HIDDEN_ITEMS = "drawer_hidden_items"
        private const val KEY_DRAWER_CATEGORY_ACTIONS_MIGRATED = "drawer_category_actions_migrated"
        private const val KEY_DRAWER_ITEM_LABEL_PREFIX = "drawer_item_label_"
        private const val KEY_DRAWER_STYLE = "drawer_style"
        private const val KEY_DRAWER_GROUP_START_ITEMS = "drawer_group_start_items"
        private const val KEY_DRAWER_AVATAR_URI = "drawer_avatar_uri"
        private const val KEY_SELECTION_TOOLBAR_ITEM_ORDER = "selection_toolbar_item_order"
        private const val KEY_SELECTION_TOOLBAR_MORE_ITEMS = "selection_toolbar_more_items"
        private const val KEY_SELECTION_TOOLBAR_HIDDEN_ITEMS = "selection_toolbar_hidden_items"
        private const val KEY_IMAGE_PATH_MODE = "image_path_mode"
        private const val KEY_RELATIVE_IMAGE_LOCATION = "relative_image_location"
        private const val KEY_AUTO_BACKUP_INTERVAL_DAYS = "auto_backup_interval_days"
        private const val KEY_AUTO_BACKUP_LAST_MS = "auto_backup_last_ms"
        private const val KEY_AUTO_BACKUP_DIR_URI = "auto_backup_dir_uri"
        private const val KEY_HISTORY_VERSION_LIMIT = "history_version_limit"
        private const val KEY_NOTE_SIDE_PANELS_ENABLED = "note_side_panels_enabled"
        private const val KEY_NOTE_SIDE_PANEL_OPEN_MODE = "note_side_panel_open_mode"
        private const val KEY_PREVIEW_DOUBLE_TAP_INTERVAL_MS = "preview_double_tap_interval_ms"
        private const val KEY_PREVIEW_THEME = "preview_theme"
        private const val KEY_TRASH_AUTO_CLEAN_DAYS = "trash_auto_clean_days"
        private const val KEY_PASSWORD_INPUT_MODE = "password_input_mode"
        private const val KEY_SHOW_YAML_TAGS_ON_LOOSE_CARDS = "show_yaml_tags_on_loose_cards"
        private const val KEY_SHOW_MODIFIED_DATE_ON_CARDS = "show_modified_date_on_cards"
        private const val KEY_CARD_MODIFIED_DATE_FORMAT = "card_modified_date_format"
        private const val KEY_SHOW_NOTE_TITLE_ON_CARDS = "show_note_title_on_cards"
        private const val KEY_SHOW_DATE_FILENAME_TITLE_ON_CARDS = "show_date_filename_title_on_cards"
        private const val KEY_SHOW_NOTE_DETAIL_TITLE = "show_note_detail_title"
        private const val KEY_SHOW_NOTE_DETAIL_FILE_INFO = "show_note_detail_file_info"
        private const val KEY_CUSTOM_HIDDEN_FILENAME_PATTERNS = "custom_hidden_filename_patterns"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_HOME_ACTION_STYLE = "home_action_style"
        private const val KEY_HOME_WEB_CLIP_ACTION_VISIBLE = "home_web_clip_action_visible"
        private const val KEY_HOME_BOTTOM_TOOLBAR_ITEM_ORDER = "home_bottom_toolbar_item_order"
        private const val KEY_HOME_BOTTOM_TOOLBAR_HIDDEN_ITEMS = "home_bottom_toolbar_hidden_items"
        private const val KEY_HOME_BOTTOM_TOOLBAR_DEFAULTS_V2_MIGRATED = "home_bottom_toolbar_defaults_v2_migrated"
        private const val KEY_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP = "home_bottom_toolbar_button_size_dp"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val KEY_APP_LOGGING_ENABLED = "app_logging_enabled"
        private const val KEY_AUTO_UPDATE_CHECK_ENABLED = "auto_update_check_enabled"
        private const val KEY_LAST_UPDATE_CHECK_DATE = "last_update_check_date"
        private const val KEY_UPDATE_CHECK_ETAG = "update_check_etag"
        private const val KEY_UPDATE_CHECK_CACHE = "update_check_cache"
        private const val KEY_SHOW_MARKDOWN_TASKS_IN_TASK_LIST = "show_markdown_tasks_in_task_list"
        private const val KEY_QUICK_NOTE_HIDDEN_DEFAULT_MIGRATED = "quick_note_hidden_default_migrated"
        val DEFAULT_APP_LOGGING_ENABLED = BuildConfig.KARDLEAF_DEV_VARIANT
        const val DEFAULT_AUTO_UPDATE_CHECK_ENABLED = true
        const val DEFAULT_WEBDAV_REALTIME_POLL_INTERVAL_MS = 1_000L
        const val DEFAULT_TRASH_FOLDER_NAME = ".trash"
        const val DEFAULT_QUICK_NOTE_FOLDER_NAME = "速记"
        const val LEGACY_DRAFT_FOLDER_NAME = "草稿"
        const val DEFAULT_IMAGE_FOLDER = "attachments"
        const val DEFAULT_DRAWER_EDGE_WIDTH_DP = 40
        const val DEFAULT_HISTORY_VERSION_LIMIT = 20
        const val DEFAULT_NOTE_SIDE_PANELS_ENABLED = true
        val DEFAULT_NOTE_SIDE_PANEL_OPEN_MODE = NoteSidePanelOpenMode.TOOLBAR
        const val DEFAULT_PREVIEW_DOUBLE_TAP_INTERVAL_MS = 180
        const val DEFAULT_PREVIEW_THEME = "FOLLOW_APP"
        const val DEFAULT_HIDDEN_DATE_FILENAME_PATTERN = "yyyy.MM.dd.HHmmss"
        const val DEFAULT_HIDDEN_COPY_FILENAME_PATTERN = "yyyy.MM.dd.HHmmss~副本*"
        const val DEFAULT_TRASH_AUTO_CLEAN_DAYS = 0
        const val DEFAULT_CARD_MODIFIED_DATE_FORMAT = "yyyy.MM.dd HH:mm"
        const val DEFAULT_EDITOR_KERNEL = "QUILLPAD_STYLE"
        const val DEFAULT_CODEMIRROR_LIVE_PREVIEW_ENABLED = true
        const val DEFAULT_EDITING_IMAGE_PREVIEW_ENABLED = true
        const val DEFAULT_AUTO_CODEMIRROR_THRESHOLD_CHARS = 100_000
        const val DEFAULT_EDITOR_FONT_SIZE_SP = 16f
        const val DEFAULT_EDITOR_LINE_HEIGHT_MULTIPLIER = 1.55f
        const val DEFAULT_EDITOR_LETTER_SPACING_SP = 0f
        const val DEFAULT_EDITOR_PARAGRAPH_SPACING_DP = 8f
        const val DEFAULT_EDITOR_FONT_FAMILY = "system"
        const val DEFAULT_APP_LANGUAGE = "zh"
        const val DEFAULT_EDITOR_BOTTOM_TOOLBAR_ALWAYS_VISIBLE = true
        const val DEFAULT_HOME_ACTION_STYLE = "BOTTOM_TOOLBAR"
        const val DEFAULT_HOME_WEB_CLIP_ACTION_VISIBLE = false
        const val DEFAULT_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP = 46
        const val THEME_CORNER_RADIUS_FOLLOW = -1
        const val MIN_THEME_CORNER_RADIUS_DP = 0
        const val MAX_THEME_CORNER_RADIUS_DP = 40
        const val MIN_AUTO_CODEMIRROR_THRESHOLD_CHARS = 10_000
        const val MAX_AUTO_CODEMIRROR_THRESHOLD_CHARS = 1_000_000
        const val MIN_EDITOR_FONT_SIZE_SP = 12f
        const val MAX_EDITOR_FONT_SIZE_SP = 30f
        const val MIN_EDITOR_LINE_HEIGHT_MULTIPLIER = 1.0f
        const val MAX_EDITOR_LINE_HEIGHT_MULTIPLIER = 2.5f
        const val MIN_EDITOR_LETTER_SPACING_SP = -1f
        const val MAX_EDITOR_LETTER_SPACING_SP = 3f
        const val MIN_EDITOR_PARAGRAPH_SPACING_DP = 0f
        const val MAX_EDITOR_PARAGRAPH_SPACING_DP = 32f
        const val MIN_HISTORY_VERSION_LIMIT = 0
        const val MAX_HISTORY_VERSION_LIMIT = 500
        const val MIN_PREVIEW_DOUBLE_TAP_INTERVAL_MS = 120
        const val MAX_PREVIEW_DOUBLE_TAP_INTERVAL_MS = 600
        const val MIN_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP = 36
        const val MAX_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP = 56
    }

    enum class SortOrder {
        DATE_MODIFIED,
        TITLE,
        CUSTOM,
    }

    enum class SortDirection {
        ASCENDING,
        DESCENDING,
    }

    enum class ViewMode {
        GRID,
        LIST,
    }

    enum class TrashSortOrder {
        FILE_NAME,
        DELETED_TIME,
    }

    enum class CardDensity {
        LOOSE,
        COMPACT,
    }

    enum class PasswordInputMode {
        SIMPLE,
        COMPLEX,
    }

    enum class NoteSidePanelOpenMode {
        GESTURE,
        TOOLBAR,
    }

    enum class EditorKernel {
        AUTO,
        NATIVE,
        CODEMIRROR_LIVE_PREVIEW,
        QUILLPAD_STYLE,
    }

    enum class HomeActionStyle {
        SIMPLE_NEW_BUTTON,
        BOTTOM_TOOLBAR,
    }

    enum class AppThemeStyle {
        CLASSIC,
        MODERN,
        NOW_IN_ANDROID,
        CLEAN_LIST,
        GITHUB_DARK,
        DRACULA,
    }

    enum class ModernThemeColorStyle {
        CLASSIC,
        MODERN,
    }

    enum class CleanListFeatureIconStyle {
        MODERN,
        SIMPLE,
    }

    enum class AppThemeMode {
        SYSTEM,
        LIGHT,
        DARK,
    }

    /** Markdown 预览状态的配色主题；FOLLOW_APP 表示实时跟随应用主题色。 */
    enum class PreviewTheme {
        FOLLOW_APP,
        GITHUB,
        NEWSPRINT,
        VUE,
        ONE,
        DRACULA,
        NORD,
        SOLARIZED,
    }

    fun savePreviewTheme(theme: PreviewTheme) {
        prefs.edit().putString(KEY_PREVIEW_THEME, theme.name).apply()
    }

    fun getPreviewTheme(): PreviewTheme {
        val name = prefs.getString(KEY_PREVIEW_THEME, DEFAULT_PREVIEW_THEME)
        return runCatching { PreviewTheme.valueOf(name ?: DEFAULT_PREVIEW_THEME) }
            .getOrDefault(PreviewTheme.FOLLOW_APP)
    }

    fun saveEditorKernel(kernel: EditorKernel) = editorPreferences.saveKernel(kernel)

    fun getEditorKernel(): EditorKernel = editorPreferences.getKernel()

    fun saveCodeMirrorLivePreviewEnabled(enabled: Boolean) = editorPreferences.saveCodeMirrorLivePreviewEnabled(enabled)

    fun isCodeMirrorLivePreviewEnabled(): Boolean = editorPreferences.isCodeMirrorLivePreviewEnabled()

    fun saveEditingImagePreviewEnabled(enabled: Boolean) = editorPreferences.saveEditingImagePreviewEnabled(enabled)

    fun isEditingImagePreviewEnabled(): Boolean = editorPreferences.isEditingImagePreviewEnabled()

    fun saveAutoCodeMirrorThresholdChars(chars: Int) = editorPreferences.saveAutoCodeMirrorThresholdChars(chars)

    fun getAutoCodeMirrorThresholdChars(): Int = editorPreferences.getAutoCodeMirrorThresholdChars()

    fun saveEditorFontSizeSp(sizeSp: Float) = editorPreferences.saveFontSizeSp(sizeSp)

    fun getEditorFontSizeSp(): Float = editorPreferences.getFontSizeSp()

    fun saveEditorLineHeightMultiplier(multiplier: Float) = editorPreferences.saveLineHeightMultiplier(multiplier)

    fun getEditorLineHeightMultiplier(): Float = editorPreferences.getLineHeightMultiplier()

    fun saveEditorLetterSpacingSp(spacingSp: Float) = editorPreferences.saveLetterSpacingSp(spacingSp)

    fun getEditorLetterSpacingSp(): Float = editorPreferences.getLetterSpacingSp()

    fun saveEditorParagraphSpacingDp(spacingDp: Float) = editorPreferences.saveParagraphSpacingDp(spacingDp)

    fun getEditorParagraphSpacingDp(): Float = editorPreferences.getParagraphSpacingDp()

    fun saveEditorFontFamily(fontFamily: String) = editorPreferences.saveFontFamily(fontFamily)

    fun getEditorFontFamily(): String = editorPreferences.getFontFamily()

    fun saveAppLanguage(language: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE, normalizeAppLanguage(language)).apply()
    }

    fun getAppLanguage(): String = normalizeAppLanguage(prefs.getString(KEY_APP_LANGUAGE, DEFAULT_APP_LANGUAGE))

    fun saveEditorBottomToolbarAlwaysVisible(enabled: Boolean) = editorPreferences.saveBottomToolbarAlwaysVisible(enabled)

    fun isEditorBottomToolbarAlwaysVisible(): Boolean = editorPreferences.isBottomToolbarAlwaysVisible()

    fun saveHomeActionStyle(style: HomeActionStyle) {
        prefs.edit().putString(KEY_HOME_ACTION_STYLE, style.name).apply()
    }

    fun getHomeActionStyle(): HomeActionStyle {
        val name = prefs.getString(KEY_HOME_ACTION_STYLE, DEFAULT_HOME_ACTION_STYLE)
        return runCatching { HomeActionStyle.valueOf(name ?: DEFAULT_HOME_ACTION_STYLE) }
            .getOrDefault(HomeActionStyle.BOTTOM_TOOLBAR)
    }

    fun saveHomeWebClipActionVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_WEB_CLIP_ACTION_VISIBLE, visible).apply()
    }

    fun isHomeWebClipActionVisible(): Boolean =
        prefs.getBoolean(KEY_HOME_WEB_CLIP_ACTION_VISIBLE, DEFAULT_HOME_WEB_CLIP_ACTION_VISIBLE)

    enum class ThemeColor {
        BLUE,
        GREEN,
        PURPLE,
        PINK,
        AMBER,
        RED,
        CUSTOM,
    }

    enum class ThemeBackgroundColor {
        WHITE,
        BLUE,
        GREEN,
        PURPLE,
        PINK,
        AMBER,
        GRAY,
        CUSTOM,
    }

    fun saveAppThemeStyle(style: AppThemeStyle) {
        if (style == AppThemeStyle.NOW_IN_ANDROID) {
            prefs.edit()
                .putString(KEY_APP_THEME_STYLE, AppThemeStyle.MODERN.name)
                .putString(KEY_MODERN_THEME_COLOR_STYLE, ModernThemeColorStyle.MODERN.name)
                .apply()
            return
        }
        prefs.edit().putString(KEY_APP_THEME_STYLE, style.name).apply()
    }

    fun getAppThemeStyle(): AppThemeStyle {
        val name = prefs.getString(KEY_APP_THEME_STYLE, AppThemeStyle.CLEAN_LIST.name)
        val style = runCatching { AppThemeStyle.valueOf(name ?: AppThemeStyle.CLEAN_LIST.name) }
            .getOrDefault(AppThemeStyle.CLEAN_LIST)
        if (style == AppThemeStyle.NOW_IN_ANDROID) {
            prefs.edit()
                .putString(KEY_APP_THEME_STYLE, AppThemeStyle.MODERN.name)
                .putString(KEY_MODERN_THEME_COLOR_STYLE, ModernThemeColorStyle.MODERN.name)
                .apply()
            return AppThemeStyle.MODERN
        }
        return style
    }

    fun saveModernThemeColorStyle(style: ModernThemeColorStyle) {
        prefs.edit().putString(KEY_MODERN_THEME_COLOR_STYLE, style.name).apply()
    }

    fun getModernThemeColorStyle(): ModernThemeColorStyle {
        val legacyThemeStyle = prefs.getString(KEY_APP_THEME_STYLE, AppThemeStyle.CLEAN_LIST.name)
        if (legacyThemeStyle == AppThemeStyle.NOW_IN_ANDROID.name) {
            return ModernThemeColorStyle.MODERN
        }
        val name = prefs.getString(KEY_MODERN_THEME_COLOR_STYLE, ModernThemeColorStyle.CLASSIC.name)
        return runCatching { ModernThemeColorStyle.valueOf(name ?: ModernThemeColorStyle.CLASSIC.name) }
            .getOrDefault(ModernThemeColorStyle.CLASSIC)
    }

    fun saveCleanListFeatureIconStyle(style: CleanListFeatureIconStyle) {
        prefs.edit().putString(KEY_CLEAN_LIST_FEATURE_ICON_STYLE, style.name).apply()
    }

    fun getCleanListFeatureIconStyle(): CleanListFeatureIconStyle {
        val name = prefs.getString(KEY_CLEAN_LIST_FEATURE_ICON_STYLE, CleanListFeatureIconStyle.MODERN.name)
        return runCatching { CleanListFeatureIconStyle.valueOf(name ?: CleanListFeatureIconStyle.MODERN.name) }
            .getOrDefault(CleanListFeatureIconStyle.MODERN)
    }

    fun saveAppThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_APP_THEME_MODE, mode.name).apply()
    }

    fun getAppThemeMode(): AppThemeMode {
        val name = prefs.getString(KEY_APP_THEME_MODE, AppThemeMode.SYSTEM.name)
        return runCatching { AppThemeMode.valueOf(name ?: AppThemeMode.SYSTEM.name) }
            .getOrDefault(AppThemeMode.SYSTEM)
    }

    data class FolderSortSettings(
        val order: SortOrder,
        val direction: SortDirection,
    )

    enum class WebDavSyncScope {
        ROOM_DATABASE,
        DATABASE_AND_VAULT,
    }

    enum class WebDavSyncMode {
        FULL_PACKAGE,
        INCREMENTAL,
    }

    data class WebDavSettings(
        val serverUrl: String,
        val username: String,
        val password: String,
        val remoteFolder: String,
        val scope: WebDavSyncScope,
        val mode: WebDavSyncMode,
    )

    fun getWebDavSettings(): WebDavSettings = syncPreferences.getSettings()

    fun saveWebDavSettings(settings: WebDavSettings): Boolean = syncPreferences.saveSettings(settings)

    fun getWebDavIncrementalLastUploadMs(): Long = syncPreferences.getIncrementalLastUploadMs()

    fun saveWebDavIncrementalLastUploadMs(value: Long) = syncPreferences.saveIncrementalLastUploadMs(value)

    fun isWebDavRealtimeSyncEnabled(): Boolean = syncPreferences.isRealtimeSyncEnabled()

    fun saveWebDavRealtimeSyncEnabled(enabled: Boolean) = syncPreferences.saveRealtimeSyncEnabled(enabled)

    fun getWebDavRealtimePollIntervalMs(): Long = syncPreferences.getRealtimePollIntervalMs()

    fun saveWebDavRealtimePollIntervalMs(intervalMs: Long) = syncPreferences.saveRealtimePollIntervalMs(intervalMs)

    fun markWebDavRealtimeLocalDirty() = syncPreferences.markRealtimeLocalDirty()

    fun getWebDavRealtimeLocalDirtyMs(): Long = syncPreferences.getRealtimeLocalDirtyMs()

    fun clearWebDavRealtimeLocalDirtyIfUnchanged(dirtyMs: Long) =
        syncPreferences.clearRealtimeLocalDirtyIfUnchanged(dirtyMs)

    fun getWebDavRealtimeKnownRemoteMarker(): String = syncPreferences.getRealtimeKnownRemoteMarker()

    fun saveWebDavRealtimeKnownRemoteMarker(marker: String) = syncPreferences.saveRealtimeKnownRemoteMarker(marker)

    fun getWebDavRealtimeLastUploadRemoteMarker(): String = syncPreferences.getRealtimeLastUploadRemoteMarker()

    fun saveWebDavRealtimeLastUploadRemoteMarker(marker: String) =
        syncPreferences.saveRealtimeLastUploadRemoteMarker(marker)

    fun getWebDavFileSyncSnapshot(): String = syncPreferences.getFileSyncSnapshot()

    fun saveWebDavFileSyncSnapshot(snapshot: String) = syncPreferences.saveFileSyncSnapshot(snapshot)

    fun getWebDavPendingConflicts(): List<String> = syncPreferences.getPendingConflicts()

    fun saveWebDavPendingConflicts(value: String) = syncPreferences.savePendingConflicts(value)

    fun clearWebDavPendingConflicts() = syncPreferences.clearPendingConflicts()

    fun getWebDavSyncLogs(): List<String> = syncPreferences.getSyncLogs()

    fun appendWebDavSyncLog(message: String) = syncPreferences.appendSyncLog(message)

    fun clearWebDavSyncLogs() = syncPreferences.clearSyncLogs()

    fun saveSortOrder(order: SortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    fun getSortOrder(): SortOrder {
        val name = prefs.getString(KEY_SORT_ORDER, SortOrder.DATE_MODIFIED.name)
        return try {
            SortOrder.valueOf(name ?: SortOrder.DATE_MODIFIED.name)
        } catch (e: Exception) {
            SortOrder.DATE_MODIFIED
        }
    }

    fun saveSortDirection(direction: SortDirection) {
        prefs.edit().putString(KEY_SORT_DIRECTION, direction.name).apply()
    }

    fun getSortDirection(): SortDirection {
        val name = prefs.getString(KEY_SORT_DIRECTION, SortDirection.DESCENDING.name)
        return try {
            SortDirection.valueOf(name ?: SortDirection.DESCENDING.name)
        } catch (e: Exception) {
            SortDirection.DESCENDING
        }
    }

    fun saveFolderSortSettings(
        folder: String,
        settings: FolderSortSettings,
    ) = dashboardPreferences.saveFolderSortSettings(folder, settings)

    fun getFolderSortSettings(folder: String): FolderSortSettings? = dashboardPreferences.getFolderSortSettings(folder)

    fun clearFolderSortSettings(folder: String) = dashboardPreferences.clearFolderSortSettings(folder)

    fun saveFolderCustomOrder(
        folder: String,
        paths: Collection<String>,
    ) = dashboardPreferences.saveFolderCustomOrder(folder, paths)

    fun getFolderCustomOrder(folder: String): List<String> = dashboardPreferences.getFolderCustomOrder(folder)

    fun clearFolderCustomOrder(folder: String) = dashboardPreferences.clearFolderCustomOrder(folder)

    fun saveFolderDisplayOrder(
        parentFolder: String,
        folderPaths: Collection<String>,
    ) = dashboardPreferences.saveFolderDisplayOrder(parentFolder, folderPaths)

    fun getFolderDisplayOrder(parentFolder: String): List<String> = dashboardPreferences.getFolderDisplayOrder(parentFolder)

    fun clearFolderDisplayOrder(parentFolder: String) = dashboardPreferences.clearFolderDisplayOrder(parentFolder)

    fun getPinnedNotePaths(): Set<String> = dashboardPreferences.getPinnedNotePaths()

    fun replacePinnedNotePaths(paths: Collection<String>) = dashboardPreferences.replacePinnedNotePaths(paths)

    fun isNotePinned(path: String): Boolean = dashboardPreferences.isNotePinned(path)

    fun setNotePinned(
        path: String,
        pinned: Boolean,
    ) = dashboardPreferences.setNotePinned(path, pinned)

    fun replacePinnedNotePath(
        oldPath: String,
        newPath: String,
    ) = dashboardPreferences.replacePinnedNotePath(oldPath, newPath)

    fun getFavoriteNotePaths(): Set<String> = dashboardPreferences.getFavoriteNotePaths()

    fun replaceFavoriteNotePaths(paths: Collection<String>) = dashboardPreferences.replaceFavoriteNotePaths(paths)

    fun isNoteFavorite(path: String): Boolean = dashboardPreferences.isNoteFavorite(path)

    fun setNoteFavorite(
        path: String,
        favorite: Boolean,
    ) = dashboardPreferences.setNoteFavorite(path, favorite)

    fun replaceFavoriteNotePath(
        oldPath: String,
        newPath: String,
    ) = dashboardPreferences.replaceFavoriteNotePath(oldPath, newPath)

    fun saveViewMode(mode: ViewMode) {
        prefs.edit().putString(KEY_VIEW_MODE, mode.name).apply()
    }

    fun getViewMode(): ViewMode {
        val name = prefs.getString(KEY_VIEW_MODE, ViewMode.LIST.name)
        return try {
            ViewMode.valueOf(name ?: ViewMode.LIST.name)
        } catch (e: Exception) {
            ViewMode.LIST
        }
    }

    fun saveRootUri(uri: String) {
        prefs.edit().putString(KEY_ROOT_URI, uri).apply()
    }

    fun getRootUri(): String? {
        return prefs.getString(KEY_ROOT_URI, null)
    }

    fun saveTrashFolderName(name: String) {
        val normalized = normalizeTrashFolderName(name)
        prefs.edit().putString(KEY_TRASH_FOLDER_NAME, normalized).apply()
    }

    fun getTrashFolderName(): String {
        return normalizeTrashFolderName(prefs.getString(KEY_TRASH_FOLDER_NAME, DEFAULT_TRASH_FOLDER_NAME).orEmpty())
    }

    fun saveTrashSortOrder(order: TrashSortOrder) {
        prefs.edit().putString(KEY_TRASH_SORT_ORDER, order.name).apply()
    }

    fun getTrashSortOrder(): TrashSortOrder {
        val name = prefs.getString(KEY_TRASH_SORT_ORDER, TrashSortOrder.DELETED_TIME.name)
        return try {
            TrashSortOrder.valueOf(name ?: TrashSortOrder.DELETED_TIME.name)
        } catch (e: Exception) {
            TrashSortOrder.DELETED_TIME
        }
    }

    fun saveCardDensity(density: CardDensity) {
        prefs.edit().putString(KEY_CARD_DENSITY, density.name).apply()
    }

    fun getCardDensity(): CardDensity {
        val name = prefs.getString(KEY_CARD_DENSITY, CardDensity.LOOSE.name)
        return try {
            CardDensity.valueOf(name ?: CardDensity.LOOSE.name)
        } catch (e: Exception) {
            CardDensity.LOOSE
        }
    }

    fun saveDrawerEdgeWidthDp(widthDp: Int) {
        prefs.edit()
            .putInt(KEY_DRAWER_EDGE_WIDTH_DP, widthDp.coerceIn(24, 160))
            .apply()
    }

    fun getDrawerEdgeWidthDp(): Int = prefs.getInt(KEY_DRAWER_EDGE_WIDTH_DP, DEFAULT_DRAWER_EDGE_WIDTH_DP).coerceIn(24, 160)

    fun saveNoteSidePanelsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTE_SIDE_PANELS_ENABLED, enabled).apply()
    }

    fun isNoteSidePanelsEnabled(): Boolean = prefs.getBoolean(KEY_NOTE_SIDE_PANELS_ENABLED, DEFAULT_NOTE_SIDE_PANELS_ENABLED)

    fun saveNoteSidePanelOpenMode(mode: NoteSidePanelOpenMode) {
        prefs.edit().putString(KEY_NOTE_SIDE_PANEL_OPEN_MODE, mode.name).apply()
    }

    fun getNoteSidePanelOpenMode(): NoteSidePanelOpenMode {
        val name = prefs.getString(KEY_NOTE_SIDE_PANEL_OPEN_MODE, DEFAULT_NOTE_SIDE_PANEL_OPEN_MODE.name)
        return runCatching { NoteSidePanelOpenMode.valueOf(name ?: DEFAULT_NOTE_SIDE_PANEL_OPEN_MODE.name) }
            .getOrDefault(DEFAULT_NOTE_SIDE_PANEL_OPEN_MODE)
    }


    fun saveImageFolder(folder: String) {
        prefs.edit().putString(KEY_IMAGE_FOLDER, normalizeImageFolder(folder)).apply()
    }

    fun getImageFolder(): String = normalizeImageFolder(prefs.getString(KEY_IMAGE_FOLDER, DEFAULT_IMAGE_FOLDER).orEmpty())

    fun saveImageFolderUri(uri: String?) {
        prefs.edit().apply {
            if (uri.isNullOrBlank()) {
                remove(KEY_IMAGE_FOLDER_URI)
            } else {
                putString(KEY_IMAGE_FOLDER_URI, uri)
            }
        }.apply()
    }

    fun getImageFolderUri(): String? = prefs.getString(KEY_IMAGE_FOLDER_URI, null)

    fun getHiddenFolderPaths(): Set<String> {
        val saved = prefs.getStringSet(KEY_HIDDEN_FOLDER_PATHS, null)
        val source = if (prefs.getBoolean(KEY_QUICK_NOTE_HIDDEN_DEFAULT_MIGRATED, false)) {
            saved ?: setOf(getImageFolder(), DEFAULT_QUICK_NOTE_FOLDER_NAME, LEGACY_DRAFT_FOLDER_NAME)
        } else {
            val migrated = (saved ?: setOf(getImageFolder())).toMutableSet().apply {
                add(DEFAULT_QUICK_NOTE_FOLDER_NAME)
                add(LEGACY_DRAFT_FOLDER_NAME)
            }
            prefs.edit()
                .putStringSet(KEY_HIDDEN_FOLDER_PATHS, migrated)
                .putBoolean(KEY_QUICK_NOTE_HIDDEN_DEFAULT_MIGRATED, true)
                .apply()
            migrated
        }
        return source
            .map(::normalizeNotePath)
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun saveHiddenFolderPaths(paths: Collection<String>) {
        prefs.edit()
            .putStringSet(
                KEY_HIDDEN_FOLDER_PATHS,
                paths.map(::normalizeNotePath)
                    .filter { it.isNotBlank() }
                    .toSet(),
            )
            .apply()
    }

    fun saveShowMarkdownTasksInTaskList(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_MARKDOWN_TASKS_IN_TASK_LIST, enabled).apply()
    }

    fun isShowMarkdownTasksInTaskListEnabled(): Boolean =
        prefs.getBoolean(KEY_SHOW_MARKDOWN_TASKS_IN_TASK_LIST, false)

    fun saveThemeColor(color: ThemeColor) {
        prefs.edit().putString(KEY_THEME_COLOR, color.name).apply()
    }

    fun getThemeColor(): ThemeColor {
        val name = prefs.getString(KEY_THEME_COLOR, ThemeColor.BLUE.name)
        return runCatching { ThemeColor.valueOf(name ?: ThemeColor.BLUE.name) }.getOrDefault(ThemeColor.BLUE)
    }

    fun saveCustomThemeColorArgb(argb: Int) {
        prefs.edit().putInt(KEY_CUSTOM_THEME_COLOR_ARGB, argb).apply()
    }

    fun getCustomThemeColorArgb(): Int =
        prefs.getInt(KEY_CUSTOM_THEME_COLOR_ARGB, 0xFF3B82F6.toInt())

    fun saveThemeBackgroundColor(color: ThemeBackgroundColor) {
        prefs.edit().putString(KEY_THEME_BACKGROUND_COLOR, color.name).apply()
    }

    fun getThemeBackgroundColor(): ThemeBackgroundColor {
        val name = prefs.getString(KEY_THEME_BACKGROUND_COLOR, ThemeBackgroundColor.WHITE.name)
        return runCatching {
            ThemeBackgroundColor.valueOf(name ?: ThemeBackgroundColor.WHITE.name)
        }.getOrDefault(ThemeBackgroundColor.WHITE)
    }

    fun saveCustomThemeBackgroundColorArgb(argb: Int) {
        prefs.edit().putInt(KEY_CUSTOM_THEME_BACKGROUND_COLOR_ARGB, argb).apply()
    }

    fun getCustomThemeBackgroundColorArgb(): Int =
        prefs.getInt(KEY_CUSTOM_THEME_BACKGROUND_COLOR_ARGB, 0xFFFFFFFF.toInt())

    fun saveGlobalCornerRadiusDp(radiusDp: Int) {
        prefs.edit().putInt(KEY_GLOBAL_CORNER_RADIUS_DP, normalizeCornerRadiusDp(radiusDp)).apply()
    }

    fun getGlobalCornerRadiusDp(): Int =
        normalizeCornerRadiusDp(prefs.getInt(KEY_GLOBAL_CORNER_RADIUS_DP, THEME_CORNER_RADIUS_FOLLOW))

    fun saveHomeCornerRadiusDp(radiusDp: Int) {
        prefs.edit().putInt(KEY_HOME_CORNER_RADIUS_DP, normalizeCornerRadiusDp(radiusDp)).apply()
    }

    fun getHomeCornerRadiusDp(): Int =
        normalizeCornerRadiusDp(prefs.getInt(KEY_HOME_CORNER_RADIUS_DP, THEME_CORNER_RADIUS_FOLLOW))

    private fun normalizeCornerRadiusDp(radiusDp: Int): Int =
        if (radiusDp == THEME_CORNER_RADIUS_FOLLOW) {
            THEME_CORNER_RADIUS_FOLLOW
        } else {
            radiusDp.coerceIn(MIN_THEME_CORNER_RADIUS_DP, MAX_THEME_CORNER_RADIUS_DP)
        }

    private fun normalizeTrashFolderName(name: String): String {
        return name
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { DEFAULT_TRASH_FOLDER_NAME }
    }

    private fun normalizeImageFolder(folder: String): String {
        return folder
            .replace("\\", "/")
            .split("/")
            .map { segment -> segment.trim().replace(Regex("[\\\\:*?\"<>|]"), "_") }
            .filter { it.isNotBlank() && it != "." }
            .joinToString("/")
            .ifBlank { DEFAULT_IMAGE_FOLDER }
    }

    private fun normalizeNotePath(path: String): String =
        path
            .trim()
            .replace("\\", "/")
            .trim('/')

    fun saveLastFilterType(type: String) {
        prefs.edit().putString(KEY_LAST_FILTER_TYPE, type).apply()
    }

    fun getLastFilterType(): String = prefs.getString(KEY_LAST_FILTER_TYPE, "ALL") ?: "ALL"

    fun saveLastFilterLabel(label: String) {
        prefs.edit().putString(KEY_LAST_FILTER_LABEL, label).apply()
    }

    fun getLastFilterLabel(): String = prefs.getString(KEY_LAST_FILTER_LABEL, "") ?: ""

    fun saveRestoreLastFilterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESTORE_LAST_FILTER, enabled).apply()
    }

    fun isRestoreLastFilterEnabled(): Boolean = prefs.getBoolean(KEY_RESTORE_LAST_FILTER, true)

    fun saveDefaultStartLabel(label: String) {
        prefs.edit().putString(KEY_DEFAULT_START_LABEL, label).apply()
    }

    fun getDefaultStartLabel(): String = prefs.getString(KEY_DEFAULT_START_LABEL, "") ?: ""

    fun hasSeenOnboarding(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)

    fun setHasSeenOnboarding(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, seen).apply()
    }

    // region 侧边栏功能项编辑
    enum class DrawerItemId {
        ALL_NOTES, RECENT, TASKS, NEW_DRAWING, FAVORITES, DRAFTS, TAGS, RANDOM, FILES, DATES, IMAGES, RELATIONSHIP_GRAPH, ARCHIVE, TRASH, PRIVACY, ONBOARDING, SETTINGS;

        companion object {
            val DEFAULT_ORDER: List<DrawerItemId> =
                listOf(ALL_NOTES, RECENT, FAVORITES, PRIVACY, DRAFTS, ARCHIVE, DATES, IMAGES, TAGS, RANDOM, RELATIONSHIP_GRAPH, TRASH, TASKS, NEW_DRAWING, FILES, ONBOARDING, SETTINGS)
            val DEFAULT_GROUP_START_ITEMS: Set<DrawerItemId> = setOf(FAVORITES, DATES)
            val DEFAULT_HIDDEN_ITEMS: Set<DrawerItemId> = setOf(TASKS, NEW_DRAWING, FILES, ONBOARDING)
            val LEGACY_DEFAULT_ORDER: List<DrawerItemId> =
                listOf(ALL_NOTES, RECENT, FAVORITES, PRIVACY, DRAFTS, ARCHIVE, DATES, IMAGES, RELATIONSHIP_GRAPH, TAGS, RANDOM, TASKS, NEW_DRAWING, FILES, TRASH, ONBOARDING, SETTINGS)
            val LEGACY_DEFAULT_HIDDEN_ITEMS: Set<DrawerItemId> = setOf(TASKS, NEW_DRAWING, FILES, TRASH, ONBOARDING)
        }
    }

    enum class DrawerStyle {
        MINIMAL_TEXT, ICON_BOX, GROUPED_CARD, DATA_CARD;

        companion object {
            val DEFAULT: DrawerStyle = DATA_CARD
        }
    }

    fun getDrawerStyle(): DrawerStyle {
        val raw = prefs.getString(KEY_DRAWER_STYLE, null) ?: return DrawerStyle.DEFAULT
        return when (val style = runCatching { DrawerStyle.valueOf(raw) }.getOrDefault(DrawerStyle.DEFAULT)) {
            DrawerStyle.ICON_BOX, DrawerStyle.GROUPED_CARD -> DrawerStyle.MINIMAL_TEXT
            else -> style
        }
    }

    fun saveDrawerStyle(style: DrawerStyle) {
        prefs.edit().putString(KEY_DRAWER_STYLE, style.name).apply()
    }

    fun saveDrawerAvatarUri(uri: String?) {
        prefs.edit().apply {
            if (uri.isNullOrBlank()) {
                remove(KEY_DRAWER_AVATAR_URI)
            } else {
                putString(KEY_DRAWER_AVATAR_URI, uri)
            }
        }.apply()
    }

    fun getDrawerAvatarUri(): String? = prefs.getString(KEY_DRAWER_AVATAR_URI, null)

    fun getDrawerGroupStartItems(): Set<DrawerItemId> {
        val raw = prefs.getStringSet(KEY_DRAWER_GROUP_START_ITEMS, null)
            ?: return getDefaultDrawerGroupStartItems()
        return raw.mapNotNull { runCatching { DrawerItemId.valueOf(it) }.getOrNull() }
            .filter { it != DrawerItemId.DEFAULT_ORDER.first() }
            .toSet()
    }

    fun saveDrawerGroupStartItems(groupStartItems: Set<DrawerItemId>) {
        val safeItems = groupStartItems
            .filter { it in DrawerItemId.DEFAULT_ORDER && it != DrawerItemId.DEFAULT_ORDER.first() }
            .map { it.name }
            .toSet()
        prefs.edit().putStringSet(KEY_DRAWER_GROUP_START_ITEMS, safeItems).apply()
    }

    private fun getDefaultDrawerGroupStartItems(): Set<DrawerItemId> =
        DrawerItemId.DEFAULT_GROUP_START_ITEMS

    fun getDrawerItemOrder(): List<DrawerItemId> {
        val raw = prefs.getString(KEY_DRAWER_ITEM_ORDER, null) ?: return DrawerItemId.DEFAULT_ORDER
        val ids = raw.split(",").mapNotNull { runCatching { DrawerItemId.valueOf(it) }.getOrNull() }
        if (ids == DrawerItemId.LEGACY_DEFAULT_ORDER) {
            saveDrawerItemOrder(DrawerItemId.DEFAULT_ORDER)
            return DrawerItemId.DEFAULT_ORDER
        }
        val result = ids.toMutableList()
        if (DrawerItemId.RANDOM !in result) {
            val tagsIndex = result.indexOf(DrawerItemId.TAGS)
            result.add(if (tagsIndex >= 0) tagsIndex + 1 else result.size, DrawerItemId.RANDOM)
        }
        if (DrawerItemId.RELATIONSHIP_GRAPH !in result) {
            val randomIndex = result.indexOf(DrawerItemId.RANDOM)
            result.add(if (randomIndex >= 0) randomIndex + 1 else result.size, DrawerItemId.RELATIONSHIP_GRAPH)
        }
        if (DrawerItemId.TRASH !in result) {
            val relationshipIndex = result.indexOf(DrawerItemId.RELATIONSHIP_GRAPH)
            result.add(if (relationshipIndex >= 0) relationshipIndex + 1 else result.size, DrawerItemId.TRASH)
        }
        DrawerItemId.DEFAULT_ORDER.forEach { if (it !in result) result.add(it) }
        return result
    }

    fun saveDrawerItemOrder(order: List<DrawerItemId>) {
        prefs.edit().putString(KEY_DRAWER_ITEM_ORDER, order.joinToString(",") { it.name }).apply()
    }

    fun getHiddenDrawerItems(): Set<DrawerItemId> {
        migrateDrawerCategoryActionsIfNeeded()
        val savedHiddenItems = prefs.getStringSet(KEY_DRAWER_HIDDEN_ITEMS, null)
            ?.mapNotNull { runCatching { DrawerItemId.valueOf(it) }.getOrNull() }
            ?.toSet()
        val normalizedHiddenItems = when (savedHiddenItems) {
            null -> getDefaultHiddenDrawerItems()
            DrawerItemId.LEGACY_DEFAULT_HIDDEN_ITEMS -> getDefaultHiddenDrawerItems().also { saveHiddenDrawerItems(it) }
            else -> savedHiddenItems
        }
        return (normalizedHiddenItems + getBottomToolbarDrawerHiddenItems())
            .filter { canHideDrawerItem(it) }
            .toSet()
    }

    fun saveHiddenDrawerItems(hidden: Set<DrawerItemId>) {
        val safeHidden = hidden.filter { canHideDrawerItem(it) }
        prefs.edit().putStringSet(KEY_DRAWER_HIDDEN_ITEMS, safeHidden.map { it.name }.toSet()).apply()
    }

    private fun getDefaultHiddenDrawerItems(): Set<DrawerItemId> =
        DrawerItemId.DEFAULT_HIDDEN_ITEMS

    private fun migrateDrawerCategoryActionsIfNeeded() {
        if (prefs.getBoolean(KEY_DRAWER_CATEGORY_ACTIONS_MIGRATED, false)) return
        val savedHiddenItems = prefs.getStringSet(KEY_DRAWER_HIDDEN_ITEMS, null)
        prefs.edit().apply {
            if (savedHiddenItems != null) {
                putStringSet(
                    KEY_DRAWER_HIDDEN_ITEMS,
                    savedHiddenItems + setOf(DrawerItemId.NEW_DRAWING.name, DrawerItemId.FILES.name),
                )
            }
            putBoolean(KEY_DRAWER_CATEGORY_ACTIONS_MIGRATED, true)
        }.apply()
    }

    private fun getBottomToolbarDrawerHiddenItems(): Set<DrawerItemId> {
        if (getHomeActionStyle() != HomeActionStyle.BOTTOM_TOOLBAR) {
            return emptySet()
        }
        return getHomeBottomToolbarItemOrder()
            .filter { it !in getHomeBottomToolbarHiddenItems() }
            .mapNotNull { it.toDrawerItemId() }
            .toSet()
    }

    private fun canHideDrawerItem(itemId: DrawerItemId): Boolean =
        itemId != DrawerItemId.SETTINGS

    private fun HomeBottomToolbarItemId.toDrawerItemId(): DrawerItemId? =
        when (this) {
            HomeBottomToolbarItemId.ALL_NOTES -> DrawerItemId.ALL_NOTES
            HomeBottomToolbarItemId.RECENT -> DrawerItemId.RECENT
            HomeBottomToolbarItemId.TASKS -> DrawerItemId.TASKS
            HomeBottomToolbarItemId.FAVORITES -> DrawerItemId.FAVORITES
            HomeBottomToolbarItemId.DRAFTS -> DrawerItemId.DRAFTS
            HomeBottomToolbarItemId.TAGS -> DrawerItemId.TAGS
            HomeBottomToolbarItemId.FILES -> DrawerItemId.FILES
            HomeBottomToolbarItemId.DATES -> DrawerItemId.DATES
            HomeBottomToolbarItemId.IMAGES -> DrawerItemId.IMAGES
            HomeBottomToolbarItemId.ARCHIVE -> DrawerItemId.ARCHIVE
            HomeBottomToolbarItemId.TRASH -> DrawerItemId.TRASH
            HomeBottomToolbarItemId.PRIVACY -> DrawerItemId.PRIVACY
            HomeBottomToolbarItemId.SETTINGS,
            HomeBottomToolbarItemId.NEW_NOTE,
            HomeBottomToolbarItemId.NEW_DRAFT,
            HomeBottomToolbarItemId.NEW_DRAWING,
            HomeBottomToolbarItemId.NEW_FOLDER -> null
        }

    fun getDrawerItemLabel(
        itemId: DrawerItemId,
        defaultLabel: String,
    ): String = prefs.getString(drawerItemLabelKey(itemId), null)?.takeIf { it.isNotBlank() } ?: defaultLabel

    fun saveDrawerItemLabel(
        itemId: DrawerItemId,
        label: String,
    ) {
        prefs.edit().apply {
            val trimmed = label.trim()
            if (trimmed.isBlank()) {
                remove(drawerItemLabelKey(itemId))
            } else {
                putString(drawerItemLabelKey(itemId), trimmed)
            }
        }.apply()
    }

    private fun drawerItemLabelKey(itemId: DrawerItemId): String = KEY_DRAWER_ITEM_LABEL_PREFIX + itemId.name
    // endregion

    // region 首页底部工具栏功能项编辑
    enum class HomeBottomToolbarItemId {
        TASKS, NEW_NOTE, NEW_DRAFT, NEW_DRAWING, NEW_FOLDER, ALL_NOTES, RECENT, FAVORITES, DRAFTS, TAGS, FILES, DATES, IMAGES, ARCHIVE, TRASH, PRIVACY, SETTINGS;

        companion object {
            val DEFAULT_ORDER: List<HomeBottomToolbarItemId> =
                listOf(FILES, TASKS, NEW_NOTE, NEW_DRAFT, SETTINGS, NEW_FOLDER, ALL_NOTES, RECENT, FAVORITES, DRAFTS, TAGS, DATES, IMAGES, ARCHIVE, TRASH, PRIVACY)
            val DEFAULT_HIDDEN_ITEMS: Set<HomeBottomToolbarItemId> =
                setOf(NEW_FOLDER, ALL_NOTES, RECENT, FAVORITES, DRAFTS, TAGS, DATES, IMAGES, ARCHIVE, TRASH, PRIVACY)
        }
    }

    fun getHomeBottomToolbarItemOrder(): List<HomeBottomToolbarItemId> {
        migrateHomeBottomToolbarDefaultsIfNeeded()
        val raw = prefs.getString(KEY_HOME_BOTTOM_TOOLBAR_ITEM_ORDER, null) ?: return HomeBottomToolbarItemId.DEFAULT_ORDER
        val ids = raw.split(",")
            .mapNotNull { runCatching { HomeBottomToolbarItemId.valueOf(it) }.getOrNull() }
            .filter { it in HomeBottomToolbarItemId.DEFAULT_ORDER }
        val result = ids.distinct().toMutableList()
        HomeBottomToolbarItemId.DEFAULT_ORDER.forEach { if (it !in result) result.add(it) }
        return result
    }

    fun saveHomeBottomToolbarItemOrder(order: List<HomeBottomToolbarItemId>) {
        val normalized = order.filter { it in HomeBottomToolbarItemId.DEFAULT_ORDER }.distinct().toMutableList()
        HomeBottomToolbarItemId.DEFAULT_ORDER.forEach { if (it !in normalized) normalized.add(it) }
        prefs.edit().putString(KEY_HOME_BOTTOM_TOOLBAR_ITEM_ORDER, normalized.joinToString(",") { it.name }).apply()
    }

    fun getHomeBottomToolbarHiddenItems(): Set<HomeBottomToolbarItemId> {
        migrateHomeBottomToolbarDefaultsIfNeeded()
        return prefs.getStringSet(KEY_HOME_BOTTOM_TOOLBAR_HIDDEN_ITEMS, null)
            ?.mapNotNull { runCatching { HomeBottomToolbarItemId.valueOf(it) }.getOrNull() }
            ?.toSet() ?: HomeBottomToolbarItemId.DEFAULT_HIDDEN_ITEMS
    }

    private fun migrateHomeBottomToolbarDefaultsIfNeeded() {
        if (prefs.getBoolean(KEY_HOME_BOTTOM_TOOLBAR_DEFAULTS_V2_MIGRATED, false)) return
        val savedOrder = prefs.getString(KEY_HOME_BOTTOM_TOOLBAR_ITEM_ORDER, null)
            ?.split(",")
            ?.mapNotNull { runCatching { HomeBottomToolbarItemId.valueOf(it) }.getOrNull() }
        val savedHidden = prefs.getStringSet(KEY_HOME_BOTTOM_TOOLBAR_HIDDEN_ITEMS, null)
            ?.mapNotNull { runCatching { HomeBottomToolbarItemId.valueOf(it) }.getOrNull() }
            ?.toSet()
        val oldDefaultOrder = listOf(
            HomeBottomToolbarItemId.TASKS,
            HomeBottomToolbarItemId.NEW_DRAWING,
            HomeBottomToolbarItemId.NEW_NOTE,
            HomeBottomToolbarItemId.NEW_DRAFT,
            HomeBottomToolbarItemId.SETTINGS,
            HomeBottomToolbarItemId.NEW_FOLDER,
            HomeBottomToolbarItemId.ALL_NOTES,
            HomeBottomToolbarItemId.RECENT,
            HomeBottomToolbarItemId.FAVORITES,
            HomeBottomToolbarItemId.DRAFTS,
            HomeBottomToolbarItemId.TAGS,
            HomeBottomToolbarItemId.FILES,
            HomeBottomToolbarItemId.DATES,
            HomeBottomToolbarItemId.IMAGES,
            HomeBottomToolbarItemId.ARCHIVE,
            HomeBottomToolbarItemId.TRASH,
            HomeBottomToolbarItemId.PRIVACY,
        )
        val oldDefaultHidden = setOf(
            HomeBottomToolbarItemId.NEW_FOLDER,
            HomeBottomToolbarItemId.ALL_NOTES,
            HomeBottomToolbarItemId.RECENT,
            HomeBottomToolbarItemId.FAVORITES,
            HomeBottomToolbarItemId.DRAFTS,
            HomeBottomToolbarItemId.TAGS,
            HomeBottomToolbarItemId.FILES,
            HomeBottomToolbarItemId.DATES,
            HomeBottomToolbarItemId.IMAGES,
            HomeBottomToolbarItemId.ARCHIVE,
            HomeBottomToolbarItemId.TRASH,
            HomeBottomToolbarItemId.PRIVACY,
        )
        prefs.edit().apply {
            if (savedOrder == oldDefaultOrder) {
                putString(
                    KEY_HOME_BOTTOM_TOOLBAR_ITEM_ORDER,
                    HomeBottomToolbarItemId.DEFAULT_ORDER.joinToString(",") { it.name },
                )
            }
            if (savedHidden == oldDefaultHidden) {
                putStringSet(
                    KEY_HOME_BOTTOM_TOOLBAR_HIDDEN_ITEMS,
                    HomeBottomToolbarItemId.DEFAULT_HIDDEN_ITEMS.map { it.name }.toSet(),
                )
            }
            putBoolean(KEY_HOME_BOTTOM_TOOLBAR_DEFAULTS_V2_MIGRATED, true)
        }.apply()
    }

    fun saveHomeBottomToolbarHiddenItems(items: Set<HomeBottomToolbarItemId>) {
        prefs.edit().putStringSet(KEY_HOME_BOTTOM_TOOLBAR_HIDDEN_ITEMS, items.map { it.name }.toSet()).apply()
    }

    fun getHomeBottomToolbarButtonSizeDp(): Int =
        prefs.getInt(KEY_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP, DEFAULT_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP)
            .coerceIn(MIN_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP, MAX_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP)

    fun saveHomeBottomToolbarButtonSizeDp(sizeDp: Int) {
        prefs.edit()
            .putInt(
                KEY_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP,
                sizeDp.coerceIn(MIN_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP, MAX_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP),
            )
            .apply()
    }

    // region 长按选择栏功能项编辑
    enum class SelectionToolbarItemId {
        MOVE, COPY, PIN, FAVORITE, TAG, ARCHIVE, PROPERTIES, SHARE, PRIVACY, DELETE;

        companion object {
            val DEFAULT_ORDER: List<SelectionToolbarItemId> =
                listOf(MOVE, COPY, PIN, FAVORITE, TAG, ARCHIVE, PROPERTIES, SHARE, PRIVACY, DELETE)
            val DEFAULT_MORE_ITEMS: Set<SelectionToolbarItemId> =
                setOf(ARCHIVE, PROPERTIES, SHARE, PRIVACY, DELETE)
            val DEFAULT_HIDDEN_ITEMS: Set<SelectionToolbarItemId> = emptySet()
        }
    }

    fun getSelectionToolbarItemOrder(): List<SelectionToolbarItemId> {
        val raw = prefs.getString(KEY_SELECTION_TOOLBAR_ITEM_ORDER, null) ?: return SelectionToolbarItemId.DEFAULT_ORDER
        val ids = raw.split(",").mapNotNull { runCatching { SelectionToolbarItemId.valueOf(it) }.getOrNull() }
        val result = ids.toMutableList()
        SelectionToolbarItemId.DEFAULT_ORDER.forEach { if (it !in result) result.add(it) }
        return result
    }

    fun saveSelectionToolbarItemOrder(order: List<SelectionToolbarItemId>) {
        val normalized = order.distinct().toMutableList()
        SelectionToolbarItemId.DEFAULT_ORDER.forEach { if (it !in normalized) normalized.add(it) }
        prefs.edit().putString(KEY_SELECTION_TOOLBAR_ITEM_ORDER, normalized.joinToString(",") { it.name }).apply()
    }

    fun getSelectionToolbarMoreItems(): Set<SelectionToolbarItemId> =
        prefs.getStringSet(KEY_SELECTION_TOOLBAR_MORE_ITEMS, null)
            ?.mapNotNull { runCatching { SelectionToolbarItemId.valueOf(it) }.getOrNull() }
            ?.toSet() ?: SelectionToolbarItemId.DEFAULT_MORE_ITEMS

    fun saveSelectionToolbarMoreItems(items: Set<SelectionToolbarItemId>) {
        prefs.edit().putStringSet(KEY_SELECTION_TOOLBAR_MORE_ITEMS, items.map { it.name }.toSet()).apply()
    }

    fun getSelectionToolbarHiddenItems(): Set<SelectionToolbarItemId> =
        prefs.getStringSet(KEY_SELECTION_TOOLBAR_HIDDEN_ITEMS, null)
            ?.mapNotNull { runCatching { SelectionToolbarItemId.valueOf(it) }.getOrNull() }
            ?.toSet() ?: SelectionToolbarItemId.DEFAULT_HIDDEN_ITEMS

    fun saveSelectionToolbarHiddenItems(items: Set<SelectionToolbarItemId>) {
        prefs.edit().putStringSet(KEY_SELECTION_TOOLBAR_HIDDEN_ITEMS, items.map { it.name }.toSet()).apply()
    }
    // endregion

    // region 笔记详情顶部栏功能项编辑
    enum class EditorTopToolbarItemId {
        MINDMAP, LABEL, OUTLINE, REMARKS, SEARCH, EDIT, HISTORY, PRIVACY, ARCHIVE, DELETE, MORE;

        companion object {
            val DEFAULT_ORDER: List<EditorTopToolbarItemId> =
                listOf(SEARCH, EDIT, OUTLINE, REMARKS, HISTORY, MINDMAP, PRIVACY, ARCHIVE, DELETE, MORE, LABEL)
            val DEFAULT_MORE_ITEMS: Set<EditorTopToolbarItemId> = setOf(HISTORY, MINDMAP, PRIVACY, ARCHIVE, DELETE)
            val DEFAULT_HIDDEN_ITEMS: Set<EditorTopToolbarItemId> = setOf(LABEL)
        }
    }

    fun getEditorTopToolbarItemOrder(): List<EditorTopToolbarItemId> = editorPreferences.getTopToolbarItemOrder()

    fun saveEditorTopToolbarItemOrder(order: List<EditorTopToolbarItemId>) = editorPreferences.saveTopToolbarItemOrder(order)

    fun getEditorTopToolbarMoreItems(): Set<EditorTopToolbarItemId> = editorPreferences.getTopToolbarMoreItems()

    fun saveEditorTopToolbarMoreItems(items: Set<EditorTopToolbarItemId>) = editorPreferences.saveTopToolbarMoreItems(items)

    fun getEditorTopToolbarHiddenItems(): Set<EditorTopToolbarItemId> = editorPreferences.getTopToolbarHiddenItems()

    fun saveEditorTopToolbarHiddenItems(items: Set<EditorTopToolbarItemId>) = editorPreferences.saveTopToolbarHiddenItems(items)
    // endregion

    // region 应用密码 / 隐私密码
    fun getAppPasswordHash(): String? = privacyPreferences.getAppPasswordHash()

    fun saveAppPasswordHash(hash: String?) = privacyPreferences.saveAppPasswordHash(hash)

    fun isAppBiometricUnlockEnabled(): Boolean = privacyPreferences.isAppBiometricUnlockEnabled()

    fun saveAppBiometricUnlockEnabled(enabled: Boolean) = privacyPreferences.saveAppBiometricUnlockEnabled(enabled)

    fun getPrivacyPasswordHash(): String? = privacyPreferences.getPrivacyPasswordHash()

    fun savePrivacyPasswordHash(hash: String?) = privacyPreferences.savePrivacyPasswordHash(hash)


    fun isPrivacyBiometricUnlockEnabled(): Boolean = privacyPreferences.isPrivacyBiometricUnlockEnabled()

    fun savePrivacyBiometricUnlockEnabled(enabled: Boolean) =
        privacyPreferences.savePrivacyBiometricUnlockEnabled(enabled)
    // endregion

    // region 图片路径模式
    enum class ImagePathMode { ROOT, RELATIVE }

    enum class RelativeImageLocation { CURRENT_NOTE_FOLDER, FIXED_IMAGE_FOLDER }

    fun getImagePathMode(): ImagePathMode {
        val name = prefs.getString(KEY_IMAGE_PATH_MODE, ImagePathMode.ROOT.name)
        return runCatching { ImagePathMode.valueOf(name ?: ImagePathMode.ROOT.name) }.getOrDefault(ImagePathMode.ROOT)
    }

    fun saveImagePathMode(mode: ImagePathMode) {
        prefs.edit().putString(KEY_IMAGE_PATH_MODE, mode.name).apply()
    }

    fun getRelativeImageLocation(): RelativeImageLocation {
        val name = prefs.getString(KEY_RELATIVE_IMAGE_LOCATION, RelativeImageLocation.CURRENT_NOTE_FOLDER.name)
        return runCatching {
            RelativeImageLocation.valueOf(name ?: RelativeImageLocation.CURRENT_NOTE_FOLDER.name)
        }.getOrDefault(RelativeImageLocation.CURRENT_NOTE_FOLDER)
    }

    fun saveRelativeImageLocation(location: RelativeImageLocation) {
        prefs.edit().putString(KEY_RELATIVE_IMAGE_LOCATION, location.name).apply()
    }
    // endregion

    // region 自动备份
    fun getAutoBackupIntervalDays(): Int = prefs.getInt(KEY_AUTO_BACKUP_INTERVAL_DAYS, 0)

    fun saveAutoBackupIntervalDays(days: Int) {
        prefs.edit().putInt(KEY_AUTO_BACKUP_INTERVAL_DAYS, days.coerceAtLeast(0)).apply()
    }

    fun getAutoBackupLastMs(): Long = prefs.getLong(KEY_AUTO_BACKUP_LAST_MS, 0L)

    fun saveAutoBackupLastMs(ms: Long) {
        prefs.edit().putLong(KEY_AUTO_BACKUP_LAST_MS, ms).apply()
    }

    fun getAutoBackupDirUri(): String? = prefs.getString(KEY_AUTO_BACKUP_DIR_URI, null)

    fun saveAutoBackupDirUri(uri: String?) {
        prefs.edit().apply { if (uri.isNullOrBlank()) remove(KEY_AUTO_BACKUP_DIR_URI) else putString(KEY_AUTO_BACKUP_DIR_URI, uri) }.apply()
    }
    // endregion

    // region 历史版本
    fun getHistoryVersionLimit(): Int =
        prefs.getInt(KEY_HISTORY_VERSION_LIMIT, DEFAULT_HISTORY_VERSION_LIMIT)
            .coerceIn(MIN_HISTORY_VERSION_LIMIT, MAX_HISTORY_VERSION_LIMIT)

    fun saveHistoryVersionLimit(limit: Int) {
        prefs.edit()
            .putInt(KEY_HISTORY_VERSION_LIMIT, limit.coerceIn(MIN_HISTORY_VERSION_LIMIT, MAX_HISTORY_VERSION_LIMIT))
            .apply()
    }
    // endregion

    // region 编辑/预览交互
    fun getPreviewDoubleTapIntervalMs(): Int =
        prefs.getInt(KEY_PREVIEW_DOUBLE_TAP_INTERVAL_MS, DEFAULT_PREVIEW_DOUBLE_TAP_INTERVAL_MS)
            .coerceIn(MIN_PREVIEW_DOUBLE_TAP_INTERVAL_MS, MAX_PREVIEW_DOUBLE_TAP_INTERVAL_MS)

    fun savePreviewDoubleTapIntervalMs(intervalMs: Int) {
        prefs.edit()
            .putInt(
                KEY_PREVIEW_DOUBLE_TAP_INTERVAL_MS,
                intervalMs.coerceIn(MIN_PREVIEW_DOUBLE_TAP_INTERVAL_MS, MAX_PREVIEW_DOUBLE_TAP_INTERVAL_MS),
            )
            .apply()
    }
    // endregion

    // region 回收站自动清理
    fun getTrashAutoCleanDays(): Int = prefs.getInt(KEY_TRASH_AUTO_CLEAN_DAYS, DEFAULT_TRASH_AUTO_CLEAN_DAYS).coerceIn(0, 365)

    fun saveTrashAutoCleanDays(days: Int) {
        prefs.edit().putInt(KEY_TRASH_AUTO_CLEAN_DAYS, days.coerceIn(0, 365)).apply()
    }
    // endregion

    // region 密码输入方式
    fun getPasswordInputMode(): PasswordInputMode {
        val name = prefs.getString(KEY_PASSWORD_INPUT_MODE, PasswordInputMode.SIMPLE.name)
        return runCatching { PasswordInputMode.valueOf(name ?: PasswordInputMode.SIMPLE.name) }
            .getOrDefault(PasswordInputMode.SIMPLE)
    }

    fun savePasswordInputMode(mode: PasswordInputMode) {
        prefs.edit().putString(KEY_PASSWORD_INPUT_MODE, mode.name).apply()
    }
    // endregion

    // region 首页卡片显示
    fun isLooseCardYamlTagsVisible(): Boolean = prefs.getBoolean(KEY_SHOW_YAML_TAGS_ON_LOOSE_CARDS, false)

    fun saveLooseCardYamlTagsVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_YAML_TAGS_ON_LOOSE_CARDS, visible).apply()
    }

    fun isModifiedDateOnCardsVisible(): Boolean = prefs.getBoolean(KEY_SHOW_MODIFIED_DATE_ON_CARDS, false)

    fun saveModifiedDateOnCardsVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_MODIFIED_DATE_ON_CARDS, visible).apply()
    }

    fun getCardModifiedDateFormat(): String {
        val saved = prefs.getString(KEY_CARD_MODIFIED_DATE_FORMAT, DEFAULT_CARD_MODIFIED_DATE_FORMAT)
            ?.trim()
            .orEmpty()
        return saved.takeIf { isDateFormatUsable(it) } ?: DEFAULT_CARD_MODIFIED_DATE_FORMAT
    }

    fun saveCardModifiedDateFormat(format: String): Boolean {
        val normalized = format.trim()
        if (!isDateFormatUsable(normalized)) return false
        prefs.edit().putString(KEY_CARD_MODIFIED_DATE_FORMAT, normalized).apply()
        return true
    }

    fun isDateFormatUsable(format: String): Boolean {
        if (format.isBlank()) return false
        return runCatching { SimpleDateFormat(format, Locale.getDefault()).format(Date()) }.isSuccess
    }

    fun isNoteTitleOnCardsVisible(): Boolean = prefs.getBoolean(KEY_SHOW_NOTE_TITLE_ON_CARDS, true)

    fun saveNoteTitleOnCardsVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NOTE_TITLE_ON_CARDS, visible).apply()
    }

    fun isDateFilenameTitleOnCardsVisible(): Boolean = prefs.getBoolean(KEY_SHOW_DATE_FILENAME_TITLE_ON_CARDS, true)

    fun saveDateFilenameTitleOnCardsVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DATE_FILENAME_TITLE_ON_CARDS, visible).apply()
    }

    fun isNoteDetailTitleVisible(): Boolean = prefs.getBoolean(KEY_SHOW_NOTE_DETAIL_TITLE, true)

    fun saveNoteDetailTitleVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NOTE_DETAIL_TITLE, visible).apply()
    }

    fun isNoteDetailFileInfoVisible(): Boolean = prefs.getBoolean(KEY_SHOW_NOTE_DETAIL_FILE_INFO, true)

    fun saveNoteDetailFileInfoVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NOTE_DETAIL_FILE_INFO, visible).apply()
    }

    fun getCustomHiddenFilenamePatterns(): List<String> {
        val saved = prefs.getString(KEY_CUSTOM_HIDDEN_FILENAME_PATTERNS, null)
        return saved
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultHiddenFilenamePatterns()
    }

    fun saveCustomHiddenFilenamePatterns(patterns: List<String>) {
        prefs.edit()
            .putString(
                KEY_CUSTOM_HIDDEN_FILENAME_PATTERNS,
                patterns.map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("\n"),
            )
            .apply()
    }

    fun wasNotificationPermissionRequested(): Boolean =
        prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)

    fun saveNotificationPermissionRequested() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
    }

    fun isAppLoggingEnabled(): Boolean =
        prefs.getBoolean(KEY_APP_LOGGING_ENABLED, DEFAULT_APP_LOGGING_ENABLED)

    fun saveAppLoggingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOGGING_ENABLED, enabled).apply()
    }

    fun isAutoUpdateCheckEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_UPDATE_CHECK_ENABLED, DEFAULT_AUTO_UPDATE_CHECK_ENABLED)

    fun saveAutoUpdateCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE_CHECK_ENABLED, enabled).apply()
    }

    fun hasCheckedForUpdatesToday(): Boolean =
        prefs.getString(KEY_LAST_UPDATE_CHECK_DATE, null) == currentLocalDateKey()

    fun markUpdateCheckAttemptToday() {
        prefs.edit().putString(KEY_LAST_UPDATE_CHECK_DATE, currentLocalDateKey()).apply()
    }

    fun getUpdateCheckEtag(): String? = prefs.getString(KEY_UPDATE_CHECK_ETAG, null)

    fun getUpdateCheckCache(): String? = prefs.getString(KEY_UPDATE_CHECK_CACHE, null)

    fun saveUpdateCheckCache(etag: String?, responseBody: String) {
        prefs.edit().apply {
            if (etag.isNullOrBlank()) remove(KEY_UPDATE_CHECK_ETAG) else putString(KEY_UPDATE_CHECK_ETAG, etag)
            putString(KEY_UPDATE_CHECK_CACHE, responseBody)
        }.apply()
    }

    private fun currentLocalDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun defaultHiddenFilenamePatterns(): List<String> =
        listOf(DEFAULT_HIDDEN_DATE_FILENAME_PATTERN, DEFAULT_HIDDEN_COPY_FILENAME_PATTERN)
    // endregion

    private fun normalizeAppLanguage(language: String?): String =
        when (language?.trim()?.lowercase(Locale.ROOT)) {
            "en" -> "en"
            else -> DEFAULT_APP_LANGUAGE
        }

}
