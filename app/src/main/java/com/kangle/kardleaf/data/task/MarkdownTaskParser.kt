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
)

private val markdownTaskPattern = Regex("""^\s*(?:[-*+]|\d+\.)\s+\[( |x|X)]\s+(.+?)\s*$""")
private const val MAX_MARKDOWN_TASK_CACHE_ENTRIES = 300

object MarkdownTaskParserCache {
    private data class Entry(
        val title: String,
        val updatedAt: Long,
        val contentLength: Int,
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
                cached.contentLength == source.content.length
            ) {
                result += cached.items
                return@forEach
            }

            val parsed = parseMarkdownTaskSource(source)
            entries[source.notePath] = Entry(
                title = source.title,
                updatedAt = source.updatedAt,
                contentLength = source.content.length,
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

private fun parseMarkdownTaskSource(source: MarkdownTaskSource): List<MarkdownTaskItem> {
    if (!source.content.hasMarkdownTaskMarker()) return emptyList()
    return source.content
        .lineSequence()
        .mapIndexedNotNull { index, line ->
            val match = markdownTaskPattern.matchEntire(line) ?: return@mapIndexedNotNull null
            MarkdownTaskItem(
                notePath = source.notePath,
                noteTitle = source.title,
                lineNumber = index + 1,
                taskText = match.groupValues[2],
                done = match.groupValues[1].equals("x", ignoreCase = true),
            )
        }
        .toList()
}

private fun String.hasMarkdownTaskMarker(): Boolean =
    contains("[ ]") || contains("[x]", ignoreCase = true)
