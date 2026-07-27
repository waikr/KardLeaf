package org.qosp.notes.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.KardLeafCustomFeatures
import com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadActionBridge
import com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadEditorBridge
import com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadFeature
import com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadFeatureHost
import com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadFeatureResult
import com.kangle.kardleaf.ui.theme.KardLeafTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get
import org.koin.core.logger.Level
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.di.quillpadModule
import org.qosp.notes.ui.editor.EditorFragment

/**
 * Host for KardLeaf's retained Quillpad editor. The legacy class name is kept
 * to avoid a migration-only manifest and call-site rename.
 *
 * It hosts a [NavHostFragment] with [R.navigation.nav_graph_sandbox], whose
 * start destination is [org.qosp.notes.ui.editor.EditorFragment]. The bridge
 * loads KardLeaf's external Markdown file before supplying the original
 * navigation arguments, then saves through KardLeaf's existing repository.
 *
 * The host intentionally stays minimal so the Quillpad input, cursor and
 * scrolling behavior remain intact.
 *
 * Back handling: the original EditorFragment registers its own OnBackPressed
 * callback (navigateUp). Here EditorFragment is the start destination,
 * so navigateUp() would return false and consume back without exiting. To let
 * the user leave the editor, this activity registers a save-and-finish
 * callback after the fragment's, so it takes priority.
 */
class QuillpadSandboxActivity : AppCompatActivity(), QuillpadEditorHost {

    private data class AfterSaveCallback(
        val onSaved: () -> Unit,
        val onFailure: () -> Unit,
    )

