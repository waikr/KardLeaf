package com.kangle.kardleaf.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTreePickerTest {
    @Test
    fun buildsSortedTreeWithMissingFolderAncestors() {
        val nodes = buildFileTreePickerFolderNodes(
            listOf("Work/2026/Reports", "Work/2026/Plans", "Archive"),
        )

        assertEquals(
            listOf("Archive", "Work", "Work/2026", "Work/2026/Plans", "Work/2026/Reports"),
            nodes.map { it.id },
        )
        assertEquals(listOf(0, 0, 1, 2, 2), nodes.map { it.depth })
        assertEquals("Work", nodes[2].parentId)
        assertTrue(nodes.first { it.id == "Work" }.hasChildren)
    }
}
