package com.kangle.kardleaf

import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.KardLeafLogTags
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.documentfile.provider.DocumentFile
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.receiver.VaultChangeObserver
import com.kangle.kardleaf.data.repository.MetadataManager
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.data.sync.WebDavCloudSyncManager
import com.kangle.kardleaf.ui.DashboardScreen
import com.kangle.kardleaf.ui.EditorScreen
import com.kangle.kardleaf.ui.KardLeafMotion
import com.kangle.kardleaf.ui.MainViewModel
import com.kangle.kardleaf.ui.KardLeafCustomFeatures
import com.kangle.kardleaf.ui.kardLeafHorizontalContentTransform
import com.kangle.kardleaf.ui.kardLeafSharedAxisXIn
import com.kangle.kardleaf.ui.kardLeafSharedAxisXOut
import com.kangle.kardleaf.ui.kardLeafSharedAxisYIn
import com.kangle.kardleaf.ui.kardLeafSharedAxisYOut
import com.kangle.kardleaf.ui.theme.KardLeafTheme
import com.kangle.kardleaf.widget.NoteListWidgetProvider
import com.kangle.kardleaf.widget.TaskListWidgetProvider
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
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

private val STARTUP_PERF_TRACE_TAG = KardLeafLogTags.STARTUP_PERF
private val USER_PERF_TRACE_TAG = KardLeafLogTags.USER_PERF
private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val WEBDAV_REALTIME_SYNC_TAG = "KardLeafWebDavRealtime"
private const val WEBDAV_REALTIME_UPLOAD_DELAY_MS = 2_500L
private const val WEBDAV_REALTIME_HEARTBEAT_LOG_INTERVAL_MS = 10_000L
private const val REQUEST_OPEN_DOCUMENT_TREE = 1101
private const val REQUEST_CREATE_SAMPLE_VAULT_PARENT = 1109
private const val REQUEST_EXPORT_USER_DATA = 1102
private const val REQUEST_IMPORT_USER_DATA = 1103
private const val REQUEST_EXPORT_PRIVACY = 1104
private const val REQUEST_IMPORT_PRIVACY = 1105
private const val REQUEST_PICK_IMAGE_FOLDER = 1106
private const val REQUEST_PICK_BACKUP_FOLDER = 1107
private const val REQUEST_PICK_EDITOR_IMAGE = 1108
private const val REQUEST_POST_NOTIFICATIONS = 1110
private const val REQUEST_PICK_DRAWER_AVATAR = 1111
private const val MAX_IMPORT_JSON_BYTES = 25L * 1024L * 1024L

