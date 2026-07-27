package org.qosp.notes.ui.recorder

import androidx.fragment.app.DialogFragment

/** Sandbox fragment-result key used by the editor to receive a recorded attachment. */
const val RECORD_CODE = "RECORD_CODE"

/** Bundle key for the recorded attachment parcelable. */
const val RECORDED_ATTACHMENT = "RECORDED_ATTACHMENT"

/**
 * Sandbox stub of Quillpad's [RecordAudioDialog].
 *
 * The original records audio via a foreground service. The sandbox does not
 * record; this empty dialog only needs to compile and is never shown (the
 * record-audio menu item is not surfaced in the sandbox).
 */
class RecordAudioDialog : DialogFragment()
