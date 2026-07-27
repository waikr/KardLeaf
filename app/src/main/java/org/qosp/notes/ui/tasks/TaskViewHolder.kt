package org.qosp.notes.ui.tasks

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.data.model.NoteTask

/**
 * Sandbox stub of Quillpad's [TaskViewHolder].
 *
 * The original binds a task row (checkbox / edit text / drag handle) with
 * Markwon + animated colours. The sandbox uses text notes (isList = false), so
 * task view holders are never created. This stub exposes the same mutable
 * properties referenced by [org.qosp.notes.ui.editor.EditorFragment]
 * (`taskBackgroundColor`, `isBeingMoved`, `isEnabled`, `requestFocus`).
 */
class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var taskBackgroundColor: Int = 0
    var isBeingMoved: Boolean = false
    var isEnabled: Boolean = true

    fun requestFocus() {}

    fun bind(task: NoteTask) {}
}
