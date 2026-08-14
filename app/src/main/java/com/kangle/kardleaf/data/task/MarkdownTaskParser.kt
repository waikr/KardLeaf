package com.kangle.kardleaf.data.task

data class MarkdownTaskSource(
    val notePath: String,
    val title: String,
    val content: String,
    val updatedAt: Long = 0L,
)

data class MarkdownTaskItem(
    val notePath: String,
    val noteTitle: String,
    val lineNumber: Int,
    val taskText: String,
    val done: Boolean,
    val rawLine: String,
    val indent: String,
    val listMarker: String,
    val statusMarker: String,
    val createdDate: String? = null,
    val startDate: String? = null,
    val scheduledDate: String? = null,
    val dueDate: String? = null,
    val doneDate: String? = null,
    val cancelledDate: String? = null,
    val priorityMarker: String? = null,
    val recurrenceRule: String? = null,
    val taskId: String? = null,
    val notes: String = "",
    val childLines: List<String> = emptyList(),
    val noteLineIndexes: Set<Int> = emptySet(),
    val indentLevel: Int = 0,
    val parentLineNumber: Int? = null,
)

sealed interface MarkdownTaskPatchResult {
    data class Success(
        val content: String,
        val lineNumber: Int,
    ) : MarkdownTaskPatchResult

    data object Conflict : MarkdownTaskPatchResult
}

private val markdownTaskPattern = Regex("""^(\s*)((?:[-*+]|\d+\.))(\s+)\[([^]])](\s+)(.+?)\s*$""")
private val markdownListItemPattern = Regex("""^(\s*)((?:[-*+]|\d+\.))(\s+)(.+?)\s*$""")
private val taskDatePattern = Regex("""(➕|🛫|⏳|📅|✅|❌)\s*(\d{4}-\d{2}-\d{2})""")
private val taskPriorityPattern = Regex("""(?:🔺|⏫|🔼|🔽|⏬)""")
private val taskRecurrencePattern = Regex("""🔁\s+(.+?)(?=\s+(?:#|➕|🛫|⏳|📅|✅|❌|🆔|⛔|🏁|🔺|⏫|🔼|🔽|⏬)|$)""")
private val taskIdPattern = Regex("""🆔\s+([A-Za-z0-9_-]+)""")
private val taskDependsOnPattern = Regex("""⛔\s+[A-Za-z0-9_-]+(?:,[A-Za-z0-9_-]+)*""")
private val taskOnCompletionPattern = Regex("""🏁\s+(?:keep|delete)""", RegexOption.IGNORE_CASE)
private const val MAX_MARKDOWN_TASK_CACHE_ENTRIES = 300

object MarkdownTaskParserCache {
    private data class Entry(
        val title: String,
        val updatedAt: Long,
        val contentLength: Int,
        val contentHash: Int,
        val items: List<MarkdownTaskItem>,
    )

    private val entries = LinkedHashMap<String, Entry>(64, 0.75f, true)

    @Synchronized
    fun parse(sources: List<MarkdownTaskSource>): List<MarkdownTaskItem> {
        val activePaths = sources.mapTo(mutableSetOf()) { it.notePath }
        entries.keys.retainAll(activePaths)

        val result = ArrayList<MarkdownTaskItem>()
        sources.forEach { source ->
            val cached = entries[source.notePath]
            if (cached != null &&
                cached.title == source.title &&
                cached.updatedAt == source.updatedAt &&
                cached.contentLength == source.content.length &&
                cached.contentHash == source.content.hashCode()
            ) {
                result += cached.items
                return@forEach
            }

            val parsed = parseMarkdownTaskSource(source)
            entries[source.notePath] =
                Entry(
                    title = source.title,
                    updatedAt = source.updatedAt,
                    contentLength = source.content.length,
                    contentHash = source.content.hashCode(),
                    items = parsed,
                )
            result += parsed
        }

        while (entries.size > MAX_MARKDOWN_TASK_CACHE_ENTRIES) {
            entries.remove(entries.keys.first())
        }
        return result
    }
}

fun parseMarkdownTasks(sources: List<MarkdownTaskSource>): List<MarkdownTaskItem> =
    sources.flatMap(::parseMarkdownTaskSource)

