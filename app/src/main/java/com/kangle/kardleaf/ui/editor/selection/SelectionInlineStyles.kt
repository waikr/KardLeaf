package com.kangle.kardleaf.ui.editor.selection

/** Same rules and fixture corpus as marktext/inlineStyles.ts. Only toolbar commands write text. */
internal object SelectionInlineStyles {
    val palette = listOf("#e53935", "#fb8c00", "#fdd835", "#43a047", "#1e88e5", "#8e24aa", "#212121", "#ffffff")
    val names = listOf("红色", "橙色", "黄色", "绿色", "蓝色", "紫色", "黑色", "白色")
    val fontSizePalette = listOf("0.8em", "1.5em")
    val fontSizeNames = listOf("小字", "大字")
    fun safeColor(value: String): String? {
        val color = value.trim().lowercase().takeIf { Regex("^#(?:[0-9a-f]{3}|[0-9a-f]{6})$").matches(it) } ?: return null
        return if (color.length == 4) "#" + color.drop(1).map { "$it$it" }.joinToString("") else color
    }
    fun safeFontSize(value: String): String? = value.trim().lowercase().takeIf { it in fontSizePalette }
    fun parseTag(tag: String): Map<String, String>? {
        val match = Regex("""^<span\s+style\s*=\s*(["'])([^"'<>]*)\1\s*>$""", RegexOption.IGNORE_CASE).matchEntire(tag) ?: return null
        val result = mutableMapOf<String, String>()
        for (part in match.groupValues[2].split(';')) {
            if (part.isBlank()) continue
            val pieces = part.split(':')
            if (pieces.size != 2) return null
            val key = pieces[0].trim().lowercase()
            val normalized = when (key) {
                "color", "background-color" -> safeColor(pieces[1])
                "font-size" -> safeFontSize(pieces[1])
                else -> null
            } ?: return null
            result[when (key) {
                "background-color" -> "backgroundColor"
                "font-size" -> "fontSize"
                else -> "color"
            }] = normalized
        }
        return result
    }
    private fun style(colors: Map<String, String>) = listOfNotNull(
        colors["color"]?.let { "color:$it" },
        colors["backgroundColor"]?.let { "background-color:$it" },
        colors["fontSize"]?.let { "font-size:$it" },
    ).joinToString(";")
    data class Interval(val from: Int, val to: Int)
    data class Span(val from: Int, val openTo: Int, val closeFrom: Int, val to: Int, val colors: Map<String, String>)
    data class Scan(val spans: List<Span>, val blocked: List<Interval>)
    private fun intersects(a: Interval, b: Interval) = a.from < b.to && b.from < a.to
    fun scan(text: String): Scan {
        val blocked = mutableListOf<Interval>()
        var offset = 0
        var fence = ""
        var math = false
        var yaml = false
        for (line in text.split('\n')) {
            val content = line.replace(Regex("""^\s*(?:>\s*)+"""), "").trim()
            val marker = Regex("""^(?:[-+*]\s+|\d+[.)]\s+)?(`{3,}|~{3,})""").find(content)?.groupValues?.get(1)
            val wasBlocked = fence.isNotEmpty() || math || yaml
            if (offset == 0 && content == "---") yaml = true
            else if (yaml && content in listOf("---", "...")) yaml = false
            if (!yaml && marker != null) {
                if (fence.isEmpty()) fence = marker
                else if (marker[0] == fence[0] && marker.length >= fence.length && content == marker) fence = ""
            }
            if (fence.isEmpty() && content.startsWith("\$\$") && Regex("""\$\$""").findAll(content).count() == 1) math = !math
            // ponytail: conservative whole-line exclusion; use parser ranges if finer code/math selection is needed.
            if (wasBlocked || fence.isNotEmpty() || math || yaml || marker != null || Regex("""[`$|]|\\(?:\(|\)|\[|\])|^( {4}|\t)|!\[|\]\(""").containsMatchIn(line)) {
                blocked.add(Interval(offset, offset + line.length + 1))
            }
            val prefix = Regex("""^[ \t]{0,3}(?:#{1,6}(?:[ \t]+|$)|(?:[-+*]|\d+[.)])[ \t]+(?:\[[ xX]\][ \t]+)?|(?:>[ \t]*)+)""").find(line)?.value
            if (prefix != null) blocked.add(Interval(offset, offset + prefix.length))
            if (Regex("""^(?:[=-]{2,}|(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$""").matches(content)) blocked.add(Interval(offset, offset + line.length + 1))
            offset += line.length + 1
        }
        for (pattern in listOf("""(`+)[\s\S]*?\1""", """\\\[[\s\S]*?(?:\\\]|$)""", """\\\([\s\S]*?(?:\\\)|$)""")) {
            Regex(pattern).findAll(text).forEach { blocked.add(Interval(it.range.first, it.range.last + 1)) }
        }
        data class Open(val from: Int, val to: Int, val name: String, val colors: Map<String, String>?)
        val stack = mutableListOf<Open>()
        val spans = mutableListOf<Span>()
        for (match in Regex("<[^>\n]*>").findAll(text)) {
            val from = match.range.first
            val to = match.range.last + 1
            if (blocked.any { intersects(it, Interval(from, to)) }) continue
            val escaped = Regex("""(?:^|[^\\])(?:\\\\)*\\$""").containsMatchIn(text.substring(0, from))
            val openName = Regex("""^<([a-z][\w:-]*)\b""", RegexOption.IGNORE_CASE).find(match.value)?.groupValues?.get(1)?.lowercase()
            val closeName = Regex("""^</([a-z][\w:-]*)\s*>$""", RegexOption.IGNORE_CASE).find(match.value)?.groupValues?.get(1)?.lowercase()
            if (!escaped && openName != null && !Regex("/\\s*>$").containsMatchIn(match.value) && openName !in listOf("br", "img", "hr", "input", "wbr", "source", "meta", "link", "area", "base", "embed", "param", "track", "col")) {
                stack.add(Open(from, to, openName, parseTag(match.value)))
            } else if (!escaped && closeName != null && stack.lastOrNull()?.name == closeName) {
                val open = stack.removeAt(stack.lastIndex)
                if (open.colors != null) spans.add(Span(open.from, open.to, from, to, open.colors))
                else blocked.add(Interval(open.from, to))
            } else blocked.add(Interval(from, to))
        }
        stack.forEach { blocked.add(Interval(it.from, text.length)) }
        return Scan(spans.filter { span -> blocked.none { intersects(Interval(span.from, span.to), it) } }, blocked)
    }
    fun change(text: String, anchor: Int, head: Int, property: String, value: String?): SelectionBlockChange? {
        if (property !in listOf("color", "backgroundColor", "fontSize")) return null
        val normalized = value?.let { input ->
            (if (property == "fontSize") safeFontSize(input) else safeColor(input)) ?: return null
        }
        val from = minOf(anchor, head)
        val to = maxOf(anchor, head)
        if (from < 0 || to > text.length || from == to) return null
        val scan = scan(text)
        var window = Interval(from, to)
        for (span in scan.spans.sortedBy { it.from }) {
            if (intersects(Interval(span.from, span.to), window)) window = Interval(minOf(window.from, span.from), maxOf(window.to, span.to))
        }
        if (scan.blocked.any { intersects(it, window) }) return null
        val spans = scan.spans.filter { intersects(Interval(it.from, it.to), window) }
        val tags = spans.flatMap { listOf(Interval(it.from, it.openTo), Interval(it.closeFrom, it.to)) }
        if (tags.any { (from > it.from && from < it.to) || (to > it.from && to < it.to) }) return null
        val points = (listOf(window.from, window.to, from, to) + tags.flatMap { listOf(it.from, it.to) }).distinct().sorted()
        data class Run(val text: String, val colors: Map<String, String>, val selected: Boolean)
        val runs = mutableListOf<Run>()
        for ((start, end) in points.zipWithNext()) {
            if (tags.any { start >= it.from && end <= it.to }) continue
            val colors = mutableMapOf<String, String>()
            spans.filter { start >= it.openTo && end <= it.closeFrom }.sortedBy { it.from }.forEach { colors.putAll(it.colors) }
            val selected = start >= from && end <= to
            if (selected) { if (normalized == null) colors.remove(property) else colors[property] = normalized }
            for (part in Regex("[^\r\n]+|\r?\n|\r").findAll(text.substring(start, end))) {
                runs.add(Run(part.value, if ('\n' in part.value) emptyMap() else colors.toMap(), selected))
            }
        }
        if (runs.none { it.selected && it.text.isNotBlank() }) return null
        val insert = StringBuilder()
        var active = ""
        var start = -1
        var end = -1
        for (run in runs) {
            val style = style(run.colors)
            if (style != active) {
                if (active.isNotEmpty()) insert.append("</span>")
                if (style.isNotEmpty()) insert.append("<span style=\"$style\">")
                active = style
            }
            if (run.selected && start < 0) start = insert.length
            insert.append(run.text)
            if (run.selected) end = insert.length
        }
        if (active.isNotEmpty()) insert.append("</span>")
        start += window.from
        end += window.from
        return SelectionBlockChange(window.from, window.to, insert.toString(), if (anchor <= head) start else end, if (anchor <= head) end else start)
    }
}
