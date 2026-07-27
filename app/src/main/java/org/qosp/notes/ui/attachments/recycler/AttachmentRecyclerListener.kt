package org.qosp.notes.ui.attachments.recycler

import com.kangle.kardleaf.databinding.LayoutAttachmentBinding

interface AttachmentRecyclerListener {
    fun onItemClick(position: Int, viewBinding: LayoutAttachmentBinding)
    fun onLongClick(position: Int, viewBinding: LayoutAttachmentBinding): Boolean
}
