package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.utils.NoteFormatUtils

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

    val rawMatches =
        NoteFormatUtils.findMarkdownImageReferences(markdown)
            .filter { it.reference.isLocalImageReference() }
            .map { image ->
                MarkdownImageReferenceMatch(
                    target =
                        KardLeafImageClickTarget(
                            reference = image.reference,
                            markdownStart = image.start,
                            markdownEndExclusive = image.endExclusive,
                            occurrenceIndex = 0,
                            source = source,
                        ),
                    referenceStart = image.referenceStart,
                    referenceEndExclusive = image.referenceEndExclusive,
                )
            }

    val occurrences = mutableMapOf<String, Int>()
    return rawMatches.map { match ->
        val occurrence = occurrences.getOrDefault(match.target.reference, 0)
        occurrences[match.target.reference] = occurrence + 1
        match.copy(target = match.target.copy(occurrenceIndex = occurrence))
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
