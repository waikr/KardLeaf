package org.qosp.notes.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.qosp.notes.data.model.Notebook

/**
 * Sandbox stub of Quillpad's [NotebookRepository].
 *
 * The sandbox has no notebooks, so lookups always resolve to null.
 */
class NotebookRepository {

    fun getById(id: Long?): Flow<Notebook?> = flowOf(null)
}
