package com.kangle.kardleaf.ui

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState

internal fun LazyStaggeredGridState.requestDashboardScrollToItem(index: Int) = requestScrollToItem(index)

internal enum class DashboardScrollAction {
    TOP,
    REVEAL_PATH,
}

internal enum class DashboardScrollReason {
    FILE_TREE_REVEAL,
    NOTE_CREATED,
    NOTE_DUPLICATED,
    USER_PULL_REFRESH,
    COLD_START_REFRESH,
    VAULT_SWITCH_REFRESH,
    RESUME_REFRESH,
    EXTERNAL_OBSERVER,
    WEBDAV_REFRESH,
    SORT_CHANGED,
}

internal fun dashboardParentFolderPath(notePath: String): String =
    notePath.replace('\\', '/').substringBeforeLast("/", missingDelimiterValue = "").trim('/')

enum class NoteRefreshReason {
    LOCAL,
    USER_PULL_REFRESH,
    COLD_START_REFRESH,
    VAULT_SWITCH_REFRESH,
    RESUME_REFRESH,
    EXTERNAL_OBSERVER,
    WEBDAV_REFRESH,
}

internal data class DashboardScrollIntent(
    val id: Long,
    val reason: DashboardScrollReason,
    val action: DashboardScrollAction,
    val targetFilter: MainViewModel.NoteFilter,
    val targetSearchActive: Boolean,
    val targetPath: String? = null,
    val waitForEditorClose: Boolean = false,
    val requiredUserScrollVersion: Long? = null,
)

internal enum class DashboardScrollDecision {
    WAIT,
    APPLY,
    DROP,
}

internal fun decideDashboardScrollIntent(
    intent: DashboardScrollIntent,
    currentFilter: MainViewModel.NoteFilter,
    searchActive: Boolean,
    editorOpen: Boolean,
    userScrollVersion: Long,
    targetIndex: Int?,
    scrollInProgress: Boolean,
): DashboardScrollDecision {
    if (intent.targetFilter != currentFilter || intent.targetSearchActive != searchActive) {
        return DashboardScrollDecision.DROP
    }
    if (intent.requiredUserScrollVersion?.let { it != userScrollVersion } == true) {
        return DashboardScrollDecision.DROP
    }
    if ((intent.waitForEditorClose && editorOpen) || scrollInProgress) {
        return DashboardScrollDecision.WAIT
    }
    if (intent.action == DashboardScrollAction.REVEAL_PATH && targetIndex == null) {
        return DashboardScrollDecision.WAIT
    }
    return DashboardScrollDecision.APPLY
}
