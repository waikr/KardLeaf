package org.qosp.notes.ui.attachments.dialog

import androidx.fragment.app.DialogFragment

/**
 * Sandbox stub of Quillpad's [EditAttachmentDialog].
 *
 * The original edits an attachment description via a ViewModel + Room. The
 * sandbox has no attachments, so this is an empty dialog. It only needs to
 * compile; it is never shown in the sandbox.
 */
class EditAttachmentDialog : DialogFragment() {
    companion object {
        fun build(noteId: Long, attachmentPath: String): EditAttachmentDialog = EditAttachmentDialog()
    }
}
