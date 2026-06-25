package com.kangle.kardleaf.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteRecordSummary
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.repository.MetadataManager
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.data.utils.NoteTextStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DASHBOARD_NOTE_PREVIEW_LIMIT = 200
private const val EDITOR_TRACE_TAG = "KardLeafEditorTrace"
private const val LARGE_NOTE_OPEN_TRACE_TAG = "KardLeafLargeNoteOpen"
private const val STARTUP_PERF_TRACE_TAG = "KardLeafStartupPerf"
private const val YAML_TAG_TRACE_TAG = "KardLeafYamlTags"
private const val CUSTOM_SORT_TRACE_TAG = "KardLeafCustomSort"
private const val CUSTOM_SORT_FLASH_TAG = "KardLeafCustomSortFlash"
private const val ALL_NOTES_CUSTOM_SORT_KEY = "__kardleaf_all_notes__"

class MainViewModel(
    private val repository: RoomNoteRepository,
    private val metadataManager: MetadataManager,
    private val prefsManager: PrefsManager,
) : ViewModel() {
    private var notesRawEmissionCount = 0
    private var uiItemsEmissionCount = 0

    private fun logStartupPerf(message: String) {
        Log.d(STARTUP_PERF_TRACE_TAG, message)
    }
    private data class SortSettings(
        val order: PrefsManager.SortOrder,
        val direction: PrefsManager.SortDirection,
        val trashOrder: PrefsManager.TrashSortOrder,
    )

    private fun normalizeFolderPath(path: String): String =
        path.trim().replace("\\", "/").trim('/')

    private fun isHiddenFolderPath(folder: String): Boolean {
        val normalized = normalizeFolderPath(folder)
        if (normalized.isBlank()) return false
        return prefsManager.getHiddenFolderPaths().any { hidden ->
            normalized == hidden || normalized.startsWith("$hidden/")
        }
    }

    private fun List<Note>.withoutHiddenFolders(): List<Note> =
        filterNot { isHiddenFolderPath(it.folder) }

    private fun normalizeNotePath(path: String): String =
        path.trim().replace("\\", "/").trim('/')

    private fun customSortPathSummary(paths: Collection<String>, limit: Int = 5): String {
        val normalized = paths.map(::normalizeNotePath)
        val suffix = if (normalized.size > limit) ", ..." else ""
        return "size=${normalized.size} head=${normalized.take(limit)}$suffix"
    }

    private fun customSortNoteSummary(notes: Collection<Note>, limit: Int = 5): String =
        customSortPathSummary(notes.map { it.file.path }, limit)

    private fun customSortUiItemSummary(items: Collection<DashboardUiItem>, limit: Int = 5): String {
        val notePaths = items.mapNotNull { (it as? DashboardUiItem.NoteItem)?.note?.file?.path }
        val headerCount = items.count { it is DashboardUiItem.HeaderItem }
        val spacerCount = items.count { it is DashboardUiItem.SpacerItem }
        return "items=${items.size} notes=${customSortPathSummary(notePaths, limit)} headers=$headerCount spacers=$spacerCount"
    }

    private fun sortByCustomFolderOrder(
        notes: List<Note>,
        folder: String,
    ): List<Note> {
        val rawOrder = prefsManager.getFolderCustomOrder(folder)
        val orderIndex = rawOrder
            .map(::normalizeNotePath)
            .filter { it.isNotBlank() }
            .distinct()
            .withIndex()
            .associate { it.value to it.index }
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "sortByCustomFolderOrder enter folder=$folder notes=${customSortNoteSummary(notes)} order=${customSortPathSummary(rawOrder)}",
        )
        if (orderIndex.isEmpty()) {
            val fallback = notes.sortedByDescending { it.lastModified.time }
            Log.d(
                CUSTOM_SORT_TRACE_TAG,
                "sortByCustomFolderOrder fallback folder=$folder result=${customSortNoteSummary(fallback)}",
            )
            return fallback
        }

        val sorted = notes.sortedWith(
            compareBy<Note> { orderIndex[normalizeNotePath(it.file.path)] ?: Int.MAX_VALUE }
                .thenByDescending { it.lastModified.time }
                .thenBy { it.title.lowercase() }
                .thenBy { normalizeNotePath(it.file.path) },
        )
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "sortByCustomFolderOrder result folder=$folder result=${customSortNoteSummary(sorted)}",
        )
        return sorted
    }

    private data class SearchIndex(
        val histories: List<NoteHistory>,
        val noteIds: Set<String>,
    )

    private sealed interface PendingNoteUndo {
        data class Restore(val noteIds: List<String>) : PendingNoteUndo

        data class MoveBack(val moves: List<RoomNoteRepository.MovedNotePath>) : PendingNoteUndo
    }

    private var pendingNoteUndo: PendingNoteUndo? = null
    private var pendingNoteUndoJob: Job? = null

    sealed interface NoteFilter {
        object All : NoteFilter

        object Recent : NoteFilter

        object Favorites : NoteFilter

        object Drafts : NoteFilter

        data class Label(val name: String, val recursive: Boolean = false) : NoteFilter

        data class YamlTag(val name: String) : NoteFilter

        object Archive : NoteFilter

        object Trash : NoteFilter
    }

    private fun customSortStorageKeyFor(filter: NoteFilter): String? =
        when (filter) {
            NoteFilter.All -> ALL_NOTES_CUSTOM_SORT_KEY
            is NoteFilter.Label -> filter.name.takeIf { !filter.recursive && it.isNotBlank() }
            else -> null
        }

    private fun customSortLogNameFor(filter: NoteFilter, key: String): String =
        if (filter is NoteFilter.All) "全部笔记" else key

    sealed interface Screen {
        object Dashboard : Screen

        object Dates : Screen

        object Images : Screen

        object Tags : Screen

        object Folders : Screen

        object Settings : Screen
    }

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    private val _currentFilter = MutableStateFlow<NoteFilter>(restoreLastFilter(prefsManager))
    val currentFilter: StateFlow<NoteFilter> = _currentFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(prefsManager.getSortOrder())
    val sortOrder: StateFlow<PrefsManager.SortOrder> = _sortOrder.asStateFlow()

    private val _sortDirection = MutableStateFlow(prefsManager.getSortDirection())
    val sortDirection: StateFlow<PrefsManager.SortDirection> = _sortDirection.asStateFlow()

    private val _folderSortVersion = MutableStateFlow(0)
    val folderSortVersion: StateFlow<Int> = _folderSortVersion.asStateFlow()

    private val _customSortDragModeEnabled = MutableStateFlow(false)
    val customSortDragModeEnabled: StateFlow<Boolean> = _customSortDragModeEnabled.asStateFlow()

    private val _folderManagerOrderVersion = MutableStateFlow(0)
    val folderManagerOrderVersion: StateFlow<Int> = _folderManagerOrderVersion.asStateFlow()

    private val _viewMode = MutableStateFlow(prefsManager.getViewMode())
    val viewMode: StateFlow<PrefsManager.ViewMode> = _viewMode.asStateFlow()

    private val _trashSortOrder = MutableStateFlow(prefsManager.getTrashSortOrder())
    val trashSortOrder: StateFlow<PrefsManager.TrashSortOrder> = _trashSortOrder.asStateFlow()

    private val _cardDensity = MutableStateFlow(prefsManager.getCardDensity())
    val cardDensity: StateFlow<PrefsManager.CardDensity> = _cardDensity.asStateFlow()

    private val _showYamlTagsOnLooseCards = MutableStateFlow(prefsManager.isLooseCardYamlTagsVisible())
    val showYamlTagsOnLooseCards: StateFlow<Boolean> = _showYamlTagsOnLooseCards.asStateFlow()

    private val _showModifiedDateOnCards = MutableStateFlow(prefsManager.isModifiedDateOnCardsVisible())
    val showModifiedDateOnCards: StateFlow<Boolean> = _showModifiedDateOnCards.asStateFlow()

    private val _showNoteTitleOnCards = MutableStateFlow(prefsManager.isNoteTitleOnCardsVisible())
    val showNoteTitleOnCards: StateFlow<Boolean> = _showNoteTitleOnCards.asStateFlow()

    private val _showDateFilenameTitleOnCards = MutableStateFlow(prefsManager.isDateFilenameTitleOnCardsVisible())
    val showDateFilenameTitleOnCards: StateFlow<Boolean> = _showDateFilenameTitleOnCards.asStateFlow()

    private val _customHiddenFilenamePatterns = MutableStateFlow(prefsManager.getCustomHiddenFilenamePatterns())
    val customHiddenFilenamePatterns: StateFlow<List<String>> = _customHiddenFilenamePatterns.asStateFlow()

    private val _selectionToolbarItemOrder = MutableStateFlow(prefsManager.getSelectionToolbarItemOrder())
    val selectionToolbarItemOrder: StateFlow<List<PrefsManager.SelectionToolbarItemId>> = _selectionToolbarItemOrder.asStateFlow()

    private val _selectionToolbarMoreItems = MutableStateFlow(prefsManager.getSelectionToolbarMoreItems())
    val selectionToolbarMoreItems: StateFlow<Set<PrefsManager.SelectionToolbarItemId>> = _selectionToolbarMoreItems.asStateFlow()

    val yamlTags: StateFlow<List<String>> =
        repository.getYamlTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchEverywhere = MutableStateFlow(false)
    val isSearchEverywhere: StateFlow<Boolean> = _isSearchEverywhere.asStateFlow()

    private val _hiddenFoldersVersion = MutableStateFlow(0)

    val allNotes: Flow<List<Note>> =
        combine(repository.getAllNotesWithArchive(), _hiddenFoldersVersion) { notes, _ ->
            notes.withoutHiddenFolders()
        }
    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val searchHistoryPreview: StateFlow<List<NoteHistory>> =
        _searchQuery
            .debounce(300L)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf(emptyList())
                } else {
                    repository.searchHistoryPreview(query)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val searchNoteIds: StateFlow<Set<String>> =
        _searchQuery
            .debounce(300L)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf(emptyList())
                } else {
                    repository.searchNoteIds(query)
                }
            }
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val searchIndex: StateFlow<SearchIndex> =
        combine(searchHistoryPreview, searchNoteIds) { histories, noteIds ->
            SearchIndex(histories = histories, noteIds = noteIds)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchIndex(emptyList(), emptySet()))

    private val sortSettings =
        combine(
            _sortOrder,
            _sortDirection,
            _trashSortOrder,
            _folderSortVersion,
            _hiddenFoldersVersion,
        ) { order, direction, trashOrder, _, _ ->
            SortSettings(order, direction, trashOrder)
        }

    val currentFolderSortSettings: StateFlow<PrefsManager.FolderSortSettings?> =
        combine(
            _currentFilter,
            _folderSortVersion,
        ) { filter, _ ->
            customSortStorageKeyFor(filter)?.let { prefsManager.getFolderSortSettings(it) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _notesRaw: StateFlow<List<Note>> =
        combine(
            _currentFilter.flatMapLatest { filter ->
                when (filter) {
                    is NoteFilter.All -> repository.getAllNotes()
                    is NoteFilter.Recent -> repository.getAllNotes()
                    is NoteFilter.Favorites -> repository.getFavoriteNotes()
                    is NoteFilter.Drafts -> repository.getNotesByFolder(PrefsManager.DEFAULT_DRAFT_FOLDER_NAME)
                    is NoteFilter.Label ->
                        if (filter.recursive) {
                            repository.getNotesByFolderRecursive(filter.name)
                        } else {
                            repository.getNotesByFolder(filter.name)
                        }
                    is NoteFilter.YamlTag -> repository.getNotesByYamlTag(filter.name)
                    is NoteFilter.Archive -> repository.getArchivedNotes()
                    is NoteFilter.Trash -> repository.getTrashedNotes()
                }
            },
            repository.getAllNotesWithArchive(),
            searchIndex,
            sortSettings,
            _searchQuery
                .debounce(300L)
                .distinctUntilChanged(),
        ) { notesList, allNotesList, index, sorting, query ->
            val mapStartMs = SystemClock.elapsedRealtime()
            val currentFilterValue = _currentFilter.value
            val visibleNotesList =
                if (currentFilterValue is NoteFilter.Trash) notesList else notesList.withoutHiddenFolders()
            val visibleAllNotesList = allNotesList.withoutHiddenFolders()
            val searched =
                if (query.isBlank()) {
                    _isSearchEverywhere.value = false
                    visibleNotesList
                } else {
                    val filteredResults =
                        visibleNotesList.filter {
                            findSearchMatch(it, query, index.histories) != null || it.id in index.noteIds
                        }

                    if (filteredResults.isEmpty() && currentFilterValue !is NoteFilter.Trash) {
                        val globalResults =
                            visibleAllNotesList.filter {
                                findSearchMatch(it, query, index.histories) != null || it.id in index.noteIds
                            }
                        _isSearchEverywhere.value = globalResults.isNotEmpty()
                        globalResults
                    } else {
                        _isSearchEverywhere.value = false
                        filteredResults
                    }
                }

            val folderFilter = currentFilterValue as? NoteFilter.Label
            val customSortKey = customSortStorageKeyFor(currentFilterValue)
            val customSortSettings = customSortKey?.let { prefsManager.getFolderSortSettings(it) }
            val customSortName = customSortKey?.let { customSortLogNameFor(currentFilterValue, it) }
            val effectiveSortOrder = customSortSettings?.order ?: sorting.order
            val effectiveSortDirection = customSortSettings?.direction ?: sorting.direction
            val useCustomSort = customSortKey != null && effectiveSortOrder == PrefsManager.SortOrder.CUSTOM
            Log.d(
                CUSTOM_SORT_FLASH_TAG,
                "notesRaw beforeSort filter=$currentFilterValue customTarget=$customSortName recursive=${folderFilter?.recursive} " +
                    "settings=$customSortSettings effective=$effectiveSortOrder/$effectiveSortDirection useCustom=$useCustomSort queryBlank=${query.isBlank()} searched=${customSortNoteSummary(searched)} sortVersion=${_folderSortVersion.value}",
            )

            val sorted =
                if (currentFilterValue is NoteFilter.Recent) {
                    searched.sortedBy { it.lastModified }
                } else if (currentFilterValue is NoteFilter.Trash) {
                    when (sorting.trashOrder) {
                        PrefsManager.TrashSortOrder.FILE_NAME -> searched.sortedBy { it.file.name.lowercase() }
                        PrefsManager.TrashSortOrder.DELETED_TIME -> searched.sortedBy { it.deletedAt ?: it.lastModified }
                    }
                } else if (useCustomSort && customSortKey != null) {
                    sortByCustomFolderOrder(searched, customSortKey)
                } else {
                    when (effectiveSortOrder) {
                        PrefsManager.SortOrder.DATE_MODIFIED,
                        PrefsManager.SortOrder.CUSTOM -> searched.sortedBy { it.lastModified }
                        PrefsManager.SortOrder.TITLE -> searched.sortedBy { it.title.lowercase() }
                    }
                }

            val directed =
                if (!useCustomSort && (currentFilterValue is NoteFilter.Recent || effectiveSortDirection == PrefsManager.SortDirection.DESCENDING)) {
                    sorted.reversed()
                } else {
                    sorted
                }

            val result = directed.sortedByDescending { it.isPinned }
            Log.d(
                CUSTOM_SORT_FLASH_TAG,
                "notesRaw result filter=$currentFilterValue useCustom=$useCustomSort sorted=${customSortNoteSummary(sorted)} directed=${customSortNoteSummary(directed)} result=${customSortNoteSummary(result)}",
            )
            val elapsedMs = SystemClock.elapsedRealtime() - mapStartMs
            notesRawEmissionCount += 1
            if (notesRawEmissionCount <= 20 || elapsedMs >= 16L || query.isNotBlank()) {
                logStartupPerf(
                    "notesRaw emit#$notesRawEmissionCount filter=$currentFilterValue queryLen=${query.length} " +
                        "input=${notesList.size} all=${allNotesList.size} result=${result.size} elapsed=${elapsedMs}ms " +
                        "thread=${Thread.currentThread().name}",
                )
            }
            result
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Public notes StateFlow; the loading flag is driven by setRootFolder/refreshNotes, not the Flow mapper.
    val notes: StateFlow<List<Note>> = _notesRaw

    val uiItems: StateFlow<List<DashboardUiItem>> =
        combine(
            notes,
            _currentFilter,
            _isSearchEverywhere,
            _searchQuery,
            searchIndex,
        ) { notesList, filter, isGlobalSearch, query, index ->
            val mapStartMs = SystemClock.elapsedRealtime()
            val list = mutableListOf<DashboardUiItem>()
            val usedKeys = mutableSetOf<String>()

            fun addUnique(item: DashboardUiItem) {
                if (usedKeys.add(item.key)) {
                    list.add(item)
                }
            }

            if (isGlobalSearch) {
                addUnique(DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.SEARCH_EVERYWHERE))
                notesList.forEach {
                    addUnique(DashboardUiItem.NoteItem(it, findSearchMatch(it, query, index.histories) ?: bodyOnlySearchMatch(it, index.noteIds)))
                }
            } else if (query.isNotBlank()) {
                addUnique(DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.SEARCH_RESULTS))
                notesList.forEach {
                    addUnique(DashboardUiItem.NoteItem(it, findSearchMatch(it, query, index.histories) ?: bodyOnlySearchMatch(it, index.noteIds)))
                }
            } else if (filter is NoteFilter.Trash || filter is NoteFilter.Archive || filter is NoteFilter.Recent || filter is NoteFilter.Favorites || filter is NoteFilter.Drafts || filter is NoteFilter.YamlTag) {
                notesList.forEach { addUnique(DashboardUiItem.NoteItem(it)) }
            } else {
                val pinned = notesList.filter { it.isPinned && !it.isArchived }
                val others = notesList.filter { !it.isPinned && !it.isArchived }
                val archived = notesList.filter { it.isArchived }
                val showSeparator = filter is NoteFilter.Label

                if (pinned.isNotEmpty()) {
                    addUnique(DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.PINNED))
                    pinned.forEach { addUnique(DashboardUiItem.NoteItem(it)) }
                }

                if (pinned.isNotEmpty() && others.isNotEmpty()) {
                    addUnique(DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.OTHERS))
                }
                others.forEach { addUnique(DashboardUiItem.NoteItem(it)) }

                if (archived.isNotEmpty() && showSeparator) {
                    addUnique(DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.ARCHIVED))
                    archived.forEach { addUnique(DashboardUiItem.NoteItem(it)) }
                }
            }
            addUnique(DashboardUiItem.SpacerItem)
            Log.d(
                CUSTOM_SORT_FLASH_TAG,
                "uiItems build filter=$filter queryBlank=${query.isBlank()} global=$isGlobalSearch notes=${customSortNoteSummary(notesList)} result=${customSortUiItemSummary(list)}",
            )
            val elapsedMs = SystemClock.elapsedRealtime() - mapStartMs
            uiItemsEmissionCount += 1
            if (uiItemsEmissionCount <= 20 || elapsedMs >= 16L || query.isNotBlank()) {
                logStartupPerf(
                    "uiItems emit#$uiItemsEmissionCount filter=$filter queryLen=${query.length} global=$isGlobalSearch " +
                        "notes=${notesList.size} items=${list.size} elapsed=${elapsedMs}ms thread=${Thread.currentThread().name}",
                )
            }
            list
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tempLabels = MutableStateFlow<Set<String>>(emptySet())

    val labels: StateFlow<List<String>> =
        combine(
            repository.getLabels(),
            _tempLabels,
            _hiddenFoldersVersion,
        ) { dbLabels, tempLabels, _ ->
            (dbLabels + tempLabels)
                .distinct()
                .filterNot(::isHiddenFolderPath)
                .sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val childFolders: StateFlow<List<String>> =
        combine(
            labels,
            _currentFilter,
        ) { folders, filter ->
            val currentPath = (filter as? NoteFilter.Label)?.name.orEmpty()
            val prefix = currentPath.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
            folders
                .asSequence()
                .filter { it.startsWith(prefix) && it != currentPath }
                .map { it.removePrefix(prefix) }
                .filter { it.isNotBlank() && !it.contains("/") }
                .sorted()
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val initialPermissionNeeded = prefsManager.getRootUri() == null
    private val _isPermissionNeeded = MutableStateFlow(initialPermissionNeeded)
    val isPermissionNeeded: StateFlow<Boolean> = _isPermissionNeeded.asStateFlow()

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote: StateFlow<Note?> = _currentNote.asStateFlow()

    private val _externalNoteDraft = MutableStateFlow<KardLeafCustomFeatures.ExternalNoteDraft?>(null)
    val externalNoteDraft: StateFlow<KardLeafCustomFeatures.ExternalNoteDraft?> = _externalNoteDraft.asStateFlow()

    private val _isEditorOpen = MutableStateFlow(false)
    val isEditorOpen: StateFlow<Boolean> = _isEditorOpen.asStateFlow()

    private val _isOpeningNoteContent = MutableStateFlow(false)
    val isOpeningNoteContent: StateFlow<Boolean> = _isOpeningNoteContent.asStateFlow()

    // 编辑器是否有未保存的修改；用于判断外部文件变化时是否触发冲突提醒
    private val _editorDirty = MutableStateFlow(false)
    val editorDirty: StateFlow<Boolean> = _editorDirty.asStateFlow()

    // 外部冲突笔记：当编辑器有未保存修改且外部也修改了同一文件时，暂存外部版本供用户选择
    private val _externalConflict = MutableStateFlow<Note?>(null)
    val externalConflict: StateFlow<Note?> = _externalConflict.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> =
        combine(_isLoading, repository.isIndexing) { isLoading, isIndexing -> isLoading || isIndexing }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _homeScrollToTopEvents = MutableStateFlow(0)
    val homeScrollToTopEvents: StateFlow<Int> = _homeScrollToTopEvents.asStateFlow()
    private var externalRefreshJob: Job? = null
    private var openNoteRequestVersion = 0
    private var lastOpenNoteShownAtMs = 0L
    private var lastOpenNoteShownPath: String? = null

    private fun Note.looksLikeDashboardPreview(): Boolean =
        content.length >= DASHBOARD_NOTE_PREVIEW_LIMIT && content == contentPreview

    private fun Note.hasFullEditorContent(): Boolean =
        content.isNotEmpty() && !looksLikeDashboardPreview()

    private fun Note.looksLikeEmptyPlaceholder(reference: Note?): Boolean =
        content.isEmpty() &&
            contentPreview.isEmpty() &&
            reference?.content?.isNotEmpty() == true

    private fun Note.traceSummary(): String =
        "path=${file.path} titleLen=${title.length} contentLen=${content.length} previewLen=${contentPreview.length} " +
            "hasFull=${hasFullEditorContent()} dashboardPreview=${looksLikeDashboardPreview()}"

    private fun shortCallStack(): String = Throwable().stackTrace
        .drop(2)
        .take(8)
        .joinToString(" <- ") { frame -> "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}" }

    private fun markOpenNoteShown(note: Note) {
        lastOpenNoteShownAtMs = SystemClock.elapsedRealtime()
        lastOpenNoteShownPath = note.file.path
    }

    fun setRootFolder(
        uri: Uri,
        scanImmediately: Boolean = true,
    ) {
        val startMs = SystemClock.elapsedRealtime()
        logStartupPerf("setRootFolder start scanImmediately=$scanImmediately loadingBefore=${_isLoading.value}")
        _isLoading.value = scanImmediately
        _isPermissionNeeded.value = false
        viewModelScope.launch {
            try {
                kotlinx.coroutines.yield()
                logStartupPerf("setRootFolder repository start elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                repository.setRootFolder(uri.toString(), scanImmediately = scanImmediately)
                logStartupPerf("setRootFolder repository done elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                cleanupExpiredTrashIfNeeded()
                logStartupPerf("setRootFolder cleanup done elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to set root folder", e)
                _isPermissionNeeded.value = true
            } finally {
                _isLoading.value = false
                logStartupPerf("setRootFolder done elapsed=${SystemClock.elapsedRealtime() - startMs}ms loading=false")
            }
        }
    }

    fun refreshNotes() {
        val startMs = SystemClock.elapsedRealtime()
        logStartupPerf("refreshNotes start loadingBefore=${_isLoading.value}")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                kotlinx.coroutines.yield()
                logStartupPerf("refreshNotes repository start elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                repository.refreshNotes()
                logStartupPerf("refreshNotes repository done elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                cleanupExpiredTrashIfNeeded()
                logStartupPerf("refreshNotes cleanup done elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to refresh notes", e)
            } finally {
                _isLoading.value = false
                logStartupPerf("refreshNotes done elapsed=${SystemClock.elapsedRealtime() - startMs}ms loading=false")
            }
        }
    }

    fun onExternalVaultChanged(
        forceContentReloadFallback: Boolean = true,
        changedUri: Uri? = null,
    ) {
        val startMs = SystemClock.elapsedRealtime()
        logStartupPerf(
            "externalVaultChanged scheduled forceFallback=$forceContentReloadFallback changedUri=${changedUri != null} " +
                "editorOpen=${_isEditorOpen.value}",
        )
        externalRefreshJob?.cancel()
        externalRefreshJob =
            viewModelScope.launch {
                delay(100L)
                try {
                    logStartupPerf("externalVaultChanged run elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                    val openNotePath = _currentNote.value?.file?.path

                    val quickNote =
                        changedUri?.let { uri ->
                            repository.refreshSingleNoteByUri(uri)
                        } ?: openNotePath?.let { path ->
                            repository.refreshSingleNoteByPath(path)
                        }

                    // 冲突检测：编辑器打开且有未保存修改，外部又改了同一文件且内容不同，
                    // 暂存外部版本交由用户选择，不静默覆盖编辑器内容
                    val conflictDetected =
                        _isEditorOpen.value &&
                            _editorDirty.value &&
                            quickNote != null &&
                            quickNote.file.path == openNotePath &&
                            quickNote.content != _currentNote.value?.content

                    if (conflictDetected) {
                        _externalConflict.value = quickNote
                    } else {
                        if (quickNote != null && _currentNote.value?.file?.path == quickNote.file.path) {
                            _currentNote.value = quickNote
                        }

                        val shouldForceFullRefresh = forceContentReloadFallback && quickNote == null
                        if (shouldForceFullRefresh) {
                            logStartupPerf("externalVaultChanged fullRefresh start elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                            repository.refreshNotesFromExternalChange()
                        } else {
                            logStartupPerf("externalVaultChanged refresh start quickNote=${quickNote != null} elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                            repository.refreshNotes()
                        }

                        if (_isEditorOpen.value && openNotePath != null && _currentNote.value?.file?.path == openNotePath) {
                            val latest = repository.getNoteForEditor(openNotePath)
                            latest?.let { latestNote ->
                                if (_currentNote.value?.file?.path == openNotePath) {
                                    _currentNote.value = latestNote
                                }
                            }
                        }
                    }

                    // 无论是否冲突，都要刷新笔记列表索引
                    if (conflictDetected) {
                        logStartupPerf("externalVaultChanged conflict refresh start elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                        repository.refreshNotes()
                    }
                    logStartupPerf("externalVaultChanged done elapsed=${SystemClock.elapsedRealtime() - startMs}ms conflict=$conflictDetected")
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Failed to refresh notes after external change", e)
                }
            }
    }

    fun resetPermissionNeeded() {
        _isPermissionNeeded.value = true
        _isLoading.value = false
    }

    fun openNoteByPath(notePath: String) {
        val normalizedPath = normalizeNotePath(notePath)
        if (normalizedPath.isBlank()) return
        viewModelScope.launch {
            openResolvedNoteByPath(normalizedPath, "openNoteByPath")
        }
    }

    fun openRecordNote(recordKey: String) {
        val key = recordKey.trim()
        if (key.isBlank()) return
        viewModelScope.launch {
            val resolvedPath = runCatching {
                repository.resolveRecordNotePath(key)
            }.getOrNull()
            if (resolvedPath.isNullOrBlank()) {
                Log.w(EDITOR_TRACE_TAG, "openRecordNote no note key=$key")
                return@launch
            }
            openResolvedNoteByPath(normalizeNotePath(resolvedPath), "openRecordNote")
        }
    }

    private suspend fun openResolvedNoteByPath(
        normalizedPath: String,
        source: String,
    ) {
        val startMs = SystemClock.elapsedRealtime()
        Log.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "vm openResolvedNoteByPath start source=$source path=$normalizedPath",
        )
        val note = runCatching {
            repository.getCachedNote(normalizedPath) ?: repository.getNoteForEditor(normalizedPath)
        }.getOrNull()
        Log.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "vm openResolvedNoteByPath resolved source=$source path=$normalizedPath elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                "noteContentLen=${note?.content?.length ?: -1} notePreviewLen=${note?.contentPreview?.length ?: -1}",
        )
        if (note != null) {
            openNote(note)
        } else {
            Log.w(EDITOR_TRACE_TAG, "$source no note path=$normalizedPath")
        }
    }

    fun openNote(note: Note) {
        val requestVersion = ++openNoteRequestVersion
        val notePath = note.file.path
        val startMs = SystemClock.elapsedRealtime()
        Log.d(
            EDITOR_TRACE_TAG,
            "openNote start request=$requestVersion editorOpen=${_isEditorOpen.value} source=${note.traceSummary()}",
        )
        Log.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "vm openNote start request=$requestVersion path=$notePath editorOpen=${_isEditorOpen.value} " +
                "sourceContentLen=${note.content.length} sourcePreviewLen=${note.contentPreview.length} isOpening=${_isOpeningNoteContent.value}",
        )
        _externalNoteDraft.value = null
        _editorDirty.value = false
        _externalConflict.value = null

        viewModelScope.launch {
            Log.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "vm openNote coroutine start request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms path=$notePath",
            )
            // Initialize from a full editor row when it is already available.
            // Otherwise open a lightweight loading shell, never the dashboard preview text.
            val cachedNote =
                try {
                    repository.getCachedNote(notePath)
                } catch (e: Exception) {
                    Log.e(EDITOR_TRACE_TAG, "openNote cached load failed request=$requestVersion path=$notePath", e)
                    null
                }
            Log.d(
                EDITOR_TRACE_TAG,
                "openNote cached loaded request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "cached=${cachedNote?.traceSummary() ?: "null"}",
            )
            Log.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "vm cached loaded request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "cachedContentLen=${cachedNote?.content?.length ?: -1} cachedPreviewLen=${cachedNote?.contentPreview?.length ?: -1} " +
                    "hasFull=${cachedNote?.hasFullEditorContent()}",
            )

            if (requestVersion != openNoteRequestVersion) {
                Log.d(EDITOR_TRACE_TAG, "openNote cached result ignored stale request=$requestVersion latest=$openNoteRequestVersion path=$notePath")
                return@launch
            }

            val initialNote = when {
                cachedNote?.hasFullEditorContent() == true -> cachedNote
                note.hasFullEditorContent() -> note
                else -> null
            }

            if (initialNote != null) {
                Log.d(
                    EDITOR_TRACE_TAG,
                    "openNote show initial request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                        "initial=${initialNote.traceSummary()}",
                )
                _isOpeningNoteContent.value = false
                _currentNote.value = initialNote
                _isEditorOpen.value = true
                Log.d(
                    LARGE_NOTE_OPEN_TRACE_TAG,
                    "vm show initial request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                        "contentLen=${initialNote.content.length} previewLen=${initialNote.contentPreview.length} isOpening=${_isOpeningNoteContent.value} " +
                        "editorOpen=${_isEditorOpen.value}",
                )
                markOpenNoteShown(initialNote)
            } else {
                val shellNote = note.copy(content = "", contentPreview = "")
                Log.w(
                    EDITOR_TRACE_TAG,
                    "openNote show loading shell request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                        "sourceContentLen=${note.content.length} sourcePreviewLen=${note.contentPreview.length}",
                )
                _isOpeningNoteContent.value = true
                _currentNote.value = shellNote
                _isEditorOpen.value = true
                Log.d(
                    LARGE_NOTE_OPEN_TRACE_TAG,
                    "vm show loading shell request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                        "path=$notePath isOpening=${_isOpeningNoteContent.value} editorOpen=${_isEditorOpen.value}",
                )
                markOpenNoteShown(shellNote)
            }

            Log.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "vm full load start request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms path=$notePath",
            )
            val fullNote =
                try {
                    repository.getNoteForEditor(notePath)
                } catch (e: Exception) {
                    Log.e(EDITOR_TRACE_TAG, "openNote full load failed request=$requestVersion path=$notePath", e)
                    null
                }
            Log.d(
                EDITOR_TRACE_TAG,
                "openNote full loaded request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "full=${fullNote?.traceSummary() ?: "null"}",
            )
            Log.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "vm full loaded request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "fullContentLen=${fullNote?.content?.length ?: -1} fullPreviewLen=${fullNote?.contentPreview?.length ?: -1} " +
                    "isOpening=${_isOpeningNoteContent.value}",
            )

            if (requestVersion != openNoteRequestVersion) {
                Log.d(EDITOR_TRACE_TAG, "openNote full result ignored stale request=$requestVersion latest=$openNoteRequestVersion path=$notePath")
                return@launch
            }

            if (fullNote != null) {
                val current = _currentNote.value
                val isSameOpenedNote = _isEditorOpen.value && current?.file?.path == notePath
                val hasNotOpenedYet = !_isEditorOpen.value || current == null
                val currentContentEmpty = current?.content.isNullOrEmpty()
                val fullNoteIsSuspiciousBlank =
                    fullNote.looksLikeEmptyPlaceholder(current) ||
                        (fullNote.content.isEmpty() && fullNote.contentPreview.isEmpty() && note.contentPreview.isNotEmpty())

                _isOpeningNoteContent.value = false
                if (!fullNoteIsSuspiciousBlank &&
                    (hasNotOpenedYet || (isSameOpenedNote && (!_editorDirty.value || currentContentEmpty)))
                ) {
                    Log.d(
                        EDITOR_TRACE_TAG,
                        "openNote show full request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                            "hasNotOpenedYet=$hasNotOpenedYet same=$isSameOpenedNote dirty=${_editorDirty.value} currentEmpty=$currentContentEmpty",
                    )
                    _currentNote.value = fullNote
                    _isEditorOpen.value = true
                    Log.d(
                        LARGE_NOTE_OPEN_TRACE_TAG,
                        "vm show full request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                            "contentLen=${fullNote.content.length} previewLen=${fullNote.contentPreview.length} isOpening=${_isOpeningNoteContent.value} " +
                            "editorOpen=${_isEditorOpen.value}",
                    )
                    markOpenNoteShown(fullNote)
                } else {
                    Log.w(
                        EDITOR_TRACE_TAG,
                        "openNote skip full request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                            "suspiciousBlank=$fullNoteIsSuspiciousBlank hasNotOpenedYet=$hasNotOpenedYet same=$isSameOpenedNote " +
                            "dirty=${_editorDirty.value} currentEmpty=$currentContentEmpty",
                    )
                    Log.w(
                        LARGE_NOTE_OPEN_TRACE_TAG,
                        "vm skip full request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                            "suspiciousBlank=$fullNoteIsSuspiciousBlank hasNotOpenedYet=$hasNotOpenedYet same=$isSameOpenedNote " +
                            "dirty=${_editorDirty.value} currentEmpty=$currentContentEmpty currentContentLen=${current?.content?.length ?: -1} " +
                            "fullContentLen=${fullNote.content.length} isOpening=${_isOpeningNoteContent.value}",
                    )
                }
            } else {
                _isOpeningNoteContent.value = false
            }

            if (fullNote == null && !_isEditorOpen.value) {
                // Do not open the editor with the dashboard preview or an empty placeholder.
                // Keeping the dashboard visible is better than entering a permanently blank note.
                val emptyCachedNote = cachedNote?.takeIf { it.content.isEmpty() && it.contentPreview.isEmpty() }
                if (emptyCachedNote != null && note.contentPreview.isEmpty()) {
                    Log.w(
                        EDITOR_TRACE_TAG,
                        "openNote show empty cached request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms path=$notePath",
                    )
                    _currentNote.value = emptyCachedNote
                    _isEditorOpen.value = true
                    markOpenNoteShown(emptyCachedNote)
                } else {
                    Log.e(
                        EDITOR_TRACE_TAG,
                        "openNote failed no editor opened request=$requestVersion elapsed=${SystemClock.elapsedRealtime() - startMs}ms path=$notePath",
                    )
                }
            }
        }
    }

    fun createNote(
        draft: KardLeafCustomFeatures.ExternalNoteDraft? = null,
        source: String = "unspecified",
    ) {
        val now = SystemClock.elapsedRealtime()
        val current = _currentNote.value
        val ageSinceOpen = if (lastOpenNoteShownAtMs > 0L) now - lastOpenNoteShownAtMs else -1L
        val stack = shortCallStack()
        if (source == "unspecified") {
            Log.w(
                EDITOR_TRACE_TAG,
                "createNote ignored source=unspecified current=${current?.traceSummary()} lastOpenPath=$lastOpenNoteShownPath ageSinceOpen=${ageSinceOpen}ms stack=$stack",
            )
            return
        }
        if (_isEditorOpen.value && current != null) {
            Log.w(
                EDITOR_TRACE_TAG,
                "createNote ignored while real note is open source=$source current=${current.traceSummary()} lastOpenPath=$lastOpenNoteShownPath ageSinceOpen=${ageSinceOpen}ms stack=$stack",
            )
            return
        }
        Log.d(
            EDITOR_TRACE_TAG,
            "createNote external allowed source=$source draftTitleLen=${draft?.title?.length ?: -1} draftContentLen=${draft?.content?.length ?: -1} " +
                "lastOpenPath=$lastOpenNoteShownPath ageSinceOpen=${ageSinceOpen}ms stack=$stack",
        )
        openNoteRequestVersion++
        _externalNoteDraft.value = draft ?: KardLeafCustomFeatures.ExternalNoteDraft()
        _currentNote.value = null
        _isOpeningNoteContent.value = false
        _isEditorOpen.value = true
        _editorDirty.value = false
        _externalConflict.value = null
    }

    fun createTemporaryNote(source: String = "dashboard_quick_draft") {
        createNote(
            KardLeafCustomFeatures.ExternalNoteDraft(
                title = "草稿",
                folder = PrefsManager.DEFAULT_DRAFT_FOLDER_NAME,
                isTemporary = false,
            ),
            source = source,
        )
    }

    fun closeEditor() {
        openNoteRequestVersion++
        _isEditorOpen.value = false
        _currentNote.value = null
        _externalNoteDraft.value = null
        _isOpeningNoteContent.value = false
        _editorDirty.value = false
        _externalConflict.value = null
    }

    /** 编辑器标记是否有未保存修改，用于外部文件变化时的冲突检测 */
    fun setEditorDirty(dirty: Boolean) {
        _editorDirty.value = dirty
    }

    /** 用户选择保留自己的修改：丢弃外部版本，继续编辑 */
    fun dismissExternalConflict() {
        _externalConflict.value = null
    }

    /** 用户选择使用外部版本：加载外部内容，清空脏标记与冲突 */
    fun applyExternalConflict() {
        val external = _externalConflict.value ?: return
        _currentNote.value = external
        _editorDirty.value = false
        _externalConflict.value = null
    }

    fun setFilter(filter: NoteFilter) {
        Log.d(
            CUSTOM_SORT_FLASH_TAG,
            "setFilter from=${_currentFilter.value} to=$filter sortVersion=${_folderSortVersion.value}",
        )
        if (_currentFilter.value != filter) {
            _customSortDragModeEnabled.value = false
        }
        _currentFilter.value = filter
        persistLastFilter(filter)
    }

    fun setCustomSortDragModeEnabled(enabled: Boolean) {
        _customSortDragModeEnabled.value = enabled
    }

    /**
     * "显示当前标签全部笔记"：点击高亮的分类标签时 toggle 递归显示。
     * 进入递归模式后展示该文件夹及其所有子文件夹的笔记；再次点击高亮标签回到精确模式。
     * recursive 是临时浏览状态，不持久化，重启后回到精确模式。
     */
    fun showAllInFolder(path: String) {
        val current = _currentFilter.value
        val alreadyRecursive = current is NoteFilter.Label && current.recursive && current.name == path
        val newFilter = NoteFilter.Label(path, recursive = !alreadyRecursive)
        Log.d(
            CUSTOM_SORT_FLASH_TAG,
            "showAllInFolder current=$current path=$path alreadyRecursive=$alreadyRecursive to=$newFilter",
        )
        _customSortDragModeEnabled.value = false
        _currentFilter.value = newFilter
        // 持久化为普通 LABEL（不带 recursive），重启恢复精确模式
        persistLastFilter(NoteFilter.Label(path))
    }

    fun navigateUpFolder(): Boolean {
        val path = (_currentFilter.value as? NoteFilter.Label)?.name.orEmpty()
        if (path.isBlank()) return false
        val parent = path.substringBeforeLast("/", missingDelimiterValue = "")
        val nextFilter = if (parent.isBlank()) NoteFilter.All else NoteFilter.Label(parent)
        Log.d(
            CUSTOM_SORT_FLASH_TAG,
            "navigateUpFolder path=$path parent=$parent to=$nextFilter",
        )
        _customSortDragModeEnabled.value = false
        _currentFilter.value = nextFilter
        _homeScrollToTopEvents.value += 1
        return true
    }

    fun createLabel(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val success = repository.createLabel(name)
            if (success) {
                val current = _tempLabels.value.toMutableSet()
                current.add(name)
                _tempLabels.value = current
            }
        }
    }

    fun deleteLabel(
        name: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val success = repository.deleteLabel(name)
            if (success) {
                val current = _tempLabels.value.toMutableSet()
                if (current.remove(name)) {
                    _tempLabels.value = current
                }
                onSuccess()
            } else {
                onError("Label must be empty to delete it")
            }
        }
    }

    fun deleteLabelWithContents(
        name: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val folder = normalizeFolderPath(name)
        if (folder.isBlank()) return
        viewModelScope.launch {
            val success = repository.deleteLabelWithContents(folder)
            if (success) {
                val prefix = "$folder/"
                val current = _tempLabels.value
                    .filterNot { it == folder || it.startsWith(prefix) }
                    .toSet()
                _tempLabels.value = current
                val filter = _currentFilter.value
                if (filter is NoteFilter.Label && (filter.name == folder || filter.name.startsWith(prefix))) {
                    _currentFilter.value = NoteFilter.All
                    persistLastFilter(NoteFilter.All)
                }
                onSuccess()
            } else {
                onError("删除文件夹失败")
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note.file.path)
        }
    }

    fun archiveNote(note: Note) {
        viewModelScope.launch {
            repository.archiveNote(note.file.path)
        }
    }

    fun restoreNote(note: Note) {
        viewModelScope.launch {
            repository.restoreNote(note.file.path)
        }
    }

    fun saveNote(
        note: Note,
        oldFile: java.io.File? = null,
        saveHistory: Boolean = false,
    ) {
        viewModelScope.launch {
            try {
                Log.d(
                    YAML_TAG_TRACE_TAG,
                    "MainViewModel.saveNote start oldFile=${oldFile?.path} noteFile=${note.file.path} title=${note.title} tags=${note.tags} saveHistory=$saveHistory",
                )
                val savedPath = repository.saveNote(note, oldFile, saveHistory)
                Log.d(YAML_TAG_TRACE_TAG, "MainViewModel.saveNote result savedPath=$savedPath oldFile=${oldFile?.path} inputTags=${note.tags}")
                if (savedPath.isNotEmpty()) {
                    val updatedFile = java.io.File(savedPath)
                    val newTitle = updatedFile.nameWithoutExtension
                    val finalNote = note.copy(file = updatedFile, title = newTitle)
                    Log.d(
                        YAML_TAG_TRACE_TAG,
                        "MainViewModel.saveNote finalNote path=${finalNote.file.path} title=${finalNote.title} tags=${finalNote.tags}",
                    )

                    // 保存到文件夹后，把文件夹路径加入临时标签，使分类标签栏即时显示
                    // 新创建的文件夹，无需等待全量扫描刷新 labels（修复 URL 新建笔记后
                    // 文件夹在标签栏迟迟不出现、需手动下滑才显示的问题）。
                    if (finalNote.folder.isNotBlank()) {
                        val current = _tempLabels.value.toMutableSet()
                        if (current.add(finalNote.folder)) {
                            _tempLabels.value = current
                        }
                    }

                    val current = _currentNote.value
                    val editorOpen = _isEditorOpen.value
                    val wasNewNote = oldFile == null
                    val finalFolder = normalizeFolderPath(finalNote.folder)
                    val finalPath = normalizeNotePath(finalNote.file.path)
                    val finalFolderSettings = prefsManager.getFolderSortSettings(finalFolder)
                    val finalCustomOrder = prefsManager.getFolderCustomOrder(finalFolder)
                    Log.d(
                        CUSTOM_SORT_TRACE_TAG,
                        "saveNote outer savedPath=$savedPath finalFolder=$finalFolder finalPath=$finalPath oldFile=${oldFile?.path} current=${current?.file?.path} editorOpen=$editorOpen wasNewNote=$wasNewNote settings=$finalFolderSettings orderBefore=${customSortPathSummary(finalCustomOrder)}",
                    )
                    if (current != null && current.file.path == oldFile?.path) {
                        _currentNote.value = finalNote
                    } else if (wasNewNote && editorOpen) {
                        _currentNote.value = finalNote
                    }
                    _externalNoteDraft.value = null
                    // 保存成功后清除未保存标记，避免自身保存触发的文件变化被误判为冲突
                    _editorDirty.value = false
                    _externalConflict.value = null
                    if (wasNewNote) {
                        Log.d(
                            CUSTOM_SORT_TRACE_TAG,
                            "saveNote outer trigger prepend folder=${finalNote.folder} path=${finalNote.file.path}",
                        )
                        prependNewNoteToFolderCustomOrder(finalNote.folder, finalNote.file.path)
                        _homeScrollToTopEvents.value += 1
                    } else {
                        Log.d(
                            CUSTOM_SORT_TRACE_TAG,
                            "saveNote outer skip prepend wasNewNote=false folder=${finalNote.folder} path=${finalNote.file.path}",
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to save note", e)
            }
        }
    }

    private fun prependNewNoteToFolderCustomOrder(
        folder: String,
        newPath: String,
    ) {
        val normalizedFolder = normalizeFolderPath(folder)
        val normalizedPath = normalizeNotePath(newPath)
        val settings = prefsManager.getFolderSortSettings(normalizedFolder)
        val currentOrder = prefsManager.getFolderCustomOrder(normalizedFolder)
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "prepend enter folder=$folder normalizedFolder=$normalizedFolder path=$newPath normalizedPath=$normalizedPath settings=$settings orderBefore=${customSortPathSummary(currentOrder)}",
        )
        if (normalizedPath.isBlank()) {
            Log.d(CUSTOM_SORT_TRACE_TAG, "prepend skip blank path folder=$normalizedFolder")
            return
        }
        if (settings?.order != PrefsManager.SortOrder.CUSTOM) {
            Log.d(
                CUSTOM_SORT_TRACE_TAG,
                "prepend skip not custom folder=$normalizedFolder settings=$settings",
            )
            return
        }

        val nextOrder = mutableListOf(normalizedPath)
        currentOrder
            .map(::normalizeNotePath)
            .filter { it.isNotBlank() && it != normalizedPath }
            .distinct()
            .forEach { nextOrder += it }

        prefsManager.saveFolderCustomOrder(normalizedFolder, nextOrder)
        _folderSortVersion.value += 1
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "prepend saved folder=$normalizedFolder orderAfter=${customSortPathSummary(nextOrder)} folderSortVersion=${_folderSortVersion.value}",
        )
    }

    fun setSortOrder(order: PrefsManager.SortOrder) {
        val currentFilter = _currentFilter.value
        val folderFilter = currentFilter as? NoteFilter.Label
        val customSortKey = customSortStorageKeyFor(currentFilter)
        val isAllNotesFilter = currentFilter is NoteFilter.All
        val customSortSettings = customSortKey?.let { prefsManager.getFolderSortSettings(it) }
        val customSortName = customSortKey?.let { customSortLogNameFor(currentFilter, it) }
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "setSortOrder request order=$order customTarget=$customSortName recursive=${folderFilter?.recursive} customSettings=$customSortSettings globalOrder=${_sortOrder.value} globalDirection=${_sortDirection.value}",
        )
        if (order == PrefsManager.SortOrder.CUSTOM && customSortKey != null) {
            enableCurrentFolderCustomSort(emptyList())
        } else if (isAllNotesFilter && customSortKey != null && order != PrefsManager.SortOrder.CUSTOM) {
            _customSortDragModeEnabled.value = false
            prefsManager.clearFolderSortSettings(customSortKey)
            _sortOrder.value = order
            prefsManager.saveSortOrder(order)
            _folderSortVersion.value += 1
        } else if (customSortKey != null && customSortSettings != null) {
            if (order != PrefsManager.SortOrder.CUSTOM) {
                _customSortDragModeEnabled.value = false
            }
            prefsManager.saveFolderSortSettings(customSortKey, customSortSettings.copy(order = order))
            _folderSortVersion.value += 1
        } else if (order != PrefsManager.SortOrder.CUSTOM) {
            _customSortDragModeEnabled.value = false
            _sortOrder.value = order
            prefsManager.saveSortOrder(order)
        }
    }

    fun enableCurrentFolderCustomSort(initialPaths: Collection<String>) {
        val currentFilter = _currentFilter.value
        val customSortKey = customSortStorageKeyFor(currentFilter) ?: return
        val customSortName = customSortLogNameFor(currentFilter, customSortKey)
        val beforeOrder = prefsManager.getFolderCustomOrder(customSortKey)
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "enableCurrentFolderCustomSort enter target=$customSortName initial=${customSortPathSummary(initialPaths)} orderBefore=${customSortPathSummary(beforeOrder)} settingsBefore=${prefsManager.getFolderSortSettings(customSortKey)}",
        )
        if (beforeOrder.isEmpty() && initialPaths.isNotEmpty()) {
            prefsManager.saveFolderCustomOrder(customSortKey, initialPaths)
        }
        val current = prefsManager.getFolderSortSettings(customSortKey)
        val next = (current ?: PrefsManager.FolderSortSettings(PrefsManager.SortOrder.CUSTOM, _sortDirection.value))
            .copy(order = PrefsManager.SortOrder.CUSTOM)
        prefsManager.saveFolderSortSettings(customSortKey, next)
        _folderSortVersion.value += 1
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "enableCurrentFolderCustomSort saved target=$customSortName settingsAfter=$next orderAfter=${customSortPathSummary(prefsManager.getFolderCustomOrder(customSortKey))} folderSortVersion=${_folderSortVersion.value}",
        )
    }

    fun saveCurrentFolderCustomSortOrder(paths: Collection<String>) {
        val currentFilter = _currentFilter.value
        val customSortKey = customSortStorageKeyFor(currentFilter) ?: return
        val customSortName = customSortLogNameFor(currentFilter, customSortKey)
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "saveCurrentFolderCustomSortOrder enter target=$customSortName paths=${customSortPathSummary(paths)} settingsBefore=${prefsManager.getFolderSortSettings(customSortKey)}",
        )
        prefsManager.saveFolderCustomOrder(customSortKey, paths)
        val current = prefsManager.getFolderSortSettings(customSortKey)
        val next = (current ?: PrefsManager.FolderSortSettings(PrefsManager.SortOrder.CUSTOM, _sortDirection.value))
            .copy(order = PrefsManager.SortOrder.CUSTOM)
        prefsManager.saveFolderSortSettings(customSortKey, next)
        _folderSortVersion.value += 1
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "saveCurrentFolderCustomSortOrder saved target=$customSortName settingsAfter=$next orderAfter=${customSortPathSummary(prefsManager.getFolderCustomOrder(customSortKey))} folderSortVersion=${_folderSortVersion.value}",
        )
    }

    fun getFolderSortSettings(folder: String): PrefsManager.FolderSortSettings? {
        val settings = prefsManager.getFolderSortSettings(folder)
        Log.d(
            CUSTOM_SORT_FLASH_TAG,
            "getFolderSortSettings folder=$folder settings=$settings sortVersion=${_folderSortVersion.value}",
        )
        return settings
    }

    fun getFolderCustomSortOrder(folder: String): List<String> {
        val order = prefsManager.getFolderCustomOrder(folder)
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "getFolderCustomSortOrder folder=$folder order=${customSortPathSummary(order)} settings=${prefsManager.getFolderSortSettings(folder)}",
        )
        return order
    }

    fun getFolderDisplayOrder(parentFolder: String): List<String> =
        prefsManager.getFolderDisplayOrder(parentFolder)

    fun saveFolderDisplayOrder(
        parentFolder: String,
        folderPaths: Collection<String>,
    ) {
        prefsManager.saveFolderDisplayOrder(parentFolder, folderPaths)
        _folderManagerOrderVersion.value += 1
    }

    fun setSortDirection(direction: PrefsManager.SortDirection) {
        val currentFilter = _currentFilter.value
        val customSortKey = customSortStorageKeyFor(currentFilter)
        val customSortSettings = customSortKey?.let { prefsManager.getFolderSortSettings(it) }
        if (currentFilter !is NoteFilter.All && customSortKey != null && customSortSettings != null) {
            prefsManager.saveFolderSortSettings(customSortKey, customSortSettings.copy(direction = direction))
            _folderSortVersion.value += 1
        } else {
            _sortDirection.value = direction
            prefsManager.saveSortDirection(direction)
        }
    }

    fun setCurrentFolderSortOverrideEnabled(enabled: Boolean) {
        val folder = (_currentFilter.value as? NoteFilter.Label)?.name ?: return
        if (enabled) {
            val current = prefsManager.getFolderSortSettings(folder)
            prefsManager.saveFolderSortSettings(
                folder,
                current ?: PrefsManager.FolderSortSettings(_sortOrder.value, _sortDirection.value),
            )
        } else {
            prefsManager.clearFolderSortSettings(folder)
        }
        _folderSortVersion.value += 1
    }

    fun setViewMode(mode: PrefsManager.ViewMode) {
        _viewMode.value = mode
        prefsManager.saveViewMode(mode)
    }

    fun reloadCustomSettings() {
        _sortOrder.value = prefsManager.getSortOrder()
        _sortDirection.value = prefsManager.getSortDirection()
        _trashSortOrder.value = prefsManager.getTrashSortOrder()
        _viewMode.value = prefsManager.getViewMode()
        _cardDensity.value = prefsManager.getCardDensity()
        _showYamlTagsOnLooseCards.value = prefsManager.isLooseCardYamlTagsVisible()
        _showModifiedDateOnCards.value = prefsManager.isModifiedDateOnCardsVisible()
        _showNoteTitleOnCards.value = prefsManager.isNoteTitleOnCardsVisible()
        _showDateFilenameTitleOnCards.value = prefsManager.isDateFilenameTitleOnCardsVisible()
        _customHiddenFilenamePatterns.value = prefsManager.getCustomHiddenFilenamePatterns()
        _selectionToolbarItemOrder.value = prefsManager.getSelectionToolbarItemOrder()
        _selectionToolbarMoreItems.value = prefsManager.getSelectionToolbarMoreItems()
        _hiddenFoldersVersion.value += 1
        viewModelScope.launch {
            repository.refreshNotes()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private val _selectedNotes = MutableStateFlow<Set<String>>(emptySet())
    val selectedNotes: StateFlow<Set<String>> = _selectedNotes.asStateFlow()

    fun toggleSelection(note: Note) {
        val current = _selectedNotes.value.toMutableSet()
        if (current.contains(note.file.path)) {
            current.remove(note.file.path)
        } else {
            current.add(note.file.path)
        }
        _selectedNotes.value = current
    }

    fun clearSelection() {
        _selectedNotes.value = emptySet()
    }

    fun deleteSelectedNotes() {
        val selectedIds = _selectedNotes.value.toList()
        if (selectedIds.isEmpty()) return
        pendingNoteUndo = PendingNoteUndo.Restore(selectedIds)
        clearSelection()
        pendingNoteUndoJob = viewModelScope.launch {
            repository.deleteNotes(selectedIds)
        }
    }

    fun archiveSelectedNotes() {
        val selectedIds = _selectedNotes.value.toList()
        if (selectedIds.isEmpty()) return
        pendingNoteUndo = PendingNoteUndo.Restore(selectedIds)
        clearSelection()
        pendingNoteUndoJob = viewModelScope.launch {
            repository.archiveNotes(selectedIds)
        }
    }

    fun restoreSelectedNotes() {
        val selectedIds = _selectedNotes.value.toList()
        clearSelection()
        viewModelScope.launch {
            selectedIds.forEach { repository.restoreNote(it) }
        }
    }

    fun moveSelectedNotes(
        targetLabel: String,
        selectedSnapshot: List<Note> = emptyList(),
    ) {
        val selectedIds = _selectedNotes.value.toSet()
        if (selectedIds.isEmpty()) return
        if (selectedSnapshot.isNotEmpty()) {
            pendingNoteUndo = PendingNoteUndo.MoveBack(
                selectedSnapshot.map { note ->
                    RoomNoteRepository.MovedNotePath(
                        oldPath = note.file.path,
                        newPath = joinPath(targetLabel, note.file.name),
                    )
                },
            )
        }
        clearSelection()

        pendingNoteUndoJob = viewModelScope.launch {
            val allNotesList = allNotes.first()
            val notesToMove = allNotesList.filter { selectedIds.contains(it.file.path) }

            val targetFolder = targetLabel

            if (targetFolder.isNotBlank()) {
                val current = _tempLabels.value.toMutableSet()
                current.add(targetFolder)
                _tempLabels.value = current
            }

            val moves = repository.moveNotesWithResult(notesToMove, targetFolder)
            if (moves.isNotEmpty()) {
                pendingNoteUndo = PendingNoteUndo.MoveBack(moves)
                _homeScrollToTopEvents.value += 1
                runCatching { repository.refreshNotesFromExternalChange() }
            }
        }
    }


    fun duplicateSelectedNotes(
        targetFolder: String,
        onDone: (Int) -> Unit = {},
    ) {
        val selectedIds = _selectedNotes.value.toSet()
        if (selectedIds.isEmpty()) return
        clearSelection()

        viewModelScope.launch {
            val normalizedTargetFolder = normalizeFolderPath(targetFolder)
            val allNotesList = allNotes.first()
            val selectedNotes = allNotesList.filter { it.file.path in selectedIds && !it.isArchived && !it.isTrashed }
            val existingTitles = allNotesList
                .filter { normalizeFolderPath(it.folder) == normalizedTargetFolder && !it.isTrashed }
                .map { it.title }
                .toMutableSet()
            var copiedCount = 0
            val copiedPathPairs = mutableListOf<Pair<String, String>>()
            val folderNotesBeforeCopy = allNotesList.filter { note ->
                normalizeFolderPath(note.folder) == normalizedTargetFolder && !note.isArchived && !note.isTrashed
            }

            selectedNotes.forEach { note ->
                val sourceNote = repository.getNoteForEditor(note.file.path) ?: note
                val copyTitle = buildDuplicateNoteTitle(sourceNote.title, existingTitles)
                existingTitles.add(copyTitle)
                val copiedPath = joinPath(normalizedTargetFolder, "$copyTitle.md")
                val savedPath = repository.saveNote(
                    sourceNote.copy(
                        file = java.io.File(copiedPath),
                        title = copyTitle,
                        isArchived = false,
                        isTrashed = false,
                        deletedAt = null,
                    ),
                    oldFile = null,
                    saveHistory = false,
                )
                if (savedPath.isNotEmpty()) {
                    copiedCount += 1
                    copiedPathPairs += normalizeNotePath(note.file.path) to normalizeNotePath(savedPath)
                }
            }

            if (copiedPathPairs.isNotEmpty()) {
                appendCopiedNotesToFolderCustomOrder(
                    folder = normalizedTargetFolder,
                    folderNotesBeforeCopy = folderNotesBeforeCopy,
                    copiedPathPairs = copiedPathPairs,
                )
            }

            if (normalizedTargetFolder.isNotBlank() && copiedCount > 0) {
                val current = _tempLabels.value.toMutableSet()
                current.add(normalizedTargetFolder)
                _tempLabels.value = current
            }
            if (copiedCount > 0 && shouldScrollHomeToTopAfterDuplicate(normalizedTargetFolder)) {
                _homeScrollToTopEvents.value += 1
            }
            onDone(copiedCount)
        }
    }

    private fun shouldScrollHomeToTopAfterDuplicate(targetFolder: String): Boolean {
        val filter = _currentFilter.value
        val copyWillBeVisible = when (filter) {
            NoteFilter.All,
            NoteFilter.Recent -> true
            NoteFilter.Favorites -> true
            NoteFilter.Drafts -> targetFolder == PrefsManager.DEFAULT_DRAFT_FOLDER_NAME
            is NoteFilter.Label -> {
                val folder = normalizeFolderPath(filter.name)
                targetFolder == folder || (filter.recursive && targetFolder.startsWith("$folder/"))
            }
            NoteFilter.Archive,
            NoteFilter.Trash -> false
            is NoteFilter.YamlTag -> false
        }
        if (!copyWillBeVisible) return false
        if (filter is NoteFilter.Recent) return true

        val folderSettings = (filter as? NoteFilter.Label)
            ?.takeIf { !it.recursive }
            ?.name
            ?.let { prefsManager.getFolderSortSettings(it) }
        val effectiveSortOrder = folderSettings?.order ?: _sortOrder.value
        val effectiveSortDirection = folderSettings?.direction ?: _sortDirection.value
        return effectiveSortOrder == PrefsManager.SortOrder.DATE_MODIFIED &&
            effectiveSortDirection == PrefsManager.SortDirection.DESCENDING
    }

    private fun buildDuplicateNoteTitle(
        sourceTitle: String,
        existingTitles: Set<String>,
    ): String {
        val normalizedSourceTitle = sourceTitle.trim().ifBlank { "Untitled" }
        val duplicateTitleRegex = Regex(".*~副本(?:\\d+)?(?:~\\d+)*$")
        val appendSerialOnly = duplicateTitleRegex.matches(normalizedSourceTitle)

        var index = 1
        var copyTitle = if (appendSerialOnly) {
            "$normalizedSourceTitle~$index"
        } else {
            "$normalizedSourceTitle~副本$index"
        }
        while (copyTitle in existingTitles) {
            index += 1
            copyTitle = if (appendSerialOnly) {
                "$normalizedSourceTitle~$index"
            } else {
                "$normalizedSourceTitle~副本$index"
            }
        }
        return copyTitle
    }

    private fun appendCopiedNotesToFolderCustomOrder(
        folder: String,
        folderNotesBeforeCopy: List<Note>,
        copiedPathPairs: List<Pair<String, String>>,
    ) {
        val currentOrder = prefsManager.getFolderCustomOrder(folder)
        val isCustomSortFolder =
            prefsManager.getFolderSortSettings(folder)?.order == PrefsManager.SortOrder.CUSTOM
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "appendCopy enter folder=$folder isCustomSortFolder=$isCustomSortFolder beforeNotes=${customSortNoteSummary(folderNotesBeforeCopy)} copied=$copiedPathPairs orderBefore=${customSortPathSummary(currentOrder)}",
        )
        if (currentOrder.isEmpty() && !isCustomSortFolder) {
            Log.d(CUSTOM_SORT_TRACE_TAG, "appendCopy skip folder=$folder orderEmpty=true notCustom=true")
            return
        }

        val visibleOrder = sortByCustomFolderOrder(folderNotesBeforeCopy, folder)
            .map { normalizeNotePath(it.file.path) }
            .filter { it.isNotBlank() }
            .distinct()
        val nextOrder = if (currentOrder.isEmpty() && isCustomSortFolder) {
            visibleOrder.toMutableList()
        } else {
            currentOrder
                .map(::normalizeNotePath)
                .filter { it.isNotBlank() }
                .distinct()
                .toMutableList()
        }

        copiedPathPairs.forEach { (sourcePathRaw, copyPathRaw) ->
            val sourcePath = normalizeNotePath(sourcePathRaw)
            val copyPath = normalizeNotePath(copyPathRaw)
            if (sourcePath.isBlank() || copyPath.isBlank()) return@forEach

            nextOrder.remove(copyPath)
            val sourceIndex = nextOrder.indexOf(sourcePath).takeIf { it >= 0 }
                ?: insertMissingSourceIntoCustomOrder(
                    nextOrder = nextOrder,
                    visibleOrder = visibleOrder,
                    sourcePath = sourcePath,
                )

            val insertIndex = (sourceIndex + 1).coerceAtMost(nextOrder.size)
            nextOrder.add(insertIndex, copyPath)
        }

        prefsManager.saveFolderCustomOrder(folder, nextOrder)
        _folderSortVersion.value += 1
        Log.d(
            CUSTOM_SORT_TRACE_TAG,
            "appendCopy saved folder=$folder orderAfter=${customSortPathSummary(nextOrder)} folderSortVersion=${_folderSortVersion.value}",
        )
    }

    private fun insertMissingSourceIntoCustomOrder(
        nextOrder: MutableList<String>,
        visibleOrder: List<String>,
        sourcePath: String,
    ): Int {
        val visibleIndex = visibleOrder.indexOf(sourcePath)
        if (visibleIndex < 0) {
            nextOrder += sourcePath
            return nextOrder.lastIndex
        }

        val nextVisiblePath = visibleOrder
            .drop(visibleIndex + 1)
            .firstOrNull { it in nextOrder }
        val insertIndex = nextVisiblePath
            ?.let { nextOrder.indexOf(it).takeIf { index -> index >= 0 } }
            ?: nextOrder.size
        nextOrder.add(insertIndex, sourcePath)
        return insertIndex
    }

    fun moveNoteToPrivacy(
        note: Note,
        title: String? = null,
        content: String? = null,
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val moved = repository.moveNoteToPrivacy(note.file.path, title, content)
            onDone(moved)
        }
    }

    fun moveSelectedNotesToPrivacy(onDone: (Int) -> Unit = {}) {
        val selectedIds = _selectedNotes.value.toList()
        if (selectedIds.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            onDone(repository.moveNotesToPrivacy(selectedIds))
        }
    }

    fun undoLastNoteAction() {
        val runningJob = pendingNoteUndoJob
        viewModelScope.launch {
            runningJob?.join()
            val undo = pendingNoteUndo ?: return@launch
            pendingNoteUndo = null
            pendingNoteUndoJob = null
            when (undo) {
                is PendingNoteUndo.Restore -> {
                    undo.noteIds.forEach { repository.restoreNote(it) }
                }
                is PendingNoteUndo.MoveBack -> {
                    val allNotesList = allNotes.first()
                    undo.moves.forEach { move ->
                        val movedNote = allNotesList.firstOrNull { it.file.path == move.newPath }
                        if (movedNote != null) {
                            repository.moveNotes(listOf(movedNote), folderFromPath(move.oldPath))
                        }
                    }
                }
            }
        }
    }

    private fun folderFromPath(path: String): String =
        path.substringBeforeLast("/", missingDelimiterValue = "")

    private fun joinPath(
        folder: String,
        fileName: String,
    ): String = if (folder.isBlank()) fileName else "$folder/$fileName"

    fun togglePinSelectedNotes() {
        val selectedIds = _selectedNotes.value.toList()
        clearSelection()

        viewModelScope.launch {
            val allNotesList = allNotes.first()
            val notesToUpdate = allNotesList.filter { selectedIds.contains(it.file.path) }
            val shouldPin = notesToUpdate.any { !it.isPinned }
            repository.togglePinStatus(selectedIds, shouldPin)
        }
    }

    fun addTagsToSelectedNotes(
        tags: Collection<String>,
        onDone: () -> Unit = {},
    ) {
        val selectedIds = _selectedNotes.value.toList()
        val normalizedTags = NoteFormatUtils.normalizeTags(tags)
        if (selectedIds.isEmpty() || normalizedTags.isEmpty()) return
        viewModelScope.launch {
            val notesByPath = allNotes.first().associateBy { it.file.path }
            selectedIds.forEach { noteId ->
                val note = notesByPath[noteId]
                val mergedTags = NoteFormatUtils.normalizeTags((note?.tags.orEmpty()) + normalizedTags)
                Log.d(YAML_TAG_TRACE_TAG, "addTagsToSelectedNotes noteId=$noteId currentTags=${note?.tags.orEmpty()} inputTags=$normalizedTags mergedTags=$mergedTags")
                repository.updateNoteTags(noteId, mergedTags)
            }
            clearSelection()
            repository.refreshNotes()
            onDone()
        }
    }

    fun renameYamlTag(
        oldTag: String,
        newTag: String,
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val changed = repository.renameYamlTag(oldTag, newTag)
            if (changed) repository.refreshNotes()
            onDone(changed)
        }
    }

    fun deleteYamlTag(
        tag: String,
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val changed = repository.deleteYamlTag(tag)
            if (changed) repository.refreshNotes()
            onDone(changed)
        }
    }

    fun getNoteHistory(noteId: String): Flow<List<NoteHistory>> = repository.getNoteHistory(noteId)


    fun getNoteRemarks(noteId: String): Flow<List<NoteRemark>> = repository.getNoteRemarks(noteId)

    suspend fun getNoteFrontMatterProperties(noteId: String): List<NoteFormatUtils.FrontMatterProperty> =
        repository.getNoteFrontMatterProperties(noteId)

    suspend fun getNoteForProperties(noteId: String): Note? =
        repository.getNote(noteId)

    suspend fun getNoteTextStatsForProperties(noteId: String): NoteTextStats =
        repository.getNoteTextStatsForProperties(noteId)

    fun addNoteRemark(
        noteId: String,
        content: String,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repository.addNoteRemark(noteId, content)
            onComplete()
        }
    }

    fun deleteNoteRemark(remarkId: Long) {
        viewModelScope.launch {
            repository.deleteNoteRemark(remarkId)
        }
    }

    suspend fun getRemarkNoteSummaries(): List<NoteRecordSummary> = repository.getRemarkNoteSummaries()

    suspend fun getHistoryNoteSummaries(): List<NoteRecordSummary> = repository.getHistoryNoteSummaries()

    fun deleteNoteHistory(historyId: Long) {
        viewModelScope.launch {
            repository.deleteNoteHistory(historyId)
        }
    }

    fun restoreNoteHistory(
        noteId: String,
        historyId: Long,
        currentDraft: Note? = null,
    ) {
        viewModelScope.launch {
            val activeNoteId =
                if (currentDraft != null && _currentNote.value != null) {
                    repository.saveNote(currentDraft, _currentNote.value!!.file, saveHistory = false).ifBlank { noteId }
                } else {
                    noteId
                }
            val savedPath = repository.restoreNoteHistory(activeNoteId, historyId)
            if (savedPath.isNotEmpty()) {
                repository.getNote(savedPath)?.let { restored ->
                    _currentNote.value = restored
                }
            }
        }
    }

    suspend fun getHistoryCleanupPreview(keep: Int): List<HistoryCleanupPreview> =
        repository.getHistoryCleanupPreview(keep)

    fun cleanupOldHistoryVersions() {
        viewModelScope.launch {
            try {
                repository.cleanupOldHistoryVersions()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to cleanup old history versions", e)
            }
        }
    }

    // region 隐私空间
    val privacyNotes: StateFlow<List<com.kangle.kardleaf.data.database.PrivacyNoteEntity>> =
        repository.getAllPrivacyNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun savePrivacyNote(id: Long, title: String, content: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.savePrivacyNote(id, title, content)
            onDone()
        }
    }

    fun savePrivacyNoteAndReturnId(id: Long, title: String, content: String, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val savedId = repository.savePrivacyNote(id, title, content)
            onSaved(savedId)
        }
    }

    fun deletePrivacyNote(id: Long) {
        viewModelScope.launch { repository.deletePrivacyNote(id) }
    }

    fun exportPrivacyNotes(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onSuccess(repository.exportPrivacyNotes())
            } catch (e: Exception) {
                onError(e.message ?: "导出失败")
            }
        }
    }

    fun importPrivacyNotes(json: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onSuccess(repository.importPrivacyNotes(json))
            } catch (e: Exception) {
                onError(e.message ?: "导入失败")
            }
        }
    }
    // endregion

    fun exportUserDataBackup(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                onSuccess(repository.exportUserDataBackup())
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to export user data backup", e)
                onError(e.message ?: "Export failed")
            }
        }
    }

    fun importUserDataBackup(
        json: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                repository.importUserDataBackup(json)
                repository.refreshNotes()
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to import user data backup", e)
                onError(e.message ?: "Import failed")
            }
        }
    }

    fun renameLabel(
        oldPath: String,
        newPath: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        if (oldPath.isBlank() || newPath.isBlank() || oldPath == newPath) return
        viewModelScope.launch {
            val success = repository.renameLabel(oldPath, newPath)
            if (success) {
                val current = _tempLabels.value.toMutableSet()
                val oldPrefix = "$oldPath/"
                val renamedTempLabels =
                    current.map { label ->
                        when {
                            label == oldPath -> newPath
                            label.startsWith(oldPrefix) -> newPath + label.removePrefix(oldPath)
                            else -> label
                        }
                    }.toSet()
                _tempLabels.value = renamedTempLabels

                val filter = _currentFilter.value
                if (filter is NoteFilter.Label) {
                    val filterPath = filter.name
                    val newFilter =
                        when {
                            filterPath == oldPath -> newPath
                            filterPath.startsWith(oldPrefix) -> newPath + filterPath.removePrefix(oldPath)
                            else -> null
                        }
                    if (newFilter != null) {
                        _currentFilter.value = NoteFilter.Label(newFilter)
                    }
                }
                onSuccess()
            } else {
                onError("Folder rename failed")
            }
        }
    }

    fun toggleFavoriteSelectedNotes() {
        val selectedIds = _selectedNotes.value.toList()
        clearSelection()

        viewModelScope.launch {
            val allNotesList = allNotes.first()
            val notesToUpdate = allNotesList.filter { selectedIds.contains(it.file.path) }
            val shouldFavorite = notesToUpdate.any { !it.isFavorite }
            repository.toggleFavoriteStatus(selectedIds, shouldFavorite)
        }
    }

    fun togglePinNote(note: Note) {
        if (note.isArchived || note.isTrashed) return
        viewModelScope.launch {
            repository.togglePinStatus(listOf(note.file.path), !note.isPinned)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    private suspend fun cleanupExpiredTrashIfNeeded() {
        val days = prefsManager.getTrashAutoCleanDays()
        if (days > 0) {
            repository.cleanupExpiredTrash(days)
        }
    }

    suspend fun preparePreviewMarkdown(
        markdown: String,
        currentFolder: String,
    ): String = repository.resolveMarkdownImages(markdown, currentFolder)

    suspend fun importImage(
        uri: Uri,
        currentFolder: String,
    ): String = repository.importImage(uri, currentFolder)

    suspend fun importDrawingImage(
        bitmap: Bitmap,
        currentFolder: String,
    ): String = repository.importDrawingImage(bitmap, currentFolder)

    suspend fun resolveNoteImages(note: Note): List<RoomNoteRepository.NoteImage> =
        repository.resolveNoteImages(note.content, note.folder)

    suspend fun resolveNoteThumbnailBitmap(note: Note): android.graphics.Bitmap? =
        repository.resolveNoteThumbnailBitmap(note)

    private fun persistLastFilter(filter: NoteFilter) {
        when (filter) {
            is NoteFilter.All -> {
                prefsManager.saveLastFilterType("ALL")
            }
            is NoteFilter.Label -> {
                prefsManager.saveLastFilterType("LABEL")
                prefsManager.saveLastFilterLabel(filter.name)
            }
            is NoteFilter.YamlTag -> {
                prefsManager.saveLastFilterType("YAML_TAG")
                prefsManager.saveLastFilterLabel(filter.name)
            }
            is NoteFilter.Favorites -> {
                prefsManager.saveLastFilterType("FAVORITES")
            }
            is NoteFilter.Drafts -> {
                prefsManager.saveLastFilterType("DRAFTS")
            }
            is NoteFilter.Recent -> {
                prefsManager.saveLastFilterType("RECENT")
            }
            // 不记住 Trash / Archive
            else -> {}
        }
    }

    companion object {
        private fun restoreLastFilter(prefsManager: PrefsManager): NoteFilter {
            // 优先级：恢复上次标签 > 默认启动标签 > 全部笔记
            if (prefsManager.isRestoreLastFilterEnabled()) {
                return when (prefsManager.getLastFilterType()) {
                    "LABEL" -> {
                        val label = prefsManager.getLastFilterLabel()
                        if (label.isNotBlank()) NoteFilter.Label(label) else NoteFilter.All
                    }
                    "YAML_TAG" -> {
                        val tag = prefsManager.getLastFilterLabel()
                        if (tag.isNotBlank()) NoteFilter.YamlTag(tag) else NoteFilter.All
                    }
                    "FAVORITES" -> NoteFilter.Favorites
                    "DRAFTS" -> NoteFilter.Drafts
                    "RECENT" -> NoteFilter.Recent
                    else -> NoteFilter.All
                }
            }
            val defaultLabel = prefsManager.getDefaultStartLabel()
            if (defaultLabel.isNotBlank()) {
                return NoteFilter.Label(defaultLabel)
            }
            return NoteFilter.All
        }
    }
}

private fun bodyOnlySearchMatch(
    note: Note,
    matchedNoteIds: Set<String>,
): SearchMatch? =
    if (note.id in matchedNoteIds) {
        SearchMatch("正文", note.content.ifBlank { "正文中命中，打开笔记查看完整内容" })
    } else {
        null
    }
