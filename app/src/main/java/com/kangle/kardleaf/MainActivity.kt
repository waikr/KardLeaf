package com.kangle.kardleaf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kangle.kardleaf.data.receiver.VaultChangeObserver
import com.kangle.kardleaf.data.repository.MetadataManager
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.ui.DashboardScreen
import com.kangle.kardleaf.ui.EditorScreen
import com.kangle.kardleaf.ui.MainViewModel
import com.kangle.kardleaf.ui.KardLeafCustomFeatures
import com.kangle.kardleaf.ui.theme.KardLeafTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private const val STARTUP_PERF_TRACE_TAG = "KardLeafStartupPerf"
private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val REQUEST_OPEN_DOCUMENT_TREE = 1101
private const val REQUEST_EXPORT_USER_DATA = 1102
private const val REQUEST_IMPORT_USER_DATA = 1103
private const val REQUEST_EXPORT_PRIVACY = 1104
private const val REQUEST_IMPORT_PRIVACY = 1105
private const val REQUEST_PICK_IMAGE_FOLDER = 1106
private const val REQUEST_PICK_BACKUP_FOLDER = 1107
private const val REQUEST_PICK_EDITOR_IMAGE = 1108

class MainActivity : FragmentActivity() {
    private lateinit var repository: RoomNoteRepository
    private lateinit var metadataManager: MetadataManager
    private lateinit var prefsManager: PrefsManager
    private var vaultChangeObserver: VaultChangeObserver? = null
    private var hasCompletedFirstResume = false
    private var pendingUserDataExport: String? = null
    private var pendingPrivacyExport: String? = null
    private var pendingImageFolderPicker: ((Uri) -> Unit)? = null
    private var pendingBackupFolderPicker: ((Uri) -> Unit)? = null
    private var pendingEditorImagePicker: ((Uri) -> Unit)? = null

