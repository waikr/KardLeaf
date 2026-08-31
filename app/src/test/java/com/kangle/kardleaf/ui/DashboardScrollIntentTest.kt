package com.kangle.kardleaf.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardScrollIntentTest {
    private val filter = MainViewModel.NoteFilter.All

    @Test
    fun appliesOnlyWhenContextIsStableAndTargetIsReady() {
        val intent =
            DashboardScrollIntent(
                id = 1L,
                reason = DashboardScrollReason.NOTE_CREATED,
                action = DashboardScrollAction.REVEAL_PATH,
                targetFilter = filter,
                targetSearchActive = false,
                targetPath = "new.md",
                waitForEditorClose = true,
                requiredUserScrollVersion = 4L,
            )

        assertEquals(
            DashboardScrollDecision.WAIT,
            decideDashboardScrollIntent(intent, filter, false, true, 4L, 0, false),
        )
        assertEquals(
            DashboardScrollDecision.WAIT,
            decideDashboardScrollIntent(intent, filter, false, false, 4L, null, false),
        )
        assertEquals(
            DashboardScrollDecision.DROP,
            decideDashboardScrollIntent(intent, filter, false, false, 5L, 0, false),
        )
        assertEquals(
            DashboardScrollDecision.DROP,
            decideDashboardScrollIntent(intent, MainViewModel.NoteFilter.Favorites, false, false, 4L, 0, false),
        )
        assertEquals(
            DashboardScrollDecision.DROP,
            decideDashboardScrollIntent(intent, filter, true, false, 4L, 0, false),
        )
        assertEquals(
            DashboardScrollDecision.APPLY,
            decideDashboardScrollIntent(intent, filter, false, false, 4L, 0, false),
        )
    }

    @Test
    fun noteRevealUsesTheFileParentFolder() {
        assertEquals("journal/topic", dashboardParentFolderPath("journal\\topic/note.md"))
        assertEquals("", dashboardParentFolderPath("note.md"))
    }
}
