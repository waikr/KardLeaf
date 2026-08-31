package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.Date

class HeatmapLogicTest {
    @Test
    fun noteCountsUseFourConfiguredDepths() {
        assertEquals(0.42f, heatmapColorAlpha(0), 0f)
        assertEquals(0.28f, heatmapColorAlpha(1), 0f)
        assertEquals(0.28f, heatmapColorAlpha(10), 0f)
        assertEquals(0.46f, heatmapColorAlpha(11), 0f)
        assertEquals(0.46f, heatmapColorAlpha(20), 0f)
        assertEquals(0.64f, heatmapColorAlpha(21), 0f)
        assertEquals(0.64f, heatmapColorAlpha(30), 0f)
        assertEquals(0.86f, heatmapColorAlpha(31), 0f)
    }

    @Test
    fun supportsCreationAndEditTimeMetrics() {
        val createdAt = day(2026, Calendar.AUGUST, 10)
        val editedAt = day(2026, Calendar.AUGUST, 12)
        val note = Note(
            file = File("note.md"),
            title = "Note",
            content = "",
            lastModified = editedAt,
            createdAt = createdAt,
            color = 0L,
        )

        val createdStats = buildHeatmapStats(
            notes = listOf(note),
            rangeStart = createdAt,
            rangeEnd = editedAt,
            gridStart = createdAt,
            metric = HeatmapMetric.CREATED,
        )
        val editedStats = buildHeatmapStats(
            notes = listOf(note),
            rangeStart = createdAt,
            rangeEnd = editedAt,
            gridStart = createdAt,
            metric = HeatmapMetric.EDITED,
        )

        assertEquals(createdAt, createdStats.peakDay?.date)
        assertEquals(editedAt, editedStats.peakDay?.date)
        assertEquals(1, createdStats.noteCount)
        assertEquals(1, editedStats.noteCount)
    }

    private fun day(year: Int, month: Int, day: Int): Date =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.time
}
