package com.kangle.kardleaf.ui

enum class ImageClickSource {
    NativeEditor,
    CodeMirror,
    MarkdownPreview,
}

data class KardLeafImageClickTarget(
    val reference: String,
    val markdownStart: Int?,
    val markdownEndExclusive: Int?,
    val occurrenceIndex: Int,
    val source: ImageClickSource,
)

internal data class MarkdownImageReferenceMatch(
    val target: KardLeafImageClickTarget,
    val referenceStart: Int,
    val referenceEndExclusive: Int,
)

internal data class MarkdownImageReferenceReplacement(
    val content: String,
    val replaceStart: Int,
    val replaceEndExclusive: Int,
)

private val obsidianImageMatchRegex = Regex("""!\[\[([^|\]\n]+)(?:\|[^\]\n]*)?]]""")
private val markdownImageMatchRegex = Regex("""!\[([^\]\n]*)]\(\s*(?:<([^>\n]+)>|([^\s)\n]+))[^)\n]*\)""")

internal fun extractMarkdownImageClickTargets(
    markdown: String,
    source: ImageClickSource,
): List<KardLeafImageClickTarget> = extractMarkdownImageMatches(markdown, source).map { it.target }

internal fun replaceClickedMarkdownImageReference(
    markdown: String,
    target: KardLeafImageClickTarget,
    newReference: String,
): MarkdownImageReferenceReplacement? {
    if (newReference.isBlank()) return null
    val matches = extractMarkdownImageMatches(markdown, target.source)
    val rangedMatch =
        if (target.markdownStart != null && target.markdownEndExclusive != null) {
            matches.firstOrNull { match ->
                match.target.markdownStart == target.markdownStart &&
                    match.target.markdownEndExclusive == target.markdownEndExclusive &&
                    match.target.reference == target.reference
            }
        } else {
            null
        }
    val resolved =
        rangedMatch ?: matches.firstOrNull { match ->
            match.target.reference == target.reference && match.target.occurrenceIndex == target.occurrenceIndex
        } ?: return null
    return MarkdownImageReferenceReplacement(
        content = markdown.replaceRange(resolved.referenceStart, resolved.referenceEndExclusive, newReference),
        replaceStart = resolved.referenceStart,
        replaceEndExclusive = resolved.referenceEndExclusive,
    )
}

internal fun occurrenceIndexForImageReference(
    markdown: String,
    reference: String,
    markdownStart: Int?,
): Int {
    val matches =
        extractMarkdownImageMatches(markdown, ImageClickSource.CodeMirror)
            .filter { it.target.reference == reference }
    return matches.indexOfFirst { it.target.markdownStart == markdownStart }.takeIf { it >= 0 }
        ?: matches.indexOfFirst { match ->
            markdownStart != null &&
                match.target.markdownStart != null &&
                match.target.markdownEndExclusive != null &&
                markdownStart in match.target.markdownStart!! until match.target.markdownEndExclusive!!
        }.takeIf { it >= 0 }
        ?: 0
}

private fun extractMarkdownImageMatches(
    markdown: String,
    source: ImageClickSource,
): List<MarkdownImageReferenceMatch> {
    if (markdown.isBlank()) return emptyList()

    data class RawMatch(
        val markdownStart: Int,
        val markdownEndExclusive: Int,
        val referenceStart: Int,
        val referenceEndExclusive: Int,
        val reference: String,
    )

    val rawMatches =
        buildList {
            obsidianImageMatchRegex.findAll(markdown).forEach { match ->
                val group = match.groups[1] ?: return@forEach
                val rawReference = group.value
                val leadingWhitespace = rawReference.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val trailingWhitespace = rawReference.indexOfLast { !it.isWhitespace() }.let { if (it < 0) 0 else rawReference.length - it - 1 }
                val reference = rawReference.trim()
                if (reference.isLocalImageReference()) {
                    add(
                        RawMatch(
                            markdownStart = match.range.first,
                            markdownEndExclusive = match.range.last + 1,
                            referenceStart = group.range.first + leadingWhitespace,
                            referenceEndExclusive = group.range.last + 1 - trailingWhitespace,
                            reference = reference,
                        ),
                    )
                }
            }
            markdownImageMatchRegex.findAll(markdown).forEach { match ->
                val group = match.groups[2] ?: match.groups[3] ?: return@forEach
                val reference = group.value.trim()
                if (reference.isLocalImageReference()) {
                    add(
                        RawMatch(
                            markdownStart = match.range.first,
                            markdownEndExclusive = match.range.last + 1,
                            referenceStart = group.range.first,
                            referenceEndExclusive = group.range.last + 1,
                            reference = reference,
                        ),
                    )
                }
            }
        }.sortedBy { it.markdownStart }

    val occurrences = mutableMapOf<String, Int>()
    return rawMatches.map { match ->
        val occurrence = occurrences.getOrDefault(match.reference, 0)
        occurrences[match.reference] = occurrence + 1
        MarkdownImageReferenceMatch(
            target =
                KardLeafImageClickTarget(
                    reference = match.reference,
                    markdownStart = match.markdownStart,
                    markdownEndExclusive = match.markdownEndExclusive,
                    occurrenceIndex = occurrence,
                    source = source,
                ),
            referenceStart = match.referenceStart,
            referenceEndExclusive = match.referenceEndExclusive,
        )
    }
}

private fun String.isLocalImageReference(): Boolean {
    val value = trim()
    if (value.isBlank()) return false
    return !value.startsWith("http://", ignoreCase = true) &&
        !value.startsWith("https://", ignoreCase = true) &&
        !value.startsWith("data:", ignoreCase = true) &&
        !value.startsWith("file:", ignoreCase = true) &&
        !value.startsWith("content:", ignoreCase = true) &&
        !value.startsWith("blob:", ignoreCase = true)
}
