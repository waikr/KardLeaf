package com.kangle.kardleaf.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class FileTreeMutationPolicyTest {
    @Test
    fun fileTreeMutationsStayIncrementalAndRollbackOnFailure() {
        val repository = readSource("src/main/java/com/kangle/kardleaf/data/repository/RoomNoteRepository.kt")
        val viewModel = readSource("src/main/java/com/kangle/kardleaf/ui/MainViewModel.kt")
        val drawer = readSource("src/main/java/com/kangle/kardleaf/ui/AppDrawer.kt")
        val folderRename = repository.substringAfter("private suspend fun renameLabelLocked")
            .substringBefore("suspend fun renameNoteFile")
        val fileRename = repository.substringAfter("suspend fun renameNoteFile")
            .substringBefore("suspend fun moveNoteFile")
        val fileMove = repository.substringAfter("suspend fun moveNoteFile")
            .substringBefore("private fun remapFileTreeCaches")
        val documentMove = repository.substringAfter("private fun relocateDocument")
            .substringBefore("private fun renameFolderDocument")
        val viewModelRename = viewModel.substringAfter("fun renameNote(").substringBefore("fun moveNote(")
        val viewModelMove = viewModel.substringAfter("fun moveNote(").substringBefore("fun saveNote(")

        assertTrue(folderRename.contains("noteDao.renameFolderPaths"))
        assertTrue(folderRename.contains("noteDao.moveActiveFolderPaths"))
        assertTrue(folderRename.contains("noteLinkDao.moveActiveFolderSourcePaths"))
        assertTrue(folderRename.contains("database.withTransaction"))
        assertTrue(folderRename.contains("relocateDocument(relocatedFolder, targetParent, sourceParent"))
        assertFalse(folderRename.contains("refreshNotes()"))
        assertTrue(fileRename.contains("renameFolderDocument"))
        assertFalse(fileRename.contains("openOutputStream"))
        assertTrue(fileMove.contains("findFolder(root, normalizedTarget)"))
        assertTrue(fileMove.contains("targetParent.findFile(fileName)"))
        assertTrue(fileMove.contains("database.withTransaction"))
        assertTrue(fileMove.contains("noteDao.moveNotePath"))
        assertTrue(fileMove.contains("relocateDocument(movedFile, targetParent, sourceParent"))
        assertFalse(fileMove.contains("getOrCreateFolder"))
        assertFalse(fileMove.contains("openOutputStream"))
        assertTrue(documentMove.contains("DocumentsContract.moveDocument"))
        assertTrue(documentMove.contains("DocumentsContract.getDocumentId"))
        assertTrue(viewModelRename.contains("repository.renameNoteFile"))
        assertFalse(viewModelRename.contains("saveNoteFromQuickEditor"))
        assertTrue(viewModelMove.contains("repository.moveNoteFile"))
        assertFalse(viewModelMove.contains("moveNotesWithResult"))
        assertTrue(drawer.contains("private fun DrawerMoveFolderMenu("))
        assertTrue(drawer.contains("FileTreePickerDialog("))
        assertFalse(drawer.contains("private fun DrawerMoveFolderDialog("))
        assertFalse(readSource("src/main/java/com/kangle/kardleaf/data/database/NoteDao.kt").contains("UPDATE OR REPLACE notes"))
        assertFalse(readSource("src/main/java/com/kangle/kardleaf/data/database/LabelDao.kt").contains("UPDATE OR REPLACE labels"))
    }

    private fun readSource(path: String): String {
        val candidates = listOf(Path.of(path), Path.of("app").resolve(path))
        return candidates.first(Files::exists).toFile().readText()
    }
}
