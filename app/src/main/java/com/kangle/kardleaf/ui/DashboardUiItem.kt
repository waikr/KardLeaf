package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.model.Note

sealed interface DashboardUiItem {
    val key: String

    data class NoteItem(
        val note: Note,
        val searchMatch: SearchMatch? = null,
    ) : DashboardUiItem {
        // 使用文件路径作为稳定 key；搜索状态下附加匹配范围，避免同一路径搜索结果冲突
        override val key: String = buildString {
            append(note.file.path)
            searchMatch?.scope
                ?.takeIf { it.isNotBlank() }
                ?.let { scope -> append("|$scope") }
        }
    }

    data class HeaderItem(val type: HeaderType) : DashboardUiItem {
        override val key: String = "header_${type.name}"
    }

    object SpacerItem : DashboardUiItem {
        override val key: String = "spacer_bottom"
    }

    enum class HeaderType {
        PINNED,
        OTHERS,
        ARCHIVED,
        SEARCH_RESULTS,
        SEARCH_EVERYWHERE,
    }
}