fun patchMarkdownTaskDone(
    content: String,
    item: MarkdownTaskItem,
    done: Boolean,
): MarkdownTaskPatchResult =
    patchMarkdownTaskLine(content, item) { line ->
        val match = markdownTaskPattern.matchEntire(line) ?: return@patchMarkdownTaskLine null
        buildString(line.length) {
            append(line, 0, match.groups[4]!!.range.first)
            append(if (done) 'x' else ' ')
            append(line, match.groups[4]!!.range.last + 1, line.length)
        }
    }

fun patchMarkdownTaskDueDate(
    content: String,
    item: MarkdownTaskItem,
    dueDate: String?,
): MarkdownTaskPatchResult =
    patchMarkdownTaskLine(content, item) { line ->
        val current = Regex("""\s*📅\s*\d{4}-\d{2}-\d{2}""")
        when {
            current.containsMatchIn(line) -> current.replaceFirst(line, dueDate?.let { " 📅 $it" }.orEmpty())
            dueDate == null -> line
            else -> {
                val trailingMetadata = Regex("""\s+(?=(?:✅|❌|🆔|⛔))""").find(line)
                val insertAt = trailingMetadata?.range?.first ?: line.length
                line.substring(0, insertAt).trimEnd() + " 📅 $dueDate" + line.substring(insertAt)
            }
        }
    }

fun patchMarkdownTaskFields(
    content: String,
    item: MarkdownTaskItem,
    taskText: String,
    taskId: String,
): MarkdownTaskPatchResult =
    patchMarkdownTaskLine(content, item) { line ->
        val match = markdownTaskPattern.matchEntire(line) ?: return@patchMarkdownTaskLine null
        val body = match.groupValues[6]
        val metadataStart = listOfNotNull(
            taskDatePattern.find(body)?.range?.first,
            taskPriorityPattern.find(body)?.range?.first,
            taskRecurrencePattern.find(body)?.range?.first,
            taskIdPattern.find(body)?.range?.first,
            taskDependsOnPattern.find(body)?.range?.first,
            taskOnCompletionPattern.find(body)?.range?.first,
        ).minOrNull() ?: body.length
        val suffix = body.substring(metadataStart).trimStart()
        val updatedSuffix = taskIdPattern.replace(suffix, "🆔 $taskId")
        val updatedBody = listOf(taskText.trim(), updatedSuffix).filter(String::isNotBlank).joinToString(" ")
        val bodyRange = match.groups[6]!!.range
        line.substring(0, bodyRange.first) + updatedBody + line.substring(bodyRange.last + 1)
    }

