package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Date
import kotlin.system.measureNanoTime

class FileTreeBaselineBenchmarkTest {
    @Test
    fun remapsExpandedAndSelectedDescendantsWithTheirFolder() {
        assertEquals("Renamed/Child", remapDrawerTreePath("Original/Child", "Original", "Renamed"))
        assertEquals("Other/Child", remapDrawerTreePath("Other/Child", "Original", "Renamed"))
    }

    @Test
    fun printCurrentFolderTreeBuildBaseline() {
        val paths = (0 until 100).flatMap { group ->
            (0 until 10).map { child -> "group-$group/child-$child" }
        }
        val notes = (0 until 5_000).map { index ->
            val folder = paths[index % paths.size]
            Note(
                file = File(folder, "note-$index.md"),
                title = "note-$index",
                content = "",
                lastModified = Date(0),
                color = 0xFFFFFFFF,
            )
        }
        val method = Class.forName("com.kangle.kardleaf.ui.AppDrawerKt")
            .declaredMethods
            .single { it.name == "buildFolderTree" && it.parameterCount == 4 }
            .apply { isAccessible = true }
        val savedOrderFor: (String) -> List<String> = { emptyList() }

        val tree = method.invoke(null, paths, notes, savedOrderFor, emptyList<Any>()) as List<*>
        assertEquals(100, tree.size)
        val elapsed = measureNanoTime {
            repeat(5) { method.invoke(null, paths, notes, savedOrderFor, emptyList<Any>()) }
        }
        println("FILE_TREE_BENCHMARK folders=${paths.size} notes=${notes.size} meanMs=${elapsed / 5 / 1_000_000.0}")
    }
}
