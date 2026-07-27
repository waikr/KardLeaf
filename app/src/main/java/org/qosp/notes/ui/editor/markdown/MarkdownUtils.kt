package org.qosp.notes.ui.editor.markdown

import org.qosp.notes.ui.utils.views.ExtendedEditText
import org.qosp.notes.ui.utils.views.OperationType

enum class MarkdownSpan(val value: String) {
    BOLD("**"),
    ITALICS("_"),
    STRIKETHROUGH("~~"),
    CODE("`"),
    QUOTE(">"),
    HEADING("#"),
    HIGHLIGHT("=="),
}

fun ExtendedEditText.insertMarkdown(markdownSpan: MarkdownSpan) {
    editHistory(OperationType.TOOLBAR) {
        val start = selectionStart
        val end = selectionEnd

        if (start < 0) return@editHistory
        val s = markdownSpan.value

        when (markdownSpan) {
            MarkdownSpan.HEADING -> toggleHeadingCurrentLine(1)
            MarkdownSpan.QUOTE -> toggleBlockquoteCurrentLine()
            else -> toggleSelectionMarkers(s, s)
        }
    }
}

fun ExtendedEditText.toggleCheckmarkCurrentLine() {
    val line = text?.lines()?.get(currentLineIndex) ?: return
    val currentMark = checkmarkPrefix.find(line)?.groupValues?.get(2)
    setCheckmarkCurrentLine(currentMark != null && !currentMark.equals("x", ignoreCase = true))
}

fun ExtendedEditText.setCheckmarkCurrentLine(done: Boolean) {
    editHistory(OperationType.TOOLBAR) {
        var line = text?.lines()?.get(currentLineIndex) ?: return@editHistory
        val lineStart = currentLineStartPos
        val oldLength = line.length
        val mark = if (done) "x" else " "
        val match = checkmarkPrefix.find(line)
        line = match?.let {
            line.replaceRange(it.range, "${it.groupValues[1]}$mark${it.groupValues[3]}").trimEnd() + " "
        } ?: "- [$mark] $line"

        text?.replace(lineStart, lineStart + oldLength, line)
        setSelection(lineStart + line.length)
    }
}

private val checkmarkPrefix = Regex("^([-+*]\\s*\\[)([ xX])(\\]\\s+)")

fun ExtendedEditText.toggleBulletCurrentLine() =
    editHistory(OperationType.TOOLBAR) { toggleCurrentLineList(MarkdownListType.UNORDERED) }

fun ExtendedEditText.toggleOrderedCurrentLine() =
    editHistory(OperationType.TOOLBAR) { toggleCurrentLineList(MarkdownListType.ORDERED) }

fun ExtendedEditText.toggleChecklistCurrentLine() =
    editHistory(OperationType.TOOLBAR) { toggleCurrentLineList(MarkdownListType.CHECK) }

fun ExtendedEditText.indentCurrentLine() = editHistory(OperationType.TOOLBAR) { replaceCurrentLine("    $currentLine", 4) }

fun ExtendedEditText.outdentCurrentLine() {
    editHistory(OperationType.TOOLBAR) {
        val line = currentLine
        val prefixLength = when {
            line.startsWith('\t') -> 1
            line.startsWith("    ") -> 4
            else -> 0
        }
        if (prefixLength > 0) replaceCurrentLine(line.drop(prefixLength), -prefixLength)
    }
}

fun ExtendedEditText.insertCodeBlock() =
    editHistory(OperationType.TOOLBAR) { replaceSelection("```\n${selectedText.orEmpty()}\n```", 4) }

fun ExtendedEditText.insertDivider() = editHistory(OperationType.TOOLBAR) { replaceSelection("\n***\n", 5) }

fun ExtendedEditText.setHeadingLevel(level: Int) {
    editHistory(OperationType.TOOLBAR) { toggleHeadingCurrentLine(level) }
}

fun ExtendedEditText.insertUnderline() =
    editHistory(OperationType.TOOLBAR) { toggleSelectionMarkers("<u>", "</u>") }

fun ExtendedEditText.insertMath() {
    editHistory(OperationType.TOOLBAR) {
        val content = selectedText.orEmpty()
        replaceSelection(mathMarkdown(content), if ('\n' in content) 3 else 1)
    }
}

private val ExtendedEditText.currentLine: String
    get() = text?.lines()?.get(currentLineIndex).orEmpty()

private enum class MarkdownListType {
    UNORDERED,
    ORDERED,
    CHECK,
}

private data class MarkdownListPrefix(
    val type: MarkdownListType,
    val indent: String,
    val fullPrefix: String,
)

