package org.qosp.notes.ui.attachments.recycler

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.data.model.Attachment

/**
 * Sandbox stub of Quillpad's [AttachmentsAdapter].
 *
 * The original extends `ExtendedListAdapter` and binds attachments via Coil.
 * The sandbox has no attachments, so this keeps an inert backing list with the
 * same public surface used by
 * [org.qosp.notes.ui.editor.EditorFragment] (`submitList`,
 * `getItemAtPosition`, `listener`).
 */
class AttachmentsAdapter(
    var listener: AttachmentRecyclerListener? = null,
) : RecyclerView.Adapter<AttachmentViewHolder>() {

    private val items = mutableListOf<Attachment>()

    fun submitList(list: List<Attachment>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getItemAtPosition(position: Int): Attachment = items[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        // Never populated in the sandbox; a plain TextView is sufficient.
        return AttachmentViewHolder(TextView(parent.context))
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {}

    override fun getItemCount(): Int = items.size
}

class AttachmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
