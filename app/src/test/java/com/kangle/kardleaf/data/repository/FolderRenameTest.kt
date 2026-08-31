package com.kangle.kardleaf.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRenameTest {
    @Test
    fun detectsCaseOnlyFolderRenames() {
        assertTrue(isCaseOnlyFolderRename("test", "Test"))
        assertFalse(isCaseOnlyFolderRename("test", "test"))
        assertFalse(isCaseOnlyFolderRename("test", "Test2"))
    }

    @Test
    fun remapsOnlyTheRenamedSubtree() {
        assertEquals("Projects New/note.md", remapTreePath("Projects/note.md", "Projects", "Projects New"))
        assertEquals(
            "Archive/Projects/Ideas/note.md",
            remapTreePath("Projects/Ideas/note.md", "Projects", "Archive/Projects"),
        )
        assertEquals("Other/note.md", remapTreePath("Other/note.md", "Projects", "Projects New"))
    }

    @Test
    fun validatesFolderMovesWithoutAllowingCyclesOrCrossParentRename() {
        assertTrue(isValidFolderRelocation("Projects/Ideas", "Archive/Ideas"))
        assertTrue(isValidFolderRelocation("Projects/Ideas", "Ideas"))
        assertTrue(isValidFolderRelocation("Projects/Ideas", "Projects/Renamed"))
        assertFalse(isValidFolderRelocation("Projects", "Projects"))
        assertFalse(isValidFolderRelocation("Projects", "Projects/Ideas/Projects"))
        assertFalse(isValidFolderRelocation("Projects/Ideas", "Archive/Renamed"))
    }

    @Test
    fun resolvesRootAndNestedFileMovePaths() {
        assertEquals("Archive/root.md", resolveFileTreeMovePath("root.md", "Archive"))
        assertEquals("Published/note.md", resolveFileTreeMovePath("Drafts/2026/note.md", "Published"))
        assertEquals("note.md", resolveFileTreeMovePath("Drafts/note.md", ""))
        assertEquals(null, resolveFileTreeMovePath("Drafts/note.md", "Drafts"))
    }
}
