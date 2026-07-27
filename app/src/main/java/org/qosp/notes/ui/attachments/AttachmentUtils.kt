package org.qosp.notes.ui.attachments

import android.content.Context
import android.net.Uri
import org.qosp.notes.data.model.Attachment

/**
 * Sandbox stubs for Quillpad attachment helpers.
 *
 * The original uses Coil / FileProvider / MediaMetadataRetriever. The sandbox
 * has no attachments, so [fromUri] builds a minimal [Attachment] from the uri
 * string and [uri] resolves to null. These only need to compile; they are never
 * exercised in the sandbox (no attachment picker / no attachments rendered).
 */
fun Attachment.Companion.fromUri(context: Context, uri: Uri): Attachment = Attachment(path = uri.toString())

fun Attachment.uri(context: Context): Uri? = null
