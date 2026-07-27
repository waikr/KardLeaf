package org.qosp.notes.ui.editor

import android.app.AlarmManager
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.clearFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.LEFT
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialSharedAxis
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.KardLeafCustomFeatures
import com.kangle.kardleaf.ui.editor.NoteSearchMatchRange
import com.kangle.kardleaf.ui.editor.buildCurrentReplacement
import com.kangle.kardleaf.ui.editor.buildNoteSearchMatches
import com.kangle.kardleaf.ui.extractMarkdownImageReferences
import com.kangle.kardleaf.ui.editor.replaceAllNoteSearchMatches
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.commonmark.node.Code
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.kangle.kardleaf.R
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteColor
import org.qosp.notes.data.model.NoteTask
import com.kangle.kardleaf.databinding.FragmentEditorBinding
import com.kangle.kardleaf.databinding.LayoutAttachmentBinding
import org.qosp.notes.preferences.DefaultEditorMode
import org.qosp.notes.ui.attachments.dialog.EditAttachmentDialog
import org.qosp.notes.ui.attachments.fromUri
import org.qosp.notes.ui.attachments.recycler.AttachmentRecyclerListener
import org.qosp.notes.ui.attachments.recycler.AttachmentsAdapter
import org.qosp.notes.ui.attachments.recycler.AttachmentsGridManager
import org.qosp.notes.ui.attachments.uri
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.common.showMoveToNotebookDialog
import org.qosp.notes.ui.QuillpadSandboxActivity
import org.qosp.notes.ui.findQuillpadEditorHost
import org.qosp.notes.ui.editor.dialog.InsertHyperlinkDialog
import org.qosp.notes.ui.editor.dialog.InsertTableDialog
import org.qosp.notes.ui.editor.markdown.MarkdownSpan
import org.qosp.notes.ui.editor.markdown.applyTo
import org.qosp.notes.ui.editor.markdown.indentCurrentLine
import org.qosp.notes.ui.editor.markdown.insertCodeBlock
import org.qosp.notes.ui.editor.markdown.insertDivider
import org.qosp.notes.ui.editor.markdown.insertMath
import org.qosp.notes.ui.editor.markdown.insertMarkdown
import org.qosp.notes.ui.editor.markdown.insertUnderline
import org.qosp.notes.ui.editor.markdown.outdentCurrentLine
import org.qosp.notes.ui.editor.markdown.setCheckmarkCurrentLine
import org.qosp.notes.ui.editor.markdown.setHeadingLevel
import org.qosp.notes.ui.editor.markdown.toggleBulletCurrentLine
import org.qosp.notes.ui.editor.markdown.toggleChecklistCurrentLine
import org.qosp.notes.ui.editor.markdown.toggleOrderedCurrentLine
import org.qosp.notes.ui.media.MediaActivity
import org.qosp.notes.ui.recorder.RECORDED_ATTACHMENT
import org.qosp.notes.ui.recorder.RECORD_CODE
import org.qosp.notes.ui.recorder.RecordAudioDialog
import org.qosp.notes.ui.reminders.EditReminderDialog
import org.qosp.notes.ui.tasks.TaskRecyclerListener
import org.qosp.notes.ui.tasks.TaskViewHolder
import org.qosp.notes.ui.tasks.TasksAdapter
import org.qosp.notes.ui.utils.ChooseFilesContract
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.dp
import org.qosp.notes.ui.utils.getDimensionAttribute
import org.qosp.notes.ui.utils.getDrawableCompat
import org.qosp.notes.ui.utils.hideKeyboard
import org.qosp.notes.ui.utils.liftAppBarOnScroll
import org.qosp.notes.ui.utils.requestFocusAndKeyboard
import org.qosp.notes.ui.utils.resId
import org.qosp.notes.ui.utils.resolveAttribute
import org.qosp.notes.ui.utils.shareAttachment
import org.qosp.notes.ui.utils.shareNote
import org.qosp.notes.ui.utils.viewBinding
import org.qosp.notes.ui.utils.views.BottomSheet
import org.qosp.notes.ui.utils.views.OperationType
import org.qosp.notes.ui.widget.WidgetUpdateHelper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

private typealias Data = EditorViewModel.Data

private data class ToolbarMenuItemState(
    val id: Int,
    val title: CharSequence,
    val icon: Drawable?,
    val isEnabled: Boolean,
    val isVisible: Boolean,
)

private const val QUILLPAD_LIVE_MARKDOWN_MAX_CHARS = 5_000
private const val QUILLPAD_MARKDOWN_PREVIEW_MAX_CHARS = 50_000
private const val QUILLPAD_LARGE_PREVIEW_VISIBLE_CHARS = 20_000
private const val QUILLPAD_PERF_TAG = "KardLeafQuillpadPerf"
private val QUILLPAD_MARKDOWN_EXECUTOR = Executors.newSingleThreadExecutor()

private fun quillpadMemorySnapshot(): String {
    val runtime = Runtime.getRuntime()
    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val totalMb = runtime.totalMemory() / (1024 * 1024)
    val maxMb = runtime.maxMemory() / (1024 * 1024)
    return "mem=${usedMb}MB/${totalMb}MB max=${maxMb}MB"
}

internal fun shouldSuppressQuillpadLiveMarkdown(contentLength: Int): Boolean =
    contentLength > QUILLPAD_LIVE_MARKDOWN_MAX_CHARS

internal fun shouldUseQuillpadLargePlainPreview(contentLength: Int): Boolean =
    contentLength > QUILLPAD_MARKDOWN_PREVIEW_MAX_CHARS

internal fun buildQuillpadLargePlainPreview(content: String): String = buildString {
    append(content.take(QUILLPAD_LARGE_PREVIEW_VISIBLE_CHARS))
    if (content.length > QUILLPAD_LARGE_PREVIEW_VISIBLE_CHARS) {
        append("\n\n[大文本预览已截断，请切换到编辑模式查看完整内容]")
    }
}

internal fun <T> visibleQuillpadToolbarItems(
    configured: List<T>,
    defaults: List<T>,
    hidden: Set<T>,
): List<T> = (configured + defaults).distinct().filterNot(hidden::contains)

internal fun shouldReuseQuillpadToolbarOrder(currentIds: List<Int>, desiredIds: List<Int>): Boolean =
    currentIds == desiredIds

class EditorFragment : BaseFragment(R.layout.fragment_editor) {
    private val binding by viewBinding(FragmentEditorBinding::bind)
    private val model: EditorViewModel by viewModel()
    private val prefsManager: PrefsManager by inject()

    private val args: EditorFragmentArgs by navArgs()
    private var snackbar: Snackbar? = null
    private var mainMenu: Menu? = null
    private var contentHasFocus: Boolean = false
    private var isNoteDeleted: Boolean = false
    private var markwonTextWatcher: TextWatcher? = null
    private var onBackPressHandled: Boolean = false
    private var pendingImageSelection = 0 to 0
    private var searchMatches = emptyList<NoteSearchMatchRange>()
    private var currentSearchIndex = -1
    private var lastRenderedPreview: Pair<Boolean, String>? = null
    private var suppressLiveMarkdownForLargeNote = false
    private var fragmentCreatedAt = 0L
    private var editorContentLoadStarted = false
    private var editorContentLoaded = false

    @ColorInt
    private var backgroundColor: Int = Color.TRANSPARENT
    private var data = Data()

    private var nextTaskId: Long = 0L
    private var isList: Boolean = false
    private var isFirstLoad: Boolean = true
    private var formatter: DateTimeFormatter? = null

    private lateinit var attachmentsAdapter: AttachmentsAdapter
    private lateinit var tasksAdapter: TasksAdapter

    val markwon: Markwon by inject()

    val markwonEditor: MarkwonEditor by inject()

    override val hasDefaultAnimation = false
    override val toolbar: Toolbar
        get() = binding.toolbar

    private val requestMediaLauncher = registerForActivityResult(ChooseFilesContract) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult

        val attachments = uris.map {
            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Attachment.fromUri(requireContext(), it)
        }