    private var backCallbackRegistered = false
    private var saveInProgress = false
    private var featureOpening = false
    private var featureCompleting = false
    private var featureHostContentInitialized = false
    private var pendingDrawingSelection = 0 to 0
    private var activeFeature by mutableStateOf<KardLeafQuillpadFeature?>(null)
    private val afterSaveCallbacks = mutableListOf<AfterSaveCallback>()
    private lateinit var bridge: KardLeafQuillpadEditorBridge
    private lateinit var actionBridge: KardLeafQuillpadActionBridge
    private lateinit var editorRepository: NoteRepository
    private lateinit var featureHost: ComposeView
    private lateinit var loadingOverlay: View
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            persistIfNeeded(onSaved = { finish() })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val createStart = SystemClock.uptimeMillis()
        KardLeafLog.d(
            "KardLeafQuillpadPerf",
            "host onCreate start saved=${savedInstanceState != null}",
        )
        ensureQuillpadKoinStarted(this)
        KardLeafLog.d(
            "KardLeafQuillpadPerf",
            "host ensureKoin elapsed=${SystemClock.uptimeMillis() - createStart}ms",
        )
        super.onCreate(savedInstanceState)
        val afterSuper = SystemClock.uptimeMillis()
        setContentView(R.layout.activity_quillpad_sandbox)
        KardLeafLog.d(
            "KardLeafQuillpadPerf",
            "host setContentView elapsed=${SystemClock.uptimeMillis() - afterSuper}ms total=${SystemClock.uptimeMillis() - createStart}ms",
        )
        bridge = GlobalContext.get().get()
        actionBridge = GlobalContext.get().get()
        editorRepository = GlobalContext.get().get()
        featureHost = findViewById<ComposeView>(R.id.quillpad_feature_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        loadingOverlay = findViewById(R.id.quillpad_loading_overlay)
        KardLeafLog.d(
            "KardLeafQuillpadPerf",
            "host dependencies ready total=${SystemClock.uptimeMillis() - createStart}ms composeDeferred=true",
        )

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val openStart = SystemClock.uptimeMillis()
                val note = runCatching {
                    bridge.open(
                        notePath = intent.getStringExtra(EXTRA_NOTE_PATH).orEmpty(),
                        initialTitle = intent.getStringExtra(EXTRA_INITIAL_TITLE),
                        initialContent = intent.getStringExtra(EXTRA_INITIAL_CONTENT),
                        folder = intent.getStringExtra(EXTRA_NEW_NOTE_FOLDER).orEmpty(),
                        isPinned = intent.getBooleanExtra(EXTRA_NEW_NOTE_PINNED, false),
                    )
                }.getOrElse { error ->
                    KardLeafLog.e("KardLeafQuillpad", "open failed", error)
                    finish()
                    return@launch
                }
                val defaultOpenMode = KardLeafCustomFeatures.getOpenNoteMode(this@QuillpadSandboxActivity)
                val startInEditMode = defaultOpenMode == KardLeafCustomFeatures.OpenNoteMode.EDIT
                KardLeafLog.d(
                    "KardLeafQuillpad",
                    "host defaultOpenMode=$defaultOpenMode startInEditMode=$startInEditMode contentLen=${note.content.length}",
                )
                val startArgs = bundleOf(
                    "newNoteTitle" to note.title,
                    "newNoteContent" to note.content,
                    ARG_START_IN_EDIT_MODE to startInEditMode,
                )
                KardLeafLog.d(
                    "KardLeafQuillpadPerf",
                    "host bridge open elapsed=${SystemClock.uptimeMillis() - openStart}ms contentLen=${note.content.length}",
                )
                val navCreateStart = SystemClock.uptimeMillis()
                val navHost = NavHostFragment.create(R.navigation.nav_graph_sandbox, startArgs)
                KardLeafLog.d(
                    "KardLeafQuillpadPerf",
                    "host navHost create elapsed=${SystemClock.uptimeMillis() - navCreateStart}ms",
                )
                val commitStart = SystemClock.uptimeMillis()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, navHost)
                    .setPrimaryNavigationFragment(navHost)
                    .commitNowAllowingStateLoss()
                KardLeafLog.d(
                    "KardLeafQuillpadPerf",
                    "host fragment commit elapsed=${SystemClock.uptimeMillis() - commitStart}ms " +
                        "totalFromOpen=${SystemClock.uptimeMillis() - openStart}ms",
                )
                installBackCallback()
            }
        } else {
            lifecycleScope.launch {
                runCatching {
                    bridge.open(
                        notePath = intent.getStringExtra(EXTRA_NOTE_PATH).orEmpty(),
                        initialTitle = intent.getStringExtra(EXTRA_INITIAL_TITLE),
                        initialContent = intent.getStringExtra(EXTRA_INITIAL_CONTENT),
                        folder = intent.getStringExtra(EXTRA_NEW_NOTE_FOLDER).orEmpty(),
                        isPinned = intent.getBooleanExtra(EXTRA_NEW_NOTE_PINNED, false),
                    )
                }.onFailure {
                    KardLeafLog.e("KardLeafQuillpad", "restore bridge failed", it)
                    finish()
                }
            }
        }
    }

    override suspend fun resolvePreviewMarkdown(markdown: String): String =
        bridge.resolvePreviewMarkdown(markdown)

    override fun onEditorContentReady(source: String) {
        if (!::loadingOverlay.isInitialized || loadingOverlay.visibility == View.GONE) return
        loadingOverlay.visibility = View.GONE
        KardLeafLog.d("KardLeafQuillpadPerf", "host loading overlay hidden source=$source")
    }

    override fun onPostResume() {
        super.onPostResume()
        if (savedStateHasEditor()) installBackCallback()
    }

    override fun onStop() {
        persistIfNeeded()
        super.onStop()
    }

    private fun persistIfNeeded(
        onSaved: (() -> Unit)? = null,
        onFailure: () -> Unit = {},
    ) {
        onSaved?.let { afterSaveCallbacks.add(AfterSaveCallback(it, onFailure)) }
        if (saveInProgress) return

        val note = editorRepository.currentNote()
        if (note == null || !bridge.needsSave(editorRepository.isDirty())) {
            val callbacks = afterSaveCallbacks.toList()
            afterSaveCallbacks.clear()
            callbacks.forEach { it.onSaved() }
            return
        }

        val revision = editorRepository.revision()
        saveInProgress = true
        lifecycleScope.launch {
            val saved = runCatching { bridge.save(note) }
                .onFailure { KardLeafLog.e("KardLeafQuillpad", "save failed", it) }
                .getOrDefault(false)
            saveInProgress = false
            if (saved) {
                editorRepository.markSaved(revision)
                persistIfNeeded()
            } else {
                val callbacks = afterSaveCallbacks.toList()
                afterSaveCallbacks.clear()
                callbacks.forEach { it.onFailure() }
                KardLeafLog.e("KardLeafQuillpad", "save/exit cancelled because save failed")
                Toast.makeText(this@QuillpadSandboxActivity, "保存失败，未退出编辑器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun openTags() = openFeature { KardLeafQuillpadFeature.Tags(it) }

    override fun openHistory() = openFeature { KardLeafQuillpadFeature.History(it) }

    override fun openRemarks() = openFeature { KardLeafQuillpadFeature.Remarks(it) }

    override fun openDrawing(selection: Pair<Int, Int>, reference: String?) {
        pendingDrawingSelection = selection
        openFeature { snapshot ->
            val source = reference?.let { actionBridge.loadDrawingSource(snapshot, it) }
            if (reference != null && source == null && reference.substringAfterLast('/').startsWith("drawing_")) {
                Toast.makeText(this, "这张绘图没有可编辑数据", Toast.LENGTH_SHORT).show()
                null
            } else {
                KardLeafQuillpadFeature.Drawing(
                    snapshot = snapshot,
                    reference = reference.takeIf { source != null },
                    source = source,
                )
            }
        }
    }

    private fun openFeature(
        create: suspend (com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadFeatureSnapshot) -> KardLeafQuillpadFeature?,
    ) {
        if (featureOpening || activeFeature != null) return
        featureOpening = true
        persistIfNeeded(
            onSaved = {
                lifecycleScope.launch {
                    val feature = runCatching {
                        actionBridge.featureSnapshot()?.let { create(it) }
                    }.onFailure { KardLeafLog.e("KardLeafQuillpad", "feature open failed", it) }
                        .getOrNull()
                    featureOpening = false
                    if (feature != null) {
                        activeFeature = feature
                        ensureFeatureHostContent()
                        featureHost.visibility = View.VISIBLE
                    }
                }
            },
            onFailure = { featureOpening = false },
        )
    }

    private fun ensureFeatureHostContent() {
        if (featureHostContentInitialized) return
        val start = SystemClock.uptimeMillis()
        featureHostContentInitialized = true
        featureHost.setContent {
            KardLeafTheme {
                activeFeature?.let { feature ->
                    KardLeafQuillpadFeatureHost(feature, actionBridge, ::handleFeatureResult)
                }
            }
        }
        KardLeafLog.d(
            "KardLeafQuillpadPerf",
            "feature Compose content initialized elapsed=${SystemClock.uptimeMillis() - start}ms",
        )
    }

    private fun handleFeatureResult(result: KardLeafQuillpadFeatureResult) {
        if (featureCompleting) return
        when (result) {
            KardLeafQuillpadFeatureResult.Close -> hideFeature()
            KardLeafQuillpadFeatureResult.Reload -> reloadAndHideFeature()
            is KardLeafQuillpadFeatureResult.InsertMarkdown -> {
                val editor = currentEditorFragment()
                if (editor != null) {
                    editor.insertMarkdownAt(pendingDrawingSelection, result.markdown)
                } else {
                    editorRepository.update { note ->
                        val start = pendingDrawingSelection.first.coerceIn(0, note.content.length)
                        val end = pendingDrawingSelection.second.coerceIn(start, note.content.length)
                        note.copy(content = note.content.replaceRange(start, end, result.markdown))
                    }
                }
                hideFeature()
            }
        }
    }

    private fun reloadAndHideFeature() {
        featureCompleting = true
        lifecycleScope.launch {
            val note = runCatching { actionBridge.reloadCurrent() }
                .onFailure { KardLeafLog.e("KardLeafQuillpad", "feature reload failed", it) }
                .getOrNull()
            if (note == null) {
                featureCompleting = false
                Toast.makeText(this@QuillpadSandboxActivity, "刷新笔记失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            editorRepository.replace(note)
            hideFeature()
        }
    }

    private fun hideFeature() {
        activeFeature = null
        featureHost.visibility = View.GONE
        featureCompleting = false
    }

    private fun currentEditorFragment(): EditorFragment? {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        return navHost?.childFragmentManager?.primaryNavigationFragment as? EditorFragment
    }

    private fun installBackCallback() {
        if (backCallbackRegistered) backCallback.remove()
        onBackPressedDispatcher.addCallback(this, backCallback)
        backCallbackRegistered = true
    }

    private fun savedStateHasEditor(): Boolean =
        supportFragmentManager.findFragmentById(R.id.nav_host_fragment) != null

    companion object {
        private fun ensureQuillpadKoinStarted(context: Context) {
            if (GlobalContext.getOrNull() != null) return
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(context.applicationContext)
                modules(quillpadModule)
            }
        }

        fun createIntent(
            context: Context,
            notePath: String,
            initialTitle: String? = null,
            initialContent: String? = null,
        ): Intent = Intent(context, QuillpadSandboxActivity::class.java).apply {
            putExtra(EXTRA_NOTE_PATH, notePath)
            putExtra(EXTRA_INITIAL_TITLE, initialTitle)
            putExtra(EXTRA_INITIAL_CONTENT, initialContent)
        }

        fun createNewNoteIntent(
            context: Context,
            initialTitle: String = "",
            initialContent: String = "",
            folder: String = "",
            isPinned: Boolean = false,
        ): Intent = Intent(context, QuillpadSandboxActivity::class.java).apply {
            putExtra(EXTRA_INITIAL_TITLE, initialTitle)
            putExtra(EXTRA_INITIAL_CONTENT, initialContent)
            putExtra(EXTRA_NEW_NOTE_FOLDER, folder)
            putExtra(EXTRA_NEW_NOTE_PINNED, isPinned)
        }

        internal const val ARG_START_IN_EDIT_MODE = "kardleaf_quillpad_start_in_edit_mode"
        private const val EXTRA_NOTE_PATH = "kardleaf_quillpad_note_path"
        private const val EXTRA_INITIAL_TITLE = "kardleaf_quillpad_initial_title"
        private const val EXTRA_INITIAL_CONTENT = "kardleaf_quillpad_initial_content"
        private const val EXTRA_NEW_NOTE_FOLDER = "kardleaf_quillpad_new_note_folder"
        private const val EXTRA_NEW_NOTE_PINNED = "kardleaf_quillpad_new_note_pinned"
    }
}
