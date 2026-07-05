package com.kangle.kardleaf.data.utils

object SearchQueryUtils {
    fun isMeaningfulSearchQuery(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.any { it.isCjkSearchChar() }) return true
        return trimmed.count { it.isLetterOrDigit() } >= 2
    }

    fun describeForLog(query: String): String {
        val trimmed = query.trim()
        val letterOrDigitCount = trimmed.count { it.isLetterOrDigit() }
        val hasCjk = trimmed.any { it.isCjkSearchChar() }
        val codePoints = trimmed.take(8).map { "U+%04X".format(it.code) }.joinToString(",")
        val preview = trimmed.take(8).replace("\n", "\\n").replace("\r", "\\r")
        return "rawLen=${query.length} trimLen=${trimmed.length} letters=$letterOrDigitCount hasCjk=$hasCjk " +
            "meaningful=${isMeaningfulSearchQuery(trimmed)} preview=$preview codes=$codePoints"
    }

    private fun Char.isCjkSearchChar(): Boolean =
        when (Character.UnicodeBlock.of(this)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS,
            Character.UnicodeBlock.HANGUL_SYLLABLES,
            Character.UnicodeBlock.HANGUL_JAMO,
            Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO,
            Character.UnicodeBlock.BOPOMOFO,
            Character.UnicodeBlock.BOPOMOFO_EXTENDED -> true
            else -> false
        }
}
