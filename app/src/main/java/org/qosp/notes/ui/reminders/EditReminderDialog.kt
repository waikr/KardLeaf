package org.qosp.notes.ui.reminders

import androidx.fragment.app.DialogFragment
import org.qosp.notes.data.model.Reminder

/**
 * Sandbox stub of Quillpad's [EditReminderDialog].
 *
 * The original edits a reminder via a ViewModel + AlarmManager. The sandbox has
 * no reminders, so this is an empty dialog that only needs to compile. It is
 * never shown in the sandbox.
 */
class EditReminderDialog : DialogFragment() {
    companion object {
        fun build(noteId: Long, reminder: Reminder?): EditReminderDialog = EditReminderDialog()
    }
}