    private val viewModel by viewModels<MainViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository, metadataManager, prefsManager) as T
            }
        }
    }

    private fun createOpenDocumentTreeIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
        }

    private fun launchOpenDocumentTree() {
        startActivityForResult(createOpenDocumentTreeIntent(), REQUEST_OPEN_DOCUMENT_TREE)
    }

    private fun launchImageFolderPicker(onPicked: (Uri) -> Unit) {
        pendingImageFolderPicker = onPicked
        startActivityForResult(createOpenDocumentTreeIntent(), REQUEST_PICK_IMAGE_FOLDER)
    }

    private fun launchBackupFolderPicker(onPicked: (Uri) -> Unit) {
        pendingBackupFolderPicker = onPicked
        startActivityForResult(createOpenDocumentTreeIntent(), REQUEST_PICK_BACKUP_FOLDER)
    }

    private fun launchEditorImagePicker(onPicked: (Uri) -> Unit) {
        pendingEditorImagePicker = onPicked
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PICK_EDITOR_IMAGE)
    }

    private fun launchCreateJsonDocument(fileName: String, requestCode: Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        startActivityForResult(intent, requestCode)
    }

    private fun launchOpenJsonDocument(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, requestCode)
    }

    private fun handleRootFolderSelected(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )

        prefsManager.saveRootUri(uri.toString())

        Toast.makeText(this, "正在导入...", Toast.LENGTH_SHORT).show()
        viewModel.setRootFolder(uri)
        vaultChangeObserver?.start(uri)
    }

    private fun handleUserDataExportUri(uri: Uri) {
        val backup = pendingUserDataExport
        pendingUserDataExport = null
        if (backup != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(backup.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open export file")
            }.onSuccess {
                Toast.makeText(this, "导出完成", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleUserDataImportUri(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Cannot open import file")
        }.onSuccess { json ->
            viewModel.importUserDataBackup(
                json = json,
                onSuccess = {
                    Toast.makeText(this, "导入完成", Toast.LENGTH_SHORT).show()
                },
                onError = { error ->
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                },
            )
        }.onFailure { error ->
            Toast.makeText(this, error.message ?: "导入失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePrivacyExportUri(uri: Uri) {
        val backup = pendingPrivacyExport
        pendingPrivacyExport = null
        if (backup != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(backup.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open export file")
            }.onSuccess {
                Toast.makeText(this, "隐私笔记导出完成", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePrivacyImportUri(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Cannot open import file")
        }.onSuccess { json ->
            viewModel.importPrivacyNotes(
                json = json,
                onSuccess = { count -> Toast.makeText(this, "已导入 $count 条隐私笔记", Toast.LENGTH_SHORT).show() },
                onError = { error -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show() },
            )
        }.onFailure { error ->
            Toast.makeText(this, error.message ?: "导入失败", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_OPEN_DOCUMENT_TREE -> handleRootFolderSelected(uri)
            REQUEST_EXPORT_USER_DATA -> handleUserDataExportUri(uri)
            REQUEST_IMPORT_USER_DATA -> handleUserDataImportUri(uri)
            REQUEST_EXPORT_PRIVACY -> handlePrivacyExportUri(uri)
            REQUEST_IMPORT_PRIVACY -> handlePrivacyImportUri(uri)
            REQUEST_PICK_IMAGE_FOLDER -> pendingImageFolderPicker?.invoke(uri).also { pendingImageFolderPicker = null }
            REQUEST_PICK_BACKUP_FOLDER -> pendingBackupFolderPicker?.invoke(uri).also { pendingBackupFolderPicker = null }
            REQUEST_PICK_EDITOR_IMAGE -> pendingEditorImagePicker?.invoke(uri).also { pendingEditorImagePicker = null }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val startupStartMs = SystemClock.elapsedRealtime()
        Log.d(
            STARTUP_PERF_TRACE_TAG,
            "activity onCreate start savedState=${savedInstanceState != null} thread=${Thread.currentThread().name}",
        )
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        metadataManager = MetadataManager(applicationContext)
        prefsManager = PrefsManager(applicationContext)
        repository = RoomNoteRepository(applicationContext, metadataManager, prefsManager)
        Log.d(
            STARTUP_PERF_TRACE_TAG,
            "activity repository ready elapsed=${SystemClock.elapsedRealtime() - startupStartMs}ms rootUriSaved=${prefsManager.getRootUri() != null}",
        )
        vaultChangeObserver =
            VaultChangeObserver(
                context = this,
                scope = lifecycleScope,
                onChanged = { changedUri ->
                    viewModel.onExternalVaultChanged(forceContentReloadFallback = true, changedUri = changedUri)
                },
            )

        handleIntent(intent)

        val savedRootUri = getPersistedRootUriWithPermission()
        if (savedRootUri != null) {
            Log.d(
                STARTUP_PERF_TRACE_TAG,
                "activity setRootFolder savedUri scanImmediately=false elapsed=${SystemClock.elapsedRealtime() - startupStartMs}ms",
            )
            viewModel.setRootFolder(savedRootUri, scanImmediately = false)
            vaultChangeObserver?.start(savedRootUri)
        } else if (prefsManager.getRootUri() != null) {
            viewModel.resetPermissionNeeded()
        }

        maybeRunAutoBackup()

        Log.d(
            STARTUP_PERF_TRACE_TAG,
            "activity setContent start elapsed=${SystemClock.elapsedRealtime() - startupStartMs}ms",
        )
        setContent {
            var themeRevision by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                Log.d(
                    STARTUP_PERF_TRACE_TAG,
                    "compose first composition elapsed=${SystemClock.elapsedRealtime() - startupStartMs}ms",
                )
            }
            KardLeafTheme(themeRevision = themeRevision) {
                var appUnlocked by remember {
                    mutableStateOf(prefsManager.getAppPasswordHash() == null)
                }
                if (!appUnlocked) {
                    AppPasswordLockScreen { appUnlocked = true }
                } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val isEditorOpen by viewModel.isEditorOpen.collectAsState()
                    var showPrivacy by remember { mutableStateOf(false) }
                    val currentScreen by viewModel.currentScreen.collectAsState()
                    val currentFilter by viewModel.currentFilter.collectAsState()
                    val labels by viewModel.labels.collectAsState()
                    val yamlTags by viewModel.yamlTags.collectAsState()
                    var dashboardFilterReturnScreen by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf<MainViewModel.Screen?>(null)
                    }
                    var dashboardFilterReturnFilter by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf<MainViewModel.NoteFilter?>(null)
                    }
                    fun clearTemporaryDashboardReturn() {
                        dashboardFilterReturnScreen = null
                        dashboardFilterReturnFilter = null
                    }
                    var showCreateLabelDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    var createLabelParent by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                    var labelToDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

                    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    // 首次启动自动弹出新手引导；侧边栏“使用介绍”入口复用同一 Composable。
                    var showOnboarding by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(!prefsManager.hasSeenOnboarding())
                    }
                    val dismissOnboardingAndPersist: () -> Unit = {
                        prefsManager.setHasSeenOnboarding(true)
                        showOnboarding = false
                    }
                    var drawerEdgeWidthDp by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(prefsManager.getDrawerEdgeWidthDp())
                    }
                    var drawerOpenBlockedUntil by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(0L)
                    }
                    var drawerContentBlockedUntil by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(0L)
                    }
                    var drawerBackAction by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf<(() -> Boolean)?>(null)
                    }
                    fun blockDrawerOpenBriefly() {
                        drawerOpenBlockedUntil = SystemClock.uptimeMillis() + 700L
                    }
                    fun openDrawerIfAllowed() {
                        if (isEditorOpen || SystemClock.uptimeMillis() < drawerOpenBlockedUntil) return
                        drawerContentBlockedUntil = SystemClock.uptimeMillis() + 700L
                        scope.launch { drawerState.open() }
                    }
                    val edgeDrawerWidthPx = with(LocalDensity.current) { drawerEdgeWidthDp.dp.toPx() }
                    fun isDrawerContentBlocked(): Boolean =
                        SystemClock.uptimeMillis() < drawerContentBlockedUntil ||
                            drawerState.currentValue != androidx.compose.material3.DrawerValue.Closed ||
                            drawerState.targetValue != androidx.compose.material3.DrawerValue.Closed
                    val closeDrawerThen: (() -> Unit) -> Unit = { action ->
                        scope.launch {
                            if (!drawerState.isClosed) {
                                drawerState.close()
                            }
                            action()
                        }
                    }
                    val selectFilterFromDrawer: (MainViewModel.NoteFilter) -> Unit = { filter ->
                        clearTemporaryDashboardReturn()
                        scope.launch {
                            if (!drawerState.isClosed) {
                                drawerState.close()
                            }
                            if (currentScreen != MainViewModel.Screen.Dashboard) {
                                viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                            }
                            if (currentFilter != filter) {
                                viewModel.setFilter(filter)
                            }
                        }
                    }

                    if (showCreateLabelDialog) {
                        com.kangle.kardleaf.ui.CreateLabelDialog(
                            onDismiss = {
                                createLabelParent = ""
                                showCreateLabelDialog = false
                            },
                            onConfirm = { name ->
                                val trimmed = name.trim()
                                val target =
                                    listOf(createLabelParent, trimmed)
                                        .filter { it.isNotBlank() }
                                        .joinToString("/")
                                if (target.isNotBlank()) {
                                    viewModel.createLabel(target)
                                }
                                createLabelParent = ""
                                showCreateLabelDialog = false
                            },
                        )
                    }

                    if (labelToDelete != null) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { labelToDelete = null },
                            title = {
                                androidx.compose.material3.Text(
                                    androidx.compose.ui.res.stringResource(R.string.delete_label_title),
                                )
                            },
                            text = {
                                androidx.compose.material3.Text(
                                    androidx.compose.ui.res.stringResource(R.string.delete_label_message, labelToDelete!!),
                                )
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    val name = labelToDelete!!
                                    viewModel.deleteLabel(
                                        name = name,
                                        onSuccess = {
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.label_deleted_toast),
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                        onError = { error ->
                                            val localizedError =
                                                if (error == "Label must be empty to delete it") {
                                                    context.getString(R.string.error_delete_label_not_empty)
                                                } else {
                                                    error
                                                }
                                            android.widget.Toast.makeText(context, localizedError, android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                    labelToDelete = null
                                }) {
                                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.delete_label_confirm))
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { labelToDelete = null }) {
                                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.cancel))
                                }
                            },
                        )
                    }

                    if (showOnboarding) {
                        com.kangle.kardleaf.ui.OnboardingDialog(
                            onDismiss = dismissOnboardingAndPersist,
                            onFinish = dismissOnboardingAndPersist,
                            onStepChanged = { target ->
                                when (target) {
                                    com.kangle.kardleaf.ui.OnboardingTourTarget.Home -> {
                                        if (currentScreen != MainViewModel.Screen.Dashboard) {
                                            viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                                        }
                                        if (!drawerState.isClosed) {
                                            scope.launch { drawerState.close() }
                                        }
                                    }
                                    com.kangle.kardleaf.ui.OnboardingTourTarget.Drawer -> {
                                        if (currentScreen != MainViewModel.Screen.Dashboard) {
                                            viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                                        }
                                        scope.launch { drawerState.open() }
                                    }
                                    com.kangle.kardleaf.ui.OnboardingTourTarget.Settings -> {
                                        if (!drawerState.isClosed) {
                                            scope.launch { drawerState.close() }
                                        }
                                        if (currentScreen != MainViewModel.Screen.Settings) {
                                            viewModel.navigateTo(MainViewModel.Screen.Settings)
                                        }
                                    }
                                    com.kangle.kardleaf.ui.OnboardingTourTarget.History -> {
                                        if (!drawerState.isClosed) {
                                            scope.launch { drawerState.close() }
                                        }
                                        if (currentScreen != MainViewModel.Screen.Dashboard) {
                                            viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                                        }
                                    }
                                }
                            },
                        )
                    } else {
                        androidx.compose.material3.ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = !isEditorOpen && drawerState.isOpen,
                        drawerContent = {
                            com.kangle.kardleaf.ui.AppDrawerContent(
                                currentScreen = currentScreen,
                                currentFilter = currentFilter,
                                labels = labels,
                                onScreenSelect = { screen ->
                                    clearTemporaryDashboardReturn()
                                    closeDrawerThen {
                                        viewModel.navigateTo(screen)
                                    }
                                },
                                onDashboardFilterSelect = selectFilterFromDrawer,
                                onCreateLabel = { parent ->
                                    createLabelParent = parent
                                    showCreateLabelDialog = true
                                },
                                onDeleteLabel = { name -> labelToDelete = name },
                                onRenameLabel = { oldPath, newPath ->
                                    viewModel.renameLabel(
                                        oldPath = oldPath,
                                        newPath = newPath,
                                        onError = { error ->
                                            android.widget.Toast.makeText(
                                                this@MainActivity,
                                                error,
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    )
                                },
                                onOpenSettings = {
                                    clearTemporaryDashboardReturn()
                                    closeDrawerThen {
                                        viewModel.navigateTo(MainViewModel.Screen.Settings)
                                    }
                                },
                                onBackActionChanged = { drawerBackAction = it },
                                onShowOnboarding = {
                                    closeDrawerThen { showOnboarding = true }
                                },
                                onOpenPrivacy = {
                                    clearTemporaryDashboardReturn()
                                    closeDrawerThen { showPrivacy = true }
                                },
                            )
                        },
                    ) {
                        androidx.activity.compose.BackHandler(enabled = drawerState.isOpen) {
                            Log.d(
                                BACK_TRACE_TAG,
                                "Main drawer BackHandler hit showCreateLabelDialog=$showCreateLabelDialog " +
                                    "labelToDelete=${labelToDelete != null} drawerBackAction=${drawerBackAction != null}",
                            )
                            when {
                                showCreateLabelDialog -> {
                                    createLabelParent = ""
                                    showCreateLabelDialog = false
                                }
                                labelToDelete != null -> labelToDelete = null
                                drawerBackAction?.invoke() == true -> Unit
                                else -> scope.launch { drawerState.close() }
                            }
                        }

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(isEditorOpen, currentScreen, drawerState.isClosed, edgeDrawerWidthPx, drawerOpenBlockedUntil, drawerContentBlockedUntil) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            if (
                                                SystemClock.uptimeMillis() < drawerOpenBlockedUntil ||
                                                isEditorOpen ||
                                                currentScreen is MainViewModel.Screen.Settings ||
                                                isDrawerContentBlocked() ||
                                                down.position.x > edgeDrawerWidthPx
                                            ) {
                                                return@awaitEachGesture
                                            }

                                            // 用手动 awaitPointerEvent 追踪代替 awaitHorizontalTouchSlopOrCancellation，
                                            // 避免子 HorizontalPager 消费水平拖拽后导致手势被取消
                                            val touchSlop = viewConfiguration.touchSlop
                                            var shouldOpenDrawer = false
                                            var pointerPressed = true
                                            while (pointerPressed && !shouldOpenDrawer) {
                                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                pointerPressed = change?.pressed == true
                                                if (change != null && pointerPressed) {
                                                    val dragX = change.position.x - down.position.x
                                                    if (dragX > touchSlop) {
                                                        change.consume()
                                                        shouldOpenDrawer = true
                                                    }
                                                } else if (event.type == PointerEventType.Release && change != null) {
                                                    pointerPressed = false
                                                }
                                            }
                                            if (shouldOpenDrawer) {
                                                drawerContentBlockedUntil = SystemClock.uptimeMillis() + 700L
                                                scope.launch { drawerState.open() }
                                            }
                                        }
                                    },
                        ) {
                            androidx.activity.compose.BackHandler(
                                enabled = !drawerState.isOpen &&
                                    (currentScreen is MainViewModel.Screen.Dates ||
                                        currentScreen is MainViewModel.Screen.Images ||
                                        currentScreen is MainViewModel.Screen.Tags),
                            ) {
                                Log.d(BACK_TRACE_TAG, "Main screen BackHandler hit screen=$currentScreen -> Dashboard")
                                viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                            }

                            when (currentScreen) {
                                MainViewModel.Screen.Dashboard -> {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        isDrawerOpen = drawerState.isOpen,
                                        onSelectFolder = {
                                            launchOpenDocumentTree()
                                        },
                                        edgeDrawerWidthPx = edgeDrawerWidthPx,
                                onNoteClick = { note ->
                                            if (!isDrawerContentBlocked()) {
                                                blockDrawerOpenBriefly()
                                                viewModel.openNote(note)
                                            }
                                        },
                                        onFabClick = {
                                            if (!isDrawerContentBlocked()) {
                                                blockDrawerOpenBriefly()
                                                viewModel.createNote(source = "dashboard_fab")
                                            }
                                        },
                                        onOpenDrawer = { openDrawerIfAllowed() },
                                        onBackFromTemporaryFilter = { filter ->
                                            val returnScreen = dashboardFilterReturnScreen
                                            val returnFilter = dashboardFilterReturnFilter
                                            if (returnScreen != null && filter is MainViewModel.NoteFilter.YamlTag) {
                                                clearTemporaryDashboardReturn()
                                                if (returnFilter != null) {
                                                    viewModel.setFilter(returnFilter)
                                                }
                                                viewModel.navigateTo(returnScreen)
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    )
                                }
                                MainViewModel.Screen.Dates -> {
                                    com.kangle.kardleaf.ui.DateNotesScreen(
                                        viewModel = viewModel,
                                        onOpenDrawer = { openDrawerIfAllowed() },
                                        onNoteClick = { note ->
                                            if (!isDrawerContentBlocked()) {
                                                blockDrawerOpenBriefly()
                                                viewModel.openNote(note)
                                            }
                                        },
                                    )
                                }
                                MainViewModel.Screen.Images -> {
                                    com.kangle.kardleaf.ui.NoteImagesScreen(
                                        viewModel = viewModel,
                                        onOpenDrawer = { openDrawerIfAllowed() },
                                        onNoteClick = { note ->
                                            if (!isDrawerContentBlocked()) {
                                                blockDrawerOpenBriefly()
                                                viewModel.openNote(note)
                                            }
                                        },
                                    )
                                }
                                MainViewModel.Screen.Tags -> {
                                    com.kangle.kardleaf.ui.TagManagementScreen(
                                        tags = yamlTags,
                                        allNotes = viewModel.allNotes.collectAsState(initial = emptyList()).value,
                                        onOpenDrawer = { openDrawerIfAllowed() },
                                        onTagClick = { tag ->
                                            dashboardFilterReturnScreen = MainViewModel.Screen.Tags
                                            dashboardFilterReturnFilter = currentFilter
                                            viewModel.setFilter(MainViewModel.NoteFilter.YamlTag(tag))
                                            viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                                        },
                                        onRenameTag = { oldTag, newTag -> viewModel.renameYamlTag(oldTag, newTag) },
                                        onDeleteTag = { tag -> viewModel.deleteYamlTag(tag) },
                                    )
                                }
                            MainViewModel.Screen.Settings -> {
                                com.kangle.kardleaf.ui.KardLeafSettingsScreen(
                                    onBack = { viewModel.navigateTo(MainViewModel.Screen.Dashboard) },
                                    onSelectDatabase = {
                                        launchOpenDocumentTree()
                                    },
                                    onSettingsChanged = {
                                        drawerEdgeWidthDp = prefsManager.getDrawerEdgeWidthDp()
                                        viewModel.reloadCustomSettings()
                                    },
                                    onRestartNeeded = { themeRevision += 1 },
                                    onExportUserData = { exportUserDataBackup() },
                                    onImportUserData = {
                                        launchOpenJsonDocument(REQUEST_IMPORT_USER_DATA)
                                    },
                                    onSelectImageFolder = { onPicked -> launchImageFolderPicker(onPicked) },
                                    onSelectBackupDir = { onPicked -> launchBackupFolderPicker(onPicked) },
                                    onLoadHistoryCleanupPreview = { keep -> viewModel.getHistoryCleanupPreview(keep) },
                                    onLoadRemarkNoteSummaries = { viewModel.getRemarkNoteSummaries() },
                                    onLoadHistoryNoteSummaries = { viewModel.getHistoryNoteSummaries() },
                                    onCleanupHistory = { viewModel.cleanupOldHistoryVersions() },
                                    labels = labels,
                                )
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                                visible = isEditorOpen,
                                enter = slideInHorizontally { width -> width },
                                exit = slideOutHorizontally { width -> width },
                                label = "EditorTransition",
                            ) {
                                val filter = viewModel.currentFilter.value
                                val label = if (filter is MainViewModel.NoteFilter.Label) filter.name else ""

                                EditorScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.closeEditor() },
                                    initialLabel = label,
                                    onPickImage = { onPicked -> launchEditorImagePicker(onPicked) },
                                )
                            }

                            if (showPrivacy) {
                                com.kangle.kardleaf.ui.PrivacyScreen(
                                    viewModel = viewModel,
                                    onBack = { showPrivacy = false },
                                    onExport = { exportPrivacyBackup() },
                                    onImport = { launchOpenJsonDocument(REQUEST_IMPORT_PRIVACY) },
                                    onPickImage = { onPicked -> launchEditorImagePicker(onPicked) },
                                )
                            }
                        }
                    }
                    }
                }
                }
            }
        }
        window.decorView.post {
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        Log.d(BACK_TRACE_TAG, "Activity dispatcher probe hit before forwarding")
                        isEnabled = false
                        try {
                            onBackPressedDispatcher.onBackPressed()
                        } finally {
                            isEnabled = true
                            Log.d(BACK_TRACE_TAG, "Activity dispatcher probe restored after forwarding")
                        }
                    }
                },
            )
            Log.d(BACK_TRACE_TAG, "Activity dispatcher probe registered after first frame")
        }
        Log.d(
            STARTUP_PERF_TRACE_TAG,
            "activity setContent returned elapsed=${SystemClock.elapsedRealtime() - startupStartMs}ms",
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(
                BACK_TRACE_TAG,
                "Activity dispatchKeyEvent back action=${event.action} repeat=${event.repeatCount}",
            )
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        Log.d(BACK_TRACE_TAG, "Activity onBackPressed enter")
        super.onBackPressed()
        Log.d(BACK_TRACE_TAG, "Activity onBackPressed exit")
    }

    override fun onResume() {
        val resumeStartMs = SystemClock.elapsedRealtime()
        Log.d(STARTUP_PERF_TRACE_TAG, "activity onResume start firstResume=${!hasCompletedFirstResume}")
        super.onResume()
        val persistedRootUri = getPersistedRootUriWithPermission()
        persistedRootUri?.let { uri ->
            vaultChangeObserver?.start(uri)
            if (hasCompletedFirstResume) {
                Log.d(
                    STARTUP_PERF_TRACE_TAG,
                    "activity onResume external refresh trigger elapsed=${SystemClock.elapsedRealtime() - resumeStartMs}ms",
                )
                viewModel.onExternalVaultChanged(forceContentReloadFallback = false)
            }
        }
        hasCompletedFirstResume = true
        Log.d(
            STARTUP_PERF_TRACE_TAG,
            "activity onResume done elapsed=${SystemClock.elapsedRealtime() - resumeStartMs}ms persistedRoot=${persistedRootUri != null}",
        )
    }

    override fun onDestroy() {
        vaultChangeObserver?.stop()
        vaultChangeObserver = null
        super.onDestroy()
    }

    private fun getPersistedRootUriWithPermission(): Uri? {
        val savedUriStr = prefsManager.getRootUri() ?: run {
            return null
        }
        val uri = Uri.parse(savedUriStr)
        val hasPermission =
            contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == uri && permission.isReadPermission
            }

        return if (hasPermission) uri else null
    }

    private fun handleIntent(intent: Intent) {
        KardLeafCustomFeatures.parseExternalCreateNoteUri(intent.data)?.let { draft ->
            viewModel.createNote(draft, source = "external_intent")
            return
        }

        intent.getStringExtra("note_id")?.let { noteId ->
            lifecycleScope.launch {
                val note = repository.getNote(noteId)
                if (note != null) {
                    viewModel.openNote(note)
                }
            }
        }
    }

    private fun exportUserDataBackup() {
        viewModel.exportUserDataBackup(
            onSuccess = { json ->
                pendingUserDataExport = json
                launchCreateJsonDocument("KardLeaf-user-data.json", REQUEST_EXPORT_USER_DATA)
            },
            onError = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun exportPrivacyBackup() {
        viewModel.exportPrivacyNotes(
            onSuccess = { json ->
                pendingPrivacyExport = json
                launchCreateJsonDocument("KardLeaf-privacy.json", REQUEST_EXPORT_PRIVACY)
            },
            onError = { error -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show() },
        )
    }

    private fun maybeRunAutoBackup() {
        val intervalDays = prefsManager.getAutoBackupIntervalDays()
        if (intervalDays <= 0) return
        val dirUri = prefsManager.getAutoBackupDirUri() ?: return
        val now = System.currentTimeMillis()
        val intervalMs = intervalDays * 24L * 60L * 60L * 1000L
        if (now - prefsManager.getAutoBackupLastMs() < intervalMs) return
        lifecycleScope.launch {
            try {
                val json = repository.exportUserDataBackup()
                val dir = runCatching {
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(this@MainActivity, Uri.parse(dirUri))
                }.getOrNull()
                if (dir == null || !dir.canWrite()) return@launch
                val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date(now))
                val file = dir.createFile("application/json", "KardLeaf-backup-$ts.json") ?: return@launch
                contentResolver.openOutputStream(file.uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                prefsManager.saveAutoBackupLastMs(now)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Auto backup failed", e)
            }
        }
    }
}

@Composable
private fun AppPasswordLockScreen(onUnlocked: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    var pwd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val canUseBiometric =
        prefsManager.isAppBiometricUnlockEnabled() && com.kangle.kardleaf.ui.isBiometricUnlockAvailable(context)

    fun unlockWithBiometric() {
        com.kangle.kardleaf.ui.showBiometricUnlockPrompt(
            context = context,
            title = "应用指纹解锁",
            subtitle = "验证后进入 KardLeaf",
            onSuccess = onUnlocked,
            onError = { error = it },
        )
    }

    fun verifyAppPassword(input: String) {
        val inputHash = com.kangle.kardleaf.ui.hashPassword(input)
        if (inputHash == prefsManager.getAppPasswordHash() || inputHash == prefsManager.getSafetyWordHash()) {
            onUnlocked()
        } else {
            error = "密码或安全词错误"
            if (prefsManager.getPasswordInputMode() == PrefsManager.PasswordInputMode.SIMPLE) {
                pwd = ""
            }
        }
    }

    com.kangle.kardleaf.ui.PasswordLockCardScreen(
        screenTitle = "应用锁",
        headline = "欢迎回来",
        description = "输入应用密码后继续使用卡叶笔记。",
        passwordLabel = if (prefsManager.getSafetyWordHash() != null) "密码或安全词" else "密码",
        password = pwd,
        onPasswordChange = { pwd = it },
        primaryButtonText = "解锁",
        onPasswordSubmit = { verifyAppPassword(pwd) },
        onSimplePasswordComplete = { completed -> verifyAppPassword(completed) },
        errorMessage = error,
        biometricAvailable = canUseBiometric,
        onBiometricUnlock = { unlockWithBiometric() },
        autoShowBiometric = canUseBiometric,
        passwordInputMode = prefsManager.getPasswordInputMode(),
    )
}
