package com.kangle.kardleaf.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeMode
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppDrawerContent(
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    labels: List<String>,
    allNotes: List<Note> = emptyList(),
    onScreenSelect: (MainViewModel.Screen) -> Unit,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onCreateLabel: (String) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFolderManagement: () -> Unit,
    onBackActionChanged: ((() -> Boolean)?) -> Unit = {},
    onShowOnboarding: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onPickDrawerAvatar: () -> Unit = {},
    onThemeModeChange: (PrefsManager.AppThemeMode) -> Unit = {},
) {
    val context = LocalContext.current
    val drawerPrefs = remember { PrefsManager(context) }
    val drawerOrder = drawerPrefs.getDrawerItemOrder()
    val hiddenItems = drawerPrefs.getHiddenDrawerItems()
    val drawerStyle = drawerPrefs.getDrawerStyle()
    val drawerGroupStartItems = drawerPrefs.getDrawerGroupStartItems()
    val isModern = LocalKardLeafThemeStyle.current != PrefsManager.AppThemeStyle.CLASSIC
    val drawerBackground = MaterialTheme.colorScheme.surfaceContainer

    ModalDrawerSheet(
        modifier = Modifier.width(if (isModern) 292.dp else 280.dp),
        drawerContainerColor = drawerBackground,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        var collapsedFolders by remember(labels) { mutableStateOf<Set<String>>(emptySet()) }
        var showFiles by remember { mutableStateOf(false) }
        var selectedFolderPath by remember(labels) { mutableStateOf<String?>(null) }
        var drawerUiBackStack by remember(labels) { mutableStateOf<List<DrawerUiState>>(emptyList()) }
        val visibleLabels = labels

        fun currentDrawerUiState(): DrawerUiState =
            DrawerUiState(
                showFiles = showFiles,
                selectedFolderPath = selectedFolderPath,
                collapsedFolders = collapsedFolders,
            )

        fun pushDrawerUiState() {
            val state = currentDrawerUiState()
            if (drawerUiBackStack.lastOrNull() != state) {
                drawerUiBackStack = drawerUiBackStack + state
            }
        }

        fun restoreDrawerUiState(state: DrawerUiState) {
            showFiles = state.showFiles
            selectedFolderPath = state.selectedFolderPath
            collapsedFolders = state.collapsedFolders
        }

        LaunchedEffect(drawerUiBackStack, showFiles, selectedFolderPath, collapsedFolders) {
            onBackActionChanged {
                val previous = drawerUiBackStack.lastOrNull()
                if (previous == null) {
                    false
                } else {
                    drawerUiBackStack = drawerUiBackStack.dropLast(1)
                    restoreDrawerUiState(previous)
                    true
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose { onBackActionChanged(null) }
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(drawerBackground),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
            ) {
                if (drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) {
                    // 方案四是独立侧边栏布局，不依赖“非旧主题”。
                    // 否则用户在旧主题/经典主题下选择数据卡片式时，看不到热力图。
                    DataCardDrawerHeader(
                        avatarUri = drawerPrefs.getDrawerAvatarUri(),
                        onPickAvatar = onPickDrawerAvatar,
                        onOpenSettings = onOpenSettings,
                        onThemeModeChange = onThemeModeChange,
                    )
                    DataCardHeatmap(allNotes = allNotes)
                } else if (isModern) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(28.dp),
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${stringResource(R.string.app_name_cn)} · ${stringResource(R.string.app_author)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "${stringResource(R.string.app_name_cn)} · ${stringResource(R.string.app_author)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }

                // 可编辑的侧边栏功能项（顺序、显隐、分组由设置“侧边栏调整”控制）
                val visibleDrawerItems = drawerOrder.filter { it !in hiddenItems }
                if (drawerStyle.isGroupedDrawerStyle()) {
                    buildDrawerItemGroups(visibleDrawerItems, drawerGroupStartItems).forEach { groupItems ->
                        DrawerItemGroup(drawerStyle = drawerStyle) {
                            groupItems.forEach { itemId ->
                                AppDrawerFunctionalItem(
                                    itemId = itemId,
                                    drawerPrefs = drawerPrefs,
                                    currentScreen = currentScreen,
                                    currentFilter = currentFilter,
                                    onDashboardFilterSelect = onDashboardFilterSelect,
                                    onScreenSelect = onScreenSelect,
                                    onOpenFolderManagement = onOpenFolderManagement,
                                    onShowOnboarding = onShowOnboarding,
                                    onOpenSettings = onOpenSettings,
                                    onOpenPrivacy = onOpenPrivacy,
                                )
                            }
                        }
                    }
                } else {
                    visibleDrawerItems.forEach { itemId ->
                        AppDrawerFunctionalItem(
                            itemId = itemId,
                            drawerPrefs = drawerPrefs,
                            currentScreen = currentScreen,
                            currentFilter = currentFilter,
                            onDashboardFilterSelect = onDashboardFilterSelect,
                            onScreenSelect = onScreenSelect,
                            onOpenFolderManagement = onOpenFolderManagement,
                            onShowOnboarding = onShowOnboarding,
                            onOpenSettings = onOpenSettings,
                            onOpenPrivacy = onOpenPrivacy,
                        )
                    }
                }

            }
        }
    }
}


@Composable
private fun DataCardDrawerHeader(
    avatarUri: String?,
    onPickAvatar: () -> Unit,
    onOpenSettings: () -> Unit,
    onThemeModeChange: (PrefsManager.AppThemeMode) -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val themeStyle = LocalKardLeafThemeStyle.current
    val themeMode = LocalKardLeafThemeMode.current
    val isDarkNow = themeStyle == PrefsManager.AppThemeStyle.DRACULA ||
        themeStyle == PrefsManager.AppThemeStyle.GITHUB_DARK ||
        when (themeMode) {
            PrefsManager.AppThemeMode.SYSTEM -> systemDark
            PrefsManager.AppThemeMode.LIGHT -> false
            PrefsManager.AppThemeMode.DARK -> true
        }
    val avatarImage = rememberDrawerAvatarImage(avatarUri)
    var showAvatarDialog by remember { mutableStateOf(false) }

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text("头像") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(132.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarImage != null) {
                            Image(
                                bitmap = avatarImage,
                                contentDescription = "头像预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = "头像预览",
                                modifier = Modifier.size(96.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(onClick = onPickAvatar) {
                        Text("上传头像")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarDialog = false }) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAvatarDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { showAvatarDialog = true },
            contentAlignment = Alignment.Center,
        ) {
            if (avatarImage != null) {
                Image(
                    bitmap = avatarImage,
                    contentDescription = "更换头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = "更换头像",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.app_name_cn),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = {
                onThemeModeChange(
                    if (isDarkNow) PrefsManager.AppThemeMode.LIGHT else PrefsManager.AppThemeMode.DARK,
                )
            },
        ) {
            Icon(
                imageVector = if (isDarkNow) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = "切换黑夜模式",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberDrawerAvatarImage(avatarUri: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(avatarUri) {
        avatarUri
            ?.takeIf { it.isNotBlank() }
            ?.let { uriText ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uriText))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
    }
}

@Composable
private fun DataCardHeatmap(allNotes: List<Note>) {
    val today = remember { heatmapDayStart(Date()) }
    val monthStart = remember(today) {
        Calendar.getInstance().apply {
            time = today
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -2)
        }.time
    }
    val gridStart = remember(monthStart) {
        Calendar.getInstance().apply {
            time = monthStart
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
        }.time
    }
    val heatmapStats = remember(allNotes, today, monthStart, gridStart) {
        buildHeatmapStats(
            notes = allNotes,
            rangeStart = monthStart,
            rangeEnd = today,
            gridStart = gridStart,
        )
    }
    val monthFormatter = remember { SimpleDateFormat("M月", Locale.getDefault()) }
    val monthLabels = remember(monthStart) {
        List(3) { offset ->
            Calendar.getInstance().apply {
                time = monthStart
                add(Calendar.MONTH, offset)
            }.time
        }.map { monthFormatter.format(it) }
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            monthLabels.forEach { month ->
                Text(
                    text = month,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                heatmapStats.columns.forEach { week ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        week.forEach { day ->
                            val color = when {
                                day.date.before(monthStart) || day.date.after(today) -> {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
                                }
                                day.noteCount <= 0 -> {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                                }
                                day.noteCount == 1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                day.noteCount == 2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.46f)
                                day.noteCount == 3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(11.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color),
                            )
                        }
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        modifier = Modifier.height(11.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DataCardHeatmapStat(
                value = heatmapStats.activeDayCount.toString(),
                label = "使用天数",
                modifier = Modifier.weight(1f),
            )
            DataCardStatDivider()
            DataCardHeatmapStat(
                value = heatmapStats.noteCount.toString(),
                label = "笔记数量",
                modifier = Modifier.weight(1f),
            )
            DataCardStatDivider()
            DataCardHeatmapStat(
                value = formatHeatmapNumber(heatmapStats.characterCount),
                label = "文字数量",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DataCardHeatmapStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DataCardStatDivider() {
    Box(
        modifier = Modifier
            .height(30.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    )
}

private data class HeatmapStats(
    val columns: List<List<HeatmapDay>>,
    val activeDayCount: Int,
    val noteCount: Int,
    val characterCount: Long,
)

private data class HeatmapDay(
    val date: Date,
    val noteCount: Int,
)

private fun buildHeatmapStats(
    notes: List<Note>,
    rangeStart: Date,
    rangeEnd: Date,
    gridStart: Date,
): HeatmapStats {
    val dayCounts = mutableMapOf<Long, Int>()
    var noteCount = 0
    var characterCount = 0L

    notes.forEach { note ->
        if (note.isTrashed || note.isArchived) return@forEach
        val createdDay = heatmapDayStart(note.createdAt)
        if (createdDay.before(rangeStart) || createdDay.after(rangeEnd)) return@forEach
        val key = createdDay.time
        dayCounts[key] = (dayCounts[key] ?: 0) + 1
        noteCount++
        val text = note.content.ifBlank { note.contentPreview }
        characterCount += text.count { !it.isWhitespace() }.toLong()
    }

    val columns = mutableListOf<List<HeatmapDay>>()
    val cursor = Calendar.getInstance().apply { time = gridStart }
    while (!cursor.time.after(rangeEnd)) {
        val week = mutableListOf<HeatmapDay>()
        repeat(7) {
            val date = cursor.time
            week.add(HeatmapDay(date = date, noteCount = dayCounts[date.time] ?: 0))
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        columns.add(week)
    }

    return HeatmapStats(
        columns = columns,
        activeDayCount = dayCounts.size,
        noteCount = noteCount,
        characterCount = characterCount,
    )
}

private fun heatmapDayStart(date: Date): Date =
    Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

private fun formatHeatmapNumber(value: Long): String =
    when {
        value >= 100_000_000L -> String.format(Locale.getDefault(), "%.1f亿", value / 100_000_000f)
        value >= 10_000L -> String.format(Locale.getDefault(), "%.1f万", value / 10_000f)
        else -> value.toString()
    }


private fun PrefsManager.DrawerStyle.isGroupedDrawerStyle(): Boolean =
    this == PrefsManager.DrawerStyle.GROUPED_CARD || this == PrefsManager.DrawerStyle.DATA_CARD

private fun buildDrawerItemGroups(
    visibleItems: List<PrefsManager.DrawerItemId>,
    groupStartItems: Set<PrefsManager.DrawerItemId>,
): List<List<PrefsManager.DrawerItemId>> {
    if (visibleItems.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<PrefsManager.DrawerItemId>>()
    visibleItems.forEachIndexed { index, itemId ->
        if (index == 0 || itemId in groupStartItems) {
            groups.add(mutableListOf())
        }
        groups.last().add(itemId)
    }
    return groups.filter { it.isNotEmpty() }
}

@Composable
private fun DrawerItemGroup(
    drawerStyle: PrefsManager.DrawerStyle,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(if (drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) 24.dp else 22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) 0.72f else 0.88f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                shape = shape,
            )
            .padding(vertical = if (drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) 8.dp else 6.dp),
    ) {
        content()
    }
}

@Composable
private fun AppDrawerFunctionalItem(
    itemId: PrefsManager.DrawerItemId,
    drawerPrefs: PrefsManager,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onScreenSelect: (MainViewModel.Screen) -> Unit,
    onOpenFolderManagement: () -> Unit,
    onShowOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    if (itemId == PrefsManager.DrawerItemId.FILES) {
        ThemedDrawerItem(
            label = drawerPrefs.getDrawerItemLabel(
                itemId,
                if (drawerPrefs.getAppLanguage() == "en") englishDrawerItemLabel(itemId) else defaultDrawerItemLabel(itemId),
            ),
            icon = Icons.Outlined.Folder,
            selected = currentScreen is MainViewModel.Screen.Folders,
            onClick = { onOpenFolderManagement() },
        )
    } else {
        DrawerEntry(
            itemId = itemId,
            currentScreen = currentScreen,
            currentFilter = currentFilter,
            onDashboardFilterSelect = onDashboardFilterSelect,
            onScreenSelect = onScreenSelect,
            onShowOnboarding = onShowOnboarding,
            onOpenSettings = onOpenSettings,
            onOpenPrivacy = onOpenPrivacy,
        )
    }
}

private data class DrawerUiState(
    val showFiles: Boolean,
    val selectedFolderPath: String?,
    val collapsedFolders: Set<String>,
)

private data class FolderNode(
    val name: String,
    val path: String,
    val children: List<FolderNode>,
)

@Composable
private fun FileDrawerSection(
    visibleLabels: List<String>,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    collapsedFolders: Set<String>,
    selectedFolderPath: String?,
    onToggleFolder: (String) -> Unit,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onSelectFolder: (String?) -> Unit,
) {
    if (visibleLabels.isNotEmpty()) {
        FolderTree(
            nodes = buildFolderTree(visibleLabels),
            currentScreen = currentScreen,
            currentFilter = currentFilter,
            collapsedFolders = collapsedFolders,
            selectedFolderPath = selectedFolderPath,
            onToggleFolder = onToggleFolder,
            onDashboardFilterSelect = onDashboardFilterSelect,
            onDeleteLabel = onDeleteLabel,
            onRenameLabel = onRenameLabel,
            onSelectFolder = onSelectFolder,
        )
    }
}

private fun buildFolderTree(paths: List<String>): List<FolderNode> {
    fun build(prefix: String): List<FolderNode> {
        val prefixWithSlash = prefix.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
        return paths
            .asSequence()
            .filter { it.startsWith(prefixWithSlash) && it != prefix }
            .map { it.removePrefix(prefixWithSlash).substringBefore("/") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { name ->
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                FolderNode(
                    name = name,
                    path = path,
                    children = build(path),
                )
            }
            .toList()
    }
    return build("")
}

@Composable
private fun FolderTree(
    nodes: List<FolderNode>,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    collapsedFolders: Set<String>,
    selectedFolderPath: String?,
    onToggleFolder: (String) -> Unit,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    depth: Int = 0,
) {
    nodes.forEach { node ->
        FolderTreeItem(
            node = node,
            depth = depth,
            currentScreen = currentScreen,
            currentFilter = currentFilter,
            collapsedFolders = collapsedFolders,
            selectedFolderPath = selectedFolderPath,
            onToggleFolder = onToggleFolder,
            onDashboardFilterSelect = onDashboardFilterSelect,
            onDeleteLabel = onDeleteLabel,
            onRenameLabel = onRenameLabel,
            onSelectFolder = onSelectFolder,
        )
        if (node.children.isNotEmpty() && node.path !in collapsedFolders) {
            FolderTree(
                nodes = node.children,
                currentScreen = currentScreen,
                currentFilter = currentFilter,
                collapsedFolders = collapsedFolders,
                selectedFolderPath = selectedFolderPath,
                onToggleFolder = onToggleFolder,
                onDashboardFilterSelect = onDashboardFilterSelect,
                onDeleteLabel = onDeleteLabel,
                onRenameLabel = onRenameLabel,
                onSelectFolder = onSelectFolder,
                depth = depth + 1,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FolderTreeItem(
    node: FolderNode,
    depth: Int,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    collapsedFolders: Set<String>,
    selectedFolderPath: String?,
    onToggleFolder: (String) -> Unit,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onSelectFolder: (String?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isModern = LocalKardLeafThemeStyle.current != PrefsManager.AppThemeStyle.CLASSIC
    val isFilterSelected = currentScreen is MainViewModel.Screen.Dashboard && (currentFilter as? MainViewModel.NoteFilter.Label)?.name == node.path
    val isActionSelected = selectedFolderPath == node.path
    val isSelected = isFilterSelected || isActionSelected
    val hasChildren = node.children.isNotEmpty()
    val isCollapsed = node.path in collapsedFolders
    var isEditing by remember(node.path) { mutableStateOf(false) }
    var editedName by remember(node.path) { mutableStateOf(node.name) }
    val editFocusRequester = remember { FocusRequester() }
    var hasEditFocused by remember(node.path) { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            hasEditFocused = false
            delay(100)
            editFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(selectedFolderPath) {
        if (selectedFolderPath != node.path && isEditing) {
            isEditing = false
            editedName = node.name
            hasEditFocused = false
        }
    }

    if (isEditing) {
        val editShape = RoundedCornerShape(if (isModern) 20.dp else 0.dp)
        Row(
            modifier =
                if (isModern) {
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp + (depth * 12).dp, end = 12.dp, top = 3.dp, bottom = 3.dp)
                        .height(48.dp)
                        .clip(editShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), editShape)
                        .padding(start = 4.dp, end = 4.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 4.dp + (depth * 16).dp, end = 4.dp)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    isEditing = false
                    onDeleteLabel(node.path)
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除文件夹",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            TextField(
                value = editedName,
                onValueChange = { editedName = it },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(editFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            hasEditFocused = true
                        } else if (hasEditFocused && !focusState.isFocused) {
                            isEditing = false
                            hasEditFocused = false
                        }
                    },
                textStyle = MaterialTheme.typography.bodyMedium,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
            )
            IconButton(
                onClick = {
                    val trimmed = editedName.trim()
                    if (trimmed.isNotBlank() && trimmed != node.name) {
                        val parent = node.path.substringBeforeLast("/", missingDelimiterValue = "")
                        val newPath = if (parent.isBlank()) trimmed else "$parent/$trimmed"
                        onRenameLabel(node.path, newPath)
                    }
                    isEditing = false
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = "保存文件夹名称")
            }
        }
        return
    }

    val folderClick: () -> Unit = {
        if (isActionSelected) {
            editedName = node.name
            isEditing = true
        } else {
            onSelectFolder(null)
            onDashboardFilterSelect(MainViewModel.NoteFilter.Label(node.path))
        }
    }
    val folderLongClick: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        editedName = node.name
        onSelectFolder(node.path)
    }

    if (!isModern) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color.Transparent
                        },
                    )
                    .padding(start = 24.dp + (depth * 16).dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = folderClick,
                            onLongClick = folderLongClick,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = selectedFolderPath != null,
                            onClick = { onSelectFolder(null) },
                        ),
            )
            if (hasChildren) {
                IconButton(
                    onClick = { onToggleFolder(node.path) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isCollapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (isCollapsed) "展开文件夹" else "折叠文件夹",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val folderShape = RoundedCornerShape(20.dp)
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            Color.Transparent
        },
        label = "DrawerFolderBackground",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        } else {
            Color.Transparent
        },
        label = "DrawerFolderBorder",
    )
    val iconBackgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
        },
        label = "DrawerFolderIconBackground",
    )
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        label = "DrawerFolderPressedScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp + (depth * 12).dp, end = 12.dp, top = 3.dp, bottom = 3.dp)
            .graphicsLayer {
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .clip(folderShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, folderShape)
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = folderClick,
                    onLongClick = folderLongClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(
            modifier = Modifier
                .width(4.dp)
                .height(42.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = selectedFolderPath != null,
                    onClick = { onSelectFolder(null) },
                ),
        )
        if (hasChildren) {
            IconButton(
                onClick = { onToggleFolder(node.path) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (isCollapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (isCollapsed) "展开文件夹" else "折叠文件夹",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