private fun mainScreenMotionIndex(screen: MainViewModel.Screen): Int = when (screen) {
    MainViewModel.Screen.Dashboard -> 0
    MainViewModel.Screen.Dates -> 1
    MainViewModel.Screen.Images -> 2
    MainViewModel.Screen.Tags -> 3
    MainViewModel.Screen.Folders -> 4
    MainViewModel.Screen.Tasks -> 5
    MainViewModel.Screen.Settings -> 6
}

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
    private var editorImagePickerLaunchElapsedMs = 0L
    private var webDavRealtimeSyncJob: Job? = null
    private var sampleVaultCleanupPromptJob: Job? = null
    private var latestSampleVaultUri: Uri? = null
    private val sampleVaultCleanupPromptRequest = MutableStateFlow(0L)
    private val drawerAvatarRevisionRequest = MutableStateFlow(0L)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrap(newBase))
    }

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

    private fun launchCreateSampleVaultParentPicker() {
        startActivityForResult(createOpenDocumentTreeIntent(), REQUEST_CREATE_SAMPLE_VAULT_PARENT)
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
        editorImagePickerLaunchElapsedMs = SystemClock.elapsedRealtime()
        KardLeafLog.d("KardLeafCM6Trace", "[insert-image] picker launch request")
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PICK_EDITOR_IMAGE)
    }

    private fun launchDrawerAvatarPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_DRAWER_AVATAR)
    }

    private fun launchCreateJsonDocument(fileName: String, requestCode: Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        startActivityForResult(intent, requestCode)
    }

    private fun requestInitialNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (prefsManager.wasNotificationPermissionRequested()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            prefsManager.saveNotificationPermissionRequested()
            return
        }
        prefsManager.saveNotificationPermissionRequested()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
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
        persistTreePermission(uri)

        prefsManager.saveRootUri(uri.toString())

        Toast.makeText(this, "正在导入...", Toast.LENGTH_SHORT).show()
        viewModel.setRootFolder(uri)
        vaultChangeObserver?.start(uri)
    }

    private fun handleCreateSampleVaultParentSelected(uri: Uri) {
        persistTreePermission(uri)
        val sampleVaultUri = runCatching { createSampleVault(uri) }
            .onFailure { error ->
                KardLeafLog.e("KardLeafSampleVault", "Create sample vault failed", error)
            }
            .getOrNull()
        if (sampleVaultUri == null) {
            Toast.makeText(this, "新建笔记库失败", Toast.LENGTH_SHORT).show()
            return
        }

        prefsManager.saveRootUri(sampleVaultUri.toString())
        Toast.makeText(this, "已新建 KardLeaf 示例笔记库，正在导入示例笔记", Toast.LENGTH_SHORT).show()
        viewModel.setFilter(MainViewModel.NoteFilter.All)
        viewModel.setRootFolder(sampleVaultUri) {
            viewModel.setFilter(MainViewModel.NoteFilter.All)
            lifecycleScope.launch {
                delay(300L)
                viewModel.refreshNotes()
            }
        }
        vaultChangeObserver?.start(sampleVaultUri)
        scheduleSampleVaultCleanupPrompt(sampleVaultUri)
    }

    private fun scheduleSampleVaultCleanupPrompt(sampleVaultUri: Uri) {
        latestSampleVaultUri = sampleVaultUri
        sampleVaultCleanupPromptJob?.cancel()
        sampleVaultCleanupPromptJob = lifecycleScope.launch {
            delay(20_000L)
            if (latestSampleVaultUri == sampleVaultUri) {
                sampleVaultCleanupPromptRequest.value = SystemClock.uptimeMillis()
            }
        }
    }

    private fun persistTreePermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { error ->
            KardLeafLog.w("MainActivity", "Could not persist tree permission: ${error.message}")
        }
    }

    private fun createSampleVault(parentUri: Uri): Uri? {
        val parent = DocumentFile.fromTreeUri(this, parentUri)?.takeIf { it.canWrite() } ?: return null
        val kardLeafRoot = parent.findFile("KardLeaf")?.takeIf { it.isDirectory }
            ?: parent.createDirectory("KardLeaf")
            ?: return null

        populateSampleVault(kardLeafRoot)

        return kardLeafRoot.uri
    }

    private fun populateSampleVault(kardLeafRoot: DocumentFile) {
        sampleVaultFolders().forEach { folder ->
            val secondLevel = kardLeafRoot.findFile(folder.name)?.takeIf { it.isDirectory }
                ?: kardLeafRoot.createDirectory(folder.name)
                ?: return@forEach
            folder.notes.forEachIndexed { index, note ->
                val title = "示例笔记${index + 1} ${note.titleSuffix}"
                val fileName = "$title.md"
                if (secondLevel.findFile(fileName) == null) {
                    val file = secondLevel.createFile("text/markdown", fileName) ?: return@forEachIndexed
                    val content = buildSecondLevelSampleNoteContent(
                        title = title,
                        folder = folder,
                        note = note,
                    )
                    contentResolver.openOutputStream(file.uri)?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            folder.children.forEach { childName ->
                val thirdLevel = secondLevel.findFile(childName)?.takeIf { it.isDirectory }
                    ?: secondLevel.createDirectory(childName)
                    ?: return@forEach
                repeat(4) { index ->
                    val title = "示例笔记${index + 1} $childName"
                    val fileName = "$title.md"
                    if (thirdLevel.findFile(fileName) == null) {
                        val file = thirdLevel.createFile("text/markdown", fileName) ?: return@repeat
                        val content = buildSampleNoteContent(
                            title = title,
                            secondLevel = folder.name,
                            thirdLevel = childName,
                            index = index + 1,
                        )
                        contentResolver.openOutputStream(file.uri)?.use { output ->
                            output.write(content.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
            }
        }
    }

    private suspend fun clearLatestSampleVaultSamples(): Boolean =
        withContext(Dispatchers.IO) {
            val rootUri = latestSampleVaultUri ?: return@withContext false
            val kardLeafRoot = DocumentFile.fromTreeUri(this@MainActivity, rootUri)?.takeIf { it.canWrite() }
                ?: return@withContext false
            sampleVaultFolders().forEach { folder ->
                val secondLevel = kardLeafRoot.findFile(folder.name)?.takeIf { it.isDirectory } ?: return@forEach
                folder.notes.forEachIndexed { index, note ->
                    val title = "示例笔记${index + 1} ${note.titleSuffix}"
                    secondLevel.findFile("$title.md")?.delete()
                }
                folder.children.forEach { childName ->
                    val thirdLevel = secondLevel.findFile(childName)?.takeIf { it.isDirectory } ?: return@forEach
                    repeat(4) { index ->
                        val title = "示例笔记${index + 1} $childName"
                        thirdLevel.findFile("$title.md")?.delete()
                    }
                    if (thirdLevel.listFiles().isEmpty()) {
                        thirdLevel.delete()
                    }
                }
            }
            true
        }

    private suspend fun restoreLatestSampleVaultSamples(): Boolean =
        withContext(Dispatchers.IO) {
            val rootUri = latestSampleVaultUri ?: return@withContext false
            val kardLeafRoot = DocumentFile.fromTreeUri(this@MainActivity, rootUri)?.takeIf { it.canWrite() }
                ?: return@withContext false
            populateSampleVault(kardLeafRoot)
            true
        }

    private data class SampleVaultFolder(
        val name: String,
        val children: List<String>,
        val notes: List<SampleVaultNote>,
    )

    private data class SampleVaultNote(
        val titleSuffix: String,
        val body: String,
    )

    private fun sampleVaultFolders(): List<SampleVaultFolder> = listOf(
        SampleVaultFolder(
            name = "办公",
            children = listOf("电脑", "手机"),
            notes = listOf(
                SampleVaultNote(
                    titleSuffix = "办公目录说明",
                    body = "这里适合记录会议纪要、项目任务、工作流程和设备资料。下方还准备了电脑、手机两个子文件夹，可以分别放桌面工作和移动办公相关内容。",
                ),
                SampleVaultNote(
                    titleSuffix = "办公时间管理",
                    body = "办公记录可以按今天要做、正在推进、等待反馈三个层次整理。先把任务写下来，再补充截止时间和下一步动作，会比只记标题更容易执行。",
                ),
                SampleVaultNote(
                    titleSuffix = "办公沟通技巧",
                    body = "工作沟通建议先写结论，再写背景和需要对方确认的事项。这样笔记既能保存过程，也方便后续复盘是谁、何时、决定了什么。",
                ),
            ),
        ),
        SampleVaultFolder(
            name = "生活",
            children = listOf("灵感", "科普"),
            notes = listOf(
                SampleVaultNote(
                    titleSuffix = "生活目录说明",
                    body = "这里适合记录日常安排、购物清单、习惯打卡和突然想到的小点子。下方还准备了灵感、科普两个子文件夹，可以把随手想法和知识资料分开放。",
                ),
                SampleVaultNote(
                    titleSuffix = "生活整理方法",
                    body = "生活笔记不一定要很长，可以用清单记录待办，用日期记录事件，用小标题区分饮食、运动、账单和家务。稳定记录比一次写很多更重要。",
                ),
                SampleVaultNote(
                    titleSuffix = "生活习惯观察",
                    body = "想培养习惯时，可以记录触发场景、完成情况和感受。几天之后再回看，就能发现真正影响自己的不是目标大小，而是环境和开始动作。",
                ),
            ),
        ),
        SampleVaultFolder(
            name = "学习",
            children = listOf("物理", "数学"),
            notes = listOf(
                SampleVaultNote(
                    titleSuffix = "学习目录说明",
                    body = "这里适合记录课程笔记、阅读摘要、错题复盘和知识框架。下方还准备了物理、数学两个子文件夹，可以按学科继续细分。",
                ),
                SampleVaultNote(
                    titleSuffix = "学习复盘方法",
                    body = "学习笔记可以分成概念、例子、疑问和复盘四块。不要只摘抄原文，最好用自己的话解释一遍，再写一个能验证理解的小例子。",
                ),
                SampleVaultNote(
                    titleSuffix = "知识卡片技巧",
                    body = "一个知识点适合拆成一张小卡片：标题写问题，正文写答案、来源和延伸链接。以后搜索或复习时，会比长篇连续笔记更容易定位。",
                ),
            ),
        ),
    )

    private fun buildSecondLevelSampleNoteContent(
        title: String,
        folder: SampleVaultFolder,
        note: SampleVaultNote,
    ): String = """# $title

${note.body}

## 子文件夹
${folder.children.joinToString(separator = "\n") { "- $it" }}

## 使用建议
- 可以直接编辑这篇笔记
- 可以把常用内容移动到更合适的子文件夹
- 可以删除不需要的示例内容
""".trimIndent()

    private fun buildSampleNoteContent(
        title: String,
        secondLevel: String,
        thirdLevel: String,
        index: Int,
    ): String = """# $title

这是 KardLeaf 自动创建的示例笔记，用来展示 $secondLevel / $thirdLevel 分类下的 Markdown 记录方式。

## 记录内容
- 主题：$thirdLevel 示例 $index
- 场景：快速记录想法、资料和待办
- 建议：可以直接编辑、移动或删除这篇笔记

## 待办
- [ ] 补充自己的内容
- [ ] 试试 Markdown 预览
""".trimIndent()

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
        lifecycleScope.launch {
            readImportJson(uri).onSuccess { json ->
                viewModel.importUserDataBackup(
                    json = json,
                    onSuccess = {
                        Toast.makeText(this@MainActivity, "导入完成", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                    },
                )
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, error.message ?: "导入失败", Toast.LENGTH_SHORT).show()
            }
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
        lifecycleScope.launch {
            readImportJson(uri).onSuccess { json ->
                viewModel.importPrivacyNotes(
                    json = json,
                    onSuccess = { count -> Toast.makeText(this@MainActivity, "已导入 $count 条隐私笔记", Toast.LENGTH_SHORT).show() },
                    onError = { error -> Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show() },
                )
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, error.message ?: "导入失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun readImportJson(uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val declaredSize = queryOpenableSize(uri)
                if (declaredSize != null && declaredSize > MAX_IMPORT_JSON_BYTES) {
                    error(importJsonTooLargeMessage())
                }

                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L
                val input = contentResolver.openInputStream(uri) ?: error("Cannot open import file")
                input.use { stream ->
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_IMPORT_JSON_BYTES) {
                            error(importJsonTooLargeMessage())
                        }
                        output.write(buffer, 0, read)
                    }
                }
                output.toString(Charsets.UTF_8.name())
            }
        }

    private fun queryOpenableSize(uri: Uri): Long? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex < 0 || !cursor.moveToFirst() || cursor.isNull(sizeIndex)) {
                    null
                } else {
                    cursor.getLong(sizeIndex).takeIf { it >= 0L }
                }
            }
        }.getOrNull()

    private fun importJsonTooLargeMessage(): String =
        "导入文件过大，最大支持 ${MAX_IMPORT_JSON_BYTES / 1024L / 1024L} MB"

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_OPEN_DOCUMENT_TREE -> handleRootFolderSelected(uri)
            REQUEST_CREATE_SAMPLE_VAULT_PARENT -> handleCreateSampleVaultParentSelected(uri)
            REQUEST_EXPORT_USER_DATA -> handleUserDataExportUri(uri)
            REQUEST_IMPORT_USER_DATA -> handleUserDataImportUri(uri)
            REQUEST_EXPORT_PRIVACY -> handlePrivacyExportUri(uri)
            REQUEST_IMPORT_PRIVACY -> handlePrivacyImportUri(uri)
            REQUEST_PICK_IMAGE_FOLDER -> pendingImageFolderPicker?.invoke(uri).also { pendingImageFolderPicker = null }
            REQUEST_PICK_BACKUP_FOLDER -> pendingBackupFolderPicker?.invoke(uri).also { pendingBackupFolderPicker = null }
            REQUEST_PICK_EDITOR_IMAGE -> {
                val pickerElapsedMs = (SystemClock.elapsedRealtime() - editorImagePickerLaunchElapsedMs).takeIf { editorImagePickerLaunchElapsedMs > 0L } ?: -1L
                KardLeafLog.d(
                    "KardLeafCM6Trace",
                    "[insert-image] picker activity result elapsed=${pickerElapsedMs}ms scheme=${uri.scheme.orEmpty()} mime=${contentResolver.getType(uri).orEmpty()}",
                )
                pendingEditorImagePicker?.invoke(uri).also { pendingEditorImagePicker = null }
            }
            REQUEST_PICK_DRAWER_AVATAR -> {
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                prefsManager.saveDrawerAvatarUri(uri.toString())
                drawerAvatarRevisionRequest.value = SystemClock.elapsedRealtime()
                Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val startupStartMs = SystemClock.elapsedRealtime()
        KardLeafLog.d(
            STARTUP_PERF_TRACE_TAG,
            "activity onCreate start savedState=${savedInstanceState != null} thread=${Thread.currentThread().name}",
        )
        super.onCreate(savedInstanceState)
        val firstPreDrawListener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                KardLeafLog.d(
                    STARTUP_PERF_TRACE_TAG,
                    "activity firstPreDraw elapsed=${SystemClock.elapsedRealtime() - startupStartMs}ms",
                )
                return true
            }
        }
        window.decorView.viewTreeObserver.addOnPreDrawListener(firstPreDrawListener)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        metadataManager = MetadataManager(applicationContext)
        prefsManager = PrefsManager(applicationContext)
        repository = RoomNoteRepository(applicationContext, metadataManager, prefsManager)
        KardLeafLog.d(
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
            KardLeafLog.d(
                STARTUP_PERF_TRACE_TAG,
                "activity setRootFolder savedUri scanImmediately=false elapsed=${SystemClock.elapsedRealtime() - startupStartMs}ms",
            )
            viewModel.setRootFolder(savedRootUri, scanImmediately = false)
            vaultChangeObserver?.start(savedRootUri)
        } else if (prefsManager.getRootUri() != null) {
            viewModel.resetPermissionNeeded()
        }

        maybeRunAutoBackup()
        requestInitialNotificationPermissionIfNeeded()

        KardLeafLog.d(
            STARTUP_PERF_TRACE_TAG,
            "activity setContent start elapsed=${SystemClock.elapsedRealtime() - startupStartMs}ms",
        )
        setContent {
            var themeRevision by remember { mutableStateOf(0) }
            var drawerSettingsRevision by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                KardLeafLog.d(
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
                    val editorTransitionState = remember { MutableTransitionState(false) }
                    var isEditorExitPending by remember { mutableStateOf(false) }
                    var showPrivacy by remember { mutableStateOf(false) }
                    val currentScreen by viewModel.currentScreen.collectAsState()
                    val currentFilter by viewModel.currentFilter.collectAsState()
                    val sampleCleanupPromptRequestId by sampleVaultCleanupPromptRequest.collectAsState()
                    val labels by viewModel.labels.collectAsState()
                    val allNotes by viewModel.allNotes.collectAsState(initial = emptyList())
                    val drawerAvatarRevision by drawerAvatarRevisionRequest.collectAsState()
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
                    var perceivedEditorOpenStartMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
                    var perceivedEditorOpenFirstFrameLogged by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    var perceivedEditorOpenEstimatedContentLen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(-1) }
                    var perceivedEditorOpenSizeTier by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("unknown") }
                    var perceivedEditorCloseStartMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }

                    LaunchedEffect(isEditorOpen) {
                        if (isEditorOpen) {
                            isEditorExitPending = false
                            editorTransitionState.targetState = true
                        } else if (!isEditorExitPending) {
                            editorTransitionState.targetState = false
                        }
                    }
                    LaunchedEffect(
                        editorTransitionState.isIdle,
                        editorTransitionState.currentState,
                        editorTransitionState.targetState,
                        isEditorExitPending,
                    ) {
                        if (
                            isEditorExitPending &&
                            editorTransitionState.isIdle &&
                            !editorTransitionState.currentState &&
                            !editorTransitionState.targetState
                        ) {
                            val start = perceivedEditorCloseStartMs
                            if (start != null) {
                                KardLeafLog.d(
                                    USER_PERF_TRACE_TAG,
                                    "editorClose transitionFinished elapsed=${SystemClock.elapsedRealtime() - start}ms",
                                )
                            }
                            isEditorExitPending = false
                            viewModel.finishEditorCloseAnimation()
                        }
                    }
                    fun startEditorExitAnimation() {
                        if (isEditorExitPending || !editorTransitionState.targetState) return
                        viewModel.beginEditorCloseAnimation()
                        isEditorExitPending = true
                        editorTransitionState.targetState = false
                    }

                    fun noteSizeTier(length: Int): String = when {
                        length < 10_000 -> "lt_1w"
                        length < 50_000 -> "1w_5w"
                        length < 100_000 -> "5w_10w"
                        length < 1_000_000 -> "10w_100w"
                        else -> "gte_100w"
                    }

                    fun markEditorOpenStart(source: String, note: Note? = null) {
                        val now = SystemClock.elapsedRealtime()
                        val estimatedContentLen = note?.let { maxOf(it.content.length, it.contentPreview.length) } ?: -1
                        val sizeTier = if (estimatedContentLen >= 0) noteSizeTier(estimatedContentLen) else "unknown"
                        perceivedEditorOpenStartMs = now
                        perceivedEditorOpenFirstFrameLogged = false
                        perceivedEditorOpenEstimatedContentLen = estimatedContentLen
                        perceivedEditorOpenSizeTier = sizeTier
                        KardLeafLog.d(
                            USER_PERF_TRACE_TAG,
                            "editorOpen humanStart source=$source time=$now estimatedContentLen=$estimatedContentLen " +
                                "sizeTier=$sizeTier path=${note?.file?.path}",
                        )
                    }

                    fun markEditorCloseStart(source: String) {
                        val now = SystemClock.elapsedRealtime()
                        perceivedEditorCloseStartMs = now
                        KardLeafLog.d(
                            USER_PERF_TRACE_TAG,
                            "editorClose humanStart source=$source time=$now",
                        )
                    }

                    LaunchedEffect(isEditorOpen) {
                        if (isEditorOpen) {
                            val start = perceivedEditorOpenStartMs ?: return@LaunchedEffect
                            KardLeafLog.d(
                                USER_PERF_TRACE_TAG,
                                "editorOpen humanSettledSchedule effectDelay=${SystemClock.elapsedRealtime() - start}ms wait=330ms " +
                                    "estimatedContentLen=$perceivedEditorOpenEstimatedContentLen sizeTier=$perceivedEditorOpenSizeTier",
                            )
                            delay(330L)
                            KardLeafLog.d(
                                USER_PERF_TRACE_TAG,
                                "editorOpen humanSettledAfterDelay elapsed=${SystemClock.elapsedRealtime() - start}ms waitFrame=true " +
                                    "estimatedContentLen=$perceivedEditorOpenEstimatedContentLen sizeTier=$perceivedEditorOpenSizeTier",
                            )
                            withFrameNanos { _ -> }
                            KardLeafLog.d(
                                USER_PERF_TRACE_TAG,
                                "editorOpen humanSettled elapsed=${SystemClock.elapsedRealtime() - start}ms " +
                                    "estimatedContentLen=$perceivedEditorOpenEstimatedContentLen sizeTier=$perceivedEditorOpenSizeTier",
                            )
                            perceivedEditorOpenStartMs = null
                        } else {
                            val start = perceivedEditorCloseStartMs ?: return@LaunchedEffect
                            KardLeafLog.d(
                                USER_PERF_TRACE_TAG,
                                "editorClose humanSettledSchedule effectDelay=${SystemClock.elapsedRealtime() - start}ms wait=330ms",
                            )
                            delay(330L)
                            KardLeafLog.d(
                                USER_PERF_TRACE_TAG,
                                "editorClose humanSettledAfterDelay elapsed=${SystemClock.elapsedRealtime() - start}ms waitFrame=true",
                            )
                            withFrameNanos { _ -> }
                            KardLeafLog.d(
                                USER_PERF_TRACE_TAG,
                                "editorClose humanSettled elapsed=${SystemClock.elapsedRealtime() - start}ms",
                            )
                            perceivedEditorCloseStartMs = null
                        }
                    }
                    // 首次启动自动弹出新手引导；侧边栏“使用介绍”入口复用同一 Composable。
                    val isPermissionNeededForOnboarding by viewModel.isPermissionNeeded.collectAsState()
                    var showOnboarding by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(!prefsManager.hasSeenOnboarding() && !isPermissionNeededForOnboarding)
                    }
                    LaunchedEffect(isPermissionNeededForOnboarding) {
                        if (!isPermissionNeededForOnboarding && !prefsManager.hasSeenOnboarding()) {
                            showOnboarding = true
                        }
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
                    var openDrawingPadAfterEditorOpen by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(false)
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
                            androidx.compose.runtime.key(drawerSettingsRevision, drawerAvatarRevision) {
                                com.kangle.kardleaf.ui.AppDrawerContent(
                                    currentScreen = currentScreen,
                                    currentFilter = currentFilter,
                                labels = labels,
                                allNotes = allNotes,
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
                                onOpenFolderManagement = {
                                    clearTemporaryDashboardReturn()
                                    closeDrawerThen {
                                        viewModel.navigateTo(MainViewModel.Screen.Folders)
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
                                    onPickDrawerAvatar = { launchDrawerAvatarPicker() },
                                    onThemeModeChange = { mode ->
                                        prefsManager.saveAppThemeMode(mode)
                                        themeRevision++
                                    },
                                )
                            }
                        },
                    ) {
                        androidx.activity.compose.BackHandler(enabled = drawerState.isOpen) {
                            KardLeafLog.d(
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

                        BoxWithConstraints(
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
                            // Keep the dashboard resident for fast return, but do not animate the
                            // whole note grid behind the editor. Moving/fading a large lazy grid
                            // during open/close keeps thumbnails and layout work active on frames.
                            val mainContentOffsetX by animateDpAsState(
                                targetValue = 0.dp,
                                animationSpec = tween(durationMillis = 300),
                                label = "MainContentOffset",
                            )
                            val mainContentAlpha by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 300),
                                label = "MainContentAlpha",
                            )

                            androidx.activity.compose.BackHandler(
                                enabled = !drawerState.isOpen &&
                                    (currentScreen is MainViewModel.Screen.Dates ||
                                        currentScreen is MainViewModel.Screen.Images ||
                                        currentScreen is MainViewModel.Screen.Tags ||
                                        currentScreen is MainViewModel.Screen.Folders ||
                                        currentScreen is MainViewModel.Screen.Tasks),
                            ) {
                                KardLeafLog.d(BACK_TRACE_TAG, "Main screen BackHandler hit screen=$currentScreen -> Dashboard")
                                viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(x = mainContentOffsetX)
                                    .alpha(mainContentAlpha),
                            ) {
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = {
                                        val forward = mainScreenMotionIndex(targetState) >= mainScreenMotionIndex(initialState)
                                        kardLeafHorizontalContentTransform(
                                            forward = forward,
                                            durationMillis = KardLeafMotion.ContainerDurationMillis,
                                            distanceFactor = 0.08f,
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    label = "MainScreenTransition",
                                ) { targetScreen ->
                                when (targetScreen) {
                                MainViewModel.Screen.Dashboard -> {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        isDrawerOpen = drawerState.isOpen,
                                        onSelectFolder = {
                                            launchOpenDocumentTree()
                                        },
                                        onCreateSampleVault = {
                                            launchCreateSampleVaultParentPicker()
                                        },
                                        edgeDrawerWidthPx = edgeDrawerWidthPx,
                                        pauseBackgroundWork = isEditorOpen,
                                        sampleCleanupPromptRequestId = sampleCleanupPromptRequestId,
                                        onSampleCleanupPromptConsumed = { sampleVaultCleanupPromptRequest.value = 0L },
                                        onClearSampleVaultSamples = { clearLatestSampleVaultSamples() },
                                        onRestoreSampleVaultSamples = { restoreLatestSampleVaultSamples() },
                                onNoteClick = { note ->
                                            if (!isDrawerContentBlocked()) {
                                                markEditorOpenStart("dashboard_note_click", note)
                                                blockDrawerOpenBriefly()
                                                viewModel.openNote(note)
                                            }
                                        },
                                        onFabClick = {
                                            if (!isDrawerContentBlocked()) {
                                                val source = if (currentFilter is MainViewModel.NoteFilter.Drafts) "dashboard_drafts_fab" else "dashboard_fab"
                                                markEditorOpenStart(source)
                                                blockDrawerOpenBriefly()
                                                if (currentFilter is MainViewModel.NoteFilter.Drafts) {
                                                    viewModel.createTemporaryNote(source = "dashboard_drafts_fab")
                                                } else {
                                                    viewModel.createNote(source = "dashboard_fab")
                                                }
                                            }
                                        },
                                        onCreateDraftClick = {
                                            if (!isDrawerContentBlocked()) {
                                                markEditorOpenStart("home_bottom_toolbar_draft")
                                                blockDrawerOpenBriefly()
                                                viewModel.createTemporaryNote(source = "home_bottom_toolbar_draft")
                                            }
                                        },
                                        onCreateDrawingClick = {
                                            if (!isDrawerContentBlocked()) {
                                                markEditorOpenStart("dashboard_fab_drawing")
                                                blockDrawerOpenBriefly()
                                                openDrawingPadAfterEditorOpen = true
                                                viewModel.createNote(source = "dashboard_fab_drawing")
                                            }
                                        },
                                        onOpenPrivacy = {
                                            if (!isDrawerContentBlocked()) {
                                                showPrivacy = true
                                            }
                                        },
                                        onOpenDrawer = { openDrawerIfAllowed() },
                                        appStartupStartRealtimeMs = startupStartMs,
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
                                                markEditorOpenStart("dates_note_click", note)
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
                                                markEditorOpenStart("images_note_click", note)
                                                blockDrawerOpenBriefly()
                                                viewModel.openNote(note)
                                            }
                                        },
                                    )
                                }
                                MainViewModel.Screen.Tags -> {
                                    com.kangle.kardleaf.ui.TagManagementScreen(
                                        tags = yamlTags,
                                        allNotes = allNotes,
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
                                MainViewModel.Screen.Folders -> {
                                    com.kangle.kardleaf.ui.FolderManagementScreen(
                                        viewModel = viewModel,
                                        isDrawerOpen = drawerState.isOpen,
                                        onOpenDrawer = { openDrawerIfAllowed() },
                                        onBack = { viewModel.navigateTo(MainViewModel.Screen.Dashboard) },
                                    )
                                }
                                MainViewModel.Screen.Tasks -> {
                                    com.kangle.kardleaf.ui.TaskListScreen(
                                        onOpenDrawer = { openDrawerIfAllowed() },
                                        onOpenNotePath = { notePath ->
                                            markEditorOpenStart("tasks_note_click")
                                            blockDrawerOpenBriefly()
                                            viewModel.openRecordNote(notePath)
                                        },
                                    )
                                }
                                MainViewModel.Screen.Settings -> {
                                com.kangle.kardleaf.ui.KardLeafSettingsScreen(
                                    onBack = { viewModel.navigateTo(MainViewModel.Screen.Dashboard) },
                                    onSelectDatabase = {
                                        launchOpenDocumentTree()
                                    },
                                    onSettingsChanged = {
                                        drawerSettingsRevision += 1
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
                                    onOpenRecordNote = { noteKey -> viewModel.openRecordNote(noteKey) },
                                    onCleanupHistory = { viewModel.cleanupOldHistoryVersions() },
                                    onWebDavVaultChanged = { changedPaths ->
                                        viewModel.onExternalVaultChanged(
                                            forceContentReloadFallback = changedPaths.isEmpty(),
                                            changedPaths = changedPaths,
                                        )
                                    },
                                    labels = labels,
                                )
                            }
                            }
                        }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                                visibleState = editorTransitionState,
                                enter = kardLeafSharedAxisXIn(
                                    initialOffsetX = { width ->
                                        (width * KardLeafMotion.SharedAxisOffsetFactor).toInt()
                                    },
                                ),
                                exit = kardLeafSharedAxisXOut(
                                    targetOffsetX = { width ->
                                        (width * KardLeafMotion.EditorExitOffsetFactor).toInt()
                                    },
                                    durationMillis = KardLeafMotion.EditorExitDurationMillis,
                                ),
                                label = "EditorTransition",
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onGloballyPositioned {
                                            val start = perceivedEditorOpenStartMs
                                            if (start != null && !perceivedEditorOpenFirstFrameLogged) {
                                                perceivedEditorOpenFirstFrameLogged = true
                                                KardLeafLog.d(
                                                    USER_PERF_TRACE_TAG,
                                                    "editorOpen firstFrame elapsed=${SystemClock.elapsedRealtime() - start}ms " +
                                                        "estimatedContentLen=$perceivedEditorOpenEstimatedContentLen sizeTier=$perceivedEditorOpenSizeTier",
                                                )
                                            }
                                        },
                                ) {
                                    val filter = viewModel.currentFilter.value
                                    val label = if (filter is MainViewModel.NoteFilter.Label) filter.name else ""

                                    EditorScreen(
                                        viewModel = viewModel,
                                        onBack = { startEditorExitAnimation() },
                                        onLeavingEditorStart = { markEditorCloseStart("editor_back") },
                                        editorOpenStartRealtimeMs = perceivedEditorOpenStartMs,
                                        initialLabel = label,
                                        onPickImage = { onPicked -> launchEditorImagePicker(onPicked) },
                                        openDrawingPadOnStart = openDrawingPadAfterEditorOpen,
                                        onDrawingPadStartConsumed = { openDrawingPadAfterEditorOpen = false },
                                    )
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = showPrivacy,
                                enter = kardLeafSharedAxisYIn(
                                    initialOffsetY = { height -> height / 24 },
                                    durationMillis = KardLeafMotion.ContainerDurationMillis,
                                ),
                                exit = kardLeafSharedAxisYOut(
                                    targetOffsetY = { height -> height / 24 },
                                    durationMillis = KardLeafMotion.ContainerDurationMillis,
                                ),
                                label = "PrivacyScreenTransition",
                            ) {
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
                        KardLeafLog.d(BACK_TRACE_TAG, "Activity dispatcher probe hit before forwarding")
                        isEnabled = false
                        try {
                            onBackPressedDispatcher.onBackPressed()
                        } finally {
                            isEnabled = true
                            KardLeafLog.d(BACK_TRACE_TAG, "Activity dispatcher probe restored after forwarding")
                        }
                    }
                },
            )
            KardLeafLog.d(BACK_TRACE_TAG, "Activity dispatcher probe registered after first frame")
        }
        KardLeafLog.d(
            STARTUP_PERF_TRACE_TAG,
            "activity setContent returned elapsed=${SystemClock.elapsedRealtime() - startupStartMs}ms",
        )
    }

    private fun startWebDavRealtimeSyncLoop() {
        if (webDavRealtimeSyncJob?.isActive == true) return
        webDavRealtimeSyncJob = lifecycleScope.launch {
            val syncManager = WebDavCloudSyncManager(applicationContext, prefsManager)
            var lastHeartbeatLogMs = 0L
            var consecutiveFailureCount = 0
            var nextRetryAtMs = 0L

            fun resetRealtimeBackoff() {
                consecutiveFailureCount = 0
                nextRetryAtMs = 0L
            }

            fun recordRealtimeFailure(action: String, error: Throwable) {
                consecutiveFailureCount = (consecutiveFailureCount + 1).coerceAtMost(6)
                val retryDelayMs = (
                    prefsManager.getWebDavRealtimePollIntervalMs().coerceAtLeast(2_000L) *
                        (1L shl (consecutiveFailureCount - 1).coerceAtMost(5))
                ).coerceAtMost(60_000L)
                nextRetryAtMs = System.currentTimeMillis() + retryDelayMs
                val readableError = WebDavCloudSyncManager.readableError(error, "${action}失败")
                prefsManager.appendWebDavSyncLog("$readableError；${retryDelayMs / 1000} 秒后重试")
                KardLeafLog.w(WEBDAV_REALTIME_SYNC_TAG, "$action failed: ${error.message}", error)
            }

            while (true) {
                val pollIntervalMs = prefsManager.getWebDavRealtimePollIntervalMs()
                delay(pollIntervalMs)
                if (!prefsManager.isWebDavRealtimeSyncEnabled()) {
                    continue
                }

                if (System.currentTimeMillis() < nextRetryAtMs) {
                    continue
                }

                val dirtyMs = prefsManager.getWebDavRealtimeLocalDirtyMs()
                if (dirtyMs > 0L) {
                    if (System.currentTimeMillis() - dirtyMs < WEBDAV_REALTIME_UPLOAD_DELAY_MS) {
                        continue
                    }
                    runCatching { syncManager.upload() }
                        .onSuccess { message ->
                            prefsManager.clearWebDavRealtimeLocalDirtyIfUnchanged(dirtyMs)
                            resetRealtimeBackoff()
                            prefsManager.appendWebDavSyncLog("自动上传成功：$message")
                            KardLeafLog.d(WEBDAV_REALTIME_SYNC_TAG, "auto upload success message=$message")
                        }
                        .onFailure { error ->
                            recordRealtimeFailure("自动上传", error)
                        }
                    continue
                }

                runCatching { syncManager.readRealtimeRemoteState() }
                    .onSuccess { state ->
                        val marker = state?.marker.orEmpty()
                        if (marker.isBlank()) {
                            resetRealtimeBackoff()
                            return@onSuccess
                        }
                        val knownMarker = prefsManager.getWebDavRealtimeKnownRemoteMarker()
                        val ownUploadMarker = prefsManager.getWebDavRealtimeLastUploadRemoteMarker()
                        val nowMs = System.currentTimeMillis()
                        when {
                            marker == knownMarker -> {
                                resetRealtimeBackoff()
                                if (nowMs - lastHeartbeatLogMs >= WEBDAV_REALTIME_HEARTBEAT_LOG_INTERVAL_MS) {
                                    prefsManager.appendWebDavSyncLog("自动检查正常：远端无变化，间隔 ${prefsManager.getWebDavRealtimePollIntervalMs() / 1000} 秒")
                                    lastHeartbeatLogMs = nowMs
                                }
                            }
                            marker == ownUploadMarker -> {
                                resetRealtimeBackoff()
                                prefsManager.saveWebDavRealtimeKnownRemoteMarker(marker)
                                lastHeartbeatLogMs = nowMs
                            }
                            else -> {
                                prefsManager.appendWebDavSyncLog("检测到远端变化：开始下载")
                                val result = runCatching { syncManager.downloadRealtimeChangesOnly() }
                                    .onFailure { error -> recordRealtimeFailure("自动下载", error) }
                                    .getOrNull() ?: return@onSuccess
                                resetRealtimeBackoff()
                                prefsManager.appendWebDavSyncLog(
                                    if (result.skippedConflictCount > 0) {
                                        "自动下载发现冲突待处理：${result.message}"
                                    } else {
                                        "自动下载成功：${result.message}"
                                    },
                                )
                                KardLeafLog.d(WEBDAV_REALTIME_SYNC_TAG, "auto download success message=${result.message} changed=${result.changedPaths.size}")
                                lastHeartbeatLogMs = nowMs
                                if (result.changedPaths.isNotEmpty()) {
                                    viewModel.onExternalVaultChanged(
                                        forceContentReloadFallback = false,
                                        changedPaths = result.changedPaths,
                                    )
                                }
                            }
                        }
                    }
                    .onFailure { error ->
                        recordRealtimeFailure("远端检查", error)
                    }
            }
        }
    }

    private fun stopWebDavRealtimeSyncLoop() {
        webDavRealtimeSyncJob?.cancel()
        webDavRealtimeSyncJob = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            KardLeafLog.d(
                BACK_TRACE_TAG,
                "Activity dispatchKeyEvent back action=${event.action} repeat=${event.repeatCount}",
            )
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        KardLeafLog.d(BACK_TRACE_TAG, "Activity onBackPressed enter")
        super.onBackPressed()
        KardLeafLog.d(BACK_TRACE_TAG, "Activity onBackPressed exit")
    }

    override fun onResume() {
        val resumeStartMs = SystemClock.elapsedRealtime()
        KardLeafLog.d(STARTUP_PERF_TRACE_TAG, "activity onResume start firstResume=${!hasCompletedFirstResume}")
        super.onResume()
        startWebDavRealtimeSyncLoop()
        val persistedRootUri = getPersistedRootUriWithPermission()
        persistedRootUri?.let { uri ->
            vaultChangeObserver?.start(uri)
            if (hasCompletedFirstResume) {
                KardLeafLog.d(
                    STARTUP_PERF_TRACE_TAG,
                    "activity onResume external refresh trigger elapsed=${SystemClock.elapsedRealtime() - resumeStartMs}ms",
                )
                viewModel.onExternalVaultChanged(forceContentReloadFallback = false)
            }
        }
        hasCompletedFirstResume = true
        KardLeafLog.d(
            STARTUP_PERF_TRACE_TAG,
            "activity onResume done elapsed=${SystemClock.elapsedRealtime() - resumeStartMs}ms persistedRoot=${persistedRootUri != null}",
        )
    }

    override fun onPause() {
        stopWebDavRealtimeSyncLoop()
        NoteListWidgetProvider.refreshAllWidgets(applicationContext)
        TaskListWidgetProvider.refreshAllWidgets(applicationContext)
        super.onPause()
    }

    override fun onDestroy() {
        stopWebDavRealtimeSyncLoop()
        vaultChangeObserver?.stop()
        vaultChangeObserver = null
        super.onDestroy()
    }

    private fun getPersistedRootUriWithPermission(): Uri? {
        val savedUriStr = prefsManager.getRootUri() ?: run {
            return null
        }
        val uri = Uri.parse(savedUriStr)
        val savedTreeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val hasPermission =
            contentResolver.persistedUriPermissions.any { permission ->
                if (!permission.isReadPermission) return@any false
                if (permission.uri == uri) return@any true
                val grantedTreeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(permission.uri) }.getOrNull()
                savedTreeDocumentId != null &&
                    grantedTreeDocumentId != null &&
                    permission.uri.authority == uri.authority &&
                    (
                        savedTreeDocumentId == grantedTreeDocumentId ||
                            savedTreeDocumentId.startsWith("$grantedTreeDocumentId/") ||
                            (grantedTreeDocumentId.endsWith(":") && savedTreeDocumentId.startsWith(grantedTreeDocumentId))
                    )
            }

        return if (hasPermission) uri else null
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (data?.scheme == "kardleaf" && data.host == "search") {
            viewModel.requestOpenSearch()
            return
        }
        if (data?.scheme == "kardleaf" && data.host == "tasks") {
            viewModel.navigateTo(MainViewModel.Screen.Tasks)
            return
        }
        if (data?.scheme == "kardleaf" && data.host == "settings") {
            viewModel.navigateTo(MainViewModel.Screen.Settings)
            return
        }

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
                KardLeafLog.e("MainActivity", "Auto backup failed", e)
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
