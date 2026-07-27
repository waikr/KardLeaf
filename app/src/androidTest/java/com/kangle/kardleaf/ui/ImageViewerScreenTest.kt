package com.kangle.kardleaf.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextRange
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.ui.editor.native.KardLeafEditorController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ImageViewerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detachedImageReferenceReplacementRemainsUndoable() {
        val original = "before ![[photo.jpg]] after"
        val referenceStart = original.indexOf("photo.jpg")
        val controller = KardLeafEditorController()
        controller.acceptInitialSnapshot("note", "", original, TextRange(2, 2))
        controller.setSelection(referenceStart, referenceStart + "photo.jpg".length)
        controller.replaceSelection("annotation.png")
        controller.setSelection(2, 2)

        assertTrue(controller.canUndo())
        assertEquals("before ![[annotation.png]] after", controller.getText())
        controller.undo()
        assertEquals(original, controller.getText())
        assertTrue(controller.canRedo())
        controller.redo()
        assertEquals("before ![[annotation.png]] after", controller.getText())
    }

    @Test
    fun viewerShowsImageAndDispatchesEditAndBack() {
        var editCount = 0
        var dismissCount = 0
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val resource =
            RoomNoteRepository.ImageViewerResource(
                reference = "photo.jpg",
                bitmap = bitmap,
                mimeType = "image/jpeg",
                sourceWidth = 640,
                sourceHeight = 480,
                exifOrientation = 1,
                documentType = "image",
                drawingSource = null,
                editable = true,
            )

        composeRule.setContent {
            MaterialTheme {
                ImageViewerScreen(
                    resource = resource,
                    isLoading = false,
                    onDismiss = { dismissCount++ },
                    onEdit = { editCount++ },
                )
            }
        }

        composeRule.onNodeWithTag("image_viewer").assertIsDisplayed()
        composeRule.onNodeWithTag("image_viewer_edit").performClick()
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, editCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun viewerShowsResourceErrorBeforeDecodedBitmap() {
        val resource =
            RoomNoteRepository.ImageViewerResource(
                reference = "unsupported.png",
                bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888),
                mimeType = "image/png",
                sourceWidth = 640,
                sourceHeight = 480,
                exifOrientation = 1,
                documentType = "unsupported",
                drawingSource = null,
                editable = false,
                errorMessage = "图片标注文档版本不受支持",
            )
        composeRule.setContent {
            MaterialTheme {
                ImageViewerScreen(resource, false, {}, {})
            }
        }
        composeRule.onNodeWithTag("image_viewer_error").assertIsDisplayed()
    }

    @Test
    fun viewerAcceptsDoubleTapPinchAndPan() {
        val resource =
            RoomNoteRepository.ImageViewerResource(
                reference = "gesture.jpg",
                bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888),
                mimeType = "image/jpeg",
                sourceWidth = 1200,
                sourceHeight = 800,
                exifOrientation = 1,
                documentType = "image",
                drawingSource = null,
                editable = true,
            )
        composeRule.setContent {
            MaterialTheme {
                ImageViewerScreen(resource, false, {}, {})
            }
        }

        composeRule.onNodeWithTag("image_viewer_canvas").performTouchInput {
            doubleClick(center)
            val gestureCenter = center
            pinch(
                start0 = Offset(gestureCenter.x - 60f, gestureCenter.y),
                end0 = Offset(gestureCenter.x - 180f, gestureCenter.y),
                start1 = Offset(gestureCenter.x + 60f, gestureCenter.y),
                end1 = Offset(gestureCenter.x + 180f, gestureCenter.y),
                durationMillis = 300,
            )
            swipe(gestureCenter, gestureCenter + Offset(120f, 80f), durationMillis = 300)
            doubleClick(gestureCenter)
        }
        composeRule.onNodeWithTag("image_viewer_canvas").assertIsDisplayed()
    }
}
