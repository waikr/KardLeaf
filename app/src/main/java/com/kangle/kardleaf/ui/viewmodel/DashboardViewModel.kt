package com.kangle.kardleaf.ui.viewmodel

import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.DashboardUiItem
import com.kangle.kardleaf.ui.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow

internal class DashboardViewModel(private val prefs: PrefsManager) {
    val currentScreen = MutableStateFlow<MainViewModel.Screen>(MainViewModel.Screen.Dashboard)
    val openSearchRequest = MutableStateFlow(0L)

    fun navigateTo(screen: MainViewModel.Screen) {
        currentScreen.value = screen
    }

    fun requestOpenSearch() {
        currentScreen.value = MainViewModel.Screen.Dashboard
        openSearchRequest.value += 1L
    }

    fun normalizePath(path: String): String = path.trim().replace("\\", "/").trim('/')

    fun hiddenFolderPaths(): Set<String> = prefs.getHiddenFolderPaths()

    fun isHiddenFolderPath(folder: String, hiddenFolders: Set<String>): Boolean {
        val normalized = normalizePath(folder)
        if (normalized.isBlank()) return false
        if (normalized.split('/').any { it.startsWith('.') }) return true
        return hiddenFolders.any { normalized == it || normalized.startsWith("$it/") }
    }

    fun withoutHiddenFolders(notes: List<Note>, hiddenFolders: Set<String>): List<Note> =
        notes.filterNot { isHiddenFolderPath(it.folder, hiddenFolders) }

    fun customSortPathSummary(paths: Collection<String>, limit: Int = 5): String {
        val normalized = paths.map(::normalizePath)
        val suffix = if (normalized.size > limit) ", ..." else ""
        return "size=${normalized.size} head=${normalized.take(limit)}$suffix"
    }

    fun customSortNoteSummary(notes: Collection<Note>, limit: Int = 5): String =
        customSortPathSummary(notes.map { it.file.path }, limit)

    fun customSortUiItemSummary(items: Collection<DashboardUiItem>, limit: Int = 5): String {
        val notePaths = items.mapNotNull { (it as? DashboardUiItem.NoteItem)?.note?.file?.path }
        val headerCount = items.count { it is DashboardUiItem.HeaderItem }
        val spacerCount = items.count { it is DashboardUiItem.SpacerItem }
        return "items=${items.size} notes=${customSortPathSummary(notePaths, limit)} headers=$headerCount spacers=$spacerCount"
    }

    fun sortByCustomFolderOrder(notes: List<Note>, folder: String): List<Note> {
        val rawOrder = prefs.getFolderCustomOrder(folder)
        val orderIndex = rawOrder.map(::normalizePath).filter { it.isNotBlank() }.distinct()
            .withIndex().associate { it.value to it.index }
        log { "sortByCustomFolderOrder enter folder=$folder notes=${customSortNoteSummary(notes)} order=${customSortPathSummary(rawOrder)}" }
        if (orderIndex.isEmpty()) {
            return notes.sortedByDescending { it.lastModified.time }.also {
                log { "sortByCustomFolderOrder fallback folder=$folder result=${customSortNoteSummary(it)}" }
            }
        }
        return notes.sortedWith(
            compareBy<Note> { orderIndex[normalizePath(it.file.path)] ?: Int.MAX_VALUE }
                .thenByDescending { it.lastModified.time }
                .thenBy { it.title.lowercase() }
                .thenBy { normalizePath(it.file.path) },
        ).also { log { "sortByCustomFolderOrder result folder=$folder result=${customSortNoteSummary(it)}" } }
    }

    private inline fun log(message: () -> String) {
        if (KardLeafLog.isEnabled(CUSTOM_SORT_TRACE_TAG)) KardLeafLog.d(CUSTOM_SORT_TRACE_TAG, message())
    }

    private companion object {
        const val CUSTOM_SORT_TRACE_TAG = "KardLeafCustomSort"
    }
}