private fun parseMarkdownTaskSource(source: MarkdownTaskSource): List<MarkdownTaskItem> {
    if (!source.content.hasMarkdownTaskMarker()) return emptyList()
    val lines = source.content.split('\n').map { it.removeSuffix("\r") }
    val fencedLines = BooleanArray(lines.size)
    var fenceMarker: Char? = null
    var fenceLength = 0

    lines.forEachIndexed { index, line ->
        val trimmed = line.trimStart()
        val marker = trimmed.firstOrNull()
        val markerLength = if (marker == '`' || marker == '~') trimmed.takeWhile { it == marker }.length else 0
        if (fenceMarker == null && markerLength >= 3) {
            fencedLines[index] = true
            fenceMarker = marker
            fenceLength = markerLength
            return@forEachIndexed
        }
        if (fenceMarker != null) {
            fencedLines[index] = true
            if (marker == fenceMarker && markerLength >= fenceLength) {
                fenceMarker = null
                fenceLength = 0
            }
        }
    }
    val parsedItems = lines.mapIndexedNotNull taskLine@{ index, line ->
        if (fencedLines[index]) return@taskLine null
        val match = markdownTaskPattern.matchEntire(line) ?: return@taskLine null
        val body = match.groupValues[6]
        val dates = taskDatePattern.findAll(body).associate { it.groupValues[1] to it.groupValues[2] }
        val parentIndent = indentationWidth(match.groupValues[1])
        val childLines = ArrayList<String>()
        var childIndex = index + 1
        while (childIndex < lines.size) {
            val childLine = lines[childIndex]
            if (childLine.isNotBlank() && indentationWidth(childLine.takeWhile(Char::isWhitespace)) <= parentIndent) break
            childLines += childLine
            childIndex++
        }
        val childListItems =
            childLines.mapIndexedNotNull childItem@{ childLineIndex, childLine ->
                val absoluteIndex = index + childLineIndex + 1
                if (fencedLines[absoluteIndex]) return@childItem null
                val childMatch = markdownListItemPattern.matchEntire(childLine) ?: return@childItem null
                Triple(childLineIndex, childMatch, indentationWidth(childMatch.groupValues[1]))
            }
        val directChildIndent = childListItems.minOfOrNull { it.third }
        val noteItems =
            childListItems.filter { (_, childMatch, indent) ->
                indent == directChildIndent && markdownTaskPattern.matchEntire(childMatch.value) == null
            }
        MarkdownTaskItem(
            notePath = source.notePath,
            noteTitle = source.title,
            lineNumber = index + 1,
            taskText = taskDescription(body),
            done = match.groupValues[4].equals("x", ignoreCase = true),
            rawLine = line,
            indent = match.groupValues[1],
            listMarker = match.groupValues[2],
            statusMarker = match.groupValues[4],
            createdDate = dates["➕"],
            startDate = dates["🛫"],
            scheduledDate = dates["⏳"],
            dueDate = dates["📅"],
            doneDate = dates["✅"],
            cancelledDate = dates["❌"],
            priorityMarker = taskPriorityPattern.find(body)?.value,
            recurrenceRule = taskRecurrencePattern.find(body)?.groupValues?.get(1)?.trim(),
            taskId = taskIdPattern.find(body)?.groupValues?.get(1),
            notes = noteItems.joinToString("\n") { it.second.groupValues[4].trim() },
            childLines = childLines,
            noteLineIndexes = noteItems.mapTo(mutableSetOf()) { it.first },
        )
    }
    val depthByLine = mutableMapOf<Int, Int>()
    return parsedItems.map { item ->
        val itemIndent = indentationWidth(item.indent)
        val parent =
            parsedItems
                .asSequence()
                .filter { candidate ->
                    candidate.lineNumber < item.lineNumber &&
                        indentationWidth(candidate.indent) < itemIndent &&
                        item.lineNumber <= candidate.lineNumber + candidate.childLines.size
                }
                .maxByOrNull { candidate -> indentationWidth(candidate.indent) }
        val indentLevel = parent?.let { depthByLine[it.lineNumber]?.plus(1) } ?: 0
        depthByLine[item.lineNumber] = indentLevel
        item.copy(
            indentLevel = indentLevel,
            parentLineNumber = parent?.lineNumber,
        )
    }
}

private fun indentationWidth(indent: String): Int =
    indent.fold(0) { width, char -> if (char == '\t') width + 4 - (width % 4) else width + 1 }

private fun taskDescription(body: String): String =
    body
        .replace(taskRecurrencePattern, "")
        .replace(taskDatePattern, "")
        .replace(taskPriorityPattern, "")
        .replace(taskIdPattern, "")
        .replace(taskDependsOnPattern, "")
        .replace(taskOnCompletionPattern, "")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun patchMarkdownTaskLine(
    content: String,
    item: MarkdownTaskItem,
    transform: (String) -> String?,
): MarkdownTaskPatchResult {
    val newline = if (content.contains("\r\n")) "\r\n" else "\n"
    val lines = content.split(newline).toMutableList()
    val preferred = item.lineNumber - 1
    val target =
        when {
            lines.getOrNull(preferred) == item.rawLine -> preferred
            else -> {
                val nearby =
                    ((preferred - 3)..(preferred + 3))
                        .filter { it in lines.indices && lines[it] == item.rawLine }
                when {
                    nearby.size == 1 -> nearby.single()
                    nearby.size > 1 -> return MarkdownTaskPatchResult.Conflict
                    else -> {
                        val all = lines.indices.filter { lines[it] == item.rawLine }
                        if (all.size != 1) return MarkdownTaskPatchResult.Conflict
                        all.single()
                    }
                }
            }
        }
    lines[target] = transform(lines[target]) ?: return MarkdownTaskPatchResult.Conflict
    return MarkdownTaskPatchResult.Success(lines.joinToString(newline), target + 1)
}

private fun String.hasMarkdownTaskMarker(): Boolean =
    contains("[") && contains("]")
