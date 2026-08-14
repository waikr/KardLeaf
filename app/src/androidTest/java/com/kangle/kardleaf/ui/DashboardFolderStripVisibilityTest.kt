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

    private val labels = listOf("A", "B medium folder", "C wider folder", "D final folder")

    @Test
    fun selectedFullyVisibleItemDoesNotMoveRow() {
        val selectedPath = mutableStateOf("A")
        setFolderStrip(selectedPath)
        val firstLeftBefore = boundsOf("A").left

        select(selectedPath, "B medium folder")

        assertEquals(firstLeftBefore, boundsOf("A").left, POSITION_TOLERANCE_PX)
    }

    @Test
    fun selectedRightOcclusionMovesOnlyHiddenDistance() {
        val selectedPath = mutableStateOf("A")
        setFolderStrip(selectedPath)
        val viewport = viewportBounds()
        val firstBefore = boundsOf("A")
        val contentRight = viewport.right - (firstBefore.left - viewport.left)
        val targetBefore = boundsOf("C wider folder")
        assertTrue(targetBefore.width > 0f)
        assertEquals(contentRight, targetBefore.right, POSITION_TOLERANCE_PX)

        select(selectedPath, "C wider folder")

        assertEquals(contentRight, boundsOf("C wider folder").right, POSITION_TOLERANCE_PX)
        assertTrue(boundsOf("A").left < firstBefore.left)
    }

    @Test
    fun selectedLeftOcclusionMovesOnlyHiddenDistance() {
        val selectedPath = mutableStateOf("A")
        setFolderStrip(selectedPath)
        val contentLeft = boundsOf("A").left
        select(selectedPath, "D final folder")

        select(selectedPath, "B medium folder")

        val targetAfter = boundsOf("B medium folder")
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
        assertEquals(0f, boundsOf("D final folder").width, POSITION_TOLERANCE_PX)

        select(selectedPath, "D final folder")

        assertEquals(contentRight, boundsOf("D final folder").right, POSITION_TOLERANCE_PX)
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
