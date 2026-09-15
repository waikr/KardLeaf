package com.kangle.kardleaf.ui.editor

import com.google.gson.JsonParser
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteSearchOptions
import com.kangle.kardleaf.ui.findSearchMatch
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Date

class EditorSearchTest {
    @Test fun nativeReplacementKeepsUnchangedTextOutOfUndoAndRejectsOversizedEdits() {
        val prefix = "a".repeat(200_000)
        val before = prefix + "foo tail"
        val after = prefix + "bar tail"
        val range = nativeSearchChangedRange(before, after)
        val inserted = after.substring(range.start, after.length - (before.length - range.end))
        assertEquals(NoteSearchMatchRange(200_000, 200_003), range)
        assertEquals(after, before.replaceRange(range.start, range.end, inserted))
        assertTrue(canUndoNativeSearchReplacement(range.end - range.start, inserted.length))
        for ((old, new) in listOf("" to "abc", "abc" to "", "same" to "same", "aaa" to "aa", "😀" to "😁")) {
            val edit = nativeSearchChangedRange(old, new)
            assertEquals(new, old.replaceRange(edit.start, edit.end, new.substring(edit.start, new.length - (old.length - edit.end))))
        }
        assertTrue(canUndoNativeSearchReplacement(100_000, 100_000))
        assertFalse(canUndoNativeSearchReplacement(100_000, 100_001))
        assertFalse(canUndoNativeSearchReplacement(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun sharedSearchRulesAndGlobalOffsets() {
        val cases = javaClass.getResourceAsStream("/search-cases.json")!!.reader().use { JsonParser.parseReader(it).asJsonArray }
        for (item in cases) {
            val c = item.asJsonObject
            val name = c["name"].asString
            val text = c["text"].asString
            val query = c["query"].asString
            val regex = c["useRegex"]?.asBoolean ?: false
            val matchCase = c["matchCase"]?.asBoolean ?: false
            val expected = c["ranges"].asJsonArray.map { it.asJsonArray.let { r -> NoteSearchMatchRange(r[0].asInt, r[1].asInt) } }
            val result = buildNoteSearchMatches(text, query, regex, matchCase)
            assertEquals(name, expected, result.matches)
            assertEquals(name, c["error"]?.asBoolean ?: false, result.errorMessage != null)
            val note = Note(file = File("search.md"), title = "Title", content = text, lastModified = Date(0), color = 0)
            val global = findSearchMatch(note, query, options = NoteSearchOptions(useRegex = regex, matchCase = matchCase, matchTitle = false))
            assertEquals(name, expected.firstOrNull()?.start, global?.startOffset)
            if (global != null) assertTrue(name, global.snippet.contains(global.matchedText!!.replace('\r', ' ').replace('\n', ' ')))
        }
    }

    @Test fun crlfMatchingPreservesRawOffsetsAndUnchangedLineEndings() {
        val text = "😀\r\nfoo\r\nbar\r\ntail"
        val match = buildNoteSearchMatches(text, "foo\nbar", false, true).matches.single()
        assertEquals(NoteSearchMatchRange(4, 12), match)
        assertEquals("😀\r\nX\r\ntail", replaceAllNoteSearchMatches(text, "foo\nbar", "X", false, true).text)
        assertEquals("😀\r\nfoo/bar\r\ntail", replaceAllNoteSearchMatches(text, "(foo)\\s+(bar)", "$1/$2", true, true).text)
        assertEquals("foo/bar", buildCurrentReplacement(text, match, "(foo)\\s+(bar)", "$1/$2", true, true).text)
    }

    @Test fun replacementReferencesAndCurrentMatchAgreeWithCodeMirror() {
        assertEquals("foo", replaceAllNoteSearchMatches("foo", "(x)?(foo)", "$1$2", true, true).text)
        val replacement = "\$& \$\$ $1 $12 $0 $99 \\n"
        assertEquals("foo $ foo foo2 $0 $99 \\n", replaceAllNoteSearchMatches("foo", "(foo)", replacement, true, true).text)
        assertEquals(0, replaceAllNoteSearchMatches("foo", "(?=foo)", "x", true, true).count)
        assertEquals("$1\\n", replaceAllNoteSearchMatches("foo", "foo", "$1\\n", false, true).text)
        val summary = summarizeNoteSearchMatches("foo foo", "foo", 5, false, true)
        assertEquals(2, summary.currentOrdinal)
        assertEquals(4, summary.currentStart)
    }
}
