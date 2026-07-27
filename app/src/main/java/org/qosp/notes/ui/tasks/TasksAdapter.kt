package org.qosp.notes.ui.tasks

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.qosp.notes.data.model.NoteTask
import java.util.Collections

/**
 * Sandbox stub of Quillpad's [TasksAdapter].
 *
 * The original binds task rows via [TaskViewHolder]. The sandbox uses text
 * notes, so this adapter is never populated; it keeps the same public surface
 * used by [org.qosp.notes.ui.editor.EditorFragment] (`tasks`, `submitList`,
 * `moveItem`, `setFontSize`, `listener`).
 */
class TasksAdapter(
    private val inPreview: Boolean,
    var listener: TaskRecyclerListener?,
    private val markwon: Markwon,
) : RecyclerView.Adapter<TaskViewHolder>() {

    var tasks: MutableList<NoteTask> = mutableListOf()

    override fun getItemCount(): Int = tasks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        // Never populated in the sandbox; a plain TextView is sufficient.
        return TaskViewHolder(TextView(parent.context))
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {}

    fun moveItem(fromPos: Int, toPos: Int) {
        Collections.swap(tasks, fromPos, toPos)
        notifyItemMoved(fromPos, toPos)
    }

    fun submitList(list: List<NoteTask>?, useDiff: Boolean = true) {
        if (list != null) {
            tasks = list.toMutableList()
            notifyDataSetChanged()
        }
    }

    fun setFontSize(fs: Float) {}
}
