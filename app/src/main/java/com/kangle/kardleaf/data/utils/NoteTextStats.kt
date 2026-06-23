package com.kangle.kardleaf.data.utils

data class NoteTextStats(
    val characterCount: Long = 0,
    val wordCountWithPunctuation: Long = 0,
    val wordCountWithoutPunctuation: Long = 0,
    val lineCount: Long = 0,
    val paragraphCount: Long = 0,
) {
    companion object {
        fun fromText(
            text: CharSequence,
            skipFrontMatter: Boolean = true,
        ): NoteTextStats {
            if (text.isBlank()) return NoteTextStats()
            return fromLines(text.lineSequence(), skipFrontMatter)
        }

        fun fromLines(
            lines: Sequence<String>,
            skipFrontMatter: Boolean = true,
        ): NoteTextStats {
            val counter = Counter()
            var lineIndex = 0
            var inFrontMatter = false
            var skipBlankAfterFrontMatter = false

            lines.forEach { line ->
                val trimmed = line.trim()
                val skipLine =
                    if (!skipFrontMatter) {
                        false
                    } else {
                        when {
                            lineIndex == 0 && trimmed == "---" -> {
                                inFrontMatter = true
                                true
                            }
                            inFrontMatter && trimmed == "---" -> {
                                inFrontMatter = false
                                skipBlankAfterFrontMatter = true
                                true
                            }
                            inFrontMatter -> true
                            skipBlankAfterFrontMatter && line.isBlank() -> true
                            else -> {
                                skipBlankAfterFrontMatter = false
                                false
                            }
                        }
                    }

                if (!skipLine) {
                    counter.addLine(line)
                }
                lineIndex++
            }

            return counter.toStats()
        }

        private val cjkRanges = arrayOf(
            0x3400..0x4DBF,
            0x4E00..0x9FFF,
            0xF900..0xFAFF,
            0x20000..0x2A6DF,
            0x2A700..0x2B73F,
            0x2B740..0x2B81F,
            0x2B820..0x2CEAF,
            0x2F800..0x2FA1F,
            0x3040..0x30FF,
            0xAC00..0xD7AF,
        )

        private val punctuationChars = "，。、：；？！\"'（）《》「」【】!§$%&/()=?`*_:;><|,.#+~\\´{}[]-—–…"

        private fun isCjk(codePoint: Int): Boolean = cjkRanges.any { codePoint in it }

        private fun isPunctuationOrSymbol(codePoint: Int): Boolean {
            if (codePoint <= Char.MAX_VALUE.code && codePoint.toChar() in punctuationChars) {
                return true
            }
            return when (Character.getType(codePoint)) {
                Character.CONNECTOR_PUNCTUATION.toInt(),
                Character.DASH_PUNCTUATION.toInt(),
                Character.START_PUNCTUATION.toInt(),
                Character.END_PUNCTUATION.toInt(),
                Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
                Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                Character.OTHER_PUNCTUATION.toInt(),
                Character.MATH_SYMBOL.toInt(),
                Character.CURRENCY_SYMBOL.toInt(),
                Character.MODIFIER_SYMBOL.toInt(),
                Character.OTHER_SYMBOL.toInt() -> true
                else -> false
            }
        }

        private class Counter {
            private var characterCount = 0L
            private var wordCountWithPunctuation = 0L
            private var wordCountWithoutPunctuation = 0L
            private var lineCount = 0L
            private var paragraphCount = 0L
            private var hasAnyLine = false
            private var hasNonBlankText = false
            private var previousLineWasBlank = true
            private var inWord = false

            fun addLine(line: String) {
                if (hasAnyLine) {
                    characterCount++
                    lineCount++
                    inWord = false
                } else {
                    hasAnyLine = true
                    lineCount = 1
                }

                characterCount += line.length.toLong()
                val lineHasText = line.any { !it.isWhitespace() }
                if (lineHasText) {
                    hasNonBlankText = true
                    if (previousLineWasBlank) {
                        paragraphCount++
                    }
                }
                previousLineWasBlank = !lineHasText
                scanWords(line)
            }

            fun toStats(): NoteTextStats =
                if (!hasNonBlankText) {
                    NoteTextStats()
                } else {
                    NoteTextStats(
                        characterCount = characterCount,
                        wordCountWithPunctuation = wordCountWithPunctuation,
                        wordCountWithoutPunctuation = wordCountWithoutPunctuation,
                        lineCount = lineCount,
                        paragraphCount = paragraphCount,
                    )
                }

            private fun scanWords(line: String) {
                var index = 0
                while (index < line.length) {
                    val codePoint = Character.codePointAt(line, index)
                    when {
                        Character.isWhitespace(codePoint) -> {
                            inWord = false
                        }
                        isCjk(codePoint) -> {
                            wordCountWithPunctuation++
                            wordCountWithoutPunctuation++
                            inWord = false
                        }
                        isPunctuationOrSymbol(codePoint) -> {
                            wordCountWithPunctuation++
                            inWord = false
                        }
                        else -> {
                            if (!inWord) {
                                wordCountWithPunctuation++
                                wordCountWithoutPunctuation++
                                inWord = true
                            }
                        }
                    }
                    index += Character.charCount(codePoint)
                }
            }
        }
    }
}
