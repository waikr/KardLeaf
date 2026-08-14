package com.kangle.kardleaf.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class DailyNoteSupportTest {
    @Test
    fun normalizesFolderAndBuildsMarkdownPath() {
        assertEquals("Daily/Journal", DailyNoteSupport.normalizeFolder(" /Daily\\Journal// "))
        assertEquals(
            "Daily/Journal/2026-08-10.md",
            DailyNoteSupport.filePath(" /Daily\\Journal// ", "2026-08-10"),
        )
        assertEquals("2026-08-10.md", DailyNoteSupport.filePath("/", "2026-08-10"))
    }

    @Test
    fun formatsDailyTitle() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-08-10")!!
        assertEquals("2026-08-10", DailyNoteSupport.titleFor(date))
    }
}