private val checklistLinePrefix = Regex("""^(\s*)[-+*]\s+\[[ xX]\]\s+""")
private val unorderedLinePrefix = Regex("""^(\s*)[-+*]\s+""")
private val orderedLinePrefix = Regex("""^(\s*)\d+\.\s+""")
private val blockquoteLinePrefix = Regex("""^(\s*)>\s?""")

private fun ExtendedEditText.toggleSelectionMarkers(prefix: String, suffix: String) {
    val start = selectionStart
    val end = selectionEnd
    val editable = text ?: return
    if (start < 0 || end < start) return

    val hasMarkers =
        start >= prefix.length &&
            end + suffix.length <= editable.length &&
            editable.substring(start - prefix.length, start) == prefix &&
            editable.substring(end, end + suffix.length) == suffix

    if (hasMarkers) {
        editable.delete(end, end + suffix.length)
        editable.delete(start - prefix.length, start)
        setSelection(start - prefix.length, end - prefix.length)
    } else {
        val selected = editable.substring(start, end)
        editable.replace(start, end, "$prefix$selected$suffix")
        setSelection(start + prefix.length, end + prefix.length)
    }
}

private fun ExtendedEditText.toggleHeadingCurrentLine(level: Int) {
    val line = currentLine
    val oldPrefix = Regex("^#{1,6}\\s*").find(line)?.value.orEmpty()
    val targetLevel = level.coerceIn(1, 6)
    val oldLevel = oldPrefix.takeWhile { it == '#' }.length
    val newPrefix = if (oldLevel == targetLevel) "" else "${"#".repeat(targetLevel)} "
    replaceCurrentLine(newPrefix + line.removePrefix(oldPrefix), newPrefix.length - oldPrefix.length)
}

private fun ExtendedEditText.toggleBlockquoteCurrentLine() {
    val line = currentLine
    val match = blockquoteLinePrefix.find(line)
    val replacement = if (match != null) {
        match.groupValues[1] + line.removePrefix(match.value)
    } else {
        val indent = line.takeWhile { it.isWhitespace() }
        "$indent> ${line.removePrefix(indent)}"
    }
    replaceCurrentLine(replacement, replacement.length - line.length)
}

private fun ExtendedEditText.toggleCurrentLineList(targetType: MarkdownListType) {
    val line = currentLine
    val detected = detectMarkdownListPrefix(line)
    val indent = detected?.indent ?: line.takeWhile { it.isWhitespace() }
    val targetPrefix = when (targetType) {
        MarkdownListType.UNORDERED -> "- "
        MarkdownListType.ORDERED -> "1. "
        MarkdownListType.CHECK -> "- [ ] "
    }
    val body = if (detected != null) line.removePrefix(detected.fullPrefix) else line.removePrefix(indent)
    val replacement = when {
        detected?.type == targetType -> indent + body
        else -> indent + targetPrefix + body
    }
    replaceCurrentLine(replacement, replacement.length - line.length)
}

private fun detectMarkdownListPrefix(line: String): MarkdownListPrefix? {
    checklistLinePrefix.find(line)?.let {
        return MarkdownListPrefix(MarkdownListType.CHECK, it.groupValues[1], it.value)
    }
    unorderedLinePrefix.find(line)?.let {
        return MarkdownListPrefix(MarkdownListType.UNORDERED, it.groupValues[1], it.value)
    }
    orderedLinePrefix.find(line)?.let {
        return MarkdownListPrefix(MarkdownListType.ORDERED, it.groupValues[1], it.value)
    }
    return null
}

private fun ExtendedEditText.replaceCurrentLine(line: String, selectionOffset: Int) {
    val start = currentLineStartPos
    val oldLine = currentLine
    val oldSelection = selectionStart
    text?.replace(start, start + oldLine.length, line)
    setSelection((oldSelection + selectionOffset).coerceIn(start, start + line.length))
}

private fun ExtendedEditText.replaceSelection(replacement: String, selectionOffset: Int) {
    val start = selectionStart
    val end = selectionEnd
    if (start < 0 || end < start) return
    text?.replace(start, end, replacement)
    setSelection((start + selectionOffset).coerceIn(start, start + replacement.length))
}

fun hyperlinkMarkdown(url: String, content: String): String {
    return "[$content]($url)"
}

fun imageMarkdown(url: String, description: String): String {
    return "![alt text]($url \"$description\")"
}

fun mathMarkdown(content: String): String =
    if ('\n' in content) "\$\$\n$content\n\$\$" else "\$$content\$"

fun tableMarkdown(rows: Int, columns: Int): String {
    var markdown = ""

    for (r in 0..rows) {
        val space = if (r != 1) "    " else "----"
        for (c in 0 until columns) {
            markdown += "|$space"
        }
        markdown += "|\n"
    }
    return markdown
}
