package com.kangle.kardleaf.data.utils

import com.kangle.kardleaf.data.model.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object NoteFormatUtils {
    private const val DEFAULT_PREVIEW_MAX_CHARS = 200
    private const val DEFAULT_PREVIEW_MAX_LINES = 10
    private val invalidFileNameChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    const val KARDLEAF_ID_KEY = "kardleaf_id"
    const val TAGS_KEY = "tags"
    private val REMOVED_LEGACY_FRONT_MATTER_KEYS = setOf("color", "reminder")

    internal val obsidianImageReferenceRegex = Regex("""!\[\[([^|\]]+)(?:\|[^\]]*)?]]""")
    internal val markdownImageReferenceRegex = Regex("""!\[[^]]*]\(([^)]+)\)""")
    internal val localMarkdownImageReferenceRegex =
        Regex("""!\[[^]]*]\((?!https?://|data:|file:)([^)]+)\)""", RegexOption.IGNORE_CASE)
    internal val localMarkdownImageReferenceWithAltRegex =
        Regex("""!\[([^]]*)]\((?!https?://|data:|file:)([^)]+)\)""", RegexOption.IGNORE_CASE)
    private val headingPrefixRegex = Regex("""^#{1,6}\s+""")
    private val taskPrefixRegex = Regex("""^\s*[-*+]\s+\[[ xX]]\s+""")
    private val bulletPrefixRegex = Regex("""^\s*[-*+]\s+""")
    private val orderedListPrefixRegex = Regex("""^\s*\d+\.\s+""")
    private val markdownTokenRegex = Regex("""[*_`~>#]""")

    data class FrontMatterData(
        val reminder: Long?,
        val cleanContent: String,
        val properties: List<FrontMatterProperty> = emptyList(),
    )

    data class FrontMatterProperty(
        val key: String,
        val values: List<String>,
    )

    fun buildPlainTextPreview(
        content: String,
        maxChars: Int = DEFAULT_PREVIEW_MAX_CHARS,
        maxLines: Int = DEFAULT_PREVIEW_MAX_LINES,
        hideImagePlaceholders: Boolean = false,
    ): String =
        content
            .lineSequence()
            .map { line -> cleanPlainTextPreviewLine(line, hideImagePlaceholders) }
            .filter { it.isNotBlank() }
            .take(maxLines)
            .joinToString("\n")
            .take(maxChars)

    fun sanitizeMarkdownFileBaseName(title: String): String =
        title
            .trim()
            .filterNot { it in invalidFileNameChars || it.code < 32 }
            .trim()
            .ifBlank { "Untitled" }

    fun rewriteRelativeImageRefsForMove(markdown: String, fromFolder: String, toFolder: String): String {
        if (fromFolder == toFolder) return markdown
        val withObsidian = obsidianImageReferenceRegex.replace(markdown) { match ->
            val ref = match.groupValues[1].trim()
            if (!isLocalRelativeImageReference(ref)) {
                match.value
            } else {
                val realPath = normalizePath(joinPath(fromFolder, ref))
                "![[${relativePath(toFolder, realPath)}]]"
            }
        }
        return localMarkdownImageReferenceWithAltRegex.replace(withObsidian) { match ->
            val alt = match.groupValues[1]
            val ref = match.groupValues[2].trim().trim('"', '\'')
            if (!isLocalRelativeImageReference(ref)) {
                match.value
            } else {
                val realPath = normalizePath(joinPath(fromFolder, ref))
                "![${alt}](${relativePath(toFolder, realPath)})"
            }
        }
    }

    fun isLocalRelativeImageReference(reference: String): Boolean {
        val value = reference.trim().trim('"', '\'')
        if (value.isBlank()) return false
        val lower = value.lowercase(Locale.ROOT)
        if (lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("data:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:")
        ) {
            return false
        }
        return !value.startsWith("/") &&
            !value.startsWith("\\") &&
            !Regex("""^[A-Za-z]:[\\/].*""").matches(value)
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/").filter { it.isNotBlank() }
        val stack = mutableListOf<String>()
        for (part in parts) {
            when {
                part == "." -> {}
                part == ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else stack.add("..")
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun relativePath(fromFolder: String, toPath: String): String {
        val fromParts = normalizePath(fromFolder).split("/").filter { it.isNotBlank() }
        val toParts = normalizePath(toPath).split("/").filter { it.isNotBlank() }
        var common = 0
        while (common < fromParts.size && common < toParts.size && fromParts[common] == toParts[common]) {
            common++
        }
        val ups = List(fromParts.size - common) { ".." }
        return (ups + toParts.drop(common)).joinToString("/").ifBlank { toParts.lastOrNull().orEmpty() }
    }

    private fun joinPath(folder: String, fileName: String): String =
        folder.trim('/').takeIf { it.isNotBlank() }?.let { "$it/${fileName.trim('/')}" } ?: fileName.trim('/')

    private fun cleanPlainTextPreviewLine(
        line: String,
        hideImagePlaceholders: Boolean,
    ): String {
        val withoutImages =
            if (hideImagePlaceholders) {
                line
                    .replace(obsidianImageReferenceRegex, "")
                    .replace(markdownImageReferenceRegex, "")
            } else {
                line
                    .replace(obsidianImageReferenceRegex, "[图片: $1]")
                    .replace(markdownImageReferenceRegex, "[图片]")
            }
        return withoutImages
            .replace(headingPrefixRegex, "")
            .replace(taskPrefixRegex, "")
            .replace(bulletPrefixRegex, "")
            .replace(orderedListPrefixRegex, "")
            .replace(markdownTokenRegex, "")
            .trim()
    }

    /**
     * Universal Parser: Robustly handles quoted and unquoted values.
     */
    fun parseFrontMatter(rawContent: String): FrontMatterData {
        val firstLineEnd = rawContent.indexOf('\n')
        if (firstLineEnd <= 0 || rawContent.substring(0, firstLineEnd).trim() != "---") {
            return FrontMatterData(null, rawContent)
        }

        val propertyLines = mutableListOf<String>()
        var lineStart = firstLineEnd + 1
        var closingLineEnd = -1
        while (lineStart <= rawContent.length) {
            val lineEnd = rawContent.indexOf('\n', lineStart).let { if (it == -1) rawContent.length else it }
            val line = rawContent.substring(lineStart, lineEnd)
            if (line.trim() == "---") {
                closingLineEnd = lineEnd
                break
            }
            propertyLines += line
            if (lineEnd >= rawContent.length) break
            lineStart = lineEnd + 1
        }
        if (closingLineEnd == -1) {
            return FrontMatterData(null, rawContent)
        }

        var contentStart = if (closingLineEnd < rawContent.length) closingLineEnd + 1 else closingLineEnd
        while (contentStart < rawContent.length) {
            val lineEnd = rawContent.indexOf('\n', contentStart).let { if (it == -1) rawContent.length else it }
            if (!rawContent.substring(contentStart, lineEnd).isBlank()) break
            contentStart = if (lineEnd < rawContent.length) lineEnd + 1 else rawContent.length
        }

        val cleanContent = if (contentStart < rawContent.length) rawContent.substring(contentStart) else ""
        // 提醒功能已移除：只剥离 front matter，不再读取 reminder。
        val properties = parseFrontMatterProperties(propertyLines)

        return FrontMatterData(null, cleanContent, properties)
    }

    fun extractFrontMatterProperties(rawContent: String): List<FrontMatterProperty> =
        parseFrontMatter(rawContent).properties

    fun extractKardLeafId(rawContent: String): String? =
        extractKardLeafId(parseFrontMatter(rawContent))

    fun extractKardLeafId(frontMatter: FrontMatterData): String? =
        frontMatter.properties
            .firstOrNull { it.key.equals(KARDLEAF_ID_KEY, ignoreCase = true) }
            ?.values
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun extractTags(rawContent: String): List<String> =
        extractTags(parseFrontMatter(rawContent))

    fun extractTags(frontMatter: FrontMatterData): List<String> =
        frontMatter.properties
            .firstOrNull { it.key.equals(TAGS_KEY, ignoreCase = true) }
            ?.values
            ?.let(::normalizeTags)
            .orEmpty()

    fun normalizeTags(tags: Collection<String>): List<String> =
        tags
            .flatMap { value ->
                value.split(',', '，')
            }
            .map { tag ->
                tag.trim()
                    .removePrefix("#")
                    .trim()
                    .replace(Regex("\\s+"), " ")
            }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }

    fun tagsToStorage(tags: Collection<String>): String {
        val normalizedTags = normalizeTags(tags)
        return if (normalizedTags.isEmpty()) {
            ""
        } else {
            normalizedTags.joinToString(separator = "\n", prefix = "\n", postfix = "\n")
        }
    }

    fun tagsFromStorage(raw: String?): List<String> =
        raw.orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
            .let(::normalizeTags)

    private fun parseFrontMatterProperties(lines: List<String>): List<FrontMatterProperty> {
        val properties = mutableListOf<FrontMatterProperty>()
        var currentKey: String? = null
        var currentValues = mutableListOf<String>()

        fun flushCurrent() {
            val key = currentKey?.trim().orEmpty()
            val values = currentValues.map { it.trim() }.filter { it.isNotBlank() }
            if (key.isNotBlank() && values.isNotEmpty()) {
                properties += FrontMatterProperty(key = key, values = values)
            }
            currentKey = null
            currentValues = mutableListOf()
        }

        lines.forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach

            if (trimmed.startsWith("- ") && currentKey != null) {
                currentValues += cleanYamlValue(trimmed.removePrefix("- "))
                return@forEach
            }

            val separatorIndex = rawLine.indexOf(':')
            if (!rawLine.startsWith(" ") && separatorIndex > 0) {
                flushCurrent()
                currentKey = rawLine.substring(0, separatorIndex).trim()
                val rawValue = rawLine.substring(separatorIndex + 1).trim()
                if (rawValue.isNotBlank()) {
                    currentValues += parseYamlInlineValue(rawValue)
                }
            } else if (currentKey != null && trimmed.isNotBlank()) {
                currentValues += cleanYamlValue(trimmed)
            }
        }
        flushCurrent()
        return properties
    }

    private fun parseYamlInlineValue(rawValue: String): List<String> {
        val trimmed = rawValue.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed
                .removePrefix("[")
                .removeSuffix("]")
                .split(',')
                .map { cleanYamlValue(it) }
                .filter { it.isNotBlank() }
        }
        return listOf(cleanYamlValue(trimmed))
    }

    private fun cleanYamlValue(value: String): String =
        value
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()

    fun constructFileContent(
        note: Note,
        existingRawContent: String? = null,
        replaceTags: Boolean = false,
    ): String {
        return buildString {
            val frontMatterLines = buildKardLeafFrontMatter(note, existingRawContent, replaceTags)
            if (frontMatterLines.isNotEmpty()) {
                append("---\n")
                frontMatterLines.forEach { line ->
                    append(line)
                    append('\n')
                }
                append("---\n\n")
            }
            append(note.content)
        }
    }

    private fun buildKardLeafFrontMatter(
        note: Note,
        existingRawContent: String?,
        replaceTags: Boolean,
    ): List<String> {
        val frontMatterLines = existingRawContent?.let(::preserveUnknownFrontMatter).orEmpty().toMutableList()
        val existingId = findTopLevelValue(frontMatterLines, KARDLEAF_ID_KEY)
        val nowText = formatYamlDateTime(Date())

        if (existingId.isNullOrBlank()) {
            frontMatterLines.add(0, "$KARDLEAF_ID_KEY: ${generateKardLeafId()}")
        }
        if (findTopLevelValue(frontMatterLines, "created").isNullOrBlank()) {
            val createdText = formatYamlDateTime(note.createdAt)
            val insertIndex = (frontMatterLines.indexOfFirst { topLevelKeyOf(it)?.equals(KARDLEAF_ID_KEY, ignoreCase = true) == true } + 1)
                .coerceIn(0, frontMatterLines.size)
            frontMatterLines.add(insertIndex, "created: $createdText")
        }
        upsertTopLevelValue(frontMatterLines, "updated", nowText)
        val tagsForFile =
            if (replaceTags || note.tags.isNotEmpty()) {
                normalizeTags(note.tags)
            } else {
                existingRawContent?.let { extractTags(it) }.orEmpty()
            }
        replaceYamlList(frontMatterLines, TAGS_KEY, tagsForFile)

        return frontMatterLines
    }

    private fun replaceYamlList(
        lines: MutableList<String>,
        key: String,
        values: List<String>,
    ) {
        var index = 0
        while (index < lines.size) {
            val lineKey = topLevelKeyOf(lines[index])
            if (lineKey != null && lineKey.equals(key, ignoreCase = true)) {
                var end = index + 1
                while (end < lines.size && topLevelKeyOf(lines[end]) == null) {
                    end++
                }
                repeat(end - index) { lines.removeAt(index) }
                break
            }
            index++
        }
        if (values.isEmpty()) return

        val insertIndex = (lines.indexOfFirst { topLevelKeyOf(it)?.equals("updated", ignoreCase = true) == true } + 1)
            .takeIf { it > 0 }
            ?: lines.size
        lines.add(insertIndex, "$key:")
        values.forEachIndexed { offset, value ->
            lines.add(insertIndex + 1 + offset, "  - ${escapeYamlListValue(value)}")
        }
    }

    private fun escapeYamlListValue(value: String): String {
        val cleaned = value.trim().replace("\n", " ")
        return if (cleaned.any { it == ':' || it == '#' || it == '[' || it == ']' || it == ',' }) {
            "\"${cleaned.replace("\"", "\\\"")}\""
        } else {
            cleaned
        }
    }

    private fun preserveUnknownFrontMatter(rawContent: String): List<String> {
        val lines = rawContent.lines()
        if (lines.size < 3 || lines.first().trim() != "---") return emptyList()
        val closingIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }.takeIf { it >= 0 }?.plus(1) ?: return emptyList()
        val result = mutableListOf<String>()
        var skipLegacyBlock = false
        lines.subList(1, closingIndex).forEach { line ->
            val key = topLevelKeyOf(line)
            if (key != null) {
                skipLegacyBlock = key in REMOVED_LEGACY_FRONT_MATTER_KEYS
            }
            if (!skipLegacyBlock) {
                result += line
            }
        }
        return result
    }

    private fun findTopLevelValue(lines: List<String>, key: String): String? =
        lines.firstOrNull { line ->
            !line.startsWith(" ") && line.substringBefore(":", missingDelimiterValue = "").trim().equals(key, ignoreCase = true)
        }?.substringAfter(":", missingDelimiterValue = "")?.trim()?.takeIf { it.isNotBlank() }

    private fun upsertTopLevelValue(
        lines: MutableList<String>,
        key: String,
        value: String,
    ) {
        val index = lines.indexOfFirst { line ->
            !line.startsWith(" ") && line.substringBefore(":", missingDelimiterValue = "").trim().equals(key, ignoreCase = true)
        }
        if (index >= 0) {
            lines[index] = "$key: $value"
        } else {
            lines += "$key: $value"
        }
    }

    private fun topLevelKeyOf(line: String): String? {
        if (line.startsWith(" ") || line.startsWith("\t")) return null
        val key = line.substringBefore(":", missingDelimiterValue = "").trim()
        return key.takeIf { it.isNotBlank() }
    }

    private fun generateKardLeafId(): String =
        "kl_${System.currentTimeMillis().toString(16)}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

    private fun formatYamlDateTime(date: Date): String {
        val raw = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(date)
        return raw.replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
    }
}
