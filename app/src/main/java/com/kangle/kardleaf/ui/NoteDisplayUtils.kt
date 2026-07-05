package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

private val copyTitleSuffixRegex = Regex("""~副本(?:\d+)?(?:~\d+)*""")
private val wikiImageReferenceRegex = NoteFormatUtils.obsidianImageReferenceRegex
private val anyMarkdownImageReferenceRegex = NoteFormatUtils.markdownImageReferenceRegex
private val localMarkdownImageReferenceRegex = NoteFormatUtils.localMarkdownImageReferenceRegex

internal fun shouldHideDateFilenameTitle(
    title: String,
    dateFormat: String,
    hiddenFilenamePatterns: List<String>,
): Boolean {
    val patterns = hiddenFilenamePatterns.ifEmpty {
        listOf(
            PrefsManager.DEFAULT_HIDDEN_DATE_FILENAME_PATTERN,
            PrefsManager.DEFAULT_HIDDEN_COPY_FILENAME_PATTERN,
        )
    }
    return isPureDateTitle(title, dateFormat) || patterns.any { pattern ->
        isHiddenFilenamePatternMatch(title, pattern)
    }
}

private fun isHiddenFilenamePatternMatch(
    title: String,
    pattern: String,
): Boolean {
    val trimmedTitle = title.trim()
    val trimmedPattern = pattern.trim()
    if (trimmedTitle.isBlank() || trimmedPattern.isBlank()) return false
    if (trimmedTitle == trimmedPattern) return true
    if (isPureDateTitle(trimmedTitle, trimmedPattern)) return true

    val copyMarkerIndex = trimmedPattern.indexOf("~副本")
    if (copyMarkerIndex <= 0) return false

    val datePattern = trimmedPattern.substring(0, copyMarkerIndex)
    return runCatching {
        val formatter = SimpleDateFormat(datePattern, Locale.getDefault()).apply { isLenient = false }
        val position = ParsePosition(0)
        val parsedDate = formatter.parse(trimmedTitle, position)
        if (parsedDate == null || position.index <= 0) {
            false
        } else {
            val suffix = trimmedTitle.substring(position.index)
            val expectedSuffix = trimmedPattern.substring(copyMarkerIndex)
            if (expectedSuffix.endsWith("*")) {
                suffix.startsWith(expectedSuffix.removeSuffix("*"))
            } else {
                suffix == "~副本" || suffix.matches(copyTitleSuffixRegex)
            }
        }
    }.getOrDefault(false)
}

private fun isPureDateTitle(
    title: String,
    dateFormat: String,
): Boolean {
    val trimmed = title.trim()
    if (trimmed.isBlank() || dateFormat.isBlank()) return false
    return runCatching {
        val formatter = SimpleDateFormat(dateFormat, Locale.getDefault()).apply { isLenient = false }
        val position = ParsePosition(0)
        formatter.parse(trimmed, position) != null && position.index == trimmed.length
    }.getOrDefault(false)
}

internal fun containsMarkdownImageReferences(markdown: String): Boolean {
    if (markdown.isBlank()) return false
    return wikiImageReferenceRegex.containsMatchIn(markdown) ||
        anyMarkdownImageReferenceRegex.containsMatchIn(markdown)
}

internal fun extractMarkdownImageReferences(markdown: String): List<String?> {
    if (markdown.isBlank()) return emptyList()
    val matches = mutableListOf<Pair<Int, String?>>()
    wikiImageReferenceRegex
        .findAll(markdown)
        .forEach { match ->
            val reference = match.groupValues[1].trim()
            matches.add(match.range.first to reference.takeIf { it.isNotBlank() })
        }
    anyMarkdownImageReferenceRegex
        .findAll(markdown)
        .forEach { match ->
            val reference = match.groupValues[1].trim().trim('"', '\'')
            val localReference = reference.takeUnless { value ->
                value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true) ||
                    value.startsWith("data:", ignoreCase = true) ||
                    value.startsWith("file:", ignoreCase = true)
            }
            matches.add(match.range.first to localReference?.takeIf { it.isNotBlank() })
        }
    return matches.sortedBy { it.first }.map { it.second }
}

internal fun extractLocalMarkdownImageReferences(markdown: String): List<String> {
    if (markdown.isBlank()) return emptyList()
    val matches = mutableListOf<Pair<Int, String>>()
    wikiImageReferenceRegex
        .findAll(markdown)
        .forEach { match ->
            val reference = match.groupValues[1].trim()
            if (reference.isNotBlank()) {
                matches.add(match.range.first to reference)
            }
        }
    localMarkdownImageReferenceRegex
        .findAll(markdown)
        .forEach { match ->
            val reference = match.groupValues[1].trim().trim('"', '\'')
            if (reference.isNotBlank()) {
                matches.add(match.range.first to reference)
            }
        }
    return matches.sortedBy { it.first }.map { it.second }
}
