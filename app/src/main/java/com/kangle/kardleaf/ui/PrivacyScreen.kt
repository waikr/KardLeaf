package com.kangle.kardleaf.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.database.PrivacyNoteEntity
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PrivacyScreenShape = RoundedCornerShape(22.dp)
private val PrivacyCardShape = RoundedCornerShape(18.dp)
private val PrivacyChipShape = RoundedCornerShape(999.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPickImage: (((Uri) -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    val privacyNotes by viewModel.privacyNotes.collectAsState()
    var hasPrivacyPwd by remember { mutableStateOf(prefsManager.getPrivacyPasswordHash() != null) }
    var unlocked by remember { mutableStateOf(false) }
    var pwdInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf(0L) }
    var editorTitle by remember { mutableStateOf("") }
    var editorContent by remember { mutableStateOf("") }
    var editorSessionKey by remember { mutableStateOf("") }
    val canUsePrivacyBiometric =
        hasPrivacyPwd && prefsManager.isPrivacyBiometricUnlockEnabled() && isBiometricUnlockAvailable(context)

    BackHandler(enabled = !showEditor) {
        onBack()
    }

    fun unlockWithPrivacyBiometric() {
        showBiometricUnlockPrompt(
            context = context,
            title = "隐私仓库指纹解锁",
            subtitle = "验证后查看隐私笔记",
            onSuccess = {
                unlocked = true
                errorMsg = null
                pwdInput = ""
            },
            onError = { errorMsg = it },
        )
    }

    fun verifyPrivacyPassword(input: String) {
        val inputHash = hashPassword(input)
        if (inputHash == prefsManager.getPrivacyPasswordHash() || inputHash == prefsManager.getSafetyWordHash()) {
            unlocked = true
            errorMsg = null
            pwdInput = ""
        } else {
            errorMsg = "密码或安全词错误"
            if (prefsManager.getPasswordInputMode() == PrefsManager.PasswordInputMode.SIMPLE) {
                pwdInput = ""
            }
        }
    }

    if (hasPrivacyPwd && !unlocked) {
        PasswordLockCardScreen(
            screenTitle = "隐私仓库",
            headline = "隐私仓库已锁定",
            description = "输入隐私密码，查看受保护的笔记。",
            passwordLabel = if (prefsManager.getSafetyWordHash() != null) "隐私密码或安全词" else "隐私密码",
            password = pwdInput,
            onPasswordChange = { pwdInput = it },
            primaryButtonText = "进入隐私仓库",
            onPasswordSubmit = { verifyPrivacyPassword(pwdInput) },
            onSimplePasswordComplete = { completed -> verifyPrivacyPassword(completed) },
            errorMessage = errorMsg,
            biometricAvailable = canUsePrivacyBiometric,
            onBiometricUnlock = { unlockWithPrivacyBiometric() },
            autoShowBiometric = canUsePrivacyBiometric,
            onBack = onBack,
            passwordInputMode = prefsManager.getPasswordInputMode(),
        )
        return
    }

    if (showEditor) {
        EditorScreen(
            viewModel = viewModel,
            onBack = { showEditor = false },
            privacyNoteId = editingId,
            privacyInitialTitle = editorTitle,
            privacyInitialContent = editorContent,
            privacyDocumentKey = editorSessionKey,
            onSavePrivacyNote = { id, title, content, onSaved ->
                viewModel.savePrivacyNoteAndReturnId(id, title, content) { savedId ->
                    editingId = savedId
                    editorTitle = title
                    editorContent = content
                    onSaved(savedId)
                }
            },
            onPickImage = onPickImage,
            onDeletePrivacyNote = if (editingId > 0L) {
                {
                    viewModel.deletePrivacyNote(editingId)
                    showEditor = false
                }
            } else {
                null
            },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("隐私仓库", style = MaterialTheme.typography.titleLarge)
                        if (hasPrivacyPwd && unlocked && !showEditor) {
                            Text(
                                text = "${privacyNotes.size} 条受保护笔记",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onBack()
                    }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (hasPrivacyPwd && unlocked && !showEditor) {
                        IconButton(onClick = onExport) {
                            Icon(Icons.Outlined.Upload, contentDescription = "导出")
                        }
                        IconButton(onClick = onImport) {
                            Icon(Icons.Outlined.Download, contentDescription = "导入")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (hasPrivacyPwd && unlocked && !showEditor) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingId = 0L
                        editorTitle = ""
                        editorContent = ""
                        editorSessionKey = "privacy:new:${System.currentTimeMillis()}"
                        showEditor = true
                    },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("新建") },
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                )
            }
        },
    ) { padding ->
        when {
            !hasPrivacyPwd -> {
                PrivacySetupContent(
                    prefsManager = prefsManager,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onPasswordCreated = {
                        hasPrivacyPwd = true
                        unlocked = true
                        errorMsg = null
                        pwdInput = ""
                    },
                )
            }
            showEditor -> {
                PrivacyNoteEditor(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    editingId = editingId,
                    title = editorTitle,
                    content = editorContent,
                    onTitleChange = { editorTitle = it },
                    onContentChange = { editorContent = it },
                    onCancel = { showEditor = false },
                    onDelete = {
                        viewModel.deletePrivacyNote(editingId)
                        showEditor = false
                    },
                    onSave = {
                        val title = editorTitle.ifBlank { "未命名" }
                        viewModel.savePrivacyNote(editingId, title, editorContent) {
                            showEditor = false
                        }
                    },
                )
            }
            else -> {
                PrivacyNoteList(
                    notes = privacyNotes,
                    viewMode = prefsManager.getViewMode(),
                    cardDensity = prefsManager.getCardDensity(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onEditNote = { note ->
                        editingId = note.id
                        editorTitle = note.title
                        editorContent = note.content
                        editorSessionKey = "privacy:${note.id}:${note.updatedAtMs}"
                        showEditor = true
                    },
                )
            }
        }
    }
}

@Composable
private fun PrivacySetupContent(
    prefsManager: PrefsManager,
    modifier: Modifier = Modifier,
    onPasswordCreated: () -> Unit,
) {
    val passwordInputMode = remember { prefsManager.getPasswordInputMode() }
    var privacyPwd by remember { mutableStateOf("") }
    var privacyPwdConfirm by remember { mutableStateOf("") }
    var privacyPwdError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = PrivacyScreenShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "设置隐私密码",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "第一次进入隐私界面时，可以直接在这里设置密码。设置后立即进入隐私界面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = privacyPwd,
                        onValueChange = { value ->
                            privacyPwd = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) {
                                value.filter(Char::isDigit).take(4)
                            } else {
                                value
                            }
                            privacyPwdError = null
                        },
                        label = { Text("设置隐私密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = privacyPwdConfirm,
                        onValueChange = { value ->
                            privacyPwdConfirm = if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) {
                                value.filter(Char::isDigit).take(4)
                            } else {
                                value
                            }
                            privacyPwdError = null
                        },
                        label = { Text("再次输入隐私密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (privacyPwdError != null) {
                        Text(
                            privacyPwdError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        enabled = privacyPwd.isNotBlank() || privacyPwdConfirm.isNotBlank(),
                        onClick = {
                            val minSimpleOk = passwordInputMode != PrefsManager.PasswordInputMode.SIMPLE || privacyPwd.length == 4
                            when {
                                privacyPwd.isBlank() || privacyPwdConfirm.isBlank() -> privacyPwdError = "请完整输入两次隐私密码"
                                !minSimpleOk -> privacyPwdError = "简单密码必须是 4 位数字"
                                privacyPwd != privacyPwdConfirm -> privacyPwdError = "两次输入的隐私密码不一致"
                                else -> {
                                    prefsManager.savePrivacyPasswordHash(hashPassword(privacyPwd))
                                    privacyPwd = ""
                                    privacyPwdConfirm = ""
                                    privacyPwdError = null
                                    onPasswordCreated()
                                }
                            }
                        },
                    ) {
                        Text("保存并进入")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyNoteList(
    notes: List<PrivacyNoteEntity>,
    viewMode: PrefsManager.ViewMode,
    cardDensity: PrefsManager.CardDensity,
    modifier: Modifier = Modifier,
    onEditNote: (PrivacyNoteEntity) -> Unit,
) {
    if (notes.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    "暂无隐私笔记",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val columns = if (viewMode == PrefsManager.ViewMode.GRID) 2 else 1
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 96.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(notes, key = { it.id }) { note ->
            PrivacyNoteCard(
                note = note,
                cardDensity = cardDensity,
                onClick = { onEditNote(note) },
            )
        }
    }
}

@Composable
private fun PrivacyHeroCard(
    title: String,
    description: String,
    footer: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PrivacyScreenShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                PrivacyChip(text = footer)
            }
        }
    }
}

@Composable
private fun PrivacyStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = PrivacyCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacyEmptyCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                PrivacyScreenShape,
            ),
        shape = PrivacyScreenShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "暂无隐私笔记",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "点击右下角新建，把不想显示在首页的内容放到这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacyNoteCard(
    note: PrivacyNoteEntity,
    cardDensity: PrefsManager.CardDensity,
    onClick: () -> Unit,
) {
    val title = note.title.ifBlank { "未命名" }
    val preview = remember(note.content) { plainPrivacyCardPreview(note.content) }
    val isCompact = cardDensity == PrefsManager.CardDensity.COMPACT
    val cardPadding = if (isCompact) 10.dp else 16.dp
    val titleBottomPadding = if (isCompact) 4.dp else 8.dp
    val contentMaxLines = if (isCompact) 3 else 8
    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(cardPadding)) {
            Text(
                title,
                style = (if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                    .copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = titleBottomPadding),
            )
            if (preview.isNotEmpty()) {
                Text(
                    preview,
                    style = (if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                        .copy(color = MaterialTheme.colorScheme.onSurface),
                    maxLines = contentMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun plainPrivacyCardPreview(content: String): String =
    NoteFormatUtils.buildPlainTextPreview(
        content = content,
        maxChars = 500,
        maxLines = 10,
    )

@Composable
private fun PrivacyNoteEditor(
    editingId: Long,
    title: String,
    content: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = PrivacyScreenShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (editingId > 0) "编辑隐私笔记" else "新建隐私笔记",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "内容只显示在隐私仓库中，不会进入普通笔记列表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = PrivacyScreenShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("标题") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    label = { Text("内容（Markdown）") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editingId > 0) {
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("删除")
                }
            }
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = onSave) { Text("保存") }
        }
    }
}

@Composable
private fun PrivacyChip(text: String) {
    Surface(
        shape = PrivacyChipShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

private fun formatPrivacyTime(timeMs: Long): String {
    if (timeMs <= 0L) return "未更新"
    return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(timeMs))
}
