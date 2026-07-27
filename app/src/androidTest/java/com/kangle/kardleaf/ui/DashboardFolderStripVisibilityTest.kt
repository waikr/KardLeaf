package com.kangle.kardleaf.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardFolderStripVisibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val labels = listOf("A", "B medium", "C wider", "D final")

    @Test
    fun selectedFullyVisibleItemDoesNotMoveRow() {
        val selectedPath = mutableStateOf("A")
        setFolderStrip(selectedPath)
        val firstLeftBefore = boundsOf("A").left

        select(selectedPath, "B medium")

        assertEquals(firstLeftBefore, boundsOf("A").left, POSITION_TOLERANCE_PX)
    }

    @Test
    fun selectedRightOcclusionMovesOnlyHiddenDistance() {
        val selectedPath = mutableStateOf("A")
        setFolderStrip(selectedPath)
        val viewport = viewportBounds()
        val firstBefore = boundsOf("A")
        val contentRight = viewport.right - (firstBefore.left - viewport.left)
        val targetBefore = boundsOf("C wider")
        assertTrue(targetBefore.left < contentRight && targetBefore.right > contentRight)
        val rightOcclusion = targetBefore.right - contentRight

        select(selectedPath, "C wider")

        val firstAfter = boundsOf("A")
        assertEquals(rightOcclusion, firstBefore.left - firstAfter.left, POSITION_TOLERANCE_PX)
        assertEquals(contentRight, boundsOf("C wider").right, POSITION_TOLERANCE_PX)
    }

    @Test
    fun selectedLeftOcclusionMovesOnlyHiddenDistance() {
        val selectedPath = mutableStateOf("A")
        setFolderStrip(selectedPath)
        val viewport = viewportBounds()
        val contentLeft = boundsOf("A").left
        assertTrue(contentLeft > viewport.left)
        select(selectedPath, "D final")
        val targetBefore = boundsOf("B medium")
        assertTrue(targetBefore.left < contentLeft && targetBefore.right > contentLeft)
        val leftOcclusion = contentLeft - targetBefore.left

        select(selectedPath, "B medium")

        val targetAfter = boundsOf("B medium")
        assertEquals(leftOcclusion, targetAfter.left - targetBefore.left, POSITION_TOLERANCE_PX)
        assertEquals(contentLeft, targetAfter.left, POSITION_TOLERANCE_PX)
    }

    @Test
    fun selectedOffscreenItemAlignsNearestViewportEdgeInBothDirections() {
        val selectedPath = mutableStateOf("A")
        setFolderStrip(selectedPath)
        val viewport = viewportBounds()
        val firstBefore = boundsOf("A")
        val contentLeft = firstBefore.left
        val contentRight = viewport.right - (contentLeft - viewport.left)
        assertTrue(boundsOf("D final").left >= contentRight)

        select(selectedPath, "D final")

        assertEquals(contentRight, boundsOf("D final").right, POSITION_TOLERANCE_PX)
        assertTrue(boundsOf("A").right <= contentLeft)

        select(selectedPath, "A")

        assertEquals(contentLeft, boundsOf("A").left, POSITION_TOLERANCE_PX)
    }

    private fun setFolderStrip(selectedPath: MutableState<String>) {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(220.dp).testTag(VIEWPORT_TAG)) {
                    FolderPathStrip(
                        currentFilter = MainViewModel.NoteFilter.Label(selectedPath.value),
                        labels = labels,
                        onOpenFolder = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun select(
        selectedPath: MutableState<String>,
        path: String,
    ) {
        composeRule.runOnIdle { selectedPath.value = path }
        composeRule.waitForIdle()
    }

    private fun boundsOf(text: String): Rect =
        composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    private fun viewportBounds(): Rect =
        composeRule.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot

    private companion object {
        const val VIEWPORT_TAG = "folder-strip-viewport"
        const val POSITION_TOLERANCE_PX = 2f
    }
}
