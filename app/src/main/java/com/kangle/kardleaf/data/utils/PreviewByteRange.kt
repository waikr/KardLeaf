package com.kangle.kardleaf.data.utils

/** Single byte ranges used by Chromium media seeking. Null means an unsatisfiable range. */
internal fun previewByteRange(header: String, length: Long): LongRange? {
    if (length <= 0) return null
    val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(header.trim()) ?: return null
    val (first, last) = match.destructured
    if (first.isEmpty()) {
        val suffix = last.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return (length - suffix.coerceAtMost(length))..(length - 1)
    }
    val start = first.toLongOrNull()?.takeIf { it < length } ?: return null
    val end = if (last.isEmpty()) length - 1 else last.toLongOrNull() ?: return null
    if (end < start) return null
    return start..end.coerceAtMost(length - 1)
}
