package com.kangle.kardleaf.ui.editor.quillpad

import com.kangle.kardleaf.data.model.Note as KardLeafNote
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.data.utils.KardLeafLog
import java.io.File
import java.util.Date
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.qosp.notes.data.model.Note as QuillpadNote
import org.qosp.notes.data.model.Tag as QuillpadTag

/** Keeps Quillpad UI state separate from KardLeaf's file-backed note model. */
class KardLeafQuillpadEditorBridge(
    private val prefsManager: PrefsManager,
    internal val repository: RoomNoteRepository,
) {
    // ponytail: one editor session is active; serialize storage changes if lifecycle and toolbar actions overlap.
    private val storageMutex = Mutex()

    private var source: KardLeafNote? = null
    private var isNewNote = false
    private var hasPendingInitialChanges = false

    suspend fun open(
        notePath: String,
        initialTitle: String?,
        initialContent: String?,
        folder: String,
        isPinned: Boolean,
    ): QuillpadNote {
        val rootUri = prefsManager.getRootUri()?.takeIf { it.isNotBlank() }
            ?: error("KardLeaf root folder is not configured")
        repository.setRootFolder(rootUri, scanImmediately = false)

        val loaded = if (notePath.isBlank()) null else repository.getNoteForEditor(notePath)
        check(notePath.isBlank() || loaded != null) { "KardLeaf note not found: $notePath" }

        isNewNote = loaded == null
        source = loaded
            ?.copy(
                title = initialTitle ?: loaded.title,
                content = initialContent ?: loaded.content,
            )
            ?: KardLeafNote(
                file = File(folder, "Untitled.md"),
                title = initialTitle.orEmpty(),
                content = initialContent.orEmpty(),
                lastModified = Date(),
                color = 0xFFFFFFFF,
                isPinned = isPinned,
            )
        hasPendingInitialChanges = loaded != null &&
            (source?.title != loaded.title || source?.content != loaded.content)
        val note = requireNotNull(source)
        KardLeafLog.d(
            "KardLeafQuillpad",
            "open path=${notePath.ifBlank { "<new>" }} contentLen=${note.content.length} new=$isNewNote",
        )
        return note.toQuillpadNote()
    }

    fun needsSave(isDirty: Boolean): Boolean = isNewNote || hasPendingInitialChanges || isDirty

    suspend fun resolvePreviewMarkdown(markdown: String): String {
        val current = source ?: return markdown
        return repository.resolveMarkdownImages(markdown, current.folder)
    }

    suspend fun save(note: QuillpadNote): Boolean = withStorageLock {
        val current = source ?: return@withStorageLock false
        val input = current.copy(
            title = note.title,
            content = note.content,
            lastModified = Date(),
        )
        KardLeafLog.d(
            "KardLeafQuillpad",
            "save start oldPath=${current.file.path} contentLen=${input.content.length} new=$isNewNote",
        )
        val savedPath = repository.saveNote(
            note = input,
            oldFile = current.file.takeUnless { isNewNote },
            saveHistory = !isNewNote,
        )
        if (savedPath.isBlank()) {
            KardLeafLog.e("KardLeafQuillpad", "save failed oldPath=${current.file.path}")
            return@withStorageLock false
        }

        source = repository.getNoteForEditor(savedPath) ?: input.copy(
            file = File(savedPath),
            title = File(savedPath).nameWithoutExtension,
        )
        isNewNote = false
        hasPendingInitialChanges = false
        KardLeafLog.d(
            "KardLeafQuillpad",
            "save success targetPath=$savedPath contentLen=${input.content.length} roomCacheUpdated=true",
        )
        return@withStorageLock true
    }

    internal fun currentSource(): KardLeafNote? = source

    internal suspend fun <T> withStorageLock(action: suspend () -> T): T = storageMutex.withLock { action() }

    internal suspend fun reloadSource(): QuillpadNote? {
        val current = source ?: return null
        source = repository.getNoteForEditor(current.file.path) ?: return null
        return source?.toQuillpadNote()
    }

    internal fun clearSource() {
        source = null
    }

    private fun KardLeafNote.toQuillpadNote(): QuillpadNote = QuillpadNote(
        title = title,
        content = content,
        isPinned = isPinned,
        isArchived = isArchived,
        isDeleted = isTrashed,
        creationDate = createdAt.time / 1_000,
        modifiedDate = lastModified.time / 1_000,
        tags = tags.map { QuillpadTag(it) },
    )
}
