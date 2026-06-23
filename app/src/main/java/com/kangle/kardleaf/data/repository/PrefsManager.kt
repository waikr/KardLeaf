package com.kangle.kardleaf.data.repository

import android.content.Context
import android.content.SharedPreferences

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

    companion object {
        private const val PREFS_NAME = "kardleaf_prefs"
        private const val OLD_PREFS_NAME = "keepnotes_prefs"
        private const val KEY_ROOT_URI = "root_uri"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_SORT_DIRECTION = "sort_direction"
        private const val KEY_FOLDER_SORT_PREFIX = "folder_sort_"
        private const val KEY_FOLDER_CUSTOM_ORDER_PREFIX = "folder_custom_order_"
        private const val KEY_FOLDER_DISPLAY_ORDER_PREFIX = "folder_display_order_"
        private const val KEY_PINNED_NOTE_PATHS = "pinned_note_paths"
        private const val KEY_FAVORITE_NOTE_PATHS = "favorite_note_paths"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_TRASH_FOLDER_NAME = "trash_folder_name"
        private const val KEY_TRASH_SORT_ORDER = "trash_sort_order"
        private const val KEY_CARD_DENSITY = "card_density"
        private const val KEY_DRAWER_EDGE_WIDTH_DP = "drawer_edge_width_dp"
        private const val KEY_IMAGE_FOLDER = "image_folder"
        private const val KEY_IMAGE_FOLDER_URI = "image_folder_uri"
        private const val KEY_HIDDEN_FOLDER_PATHS = "hidden_folder_paths"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_THEME_BACKGROUND_COLOR = "theme_background_color"
        private const val KEY_LAST_FILTER_TYPE = "last_filter_type"
        private const val KEY_LAST_FILTER_LABEL = "last_filter_label"
        private const val KEY_RESTORE_LAST_FILTER = "restore_last_filter"
        private const val KEY_DEFAULT_START_LABEL = "default_start_label"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_DRAWER_ITEM_ORDER = "drawer_item_order"
        private const val KEY_DRAWER_HIDDEN_ITEMS = "drawer_hidden_items"
        private const val KEY_DRAWER_ITEM_LABEL_PREFIX = "drawer_item_label_"
        private const val KEY_SELECTION_TOOLBAR_ITEM_ORDER = "selection_toolbar_item_order"
        private const val KEY_SELECTION_TOOLBAR_MORE_ITEMS = "selection_toolbar_more_items"
        private const val KEY_APP_PASSWORD = "app_password_hash"
        private const val KEY_PRIVACY_PASSWORD = "privacy_password_hash"
        private const val KEY_SAFETY_WORD = "safety_word_hash"
        private const val KEY_APP_BIOMETRIC_UNLOCK = "app_biometric_unlock"
        private const val KEY_PRIVACY_BIOMETRIC_UNLOCK = "privacy_biometric_unlock"
        private const val KEY_IMAGE_PATH_MODE = "image_path_mode"
        private const val KEY_RELATIVE_IMAGE_LOCATION = "relative_image_location"
        private const val KEY_AUTO_BACKUP_INTERVAL_DAYS = "auto_backup_interval_days"
        private const val KEY_AUTO_BACKUP_LAST_MS = "auto_backup_last_ms"
        private const val KEY_AUTO_BACKUP_DIR_URI = "auto_backup_dir_uri"
        private const val KEY_HISTORY_VERSION_LIMIT = "history_version_limit"
        private const val KEY_NOTE_SIDE_PANELS_ENABLED = "note_side_panels_enabled"
        private const val KEY_PREVIEW_DOUBLE_TAP_INTERVAL_MS = "preview_double_tap_interval_ms"
        private const val KEY_TRASH_AUTO_CLEAN_DAYS = "trash_auto_clean_days"
        private const val KEY_PASSWORD_INPUT_MODE = "password_input_mode"
        private const val KEY_SHOW_YAML_TAGS_ON_LOOSE_CARDS = "show_yaml_tags_on_loose_cards"
        private const val KEY_SHOW_MODIFIED_DATE_ON_CARDS = "show_modified_date_on_cards"
        private const val KEY_SHOW_NOTE_TITLE_ON_CARDS = "show_note_title_on_cards"
        private const val KEY_SHOW_DATE_FILENAME_TITLE_ON_CARDS = "show_date_filename_title_on_cards"
        private const val KEY_CUSTOM_HIDDEN_FILENAME_PATTERNS = "custom_hidden_filename_patterns"
        const val DEFAULT_TRASH_FOLDER_NAME = ".trash"
        const val DEFAULT_DRAFT_FOLDER_NAME = "草稿"
        const val DEFAULT_IMAGE_FOLDER = "attachments"
        const val DEFAULT_DRAWER_EDGE_WIDTH_DP = 40
        const val DEFAULT_HISTORY_VERSION_LIMIT = 20
        const val DEFAULT_NOTE_SIDE_PANELS_ENABLED = true
        const val DEFAULT_PREVIEW_DOUBLE_TAP_INTERVAL_MS = 180
        const val DEFAULT_HIDDEN_DATE_FILENAME_PATTERN = "yyyy.MM.dd.HHmmss"
        const val DEFAULT_HIDDEN_COPY_FILENAME_PATTERN = "yyyy.MM.dd.HHmmss~副本"
        const val DEFAULT_TRASH_AUTO_CLEAN_DAYS = 0
        const val MIN_HISTORY_VERSION_LIMIT = 1
        const val MAX_HISTORY_VERSION_LIMIT = 500
        const val MIN_PREVIEW_DOUBLE_TAP_INTERVAL_MS = 120
        const val MAX_PREVIEW_DOUBLE_TAP_INTERVAL_MS = 600
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

    enum class ThemeColor {
        BLUE,
        GREEN,
        PURPLE,
        PINK,
        AMBER,
        RED,
    }

    enum class ThemeBackgroundColor {
        WHITE,
        BLUE,
        GREEN,
        PURPLE,
        PINK,
        AMBER,
        GRAY,
    }

    data class FolderSortSettings(
        val order: SortOrder,
        val direction: SortDirection,
    )

    fun saveSortOrder(order: SortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    fun getSortOrder(): SortOrder {
        val name = prefs.getString(KEY_SORT_ORDER, SortOrder.DATE_MODIFIED.name)
        return try {
            val order = SortOrder.valueOf(name ?: SortOrder.DATE_MODIFIED.name)
            if (order == SortOrder.CUSTOM) SortOrder.DATE_MODIFIED else order
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
    ) {
        prefs.edit()
            .putString(folderSortKey(folder), "${settings.order.name}|${settings.direction.name}")
            .apply()
    }

    fun getFolderSortSettings(folder: String): FolderSortSettings? {
        val raw = prefs.getString(folderSortKey(folder), null) ?: return null
        val parts = raw.split("|")
        return runCatching {
            FolderSortSettings(
                order = SortOrder.valueOf(parts.getOrNull(0) ?: SortOrder.DATE_MODIFIED.name),
                direction = SortDirection.valueOf(parts.getOrNull(1) ?: SortDirection.DESCENDING.name),
            )
        }.getOrNull()
    }

    fun clearFolderSortSettings(folder: String) {
        prefs.edit().remove(folderSortKey(folder)).apply()
    }

    fun saveFolderCustomOrder(
        folder: String,
        paths: Collection<String>,
    ) {
        val normalizedPaths = paths
            .map(::normalizeNotePath)
            .filter { it.isNotBlank() }
            .distinct()
        prefs.edit()
            .putString(folderCustomOrderKey(folder), normalizedPaths.joinToString("\n"))
            .apply()
    }

    fun getFolderCustomOrder(folder: String): List<String> {
        return prefs.getString(folderCustomOrderKey(folder), null)
            .orEmpty()
            .lineSequence()
            .map(::normalizeNotePath)
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    fun clearFolderCustomOrder(folder: String) {
        prefs.edit().remove(folderCustomOrderKey(folder)).apply()
    }

    fun saveFolderDisplayOrder(
        parentFolder: String,
        folderPaths: Collection<String>,
    ) {
        val normalizedPaths = folderPaths
            .map(::normalizeNotePath)
            .filter { it.isNotBlank() }
            .distinct()
        prefs.edit()
            .putString(folderDisplayOrderKey(parentFolder), normalizedPaths.joinToString("\n"))
            .apply()
    }

    fun getFolderDisplayOrder(parentFolder: String): List<String> {
        return prefs.getString(folderDisplayOrderKey(parentFolder), null)
            .orEmpty()
            .lineSequence()
            .map(::normalizeNotePath)
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    fun clearFolderDisplayOrder(parentFolder: String) {
        prefs.edit().remove(folderDisplayOrderKey(parentFolder)).apply()
    }

    fun getPinnedNotePaths(): Set<String> {
        return prefs.getStringSet(KEY_PINNED_NOTE_PATHS, emptySet()).orEmpty()
    }

    fun replacePinnedNotePaths(paths: Collection<String>) {
        prefs.edit()
            .putStringSet(KEY_PINNED_NOTE_PATHS, paths.normalizedNotePathSet())
            .apply()
    }

    fun isNotePinned(path: String): Boolean {
        return getPinnedNotePaths().contains(normalizeNotePath(path))
    }

    fun setNotePinned(
        path: String,
        pinned: Boolean,
    ) {
        val normalized = normalizeNotePath(path)
        if (normalized.isBlank()) return
        val paths = getPinnedNotePaths().toMutableSet()
        if (pinned) {
            paths.add(normalized)
        } else {
            paths.remove(normalized)
        }
        prefs.edit().putStringSet(KEY_PINNED_NOTE_PATHS, paths).apply()
    }

    fun replacePinnedNotePath(
        oldPath: String,
        newPath: String,
    ) {
        val oldNormalized = normalizeNotePath(oldPath)
        val newNormalized = normalizeNotePath(newPath)
        if (oldNormalized.isBlank() || newNormalized.isBlank()) return
        val paths = getPinnedNotePaths().toMutableSet()
        if (paths.remove(oldNormalized)) {
            paths.add(newNormalized)
            prefs.edit().putStringSet(KEY_PINNED_NOTE_PATHS, paths).apply()
        }
    }

    fun getFavoriteNotePaths(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_NOTE_PATHS, emptySet()).orEmpty()
    }

    fun replaceFavoriteNotePaths(paths: Collection<String>) {
        prefs.edit()
            .putStringSet(KEY_FAVORITE_NOTE_PATHS, paths.normalizedNotePathSet())
            .apply()
    }

    fun isNoteFavorite(path: String): Boolean {
        return getFavoriteNotePaths().contains(normalizeNotePath(path))
    }

    fun setNoteFavorite(
        path: String,
        favorite: Boolean,
    ) {
        val normalized = normalizeNotePath(path)
        if (normalized.isBlank()) return
        val paths = getFavoriteNotePaths().toMutableSet()
        if (favorite) {
            paths.add(normalized)
        } else {
            paths.remove(normalized)
        }
        prefs.edit().putStringSet(KEY_FAVORITE_NOTE_PATHS, paths).apply()
    }

    fun replaceFavoriteNotePath(
        oldPath: String,
        newPath: String,
    ) {
        val oldNormalized = normalizeNotePath(oldPath)
        val newNormalized = normalizeNotePath(newPath)
        if (oldNormalized.isBlank() || newNormalized.isBlank()) return
        val paths = getFavoriteNotePaths().toMutableSet()
        if (paths.remove(oldNormalized)) {
            paths.add(newNormalized)
            prefs.edit().putStringSet(KEY_FAVORITE_NOTE_PATHS, paths).apply()
        }
    }

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
        val source = saved ?: setOf(getImageFolder())
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

    fun saveThemeColor(color: ThemeColor) {
        prefs.edit().putString(KEY_THEME_COLOR, color.name).apply()
    }

    fun getThemeColor(): ThemeColor {
        val name = prefs.getString(KEY_THEME_COLOR, ThemeColor.BLUE.name)
        return runCatching { ThemeColor.valueOf(name ?: ThemeColor.BLUE.name) }.getOrDefault(ThemeColor.BLUE)
    }

    fun saveThemeBackgroundColor(color: ThemeBackgroundColor) {
        prefs.edit().putString(KEY_THEME_BACKGROUND_COLOR, color.name).apply()
    }

    fun getThemeBackgroundColor(): ThemeBackgroundColor {
        val name = prefs.getString(KEY_THEME_BACKGROUND_COLOR, ThemeBackgroundColor.WHITE.name)
        return runCatching {
            ThemeBackgroundColor.valueOf(name ?: ThemeBackgroundColor.WHITE.name)
        }.getOrDefault(ThemeBackgroundColor.WHITE)
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

    private fun folderSortKey(folder: String): String = KEY_FOLDER_SORT_PREFIX + normalizeNotePath(folder)

    private fun folderCustomOrderKey(folder: String): String = KEY_FOLDER_CUSTOM_ORDER_PREFIX + normalizeNotePath(folder)

    private fun folderDisplayOrderKey(parentFolder: String): String = KEY_FOLDER_DISPLAY_ORDER_PREFIX + normalizeNotePath(parentFolder)

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
        ALL_NOTES, RECENT, FAVORITES, DRAFTS, TAGS, FILES, DATES, IMAGES, ARCHIVE, TRASH, PRIVACY, ONBOARDING, SETTINGS;

        companion object {
            val DEFAULT_ORDER: List<DrawerItemId> =
                listOf(ALL_NOTES, RECENT, FAVORITES, DRAFTS, TAGS, FILES, DATES, IMAGES, ARCHIVE, TRASH, PRIVACY, ONBOARDING, SETTINGS)
        }
    }

    fun getDrawerItemOrder(): List<DrawerItemId> {
        val raw = prefs.getString(KEY_DRAWER_ITEM_ORDER, null) ?: return DrawerItemId.DEFAULT_ORDER
        val ids = raw.split(",").mapNotNull { runCatching { DrawerItemId.valueOf(it) }.getOrNull() }
        val result = ids.toMutableList()
        DrawerItemId.DEFAULT_ORDER.forEach { if (it !in result) result.add(it) }
        return result
    }

    fun saveDrawerItemOrder(order: List<DrawerItemId>) {
        prefs.edit().putString(KEY_DRAWER_ITEM_ORDER, order.joinToString(",") { it.name }).apply()
    }

    fun getHiddenDrawerItems(): Set<DrawerItemId> =
        prefs.getStringSet(KEY_DRAWER_HIDDEN_ITEMS, emptySet())
            ?.mapNotNull { runCatching { DrawerItemId.valueOf(it) }.getOrNull() }
            ?.filter { it != DrawerItemId.SETTINGS }
            ?.toSet() ?: emptySet()

    fun saveHiddenDrawerItems(hidden: Set<DrawerItemId>) {
        val safeHidden = hidden.filter { it != DrawerItemId.SETTINGS }
        prefs.edit().putStringSet(KEY_DRAWER_HIDDEN_ITEMS, safeHidden.map { it.name }.toSet()).apply()
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

    // region 长按选择栏功能项编辑
    enum class SelectionToolbarItemId {
        MOVE, COPY, PIN, FAVORITE, TAG, ARCHIVE, PROPERTIES, SHARE, PRIVACY, DELETE;

        companion object {
            val DEFAULT_ORDER: List<SelectionToolbarItemId> =
                listOf(MOVE, COPY, PIN, FAVORITE, TAG, ARCHIVE, PROPERTIES, SHARE, PRIVACY, DELETE)
            val DEFAULT_MORE_ITEMS: Set<SelectionToolbarItemId> =
                setOf(ARCHIVE, PROPERTIES, SHARE, PRIVACY, DELETE)
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
    // endregion

    // region 应用密码 / 隐私密码
    fun getAppPasswordHash(): String? = prefs.getString(KEY_APP_PASSWORD, null)?.takeIf { it.isNotBlank() }

    fun saveAppPasswordHash(hash: String?) {
        prefs.edit().apply {
            if (hash.isNullOrBlank()) {
                remove(KEY_APP_PASSWORD)
                remove(KEY_APP_BIOMETRIC_UNLOCK)
            } else {
                putString(KEY_APP_PASSWORD, hash)
            }
        }.apply()
    }

    fun isAppBiometricUnlockEnabled(): Boolean = prefs.getBoolean(KEY_APP_BIOMETRIC_UNLOCK, false)

    fun saveAppBiometricUnlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_BIOMETRIC_UNLOCK, enabled).apply()
    }

    fun getPrivacyPasswordHash(): String? =
        prefs.getString(KEY_PRIVACY_PASSWORD, null)?.takeIf { it.isNotBlank() }

    fun savePrivacyPasswordHash(hash: String?) {
        prefs.edit().apply {
            if (hash.isNullOrBlank()) {
                remove(KEY_PRIVACY_PASSWORD)
                remove(KEY_PRIVACY_BIOMETRIC_UNLOCK)
            } else {
                putString(KEY_PRIVACY_PASSWORD, hash)
            }
        }.apply()
    }

    fun getSafetyWordHash(): String? = prefs.getString(KEY_SAFETY_WORD, null)?.takeIf { it.isNotBlank() }

    fun saveSafetyWordHash(hash: String?) {
        prefs.edit().apply {
            if (hash.isNullOrBlank()) {
                remove(KEY_SAFETY_WORD)
            } else {
                putString(KEY_SAFETY_WORD, hash)
            }
        }.apply()
    }

    fun isPrivacyBiometricUnlockEnabled(): Boolean = prefs.getBoolean(KEY_PRIVACY_BIOMETRIC_UNLOCK, false)

    fun savePrivacyBiometricUnlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_BIOMETRIC_UNLOCK, enabled).apply()
    }
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

    fun isNoteTitleOnCardsVisible(): Boolean = prefs.getBoolean(KEY_SHOW_NOTE_TITLE_ON_CARDS, true)

    fun saveNoteTitleOnCardsVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NOTE_TITLE_ON_CARDS, visible).apply()
    }

    fun isDateFilenameTitleOnCardsVisible(): Boolean = prefs.getBoolean(KEY_SHOW_DATE_FILENAME_TITLE_ON_CARDS, true)

    fun saveDateFilenameTitleOnCardsVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DATE_FILENAME_TITLE_ON_CARDS, visible).apply()
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

    fun defaultHiddenFilenamePatterns(): List<String> =
        listOf(DEFAULT_HIDDEN_DATE_FILENAME_PATTERN, DEFAULT_HIDDEN_COPY_FILENAME_PATTERN)
    // endregion

    private fun Collection<String>.normalizedNotePathSet(): Set<String> =
        map(::normalizeNotePath)
            .filter { it.isNotBlank() }
            .toSet()
}
