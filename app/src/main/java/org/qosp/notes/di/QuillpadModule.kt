package org.qosp.notes.di

import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.image.ImagesPlugin
import com.kangle.kardleaf.data.repository.MetadataManager
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadActionBridge
import com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadEditorBridge
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.ActivityViewModel
import org.qosp.notes.ui.editor.EditorViewModel

/**
 * Koin module wiring the KardLeaf Quillpad editor.
 *
 * Provides:
 *  - [Markwon] / [MarkwonEditor] (injected by [org.qosp.notes.ui.editor.EditorFragment])
 *  - in-memory Quillpad session repositories
 *  - [EditorViewModel] and the activity-scoped [ActivityViewModel]
 */
val quillpadModule = module {

    single<Markwon> {
        Markwon.builder(androidContext())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(TablePlugin.create(androidContext()))
            .build()
    }

    single<MarkwonEditor> {
        MarkwonEditor.builder(get<Markwon>()).build()
    }

    single { NoteRepository() }
    single { NotebookRepository() }
    single { PreferenceRepository() }
    single { PrefsManager(androidContext()) }
    single { MetadataManager(androidContext()) }
    single { RoomNoteRepository(androidContext(), get(), get()) }
    single { KardLeafQuillpadEditorBridge(get(), get()) }
    single { KardLeafQuillpadActionBridge(get()) }

    viewModel { EditorViewModel(get(), get(), get()) }
    viewModel { ActivityViewModel(get(), get(), get()) }
}
