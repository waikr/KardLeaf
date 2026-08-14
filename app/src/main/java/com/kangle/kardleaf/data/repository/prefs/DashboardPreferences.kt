package com.kangle.kardleaf.data.repository.prefs

import android.content.SharedPreferences
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.KardLeafLogTags

private val FOLDER_NAVIGATION_TRACE_TAG = KardLeafLogTags.FOLDER_NAVIGATION

private inline fun logFolderNavigationTrace(message: () -> String) {
    if (KardLeafLog.isEnabled(FOLDER_NAVIGATION_TRACE_TAG)) {
        KardLeafLog.d(FOLDER_NAVIGATION_TRACE_TAG, message())
    }
}

internal class DashboardPreferences(private val prefs: SharedPreferences) {
    fun saveFolderSortSettings(folder: String, settings: PrefsManager.FolderSortSettings) {
        prefs.edit().putString(folderSortKey(folder), "${settings.order.name}|${settings.direction.name}").apply()
    }

    fun getFolderSortSettings(folder: String): PrefsManager.FolderSortSettings? {
        val parts = prefs.getString(folderSortKey(folder), null)?.split("|") ?: return null
        return runCatching {
            PrefsManager.FolderSortSettings(
                order = PrefsManager.SortOrder.valueOf(parts.getOrNull(0) ?: PrefsManager.SortOrder.DATE_MODIFIED.name),
                direction = PrefsManager.SortDirection.valueOf(parts.getOrNull(1) ?: PrefsManager.SortDirection.DESCENDING.name),
            )
        }.getOrNull()
    }

    fun clearFolderSortSettings(folder: String) {
        prefs.edit().remove(folderSortKey(folder)).apply()
    }

    fun saveFolderCustomOrder(folder: String, paths: Collection<String>) = saveOrder(folderCustomOrderKey(folder), paths)
    fun getFolderCustomOrder(folder: String): List<String> = getOrder(folderCustomOrderKey(folder))
    fun clearFolderCustomOrder(folder: String) = clearOrder(folderCustomOrderKey(folder))
    fun saveFolderDisplayOrder(parentFolder: String, paths: Collection<String>) {
        logFolderNavigationTrace {
            "preferences saveDisplayOrder enter parent=$parentFolder paths=${paths.joinToString(prefix = "[", postfix = "]")}"
        }
        saveOrder(folderDisplayOrderKey(parentFolder), paths)
        logFolderNavigationTrace { "preferences saveDisplayOrder applied parent=$parentFolder" }
    }

    fun getFolderDisplayOrder(parentFolder: String): List<String> {
        val order = getOrder(folderDisplayOrderKey(parentFolder))
        logFolderNavigationTrace {
            "preferences getDisplayOrder parent=$parentFolder order=${order.joinToString(prefix = "[", postfix = "]")}"
        }
        return order
    }
    fun clearFolderDisplayOrder(parentFolder: String) = clearOrder(folderDisplayOrderKey(parentFolder))

    fun getPinnedNotePaths(): Set<String> = getPaths(KEY_PINNED_NOTE_PATHS)
    fun replacePinnedNotePaths(paths: Collection<String>) = replacePaths(KEY_PINNED_NOTE_PATHS, paths)
    fun isNotePinned(path: String): Boolean = normalizeNotePath(path) in getPinnedNotePaths()
    fun setNotePinned(path: String, value: Boolean) = setPath(KEY_PINNED_NOTE_PATHS, path, value)
    fun replacePinnedNotePath(oldPath: String, newPath: String) = replacePath(KEY_PINNED_NOTE_PATHS, oldPath, newPath)

    fun getFavoriteNotePaths(): Set<String> = getPaths(KEY_FAVORITE_NOTE_PATHS)
    fun replaceFavoriteNotePaths(paths: Collection<String>) = replacePaths(KEY_FAVORITE_NOTE_PATHS, paths)
    fun isNoteFavorite(path: String): Boolean = normalizeNotePath(path) in getFavoriteNotePaths()
    fun setNoteFavorite(path: String, value: Boolean) = setPath(KEY_FAVORITE_NOTE_PATHS, path, value)
    fun replaceFavoriteNotePath(oldPath: String, newPath: String) = replacePath(KEY_FAVORITE_NOTE_PATHS, oldPath, newPath)

    private fun saveOrder(key: String, paths: Collection<String>) {
        prefs.edit().putString(key, paths.map(::normalizeNotePath).filter { it.isNotBlank() }.distinct().joinToString("\n")).apply()
    }

    private fun getOrder(key: String): List<String> =
        prefs.getString(key, null).orEmpty().lineSequence()
            .map(::normalizeNotePath)
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    private fun clearOrder(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun getPaths(key: String): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty()

    private fun replacePaths(key: String, paths: Collection<String>) {
        prefs.edit().putStringSet(key, paths.map(::normalizeNotePath).filter { it.isNotBlank() }.toSet()).apply()
    }

    private fun setPath(key: String, path: String, value: Boolean) {
        val normalized = normalizeNotePath(path)
        if (normalized.isBlank()) return
        val paths = getPaths(key).toMutableSet()
        if (value) paths.add(normalized) else paths.remove(normalized)
        prefs.edit().putStringSet(key, paths).apply()
    }

    private fun replacePath(key: String, oldPath: String, newPath: String) {
        val oldNormalized = normalizeNotePath(oldPath)
        val newNormalized = normalizeNotePath(newPath)
        if (oldNormalized.isBlank() || newNormalized.isBlank()) return
        val paths = getPaths(key).toMutableSet()
        if (paths.remove(oldNormalized)) {
            paths.add(newNormalized)
            prefs.edit().putStringSet(key, paths).apply()
        }
    }

    private fun folderSortKey(folder: String): String = KEY_FOLDER_SORT_PREFIX + normalizeNotePath(folder)
    private fun folderCustomOrderKey(folder: String): String = KEY_FOLDER_CUSTOM_ORDER_PREFIX + normalizeNotePath(folder)
    private fun folderDisplayOrderKey(folder: String): String = KEY_FOLDER_DISPLAY_ORDER_PREFIX + normalizeNotePath(folder)
    private fun normalizeNotePath(path: String): String = path.trim().replace("\\", "/").trim('/')

    private companion object {
        const val KEY_FOLDER_SORT_PREFIX = "folder_sort_"
        const val KEY_FOLDER_CUSTOM_ORDER_PREFIX = "folder_custom_order_"
        const val KEY_FOLDER_DISPLAY_ORDER_PREFIX = "folder_display_order_"
        const val KEY_PINNED_NOTE_PATHS = "pinned_note_paths"
        const val KEY_FAVORITE_NOTE_PATHS = "favorite_note_paths"
    }
}
