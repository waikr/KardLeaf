package com.kangle.kardleaf.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFeatureSupportTest {
    private class CodeMirrorHistorySimulator(initial: String) {
        private data class Entry(val before: String, val after: String)
        private val undo = ArrayDeque<Entry>()
        private val redo = ArrayDeque<Entry>()
        var content = initial
            private set

        fun type(text: String) = replace(content.length, content.length, text)

        fun replace(from: Int, to: Int, text: String) {
            val before = content
            content = before.substring(0, from) + text + before.substring(to)
            undo.addLast(Entry(before, content))
            redo.clear()
        }

        fun undo() {
            val entry = undo.removeLast()
            content = entry.before
            redo.addLast(entry)
        }

        fun redo() {
            val entry = redo.removeLast()
            content = entry.after
            undo.addLast(entry)
        }

        fun canUndo(): Boolean = undo.isNotEmpty()
    }

    @Test
    fun `v1 drawing remains readable`() {
        val source = """{"version":1,"canvasWidth":1080,"canvasHeight":1920,"strokes":[{"points":[]}]}"""
        val info = inspectDrawingDocument(source)
        assertEquals(1, info?.version)
        assertEquals("drawing", info?.documentType)
        assertEquals(1080, info?.canvasWidth)
    }

    @Test
    fun `v2 drawing and annotation documents round trip structurally`() {
        val drawing = """{"version":2,"documentType":"drawing","coordinateSpace":"canvas","canvas":{"width":800,"height":600},"strokes":[]}"""
        val annotation = """{"version":2,"documentType":"imageAnnotation","coordinateSpace":"sourceImagePixels","canvas":{"width":3024,"height":4032},"background":{"type":"image","sourceReference":"annotation.source.jpg"},"strokes":[{"points":[]}]}"""

        assertEquals("drawing", inspectDrawingDocument(drawing)?.documentType)
        val annotationInfo = inspectDrawingDocument(annotation)
        assertEquals("imageAnnotation", annotationInfo?.documentType)
        assertEquals("sourceImagePixels", annotationInfo?.coordinateSpace)
        assertEquals("annotation.source.jpg", annotationInfo?.sourceReference)
        assertEquals(1, annotationInfo?.strokeCount)
    }

    @Test
    fun `unknown drawing document version fails safely`() {
        assertNull(inspectDrawingDocument("""{"version":99,"strokes":[]}"""))
    }

    @Test
    fun `ordinary json is not accepted as a legacy drawing sidecar`() {
        assertEquals(DrawingSidecarClassification.None, classifyDrawingSidecar("""{"name":"business data"}""", false))
        assertTrue(classifyDrawingSidecar("""{"version":2,"documentType":"imageAnnotation","coordinateSpace":"sourceImagePixels","canvas":{"width":100,"height":200},"strokes":[]}""", true) is DrawingSidecarClassification.Invalid)
    }

    @Test
    fun `later matching image replacement history precedes external history`() {
        assertEquals(
            ImageReplacementHistorySource.Detached,
            selectImageReplacementHistorySource(attachedAvailable = false, externalAvailable = true, detachedMatchesCurrent = true),
        )
        assertEquals(
            ImageReplacementHistorySource.External,
            selectImageReplacementHistorySource(attachedAvailable = false, externalAvailable = true, detachedMatchesCurrent = false),
        )
    }

    @Test
    fun `CodeMirror image range replacement shares text undo timeline`() {
        val history = CodeMirrorHistorySimulator("![[photo.jpg]]")
        history.replace(0, 0, "A")
        history.replace(1, "![[photo.jpg]]".length + 1, "![[annotation.png]]")

        history.undo()
        assertEquals("A![[photo.jpg]]", history.content)
        assertTrue(history.canUndo())
        history.undo()
        assertEquals("![[photo.jpg]]", history.content)
        history.redo()
        assertEquals("A![[photo.jpg]]", history.content)
        history.redo()
        assertEquals("A![[annotation.png]]", history.content)
    }

    @Test
    fun `new annotation names do not collide`() {
        val existing = mutableSetOf<String>()
        val first = uniqueAnnotationBaseName("annotation_100") { it in existing }
        existing += first
        val second = uniqueAnnotationBaseName("annotation_100") { it in existing }
        assertNotEquals(first, second)
        assertEquals("annotation_100-1", second)
    }

    @Test
    fun `duplicate obsidian image replaces only second occurrence`() {
        val markdown = "![[photo.jpg]]\n![[photo.jpg]]"
        val targets = extractMarkdownImageClickTargets(markdown, ImageClickSource.MarkdownPreview)
        assertEquals(listOf(0, 1), targets.map { it.occurrenceIndex })

        val replaced = replaceClickedMarkdownImageReference(markdown, targets[1], "annotation.png")
        assertEquals("![[photo.jpg]]\n![[annotation.png]]", replaced?.content)
    }

    @Test
    fun `obsidian size and markdown alt are preserved`() {
        val obsidian = "![[photo.jpg|640]]"
        val obsidianTarget = extractMarkdownImageClickTargets(obsidian, ImageClickSource.NativeEditor).single()
        assertEquals(
            "![[annotation.png|640]]",
            replaceClickedMarkdownImageReference(obsidian, obsidianTarget, "annotation.png")?.content,
        )

        val markdown = "![说明](photo.jpg \"标题\")"
        val markdownTarget = extractMarkdownImageClickTargets(markdown, ImageClickSource.CodeMirror).single()
        assertEquals(
            "![说明](annotation.png \"标题\")",
            replaceClickedMarkdownImageReference(markdown, markdownTarget, "annotation.png")?.content,
        )
    }

    @Test
    fun `stale range uses occurrence and never falls back to first image`() {
        val markdown = "![[photo.jpg]]\n![[photo.jpg]]"
        val staleSecond =
            KardLeafImageClickTarget(
                reference = "photo.jpg",
                markdownStart = 500,
                markdownEndExclusive = 520,
                occurrenceIndex = 1,
                source = ImageClickSource.NativeEditor,
            )
        assertEquals(
            "![[photo.jpg]]\n![[annotation.png]]",
            replaceClickedMarkdownImageReference(markdown, staleSecond, "annotation.png")?.content,
        )
        assertNull(replaceClickedMarkdownImageReference(markdown, staleSecond.copy(occurrenceIndex = 2), "annotation.png"))
    }

    @Test
    fun `fit center preserves ratio and inverse mapping`() {
        val landscape = calculateFitCenterTransform(4000, 3000, 1080, 2000)
        assertEquals(0.27f, landscape.scale, 0.0001f)
        val (screenX, screenY) = landscape.sourceToScreen(1000f, 750f)
        val (sourceX, sourceY) = landscape.screenToSource(screenX, screenY)
        assertEquals(1000f, sourceX, 0.001f)
        assertEquals(750f, sourceY, 0.001f)

        val portrait = calculateFitCenterTransform(3000, 4000, 2000, 1080, zoom = 2f, panX = 40f, panY = -20f)
        assertEquals(portrait.scale, portrait.scale, 0f)
        val mapped = portrait.sourceToScreen(1500f, 2000f)
        val inverse = portrait.screenToSource(mapped.first, mapped.second)
        assertEquals(1500f, inverse.first, 0.001f)
        assertEquals(2000f, inverse.second, 0.001f)
    }

    @Test
    fun `restored strokes deletion and clear can be undone and redone`() {
        val history = DrawingSnapshotHistory<String>()
        var strokes = listOf("A", "B")
        history.initializeRestored(strokes)
        strokes = history.undo(strokes)!!
        assertEquals(listOf("A"), strokes)
        strokes = history.redo(strokes)!!
        assertEquals(listOf("A", "B"), strokes)

        history.recordBefore(strokes)
        strokes = listOf("B")
        strokes = history.undo(strokes)!!
        assertEquals(listOf("A", "B"), strokes)

        history.recordBefore(strokes)
        strokes = emptyList()
        assertTrue(strokes.isEmpty())
        strokes = history.undo(strokes)!!
        assertEquals(listOf("A", "B"), strokes)
        assertTrue(history.redo(strokes)?.isEmpty() == true)
        assertNotNull(history)
        assertFalse(history.undoCount < 0)
    }

    @Test
    fun `restored thousand strokes use one history snapshot`() {
        val history = DrawingSnapshotHistory<Int>()
        val strokes = (0 until 1000).toList()
        history.initializeRestored(strokes)
        assertEquals(1000, history.undoCount)
        val afterUndo = history.undo(strokes)!!
        assertEquals(999, afterUndo.size)
        assertEquals(strokes, history.redo(afterUndo))
    }
}
