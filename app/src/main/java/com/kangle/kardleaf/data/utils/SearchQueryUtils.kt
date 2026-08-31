package com.kangle.kardleaf.data.utils

object SearchQueryUtils {
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
