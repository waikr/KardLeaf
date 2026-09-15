/* MarkText selection toolbar rules: Copyright (c) 2026 Renakoni, MIT.
 * See app/src/main/codemirror-editor/src/marktext/LICENSE. */
package com.kangle.kardleaf.ui.editor.selection

import org.json.JSONArray
import org.json.JSONObject

internal data class TextSelectionToolbarSettings(
    val rows: Int = 1,
    val commands: List<String> = emptyList(),
    val enabled: Boolean = true,
) {
    fun toJson(): String = JSONObject()
        .put("enabled", enabled)
        .put("rows", rows)
        .put("commands", JSONArray(commands))
        .toString()

    companion object {
        const val ENABLED_KEY = "editor_text_selection_toolbar_enabled"
        const val ROWS_KEY = "editor_text_selection_toolbar_rows"
        const val COMMANDS_KEY = "editor_text_selection_toolbar_custom_commands"
        const val MAX_COMMANDS = 12
        val labels = linkedMapOf(
            "toggleBold" to "加粗", "toggleItalic" to "斜体", "toggleUnderline" to "下划线",
            "toggleStrike" to "删除线", "toggleCode" to "行内代码", "toggleBlockquote" to "引用",
            "toggleOrderedList" to "有序列表", "toggleUnorderedList" to "无序列表", "toggleCheckList" to "待办",
        )
        fun normalize(rows: Any?, commands: Any?) = normalize(true, rows, commands)

        fun normalize(enabled: Any?, rows: Any?, commands: Any?) = TextSelectionToolbarSettings(
            rows = if (rows == 2 || rows == "2") 2 else 1,
            commands = (commands as? String).orEmpty().split(',').filter { it in labels }.distinct().take(MAX_COMMANDS),
            enabled = enabled != false && enabled != "false",
        )

        // Literal port of MarkText's slot budget and per-rendered-arrow paging.
        fun capacity(width: Float): Int = maxOf(1, ((minOf(width * .85f, width - 16f) - 10f) / 47f).toInt())
        fun <T> pages(commands: List<T>, capacity: Int, leadingBackArrow: Boolean): List<List<T>> {
            val pages = mutableListOf<List<T>>()
            var index = 0
            while (index < commands.size) {
                val back = if (leadingBackArrow || pages.isNotEmpty()) 1 else 0
                var size = maxOf(1, maxOf(1, capacity) - back)
                if (commands.size - index > size) size = maxOf(1, maxOf(1, capacity) - back - 1)
                pages += commands.subList(index, minOf(index + size, commands.size))
                index += size
            }
            return pages
        }
    }
}
