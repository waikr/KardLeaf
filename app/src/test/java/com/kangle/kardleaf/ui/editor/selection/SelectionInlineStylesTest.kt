package com.kangle.kardleaf.ui.editor.selection

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SelectionInlineStylesTest {
    @Test fun sharedInlineStyleFixturesPreserveTextSelectionAndBoundaries() {
        val json = javaClass.getResourceAsStream("/selection-inline-colors.json")!!.bufferedReader().use { it.readText() }
        for (element in JsonParser.parseString(json).asJsonArray) {
            val fixture = element.asJsonObject
            val name = fixture["name"].asString
            val marked = fixture["marked"].asString
            val from = marked.indexOf('«')
            val to = marked.indexOf('»') - 1
            val text = marked.replace("«", "").replace("»", "")
            val reverse = fixture["reverse"]?.asBoolean == true
            val change = SelectionInlineStyles.change(text, if (reverse) to else from, if (reverse) from else to,
                fixture["property"].asString, fixture["value"].takeUnless { it.isJsonNull }?.asString)
            if (fixture["expected"].isJsonNull) { assertNull(name, change); continue }
            assertNotNull(name, change)
            val result = text.substring(0, change!!.from) + change.text + text.substring(change.to)
            assertEquals(name, fixture["expected"].asString, result)
            val selected = result.substring(minOf(change.start, change.end), maxOf(change.start, change.end)).replace(Regex("<[^>]*>"), "")
            assertEquals(name, text.substring(from, to).replace(Regex("<[^>]*>"), ""), selected)
            assertEquals(name, reverse, change.start > change.end)
            assertFalse(name, result.contains("style=\"\""))
        }
    }
}
