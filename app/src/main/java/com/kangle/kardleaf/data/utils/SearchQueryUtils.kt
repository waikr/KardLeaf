package com.kangle.kardleaf.data.utils

object SearchQueryUtils {
    data class SearchText(val text: String, private val offsets: IntArray? = null) {
        fun originalOffset(offset: Int): Int = offsets?.get(offset) ?: offset
    }

    fun normalizeLineBreaks(text: String): SearchText {
        if ('\r' !in text) return SearchText(text)
        val offsets = IntArray(text.length + 1)
        val normalized = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            offsets[normalized.length] = index
            val char = text[index++]
            normalized.append(if (char == '\r') '\n' else char)
            if (char == '\r' && text.getOrNull(index) == '\n') index++
        }
        offsets[normalized.length] = text.length
        return SearchText(normalized.toString(), offsets)
    }

    fun escapeLikePattern(query: String): String =
        query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    fun describeForLog(query: String): String {
        val trimmed = query.trim()
        val percentCount = trimmed.count { it == '%' }
        val underscoreCount = trimmed.count { it == '_' }
        return "rawLen=${query.length} trimLen=${trimmed.length} percent=$percentCount underscore=$underscoreCount"
    }
}
