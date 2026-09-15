package com.kangle.kardleaf.ui.editor.selection

/** Toolbar-only compatibility with SwarmNote's existing range commands; no core changes. */
internal data class SelectionBlockChange(val from: Int, val to: Int, val text: String, val start: Int, val end: Int)

internal fun selectionBlockChange(text: String, start: Int, end: Int, command: String): SelectionBlockChange? {
    if (command !in setOf("toggleBlockquote", "toggleOrderedList", "toggleUnorderedList", "toggleCheckList")) return null
    require(start in 0..text.length && end in start..text.length)
    val from = if (start == 0) 0 else text.lastIndexOf('\n', start - 1) + 1
    val to = text.indexOf('\n', end).takeIf { it >= 0 } ?: text.length
    val lines = text.substring(from, to).split('\n')
    val quote = Regex("^(\\s*)>\\s?")
    val patterns = linkedMapOf(
        "toggleCheckList" to Regex("^(\\s*)([-*])\\s\\[[ xX]+]\\s"),
        "toggleUnorderedList" to Regex("^(\\s*)([-*])\\s(?!\\[[ xX]+]\\s)"),
        "toggleOrderedList" to Regex("^(\\s*)(\\d+)\\.\\s"),
    )
    val allQuoted = lines.all { quote.containsMatchIn(it) }
    var offset = from
    var mappedStart = start
    var mappedEnd = end
    var delta = 0
    val replacement = lines.mapIndexed { index, line ->
        val indent = line.takeWhile { it.isWhitespace() }
        val match = if (command == "toggleBlockquote") quote.find(line) else patterns.values.firstNotNullOfOrNull { it.find(line) }
        val remove = when {
            command == "toggleBlockquote" -> if (allQuoted) match?.value?.length?.minus(indent.length) ?: 0 else 0
            else -> match?.value?.length?.minus(indent.length) ?: 0
        }
        val prefix = when {
            command == "toggleBlockquote" -> if (allQuoted || match != null) "" else "> "
            patterns.getValue(command).containsMatchIn(line) -> ""
            command == "toggleOrderedList" -> "${index + 1}. "
            command == "toggleCheckList" -> "- [ ] "
            else -> "- "
        }
        val changeAt = offset + indent.length
        fun mapPosition(pos: Int) = when {
            pos < changeAt -> pos
            pos <= changeAt + remove -> changeAt + prefix.length
            else -> pos + prefix.length - remove
        }
        if (start >= changeAt) mappedStart = mapPosition(start) + delta
        if (end >= changeAt) mappedEnd = mapPosition(end) + delta
        delta += prefix.length - remove
        offset += line.length + 1
        indent + prefix + line.drop(indent.length + remove)
    }.joinToString("\n")
    if (command != "toggleBlockquote") {
        mappedStart = (start + delta).coerceIn(0, text.length + delta)
        mappedEnd = mappedStart
    }
    return SelectionBlockChange(from, to, replacement, mappedStart, mappedEnd)
}