        model.insertAttachments(*attachments.toTypedArray())
    }

    private val importImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val markdown = runCatching { activityModel.importImage(uri) }
                .onFailure { Log.e(TAG, "KardLeaf image import failed", it) }
                .getOrDefault("")
            if (markdown.isBlank()) {
                Toast.makeText(requireContext(), "图片导入失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            with(binding.editTextContent) {
                val start = pendingImageSelection.first.coerceIn(0, length())
                val end = pendingImageSelection.second.coerceIn(start, length())
                editHistory(OperationType.TOOLBAR) {
                    text?.replace(start, end, markdown)
                    setSelection(start + markdown.length)
                }
            }
        }
    }

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(UP or DOWN, LEFT or RIGHT) {
        override fun isLongPressDragEnabled() = false

        override fun isItemViewSwipeEnabled() = model.inEditMode

        override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.5F

        override fun getSwipeEscapeVelocity(defaultValue: Float) = 3 * defaultValue

        override fun getSwipeVelocityThreshold(defaultValue: Float) = defaultValue / 3

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            tasksAdapter.tasks.removeAt(viewHolder.bindingAdapterPosition)
            model.updateTaskList(tasksAdapter.tasks)
            tasksAdapter.notifyItemRemoved(viewHolder.bindingAdapterPosition)
            tasksAdapter.notifyItemRangeChanged(viewHolder.bindingAdapterPosition, tasksAdapter.tasks.size - 1)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            tasksAdapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean,
        ) {
            when (actionState) {
                ACTION_STATE_DRAG -> {
                    val top = viewHolder.itemView.top + dY
                    val bottom = top + viewHolder.itemView.height
                    if (top > 0 && bottom < recyclerView.height) {
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    }
                }

                ACTION_STATE_SWIPE -> {
                    val newDx = dX / 3
                    val p = Paint().apply { color = context?.resolveAttribute(R.attr.colorTaskSwipe) ?: Color.RED }
                    val itemView = viewHolder.itemView
                    val icon = context?.getDrawableCompat(R.drawable.ic_indicator_delete_task)?.toBitmap()
                    val height = itemView.bottom - itemView.top
                    val size = (24).dp(requireContext())

                    if (dX < 0) {
                        val background = RectF(
                            itemView.right.toFloat() + newDx,
                            itemView.top.toFloat(),
                            itemView.right.toFloat(),
                            itemView.bottom.toFloat()
                        )
                        c.drawRect(background, p)

                        val iconRect = RectF(
                            background.right - size - 16.dp(requireContext()),
                            background.top + (height - size) / 2,
                            background.right - 16.dp(requireContext()),
                            background.bottom - (height - size) / 2,
                        )
                        if (icon != null) c.drawBitmap(icon, null, iconRect, p)
                    } else if (dX > 0) {
                        val background = RectF(
                            itemView.left.toFloat(),
                            itemView.top.toFloat(),
                            newDx,
                            itemView.bottom.toFloat()
                        )
                        c.drawRect(background, p)
                        val iconRect = RectF(
                            background.left + 16.dp(requireContext()),
                            background.top + (height - size) / 2,
                            background.left + size + 16.dp(requireContext()),
                            background.bottom - (height - size) / 2,
                        )
                        if (icon != null) c.drawBitmap(icon, null, iconRect, p)
                    }
                    return super.onChildDraw(c, recyclerView, viewHolder, newDx, dY, actionState, isCurrentlyActive)
                }
            }
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)

            (viewHolder as TaskViewHolder?)?.let { vh ->
                vh.taskBackgroundColor = backgroundColor
                vh.isBeingMoved = true
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            (viewHolder as TaskViewHolder?)?.let {
                if (it.isBeingMoved) it.isBeingMoved = false
            }
            model.updateTaskList(tasksAdapter.tasks)
        }
    })

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater =
        super.onGetLayoutInflater(savedInstanceState).cloneInContext(
            ContextThemeWrapper(requireContext(), R.style.Theme_KardLeaf_QuillpadEditor),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (findQuillpadEditorHost() != null) {
            Log.d(QUILLPAD_PERF_TAG, "fragment standalone host skips unused shared-element transition")
            return
        }
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = R.id.nav_host_fragment
            duration = 300L
            scrimColor = Color.TRANSPARENT

            ContextThemeWrapper(requireContext(), R.style.Theme_KardLeaf_QuillpadEditor)
                .resolveAttribute(R.attr.colorBackground)?.let { setAllContainerColors(it) }
        }
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true).apply { duration = 300L }

        postponeEnterTransition()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fragmentCreatedAt = SystemClock.uptimeMillis()
        Log.d(
            QUILLPAD_PERF_TAG,
            "fragment onViewCreated before super noteId=${args.noteId} saved=${savedInstanceState != null} ${quillpadMemorySnapshot()}",
        )
        val baseSetupStart = SystemClock.uptimeMillis()
        super.onViewCreated(view, savedInstanceState)
        Log.d(
            QUILLPAD_PERF_TAG,
            "fragment base onViewCreated elapsed=${SystemClock.uptimeMillis() - baseSetupStart}ms",
        )

        Log.d(
            QUILLPAD_PERF_TAG,
            "fragment onViewCreated start noteId=${args.noteId} saved=${savedInstanceState != null} ${quillpadMemorySnapshot()}",
        )
        view.doOnPreDraw {
            Log.d(
                QUILLPAD_PERF_TAG,
                "fragment root firstPreDraw elapsed=${SystemClock.uptimeMillis() - fragmentCreatedAt}ms " +
                    "root=${view.width}x${view.height} ${quillpadMemorySnapshot()}",
            )
        }

        data = Data()
        isFirstLoad = true
        lastRenderedPreview = null
        suppressLiveMarkdownForLargeNote = false
        editorContentLoadStarted = false
        editorContentLoaded = false

        if (model.isNotInitialized) {
            model.initialize(
                noteId = args.noteId,
                newNoteTitle = args.newNoteTitle,
                newNoteContent = args.newNoteContent,
                newNoteAttachments = args.newNoteAttachments?.toList() ?: emptyList(),
                newNoteIsList = args.newNoteIsList,
                newNoteNotebookId = args.newNoteNotebookId.takeIf { it > 0L }
            )
        }

        traceQuillpadSetup("attachmentsRecycler") { setupAttachmentsRecycler() }
        traceQuillpadSetup("tasksRecycler") { setupTasksRecycler() }
        traceQuillpadSetup("observeData") { observeData() }
        traceQuillpadSetup("editTexts") { setupEditTexts() }
        traceQuillpadSetup("search") { setupSearch() }
        traceQuillpadSetup("markdownWatcher") { setupMarkdown() }
        traceQuillpadSetup("listenersAndToolbarOrder") { setupListeners() }

        toolbar.setTitleTextColor(Color.TRANSPARENT)
        binding.notebookView.isVisible = false
        ViewCompat.setTransitionName(binding.root, args.transitionName)
        binding.scrollView.liftAppBarOnScroll(
            binding.layoutAppBar,
            requireContext().resources.getDimension(R.dimen.app_bar_elevation)
        )

        setFragmentResultListener(RECORD_CODE) { _, bundle ->
            val attachment = bundle.getParcelable<Attachment>(RECORDED_ATTACHMENT) ?: return@setFragmentResultListener
            model.insertAttachments(attachment)
        }

        setFragmentResultListener(MARKDOWN_DIALOG_RESULT) { _, bundle ->
            val markdown = bundle.getString(MARKDOWN_DIALOG_RESULT) ?: return@setFragmentResultListener
            binding.editTextContent.apply {
                editHistory(OperationType.TOOLBAR) {
                    val start = selectionStart.coerceAtLeast(0)
                    val end = selectionEnd.coerceAtLeast(start)
                    text?.replace(start, end, markdown)
                    setSelection(start + markdown.length)
                }
            }
        }

        binding.fabChangeMode.setOnClickListener {
            updateEditMode(!model.inEditMode)
            if (model.inEditMode) requestFocusForFields(true) else view.hideKeyboard()
        }

        Log.d(
            QUILLPAD_PERF_TAG,
            "fragment setup complete elapsed=${SystemClock.uptimeMillis() - fragmentCreatedAt}ms ${quillpadMemorySnapshot()}",
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.editor_top, menu)
        this.mainMenu = menu

        lifecycleScope.launch {
            model.data.first().note?.let(::setupMenuItems)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        data.note?.let { note ->
            when (item.itemId) {
                R.id.action_convert_note -> {
                    if (note.isList) model.toTextNote() else model.toList()
                }

                R.id.action_archive_note -> {
                    runManagedAction(closeOnSuccess = true) { activityModel.toggleArchive(note) }
                }

                R.id.action_delete_note -> {
                    runManagedAction(closeOnSuccess = true) { activityModel.delete(note) }
                }

                R.id.action_restore_note -> {
                    runManagedAction(closeOnSuccess = true) { activityModel.restore(note) }
                }

                R.id.action_delete_permanently_note -> {
                    runManagedAction(closeOnSuccess = true) { activityModel.deletePermanently(note) }
                }

                R.id.action_view_tags -> {
                    findQuillpadEditorHost()?.openTags()
                }

                R.id.action_search_note -> {
                    showSearch()
                }

                R.id.action_view_history -> {
                    findQuillpadEditorHost()?.openHistory()
                }

                R.id.action_view_remarks -> {
                    findQuillpadEditorHost()?.openRemarks()
                }

                R.id.action_view_reminders -> {
                    showRemindersDialog(note)
                }

                R.id.action_pin_note -> {
                    runManagedAction(closeOnSuccess = false) {
                        activityModel.togglePin(note).also { toggled ->
                            if (toggled) {
                                WidgetUpdateHelper.updateAllWidgets(requireContext())
                            }
                        }
                    }
                }

                R.id.action_change_mode -> {
                    updateEditMode(!model.inEditMode)
                    if (model.inEditMode) requestFocusForFields(true) else view?.hideKeyboard()
                    setupMenuItems(note)
                }

                R.id.action_change_color -> {
                    showColorChangeDialog()
                }

                R.id.action_export_note -> {
                    activityModel.notesToBackup = setOf(note)
                    exportNotesLauncher.launch(null)
                }

                R.id.action_share -> {
                    shareNote(requireContext(), note)
                }

                R.id.action_attach_file -> {
                    requestMediaLauncher.launch(null)
                }

                R.id.action_record_audio -> {
                    clearFragmentResult(RECORD_CODE)
                    RecordAudioDialog().show(parentFragmentManager, null)
                }

                R.id.action_uncheck_all_tasks -> {
                    uncheckAllTasks()
                    true
                }

                R.id.action_remove_all_checked_tasks -> {
                    removeAllCheckedTasks()
                    true
                }

                else -> false
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        model.selectedRange = with(binding.editTextContent) { selectionStart to selectionEnd }
        super.onPause()
    }

    override fun onDestroyView() {
        // Dismiss the snackbar which is shown for deleted notes
        snackbar?.dismiss()
        itemTouchHelper.attachToRecyclerView(null)
        attachmentsAdapter.listener = null
        tasksAdapter.listener = null
        setupScreenAlwaysOn(false)
        super.onDestroyView()
    }

    private fun jumpToNextTaskOrAdd(fromPosition: Int) {
        val next = tasksAdapter.tasks.getOrNull(fromPosition + 1)
        if (next == null || next.content.isNotEmpty()) {
            addTask(fromPosition + 1)
            return
        }
        (binding.recyclerTasks.findViewHolderForAdapterPosition(fromPosition + 1) as TaskViewHolder).requestFocus()
    }

    private inline fun traceQuillpadSetup(name: String, block: () -> Unit) {
        val start = SystemClock.uptimeMillis()
        block()
        Log.d(
            QUILLPAD_PERF_TAG,
            "fragment setup step=$name elapsed=${SystemClock.uptimeMillis() - start}ms ${quillpadMemorySnapshot()}",
        )
    }

    private fun setupTasksRecycler() {
        tasksAdapter = TasksAdapter(
            false,
            object : TaskRecyclerListener {
                override fun onDrag(viewHolder: TaskViewHolder) {
                    itemTouchHelper.startDrag(viewHolder)
                }

                override fun onTaskStatusChanged(position: Int, isDone: Boolean) {
                    updateTask(position = position, isDone = isDone)
                }

                override fun onTaskContentChanged(position: Int, content: String) {
                    updateTask(position = position, content = content)
                }

                override fun onNext(position: Int) {
                    jumpToNextTaskOrAdd(position)
                }
            },
            markwon = markwon,
        )

        binding.recyclerTasks.apply {
            isVisible = true
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = tasksAdapter
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    private fun setupAttachmentsRecycler() = with(binding) {
        // Create the adapter
        val listener = object : AttachmentRecyclerListener {
            override fun onItemClick(position: Int, viewBinding: LayoutAttachmentBinding) {
                val attachment = attachmentsAdapter.getItemAtPosition(position)

                if (data.openMediaInternally) {
                    startActivity(
                        Intent(requireContext(), MediaActivity::class.java).apply {
                            putExtra(MediaActivity.ATTACHMENT, attachment)
                        }
                    )
                } else {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = attachment.uri(requireContext()) ?: return@apply
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        startActivity(this)
                    }
                }
            }

            override fun onLongClick(position: Int, viewBinding: LayoutAttachmentBinding): Boolean {
                if (data.note?.isDeleted == true) return false

                data.note?.id?.let { noteId ->
                    val attachment = attachmentsAdapter.getItemAtPosition(position)

                    BottomSheet.show(attachment.description, parentFragmentManager) {
                        action(R.string.attachments_edit_description, R.drawable.ic_pencil) {
                            EditAttachmentDialog.build(noteId, attachment.path).show(parentFragmentManager, null)
                        }
                        action(R.string.action_delete, R.drawable.ic_bin) {
                            model.deleteAttachment(attachment)
                        }
                        action(R.string.action_share, R.drawable.ic_share) {
                            shareAttachment(requireContext(), attachment)
                        }
                    }
                }
                return true
            }
        }

        attachmentsAdapter = AttachmentsAdapter(listener)
        // Configure the recycler view
        recyclerAttachments.apply {
            layoutManager = AttachmentsGridManager(requireContext())
            adapter = attachmentsAdapter
        }
    }

    private fun setMarkdownToolbarVisibility(note: Note? = data.note) = with(binding) {
        if (note == null) return@with

        containerBottomToolbar.isVisible = !isList && note.isMarkdownEnabled && model.inEditMode && contentHasFocus

        scrollView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val actionBarSize = requireContext().getDimensionAttribute(R.attr.actionBarSize) ?: 0
            bottomMargin = when {
                containerBottomToolbar.isVisible -> actionBarSize
                else -> 0
            }
        }
    }

    private fun setupEditTexts() = with(binding) {
        editTextTitle.apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

            setOnEditorActionListener { _, actionId, _ ->
                when {
                    actionId == EditorInfo.IME_ACTION_NEXT && data.note?.isList == true -> {
                        jumpToNextTaskOrAdd(-1)
                        true
                    }

                    else -> false
                }
            }

            doOnTextChanged { text, _, _, _ ->
                // Only listen for meaningful changes
                if (data.note == null) {
                    return@doOnTextChanged
                }

                model.setNoteTitle(text.toString().trim())
            }
        }

        editTextContent.apply {
            enableUndoRedo()
            setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
            doOnTextChanged { text, _, _, _ ->
                // Only listen for meaningful changes, we do not care about empty text
                if (data.note == null) {
                    return@doOnTextChanged
                }

                model.setNoteContent(text.toString())
            }
            setOnFocusChangeListener { _, hasFocus ->
                contentHasFocus = hasFocus
                setMarkdownToolbarVisibility()
            }


            addTextChangedListener(object : TextWatcher {
                var changedText = ""
                private val listRegex = Regex("^((\\s*)([\\-+*] +)).*")
                private val checkRegex = Regex("^((\\s*)[-+*] *\\[([ xX])] +).*")
                private val numListRegex = Regex("((\\s*)([1-9][0-9]*)[.] +).*")
                private val indentedLine = Regex("((\\s+)).*")

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    changedText = s?.substring(start, start + count).toString()
                }

                override fun afterTextChanged(s: Editable?) {
                    if (changedText.endsWith('\n')) {
                        val txt = text ?: return
                        val prevLine = txt.lines().getOrNull(currentLineIndex - 1) ?: return
                        when {
                            prevLine.matches(checkRegex) -> nextListLine(checkRegex, prevLine, txt, "- [ ] ")
                            prevLine.matches(listRegex) -> nextListLine(listRegex, prevLine, txt)
                            prevLine.matches(numListRegex) -> {
                                val nextNum = numListRegex.find(prevLine)?.groupValues?.get(3)?.toInt()?.inc() ?: 1
                                nextListLine(numListRegex, prevLine, txt, "$nextNum. ")
                            }

                            prevLine.matches(indentedLine) -> nextListLine(indentedLine, prevLine, txt)
                        }
                    }
                }

                private fun nextListLine(regex: Regex, line: String, text: Editable, suffix: String? = null) {
                    val groups = regex.find(line)?.groupValues
                    val matchedLine = groups?.getOrNull(1) ?: ""
                    editHistory(OperationType.NEW_LINE, includePrevious = true) {
                        if (matchedLine == line) {
                            text.delete(currentLineStartPos - line.length - 1, currentLineStartPos - 1)
                        } else {
                            val indent = groups?.getOrNull(2) ?: ""
                            text.insert(currentLineStartPos, "$indent${suffix ?: groups?.getOrNull(3) ?: ""}")
                        }
                    }
                }
            })

            setOnCanUndoRedoListener { canUndo, canRedo ->
                binding.bottomToolbar.menu?.run {
                    findItem(R.id.action_undo).isEnabled = canUndo
                    findItem(R.id.action_redo).isEnabled = canRedo
                }
            }
        }

        // Used to clear focus and hide the keyboard when touching outside of the edit texts
        linearLayout.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) root.hideKeyboard()
        }
    }

    private fun setupSearch() = with(binding) {
        editTextSearch.doOnTextChanged { _, _, _, _ -> rebuildSearch(selectMatch = true, focusEditor = false) }
        checkSearchMatchCase.setOnCheckedChangeListener { _, _ -> rebuildSearch(selectMatch = true, focusEditor = false) }
        checkSearchRegex.setOnCheckedChangeListener { _, _ -> rebuildSearch(selectMatch = true, focusEditor = false) }
        editTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                moveSearch(1)
                true
            } else {
                false
            }
        }
        actionSearchPrevious.setOnClickListener { moveSearch(-1) }
        actionSearchNext.setOnClickListener { moveSearch(1) }
        actionSearchClose.setOnClickListener { closeSearch() }
        actionReplaceCurrent.setOnClickListener { replaceCurrentSearchMatch() }
        actionReplaceAll.setOnClickListener { replaceAllSearchMatches() }
    }

    private fun showSearch() = with(binding) {
        data.note?.takeIf { !it.isList }?.let {
            loadEditorContentIfNeeded(it, deferUntilResumed = false)
        }
        containerSearch.isVisible = true
        val selected = editTextContent.selectedText.orEmpty()
        if (selected.isNotEmpty() && !selected.contains('\n')) editTextSearch.setText(selected)
        rebuildSearch(selectMatch = true, focusEditor = false)
        editTextSearch.requestFocusAndKeyboard()
    }

    private fun closeSearch() = with(binding) {
        containerSearch.isVisible = false
        searchMatches = emptyList()
        currentSearchIndex = -1
        editTextContent.requestFocusAndKeyboard()
    }

    private fun rebuildSearch(
        preferredStart: Int = binding.editTextContent.selectionStart.coerceAtLeast(0),
        selectMatch: Boolean,
        focusEditor: Boolean,
    ) {
        val text = binding.editTextContent.text?.toString().orEmpty()
        val result = buildNoteSearchMatches(
            text = text,
            query = binding.editTextSearch.text?.toString().orEmpty(),
            useRegex = binding.checkSearchRegex.isChecked,
            matchCase = binding.checkSearchMatchCase.isChecked,
        )
        searchMatches = result.matches
        if (result.errorMessage != null || searchMatches.isEmpty()) {
            currentSearchIndex = -1
            binding.textSearchResult.text = result.errorMessage ?: "0/0"
            return
        }
        currentSearchIndex = searchMatches.indexOfFirst { it.start >= preferredStart }.takeIf { it >= 0 } ?: 0
        updateSearchResult(selectMatch, focusEditor)
    }

    private fun moveSearch(step: Int) = with(binding) {
        val text = editTextContent.text?.toString().orEmpty()
        val result = buildNoteSearchMatches(
            text = text,
            query = editTextSearch.text?.toString().orEmpty(),
            useRegex = checkSearchRegex.isChecked,
            matchCase = checkSearchMatchCase.isChecked,
        )
        searchMatches = result.matches
        if (result.errorMessage != null || searchMatches.isEmpty()) {
            currentSearchIndex = -1
            textSearchResult.text = result.errorMessage ?: "0/0"
            return@with
        }

        val selectedIndex = searchMatches.indexOfFirst {
            it.start == editTextContent.selectionStart && it.end == editTextContent.selectionEnd
        }
        currentSearchIndex = if (selectedIndex >= 0) {
            Math.floorMod(selectedIndex + step, searchMatches.size)
        } else if (step > 0) {
            searchMatches.indexOfFirst { it.start >= editTextContent.selectionStart }.takeIf { it >= 0 } ?: 0
        } else {
            searchMatches.indexOfLast { it.end <= editTextContent.selectionStart }.takeIf { it >= 0 }
                ?: searchMatches.lastIndex
        }
        updateSearchResult(selectMatch = true, focusEditor = true)
    }

    private fun updateSearchResult(selectMatch: Boolean, focusEditor: Boolean) {
        val match = searchMatches.getOrNull(currentSearchIndex) ?: return
        binding.textSearchResult.text = "${currentSearchIndex + 1}/${searchMatches.size}"
        if (!selectMatch) return
        binding.editTextContent.apply {
            setSelection(match.start, match.end)
            bringPointIntoView(match.end)
            if (focusEditor) requestFocusAndKeyboard()
        }
    }

    private fun replaceCurrentSearchMatch() = with(binding) {
        val source = editTextContent.text?.toString().orEmpty()
        val query = editTextSearch.text?.toString().orEmpty()
        val result = buildNoteSearchMatches(source, query, checkSearchRegex.isChecked, checkSearchMatchCase.isChecked)
        if (result.errorMessage != null || result.matches.isEmpty()) {
            textSearchResult.text = result.errorMessage ?: "0/0"
            return@with
        }
        val range = result.matches.firstOrNull {
            it.start == editTextContent.selectionStart && it.end == editTextContent.selectionEnd
        } ?: result.matches.firstOrNull { it.start >= editTextContent.selectionStart } ?: result.matches.first()
        val replacement = buildCurrentReplacement(
            text = source,
            range = range,
            query = query,
            replacement = editTextReplace.text?.toString().orEmpty(),
            useRegex = checkSearchRegex.isChecked,
            matchCase = checkSearchMatchCase.isChecked,
        )
        val replacementText = replacement.text
        if (replacementText == null) {
            textSearchResult.text = replacement.errorMessage ?: "替换失败"
            return@with
        }
        val nextStart = range.start + replacementText.length
        editTextContent.editHistory(OperationType.REPLACE) {
            text?.replace(range.start, range.end, replacementText)
            setSelection(nextStart.coerceAtMost(length()))
        }
        rebuildSearch(preferredStart = nextStart, selectMatch = true, focusEditor = true)
    }

    private fun replaceAllSearchMatches() = with(binding) {
        val source = editTextContent.text?.toString().orEmpty()
        val result = replaceAllNoteSearchMatches(
            text = source,
            query = editTextSearch.text?.toString().orEmpty(),
            replacement = editTextReplace.text?.toString().orEmpty(),
            useRegex = checkSearchRegex.isChecked,
            matchCase = checkSearchMatchCase.isChecked,
        )
        val replacementText = result.text
        if (replacementText == null) {
            textSearchResult.text = result.errorMessage ?: "替换失败"
            return@with
        }
        if (result.count == 0) {
            textSearchResult.text = "0/0"
            return@with
        }
        val cursor = editTextContent.selectionStart.coerceIn(0, replacementText.length)
        editTextContent.editHistory(OperationType.REPLACE) {
            text?.replace(0, length(), replacementText)
            setSelection(cursor)
        }
        rebuildSearch(preferredStart = cursor, selectMatch = false, focusEditor = true)
        Toast.makeText(requireContext(), "已替换 ${result.count} 处", Toast.LENGTH_SHORT).show()
    }

    private fun drawingReferenceAtSelection(): String? = with(binding.editTextContent) {
        val source = text?.toString().orEmpty()
        if (source.isEmpty()) return@with null
        val start = selectionStart.coerceIn(0, source.length)
        val end = selectionEnd.coerceIn(start, source.length)
        val markdown = if (end > start) {
            source.substring(start, end)
        } else {
            val lineStart = source.lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
            val lineEnd = source.indexOf('\n', start).takeIf { it >= 0 } ?: source.length
            source.substring(lineStart, lineEnd)
        }
        extractMarkdownImageReferences(markdown).filterNotNull().firstOrNull()
    }

    fun insertMarkdownAt(selection: Pair<Int, Int>, markdown: String) = with(binding.editTextContent) {
        val start = selection.first.coerceIn(0, length())
        val end = selection.second.coerceIn(start, length())
        editHistory(OperationType.TOOLBAR) {
            text?.replace(start, end, markdown)
            setSelection(start + markdown.length)
        }
        requestFocus()
    }

    private fun setupMenuItems(note: Note) = mainMenu?.run {
        findItem(R.id.action_restore_note)?.isVisible = note.isDeleted
        findItem(R.id.action_delete_permanently_note)?.isVisible = note.isDeleted
        findItem(R.id.action_delete_note)?.isVisible = !note.isDeleted
        findItem(R.id.action_share)?.isVisible = !note.isDeleted

        listOf(
            R.id.action_view_reminders,
            R.id.action_change_color,
            R.id.action_attach_file,
            R.id.action_record_audio,
            R.id.action_take_photo,
            R.id.action_convert_note,
            R.id.action_enable_disable_markdown,
            R.id.action_hide_note,
            R.id.action_do_not_sync,
            R.id.action_screen_always_on,
            R.id.action_export_note,
            R.id.action_uncheck_all_tasks,
            R.id.action_remove_all_checked_tasks,
        ).forEach { findItem(it)?.isVisible = false }

        val topOrder = visibleQuillpadToolbarItems(
            prefsManager.getEditorTopToolbarItemOrder(),
            PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER,
            prefsManager.getEditorTopToolbarHiddenItems(),
        )
        val topVisible = topOrder.toSet()
        val moreItems = prefsManager.getEditorTopToolbarMoreItems()
        fun configureTopItem(menuId: Int, itemId: PrefsManager.EditorTopToolbarItemId, available: Boolean = true) {
            findItem(menuId)?.apply {
                isVisible = available && itemId in topVisible
                setShowAsAction(
                    if (itemId in moreItems) MenuItem.SHOW_AS_ACTION_NEVER else MenuItem.SHOW_AS_ACTION_IF_ROOM
                )
            }
        }
        configureTopItem(R.id.action_search_note, PrefsManager.EditorTopToolbarItemId.SEARCH)
        configureTopItem(R.id.action_view_tags, PrefsManager.EditorTopToolbarItemId.LABEL, !note.isDeleted)
        configureTopItem(R.id.action_view_remarks, PrefsManager.EditorTopToolbarItemId.REMARKS)
        configureTopItem(R.id.action_view_history, PrefsManager.EditorTopToolbarItemId.HISTORY)
        findItem(R.id.action_delete_note)?.isVisible =
            !note.isDeleted && PrefsManager.EditorTopToolbarItemId.DELETE in topVisible

        findItem(R.id.action_change_mode)?.apply {
            // if view/edit mode FAB isn't displayed (user pref) show it in the top menu
            if (!data.showFabChangeMode) {
                setIcon(if (model.inEditMode) R.drawable.ic_show else R.drawable.ic_pencil)

                isVisible = !note.isDeleted &&
                    !hasNoteEmptyContent(note) &&
                    PrefsManager.EditorTopToolbarItemId.EDIT in topVisible
            }
        }

        findItem(R.id.action_pin_note)?.apply {
            setIcon(if (note.isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin)
            setTitle(if (note.isPinned) R.string.action_unpin else R.string.action_pin)
            isVisible = !note.isDeleted
        }

        findItem(R.id.action_archive_note)?.apply {
            title = if (note.isArchived) getString(R.string.action_unarchive) else getString(R.string.action_archive)
            isVisible = !note.isDeleted && PrefsManager.EditorTopToolbarItemId.ARCHIVE in topVisible
        }

    }

    private fun observeData() = with(binding) {
        model.data.collect(viewLifecycleOwner) { data ->
            val collectStart = SystemClock.uptimeMillis()
            if (data.note == null && data.isInitialized) {
                return@collect run { findNavController().navigateUp() }
            }

            if (!data.isInitialized || data.note == null) return@collect

            Log.d(
                QUILLPAD_PERF_TAG,
                "data collect start first=$isFirstLoad contentLen=${data.note.content.length} " +
                    "markdown=${data.note.isMarkdownEnabled} defaultMode=${data.defaultEditorMode} " +
                    "modelEdit=${model.inEditMode} list=${data.note.isList} ${quillpadMemorySnapshot()}",
            )
            this@EditorFragment.data = data

            val isConverted = data.note.isList != isList
            val isMarkdownEnabled = data.note.isMarkdownEnabled
            if (isFirstLoad && shouldSuppressQuillpadLiveMarkdown(data.note.content.length)) {
                suppressLiveMarkdownForLargeNote = true
                Log.d(
                    "KardLeafQuillpad",
                    "live markdown disabled for large note contentLen=${data.note.content.length}",
                )
            }
            val (dateFormat, timeFormat) = data.dateTimeFormats
            val screenAlwaysOn = data.note.screenAlwaysOn

            isList = data.note.isList
            isNoteDeleted = data.note.isDeleted

            if (isMarkdownEnabled && !suppressLiveMarkdownForLargeNote) {
                enableMarkdownTextWatcher()
            } else {
                disableMarkdownTextWatcher()
            }

            setupScreenAlwaysOn(screenAlwaysOn)

            // Update Title and Content only the first the since they are EditTexts
            if (isFirstLoad) {

                val hostStartInEditMode = arguments?.getBoolean(
                    QuillpadSandboxActivity.ARG_START_IN_EDIT_MODE,
                    false,
                ) == true
                if (hostStartInEditMode || data.defaultEditorMode == DefaultEditorMode.EDIT) {
                    model.inEditMode = true
                }
                Log.d(
                    QUILLPAD_PERF_TAG,
                    "initial mode hostEdit=$hostStartInEditMode quillpadDefault=${data.defaultEditorMode} " +
                        "resolvedEdit=${model.inEditMode} contentLen=${data.note.content.length}",
                )

                // apply font size preference
                if (data.editorFontSize != -1) { // is customised
                    val fontSizeFloat = data.editorFontSize.toFloat()

                    textViewTitlePreview.textSize = fontSizeFloat
                    textViewContentPreview.textSize = fontSizeFloat

                    editTextTitle.textSize = fontSizeFloat
                    editTextContent.textSize = fontSizeFloat

                    if (isList) {
                        tasksAdapter.setFontSize(fontSizeFloat)
                    }
                }

                editTextTitle.withoutTextWatchers {
                    setText(data.note.title)
                }

                when {
                    isList -> {
                        tasksAdapter.submitList(data.note.taskList)
                        recyclerTasks.post {
                            findQuillpadEditorHost()?.onEditorContentReady("taskList")
                        }
                    }
                    model.inEditMode -> {
                        loadEditorContentIfNeeded(data.note, deferUntilResumed = true)
                    }
                    else -> {
                        Log.d(
                            QUILLPAD_PERF_TAG,
                            "editor setText deferred for preview contentLen=${data.note.content.length} " +
                                "elapsedFromFragment=${SystemClock.uptimeMillis() - fragmentCreatedAt}ms",
                        )
                    }
                }

                nextTaskId = data.note.taskList.maxOfOrNull { it.id }?.plus(1) ?: 0L
            } else if (model.shouldReloadEditorText()) {
                if (editTextTitle.text?.toString() != data.note.title) {
                    editTextTitle.withoutTextWatchers { setText(data.note.title) }
                }
                if (editorContentLoaded && !isList && editTextContent.text?.toString() != data.note.content) {
                    val selectionStart = editTextContent.selectionStart.coerceAtLeast(0)
                    val selectionEnd = editTextContent.selectionEnd.coerceAtLeast(selectionStart)
                    editTextContent.withOnlyTextWatcher<MarkwonEditorTextWatcher> {
                        setText(data.note.content)
                    }
                    editTextContent.setSelection(
                        selectionStart.coerceAtMost(editTextContent.length()),
                        selectionEnd.coerceAtMost(editTextContent.length()),
                    )
                    editTextContent.clearHistory()
                }
            }

            // We only want to update the task list when the user converts the note from text to list
            if (isConverted) {

                if (data.editorFontSize != -1) {
                    tasksAdapter.setFontSize(data.editorFontSize.toFloat())
                }

                tasksAdapter.tasks.clear()
                tasksAdapter.notifyDataSetChanged()
                tasksAdapter.submitList(data.note.taskList)
                editTextContent.withOnlyTextWatcher<MarkwonEditorTextWatcher> {
                    setText(data.note.content)
                }
                editorContentLoadStarted = true
                editorContentLoaded = true
                editTextContent.clearHistory()
            }
            recyclerTasks.isVisible = isList

            updateEditMode(note = data.note)
            Log.d(
                QUILLPAD_PERF_TAG,
                "data collect after updateEditMode elapsed=${SystemClock.uptimeMillis() - collectStart}ms " +
                    "edit=${model.inEditMode} editorVisible=${editTextContent.isVisible} " +
                    "previewVisible=${textViewContentPreview.isVisible} contentLen=${data.note.content.length} " +
                    quillpadMemorySnapshot(),
            )

            // Must be called after updateEditMode since that method changes the visibility of the inputs
            if (isFirstLoad) requestFocusForFields()

            // Also set text of preview title. Content is rendered only when preview mode is visible.
            textViewTitlePreview.text = data.note.title.ifEmpty { getString(R.string.indicator_untitled) }

            setupMenuItems(data.note)

            // Update notebook indicator
            notebookView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                requireContext().getDrawableCompat(R.drawable.ic_notebook),
                null,
                requireContext().getDrawableCompat(if (data.notebook == null) R.drawable.ic_add else R.drawable.ic_swap),
                null
            )
            notebookView.text = data.notebook?.name ?: getString(R.string.notebooks_unassigned)

            // Update fragment background colour
            data.note.color.resId(requireContext())?.let { resId ->
                backgroundColor = resId
                root.setBackgroundColor(resId)
                containerBottomToolbar.setBackgroundColor(resId)
                toolbar.setBackgroundColor(resId)
            }

            // Update date
            val offset = ZoneId.systemDefault().rules.getOffset(Instant.now())
            val creationDate = LocalDateTime.ofEpochSecond(data.note.creationDate, 0, offset)
            val modifiedDate = LocalDateTime.ofEpochSecond(data.note.modifiedDate, 0, offset)

            formatter =
                DateTimeFormatter.ofPattern("${getString(dateFormat.patternResource)}, ${getString(timeFormat.patternResource)}")

            textViewDate.isVisible = data.showDates
            if (formatter != null && data.showDates) {
                textViewDate.text =
                    getString(
                        R.string.indicator_note_date,
                        creationDate.format(formatter),
                        modifiedDate.format(formatter)
                    )
            }

            // We want to start the transition only when everything is loaded
            if (findQuillpadEditorHost() == null) {
                binding.root.doOnPreDraw {
                    startPostponedEnterTransition()
                }
            }

            if (isNoteDeleted) {
                snackbar = Snackbar.make(binding.root, "", Snackbar.LENGTH_INDEFINITE)
                    .setText(getString(R.string.indicator_deleted_note_cannot_be_edited))
                    .setAction(getString(R.string.action_restore)) { _ ->
                        runManagedAction(closeOnSuccess = true) { activityModel.restore(data.note) }
                    }
                snackbar?.show()
                snackbar?.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onShown(transientBottomBar: Snackbar?) {
                        super.onShown(transientBottomBar)
                        scrollView.apply {
                            setPadding(paddingLeft, paddingTop, paddingRight, snackbar?.view?.height ?: paddingBottom)
                        }
                    }
                })
            }

            // Update attachments
            attachmentsAdapter.submitList(data.note.attachments)

            // Update tags
            containerTags.removeAllViews()
            data.note.tags.forEach { tag ->
                containerTags.addView(
                    TextView(ContextThemeWrapper(binding.root.context, R.style.TagChip)).apply {
                        text = "# ${tag.name}"
                    }
                )
            }

            isFirstLoad = false
        }
    }

    private fun setupListeners() = with(binding) {
        applyKardLeafToolbarOrder()
        bottomToolbar.setOnMenuItemClickListener {

            val span = when (it.itemId) {
                R.id.action_insert_bold -> MarkdownSpan.BOLD
                R.id.action_insert_italics -> MarkdownSpan.ITALICS
                R.id.action_insert_strikethrough -> MarkdownSpan.STRIKETHROUGH
                R.id.action_insert_code -> MarkdownSpan.CODE
                R.id.action_preview -> {
                    updateEditMode(!model.inEditMode)
                    if (model.inEditMode) requestFocusForFields(true) else root.hideKeyboard()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_drawing -> {
                    val selection = editTextContent.selectionStart to editTextContent.selectionEnd
                    findQuillpadEditorHost()?.openDrawing(
                        selection = selection,
                        reference = drawingReferenceAtSelection(),
                    )
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_heading_1 -> {
                    editTextContent.setHeadingLevel(1)
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_heading_2 -> {
                    editTextContent.setHeadingLevel(2)
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_heading_3 -> {
                    editTextContent.setHeadingLevel(3)
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_bullet_list -> {
                    editTextContent.toggleBulletCurrentLine()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_ordered_list -> {
                    editTextContent.toggleOrderedCurrentLine()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_code_block -> {
                    editTextContent.insertCodeBlock()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_divider -> {
                    editTextContent.insertDivider()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_indent -> {
                    editTextContent.indentCurrentLine()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_outdent -> {
                    editTextContent.outdentCurrentLine()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_underline -> {
                    editTextContent.insertUnderline()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_math -> {
                    editTextContent.insertMath()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_quote -> MarkdownSpan.QUOTE
                R.id.action_insert_highlight -> MarkdownSpan.HIGHLIGHT
                R.id.action_insert_link -> {
                    clearFragmentResult(MARKDOWN_DIALOG_RESULT)
                    InsertHyperlinkDialog
                        .build(editTextContent.selectedText ?: "")
                        .show(parentFragmentManager, null)
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_image -> {
                    pendingImageSelection = editTextContent.selectionStart to editTextContent.selectionEnd
                    importImageLauncher.launch("image/*")
                    return@setOnMenuItemClickListener true
                }

                R.id.action_insert_table -> {
                    clearFragmentResult(MARKDOWN_DIALOG_RESULT)
                    InsertTableDialog().show(parentFragmentManager, null)
                    return@setOnMenuItemClickListener true
                }

                R.id.action_toggle_check_line -> {
                    editTextContent.toggleChecklistCurrentLine()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_complete_check_line -> {
                    editTextContent.setCheckmarkCurrentLine(true)
                    return@setOnMenuItemClickListener true
                }

                R.id.action_scroll_to_top -> {
                    scrollView.smoothScrollTo(0, 0)
                    editTextContent.setSelection(0)
                    return@setOnMenuItemClickListener true
                }

                R.id.action_scroll_to_bottom -> {
                    scrollView.smoothScrollTo(
                        0,
                        editTextContent.bottom + editTextContent.paddingBottom + editTextContent.marginBottom
                    )
                    editTextContent.setSelection(editTextContent.length())
                    return@setOnMenuItemClickListener true
                }

                R.id.action_undo -> {
                    editTextContent.undo()
                    return@setOnMenuItemClickListener true
                }

                R.id.action_redo -> {
                    editTextContent.redo()
                    return@setOnMenuItemClickListener true
                }

                else -> return@setOnMenuItemClickListener false
            }
            editTextContent.insertMarkdown(span ?: return@setOnMenuItemClickListener false)
            true
        }

        notebookView.setOnClickListener {
            data.note?.let { showMoveToNotebookDialog(it) }
        }

        actionAddTask.setOnClickListener {
            // Always add new tasks at the top (position 0)
            addTask(0)
        }
    }

    private fun applyKardLeafToolbarOrder() {
        val menu = binding.bottomToolbar.menu
        val configuredItems = visibleQuillpadToolbarItems(
            KardLeafCustomFeatures.getToolbarOrder(requireContext()),
            KardLeafCustomFeatures.DefaultToolbarOrder,
            emptySet(),
        )
        val configuredIds = configuredItems.map(::toolbarMenuId)
        val extraIds = listOf(
            R.id.action_insert_highlight,
            R.id.action_indent,
            R.id.action_outdent,
            R.id.action_scroll_to_top,
            R.id.action_scroll_to_bottom,
        )
        val desiredIds = (configuredIds + extraIds).distinct().filter { menu.findItem(it) != null }
        val currentIds = (0 until menu.size()).map { index -> menu.getItem(index).itemId }
        if (shouldReuseQuillpadToolbarOrder(currentIds, desiredIds)) {
            Log.d(
                QUILLPAD_PERF_TAG,
                "toolbar order unchanged; reuse inflated menu items=${currentIds.size}",
            )
            return
        }
        val states = desiredIds.mapNotNull { id ->
            menu.findItem(id)?.let { item ->
                ToolbarMenuItemState(id, item.title ?: "", item.icon, item.isEnabled, item.isVisible)
            }
        }
        states.forEach { menu.removeItem(it.id) }
        states.forEachIndexed { order, state ->
            menu.add(Menu.NONE, state.id, order, state.title).apply {
                icon = state.icon
                isEnabled = state.isEnabled
                isVisible = state.isVisible
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        }
    }

    private fun toolbarMenuId(item: KardLeafCustomFeatures.ToolbarItem): Int = when (item) {
        KardLeafCustomFeatures.ToolbarItem.PREVIEW -> R.id.action_preview
        KardLeafCustomFeatures.ToolbarItem.UNDO -> R.id.action_undo
        KardLeafCustomFeatures.ToolbarItem.REDO -> R.id.action_redo
        KardLeafCustomFeatures.ToolbarItem.IMAGE -> R.id.action_insert_image
        KardLeafCustomFeatures.ToolbarItem.DRAWING -> R.id.action_insert_drawing
        KardLeafCustomFeatures.ToolbarItem.HEADING -> R.id.action_insert_heading_1
        KardLeafCustomFeatures.ToolbarItem.HEADING2 -> R.id.action_insert_heading_2
        KardLeafCustomFeatures.ToolbarItem.HEADING3 -> R.id.action_insert_heading_3
        KardLeafCustomFeatures.ToolbarItem.RULE -> R.id.action_insert_divider
        KardLeafCustomFeatures.ToolbarItem.BOLD -> R.id.action_insert_bold
        KardLeafCustomFeatures.ToolbarItem.ITALIC -> R.id.action_insert_italics
        KardLeafCustomFeatures.ToolbarItem.UNDERLINE -> R.id.action_insert_underline
        KardLeafCustomFeatures.ToolbarItem.STRIKE -> R.id.action_insert_strikethrough
        KardLeafCustomFeatures.ToolbarItem.LINK -> R.id.action_insert_link
        KardLeafCustomFeatures.ToolbarItem.CODE -> R.id.action_insert_code
        KardLeafCustomFeatures.ToolbarItem.CODE_BLOCK -> R.id.action_insert_code_block
        KardLeafCustomFeatures.ToolbarItem.QUOTE -> R.id.action_insert_quote
        KardLeafCustomFeatures.ToolbarItem.MATH -> R.id.action_insert_math
        KardLeafCustomFeatures.ToolbarItem.BULLET -> R.id.action_insert_bullet_list
        KardLeafCustomFeatures.ToolbarItem.NUMBERED -> R.id.action_insert_ordered_list
        KardLeafCustomFeatures.ToolbarItem.INDENT -> R.id.action_indent
        KardLeafCustomFeatures.ToolbarItem.OUTDENT -> R.id.action_outdent
        KardLeafCustomFeatures.ToolbarItem.CHECKBOX -> R.id.action_toggle_check_line
        KardLeafCustomFeatures.ToolbarItem.CHECKBOX_DONE -> R.id.action_complete_check_line
        KardLeafCustomFeatures.ToolbarItem.TABLE -> R.id.action_insert_table
    }

    private fun setupMarkdown() {
        markwonTextWatcher = MarkwonEditorTextWatcher.withPreRender(
            markwonEditor, QUILLPAD_MARKDOWN_EXECUTOR,
            binding.editTextContent
        )
    }

    private fun enableMarkdownTextWatcher() = with(binding) {
        if (markwonTextWatcher != null && !editTextContent.isMarkdownEnabled) {
            // TextWatcher is created and currently not attached to the EditText, we attach it
            editTextContent.addTextChangedListener(markwonTextWatcher)

            // Re-set text to notify the listener
            editTextContent.withOnlyTextWatcher<MarkwonEditorTextWatcher> {
                setText(text)
            }

            editTextContent.isMarkdownEnabled = true
            setMarkdownToolbarVisibility()
        }
    }

    private fun disableMarkdownTextWatcher() = with(binding) {
        if (markwonTextWatcher != null && editTextContent.isMarkdownEnabled) {
            // TextWatcher is created and currently attached to the EditText, we detach it
            editTextContent.removeTextChangedListener(markwonTextWatcher)
            val text = editTextContent.text.toString()

            editTextContent.text?.clearSpans()
            editTextContent.withoutTextWatchers {
                setText(text)
            }

            editTextContent.isMarkdownEnabled = false
            setMarkdownToolbarVisibility()
        }
    }

    private fun setupScreenAlwaysOn(enable: Boolean) {
        if (enable) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun setupToolbar(): Unit = with(binding) {
        super.setupToolbar()
        val onBackPressedHandler = {
            if (findNavController().navigateUp()) {
                // This is needed because "Notes" label briefly appears
                // during the shared element transition when returning.
                // Todo: Needs a better fix
                toolbar.setTitleTextColor(Color.TRANSPARENT)

                // This is needed because the view jumps around
                // during the shared element transition when returning.
                // Todo: Needs a better fix
                notebookView.isVisible = false
            }
        }

        toolbar.setNavigationOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            if (!onBackPressHandled) {
                onBackPressedHandler()
                onBackPressHandled = true
            }
        }
    }

    private fun addTask(position: Int = 0) {
        tasksAdapter.tasks.add(position, NoteTask(nextTaskId, "", false))
        tasksAdapter.notifyItemInserted(position)

        if (position < tasksAdapter.tasks.size - 1) {
            tasksAdapter.notifyItemRangeChanged(position, tasksAdapter.tasks.size - position)
        }

        binding.recyclerTasks.doOnNextLayout {
            (binding.recyclerTasks.findViewHolderForAdapterPosition(position) as TaskViewHolder).requestFocus()
        }

        nextTaskId += 1
        model.updateTaskList(tasksAdapter.tasks)
    }

    private fun updateTask(position: Int, content: String? = null, isDone: Boolean? = null) {
        val tasks = tasksAdapter.tasks
        val oldTask = tasks[position]
        val newTask = tasks[position].copy(
            content = content ?: oldTask.content,
            isDone = isDone ?: oldTask.isDone
        )
        tasks[position] = newTask

        if (oldTask.isDone != newTask.isDone && model.moveCheckedItems) {
            if (newTask.isDone) {
                // Move to very end
                tasks.removeAt(position)
                tasks.add(newTask)

                tasksAdapter.notifyItemMoved(position, tasks.indexOf(newTask))
                tasksAdapter.notifyItemRangeChanged(position, tasks.size - position)
            } else {
                // Move to after last open task or to very beginning if all tasks are done
                val newPosition = tasks.indexOfLast { it.id != newTask.id && !it.isDone } + 1

                // Only move upwards; don't move further down
                if (newPosition < position) {
                    tasks.removeAt(position)
                    tasks.add(newPosition, newTask)

                    tasksAdapter.notifyItemMoved(position, newPosition)
                    tasksAdapter.notifyItemRangeChanged(newPosition, position - newPosition + 1)
                }
            }
        }

        model.updateTaskList(tasksAdapter.tasks)
    }

    private fun showColorChangeDialog() {
        val selected = NoteColor.entries.indexOf(data.note?.color).coerceAtLeast(0)
        val dialog = BaseDialog.build(requireContext()) {
            setTitle(getString(R.string.action_change_color))
            setSingleChoiceItems(
                NoteColor.entries.map { it.localizedName }.toTypedArray(),
                selected
            ) { _, which ->
                model.setColor(NoteColor.entries[which])
            }
            setPositiveButton(getString(R.string.action_done)) { _, _ -> }
        }

        dialog.show()
    }

    private fun runManagedAction(
        closeOnSuccess: Boolean,
        action: suspend () -> Boolean,
    ) {
        lifecycleScope.launch {
            val succeeded = runCatching { action() }
                .onFailure { Log.e(TAG, "KardLeaf note action failed", it) }
                .getOrDefault(false)
            if (succeeded) {
                if (closeOnSuccess) activity?.onBackPressedDispatcher?.onBackPressed()
            } else {
                showActionFailure()
            }
        }
    }

    private fun showActionFailure() {
        Toast.makeText(requireContext(), "操作失败，笔记仍保留在编辑器", Toast.LENGTH_SHORT).show()
    }

    private fun showRemindersDialog(note: Note) {
        BottomSheet.show(getString(R.string.reminders), parentFragmentManager) {
            data.note?.reminders?.forEach { reminder ->
                val offset = ZoneId.systemDefault().rules.getOffset(Instant.now())
                val reminderDate = LocalDateTime.ofEpochSecond(reminder.date, 0, offset)

                action(reminder.name + " (${reminderDate.format(formatter)})", R.drawable.ic_bell) {
                    checkSchedulePermission {
                        EditReminderDialog.build(note.id, reminder).show(parentFragmentManager, null)
                    }
                }
            }
            action(R.string.action_new_reminder, R.drawable.ic_add) {
                checkSchedulePermission {
                    EditReminderDialog.build(note.id, null).show(parentFragmentManager, null)
                }
            }
        }
    }

    private fun checkSchedulePermission(onPermissionGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context?.getSystemService(AlarmManager::class.java)
            if (alarmManager?.canScheduleExactAlarms() != true) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context?.packageName, null)
                }
                context?.startActivity(intent)
                return
            }
        }
        onPermissionGranted()
    }

    /** Gives the focus to the note body if it is empty */
    private fun requestFocusForFields(forceFocus: Boolean = false) = with(binding) {
        if (data.note?.isEmpty() == true || forceFocus) {
            editTextContent.requestFocusAndKeyboard()
        }
    }

    private fun loadEditorContentIfNeeded(note: Note, deferUntilResumed: Boolean) = with(binding) {
        if (editorContentLoadStarted || isList) return@with

        editorContentLoadStarted = true
        val scheduledAt = SystemClock.uptimeMillis()
        val logPrefix = if (deferUntilResumed) "editor" else "editor deferred"
        Log.d(
            QUILLPAD_PERF_TAG,
            "$logPrefix setText scheduled contentLen=${note.content.length} " +
                "elapsedFromFragment=${scheduledAt - fragmentCreatedAt}ms",
        )

        val loadContent = {
            val currentNote = data.note ?: note
            val setTextStart = SystemClock.uptimeMillis()
            Log.d(
                QUILLPAD_PERF_TAG,
                "$logPrefix setText start contentLen=${currentNote.content.length} " +
                    "queueDelay=${setTextStart - scheduledAt}ms watchers=${editTextContent.textWatchers.size} " +
                    "markdownAttached=${editTextContent.isMarkdownEnabled} ${quillpadMemorySnapshot()}",
            )
            editTextContent.withOnlyTextWatcher<MarkwonEditorTextWatcher> {
                setText(currentNote.content)
            }
            editorContentLoaded = true
            val setTextEnd = SystemClock.uptimeMillis()
            Log.d(
                QUILLPAD_PERF_TAG,
                "$logPrefix setText end contentLen=${editTextContent.length()} " +
                    "elapsed=${setTextEnd - setTextStart}ms layoutReady=${editTextContent.layout != null} " +
                    "lineCount=${editTextContent.layout?.lineCount ?: -1} ${quillpadMemorySnapshot()}",
            )
            editTextContent.post {
                Log.d(
                    QUILLPAD_PERF_TAG,
                    "$logPrefix postLayout elapsedFromSetText=${SystemClock.uptimeMillis() - setTextStart}ms " +
                        "view=${editTextContent.width}x${editTextContent.height} " +
                        "layoutHeight=${editTextContent.layout?.height ?: -1} " +
                        "lineCount=${editTextContent.layout?.lineCount ?: -1} " +
                        "scroll=${scrollView.width}x${scrollView.height} scrollY=${scrollView.scrollY} " +
                        quillpadMemorySnapshot(),
                )
                val source = if (deferUntilResumed) "editor" else "editorDeferred"
                findQuillpadEditorHost()?.onEditorContentReady(source)
            }
            val (selStart, selEnd) = model.selectedRange
            if (selStart >= 0 && selEnd <= editTextContent.length()) {
                editTextContent.setSelection(selStart, selEnd)
            }
            editTextContent.clearHistory()
        }

        if (deferUntilResumed) {
            viewLifecycleOwner.lifecycleScope.launchWhenResumed { loadContent() }
        } else {
            loadContent()
        }
    }

    private fun updateEditMode(inEditMode: Boolean = model.inEditMode, note: Note? = data.note) = with(binding) {
        val modeStart = SystemClock.uptimeMillis()
        Log.d(
            QUILLPAD_PERF_TAG,
            "updateEditMode start requested=$inEditMode current=${model.inEditMode} " +
                "contentLen=${note?.content?.length ?: -1} list=$isList",
        )
        // If the note is empty the fragment should open in edit mode by default
        val noteHasEmptyContent = hasNoteEmptyContent(note)

        model.inEditMode = (inEditMode || noteHasEmptyContent) && !isNoteDeleted

        textViewTitlePreview.isVisible = !model.inEditMode
        editTextTitle.isVisible = model.inEditMode

        actionAddTask.isVisible = isList && model.inEditMode
        recyclerTasks.doOnPreDraw {
            for (pos in 0 until tasksAdapter.tasks.size) {
                (recyclerTasks.findViewHolderForAdapterPosition(pos) as? TaskViewHolder)?.isEnabled = model.inEditMode
            }
        }

        textViewContentPreview.isVisible = !model.inEditMode && !isList
        editTextContent.isVisible = model.inEditMode && !isList

        if (editTextContent.isVisible && note != null) {
            loadEditorContentIfNeeded(note, deferUntilResumed = false)
        }

        if (textViewContentPreview.isVisible && note != null) {
            renderContentPreview(note)
        }

        val shouldDisplayFAB = data.showFabChangeMode && !isNoteDeleted && !noteHasEmptyContent
        when {
            fabChangeMode.isVisible == shouldDisplayFAB -> { /* FAB is already like it should be, no reason to animate */
            }

            fabChangeMode.isVisible && !shouldDisplayFAB -> fabChangeMode.hide()
            else -> fabChangeMode.show()
        }

        fabChangeMode.setImageResource(if (model.inEditMode) R.drawable.ic_show else R.drawable.ic_pencil)
        bottomToolbar.menu.findItem(R.id.action_preview)?.setIcon(
            if (model.inEditMode) R.drawable.ic_show else R.drawable.ic_pencil,
        )
        setMarkdownToolbarVisibility(note)
        Log.d(
            QUILLPAD_PERF_TAG,
            "updateEditMode end elapsed=${SystemClock.uptimeMillis() - modeStart}ms " +
                "edit=${model.inEditMode} editorVisible=${editTextContent.isVisible} " +
                "previewVisible=${textViewContentPreview.isVisible}",
        )
    }

    private fun renderContentPreview(note: Note) = with(binding) {
        val requestAt = SystemClock.uptimeMillis()
        val renderKey = note.isMarkdownEnabled to note.content
        Log.d(
            QUILLPAD_PERF_TAG,
            "preview request contentLen=${note.content.length} markdown=${note.isMarkdownEnabled} " +
                "edit=${model.inEditMode} visible=${textViewContentPreview.isVisible} " +
                "cached=${lastRenderedPreview == renderKey} ${quillpadMemorySnapshot()}",
        )
        if (lastRenderedPreview == renderKey) {
            Log.d(QUILLPAD_PERF_TAG, "preview skip cached contentLen=${note.content.length}")
            return@with
        }
        if (shouldUseQuillpadLargePlainPreview(note.content.length)) {
            val fallbackStart = SystemClock.uptimeMillis()
            val previewText = buildQuillpadLargePlainPreview(note.content)
            textViewContentPreview.text = previewText
            lastRenderedPreview = renderKey
            textViewContentPreview.post {
                findQuillpadEditorHost()?.onEditorContentReady("largePreview")
            }
            Log.d(
                QUILLPAD_PERF_TAG,
                "preview large fallback contentLen=${note.content.length} visibleLen=${previewText.length} " +
                    "elapsed=${SystemClock.uptimeMillis() - fallbackStart}ms ${quillpadMemorySnapshot()}",
            )
            return@with
        }
        if (note.isMarkdownEnabled) {
            // Resolve KardLeaf/Obsidian local image references off the main thread before Markwon renders.
            viewLifecycleOwner.lifecycleScope.launch {
                val resolvedContent = runCatching {
                    findQuillpadEditorHost()
                        ?.resolvePreviewMarkdown(note.content)
                        ?: note.content
                }.onFailure { error ->
                    Log.e(QUILLPAD_PERF_TAG, "preview image resolve failed", error)
                }.getOrDefault(note.content)
                val runAt = SystemClock.uptimeMillis()
                val contentStillCurrent = data.note?.content == note.content
                Log.d(
                    QUILLPAD_PERF_TAG,
                    "preview resolve complete contentLen=${note.content.length} resolvedLen=${resolvedContent.length} " +
                        "queueDelay=${runAt - requestAt}ms edit=${model.inEditMode} list=$isList " +
                        "current=$contentStillCurrent view=${textViewContentPreview.width}x${textViewContentPreview.height}",
                )
                if (!model.inEditMode && !isList && contentStillCurrent) {
                    val applyStart = SystemClock.uptimeMillis()
                    Log.d(
                        QUILLPAD_PERF_TAG,
                        "preview applyTo start contentLen=${note.content.length} resolvedLen=${resolvedContent.length} " +
                            quillpadMemorySnapshot(),
                    )
                    markwon.applyTo(textViewContentPreview, resolvedContent) {
                        tableReplacement = { Code(getString(R.string.message_cannot_preview_table)) }
                        maximumTableColumns = 15
                    }
                    val applyEnd = SystemClock.uptimeMillis()
                    Log.d(
                        QUILLPAD_PERF_TAG,
                        "preview applyTo end contentLen=${note.content.length} " +
                            "elapsed=${applyEnd - applyStart}ms textLen=${textViewContentPreview.text.length} " +
                            "layoutReady=${textViewContentPreview.layout != null} " +
                            "lineCount=${textViewContentPreview.layout?.lineCount ?: -1} " +
                            quillpadMemorySnapshot(),
                    )
                    textViewContentPreview.post {
                        Log.d(
                            QUILLPAD_PERF_TAG,
                            "preview postLayout elapsedFromApply=${SystemClock.uptimeMillis() - applyStart}ms " +
                                "view=${textViewContentPreview.width}x${textViewContentPreview.height} " +
                                "layoutHeight=${textViewContentPreview.layout?.height ?: -1} " +
                                "lineCount=${textViewContentPreview.layout?.lineCount ?: -1} " +
                                "scroll=${scrollView.width}x${scrollView.height} scrollY=${scrollView.scrollY} " +
                                quillpadMemorySnapshot(),
                        )
                        findQuillpadEditorHost()?.onEditorContentReady("markdownPreview")
                    }
                    lastRenderedPreview = renderKey
                } else {
                    Log.d(
                        QUILLPAD_PERF_TAG,
                        "preview resolve skip contentLen=${note.content.length} edit=${model.inEditMode} " +
                            "list=$isList current=$contentStillCurrent",
                    )
                }
            }
        } else {
            val plainStart = SystemClock.uptimeMillis()
            textViewContentPreview.text = note.content
            Log.d(
                QUILLPAD_PERF_TAG,
                "preview plain setText end contentLen=${note.content.length} " +
                    "elapsed=${SystemClock.uptimeMillis() - plainStart}ms ${quillpadMemorySnapshot()}",
            )
            lastRenderedPreview = renderKey
            textViewContentPreview.post {
                findQuillpadEditorHost()?.onEditorContentReady("plainPreview")
            }
        }
    }

    private fun hasNoteEmptyContent(note: Note? = data.note): Boolean {
        return note?.content?.isBlank() == true || (note?.isList == true && note.taskList.isEmpty())
    }

    private fun uncheckAllTasks() {
        val updatedTasks = tasksAdapter.tasks.map { task ->
            task.copy(isDone = false)
        }
        tasksAdapter.submitList(updatedTasks)

        model.updateTaskList(updatedTasks)
    }

    private fun removeAllCheckedTasks() {
        val updatedTasks = tasksAdapter.tasks.filter { task ->
            !task.isDone
        }
        tasksAdapter.submitList(updatedTasks)

        model.updateTaskList(updatedTasks)
    }

    private val NoteColor.localizedName
        get() = getString(
            when (this) {
                NoteColor.Default -> R.string.default_string
                NoteColor.Red -> R.string.preferences_color_scheme_red
                NoteColor.Orange -> R.string.preferences_color_scheme_orange
                NoteColor.Yellow -> R.string.preferences_color_scheme_yellow
                NoteColor.Green -> R.string.preferences_color_scheme_green
                NoteColor.Teal -> R.string.preferences_color_scheme_teal
                NoteColor.Cyan -> R.string.preferences_color_scheme_cyan
                NoteColor.Blue -> R.string.preferences_color_scheme_blue
                NoteColor.Purple -> R.string.preferences_color_scheme_purple
                NoteColor.Pink -> R.string.preferences_color_scheme_pink
                NoteColor.Brown -> R.string.preferences_color_scheme_brown
                NoteColor.Gray -> R.string.preferences_color_scheme_gray
            }
        )

    companion object {
        const val MARKDOWN_DIALOG_RESULT = "MARKDOWN_DIALOG_RESULT"
    }
}
