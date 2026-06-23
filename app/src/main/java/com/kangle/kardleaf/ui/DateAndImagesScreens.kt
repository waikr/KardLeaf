package com.kangle.kardleaf.ui

import androidx.compose.ui.tooling.preview.Preview
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateNotesScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onNoteClick: (Note) -> Unit,
) {
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    val activeNotes = remember(notes) { notes.filter { !it.isTrashed } }
    var visibleMonth by remember { mutableStateOf(firstDayOfMonth(System.currentTimeMillis())) }
    var selectedDay by remember { mutableStateOf(startOfDay(System.currentTimeMillis())) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showDateScopeMenu by remember { mutableStateOf(false) }
    var dateScopeMode by remember { mutableStateOf(DateScopeMode.TODAY) }

    BackHandler(enabled = showDateScopeMenu) {
        showDateScopeMenu = false
    }
    val monthTitleFormat = remember { SimpleDateFormat("yyyy 年 M 月", Locale.getDefault()) }
    val dayTitleFormat = remember { SimpleDateFormat("yyyy年M月d日", Locale.getDefault()) }
    val days = remember(visibleMonth, selectedDay, dateScopeMode) {
        if (dateScopeMode == DateScopeMode.WEEK) buildWeekDays(selectedDay) else buildCalendarDays(visibleMonth)
    }
    val notesByDay = remember(activeNotes) {
        activeNotes.groupBy { startOfDay(it.lastModified.time) }
    }
    val selectedNotes = notesByDay[selectedDay].orEmpty().sortedByDescending { it.lastModified }

    if (showMonthPicker) {
        MonthPickerDialog(
            currentMonth = visibleMonth,
            onDismiss = { showMonthPicker = false },
            onMonthSelected = { month ->
                visibleMonth = month
                dateScopeMode = DateScopeMode.MONTH
                showMonthPicker = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日期") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        visibleMonth = addMonths(visibleMonth, -1)
                        dateScopeMode = DateScopeMode.MONTH
                    }) {
                        Icon(Icons.Outlined.KeyboardArrowLeft, contentDescription = "上个月")
                    }
                    Text(
                        text = monthTitleFormat.format(Date(visibleMonth)),
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showMonthPicker = true }
                                .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = {
                        visibleMonth = addMonths(visibleMonth, 1)
                        dateScopeMode = DateScopeMode.MONTH
                    }) {
                        Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }
            }
            item {
                CalendarMonthGrid(
                    days = days,
                    visibleMonth = visibleMonth,
                    selectedDay = selectedDay,
                    notesByDay = notesByDay,
                    onSelectDay = { day ->
                        selectedDay = day
                        if (dateScopeMode == DateScopeMode.TODAY && day != startOfDay(System.currentTimeMillis())) {
                            dateScopeMode = DateScopeMode.MONTH
                        }
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${dayTitleFormat.format(Date(selectedDay))} · ${selectedNotes.size} 篇",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box {
                        DateScopeButton(
                            text = dateScopeMode.label,
                            selected = true,
                            onClick = { showDateScopeMenu = !showDateScopeMenu },
                        )
                        if (showDateScopeMenu) {
                            Popup(
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, with(LocalDensity.current) { 44.dp.roundToPx() }),
                                onDismissRequest = { showDateScopeMenu = false },
                                properties = PopupProperties(focusable = false),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    DateScopeMode.values()
                                        .filter { it != dateScopeMode }
                                        .forEach { mode ->
                                            DateScopeButton(
                                                text = mode.label,
                                                selected = false,
                                                onClick = {
                                                    when (mode) {
                                                        DateScopeMode.TODAY -> {
                                                            val today = startOfDay(System.currentTimeMillis())
                                                            selectedDay = today
                                                            visibleMonth = firstDayOfMonth(today)
                                                        }
                                                        DateScopeMode.WEEK -> {
                                                            visibleMonth = firstDayOfMonth(selectedDay)
                                                        }
                                                        DateScopeMode.MONTH -> {
                                                            visibleMonth = firstDayOfMonth(selectedDay)
                                                        }
                                                    }
                                                    dateScopeMode = mode
                                                    showDateScopeMenu = false
                                                },
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }
            if (selectedNotes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("这一天没有笔记", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(selectedNotes, key = { "${it.file.path}|${it.lastModified.time}" }) { note ->
                    CompactNoteRow(note = note, onClick = { onNoteClick(note) })
                }
            }
        }
    }
}

@Composable
private fun DateScopeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        }
    val textColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

private enum class DateScopeMode(val label: String) {
    TODAY("今天"),
    WEEK("本周"),
    MONTH("本月"),
}

@Composable
private fun MonthPickerDialog(
    currentMonth: Long,
    onDismiss: () -> Unit,
    onMonthSelected: (Long) -> Unit,
) {
    val calendar = remember(currentMonth) { Calendar.getInstance().apply { timeInMillis = currentMonth } }
    var year by remember(currentMonth) { mutableStateOf(calendar.get(Calendar.YEAR)) }
    val selectedMonth = calendar.get(Calendar.MONTH)
    val monthLabels = remember { (1..12).map { "${it} 月" } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择年月") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { year -= 1 }) {
                        Icon(Icons.Outlined.KeyboardArrowLeft, contentDescription = "上一年")
                    }
                    Text(
                        text = "${year} 年",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { year += 1 }) {
                        Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "下一年")
                    }
                }
                monthLabels.chunked(3).forEachIndexed { rowIndex, rowMonths ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowMonths.forEachIndexed { columnIndex, label ->
                            val month = rowIndex * 3 + columnIndex
                            TextButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onMonthSelected(firstDayOfYearMonth(year, month)) },
                            ) {
                                Text(
                                    text = label,
                                    color = if (month == selectedMonth && year == calendar.get(Calendar.YEAR)) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun CalendarMonthGrid(
    days: List<Long>,
    visibleMonth: Long,
    selectedDay: Long,
    notesByDay: Map<Long, List<Note>>,
    onSelectDay: (Long) -> Unit,
) {
    val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        isCurrentMonth = isSameMonth(day, visibleMonth),
                        isSelected = day == selectedDay,
                        count = notesByDay[day].orEmpty().size,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectDay(day) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Long,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    count: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val calendar = remember(day) { Calendar.getInstance().apply { timeInMillis = day } }
    val container =
        when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            count > 0 -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
        }
    val content =
        when {
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            count > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
    Column(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(container)
                .clickable(onClick = onClick)
                .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = calendar.get(Calendar.DAY_OF_MONTH).toString(),
            color = content.copy(alpha = if (isCurrentMonth) 1f else 0.38f),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (count > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier.size(5.dp).clip(CircleShape).background(content),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteImagesScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onNoteClick: (Note) -> Unit,
) {
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(notes) {
        val activeNotes = notes.filter { !it.isTrashed }
        if (activeNotes.isEmpty()) {
            images = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            val result = mutableListOf<GalleryImage>()
            for (note in activeNotes) {
                ensureActive()
                // 关键修复：用 async + await 让超时真正生效。resolveNoteImages
                // 内部是 withContext(IO) 的阻塞 SAF 调用（findFile/openInputStream/
                // readBytes 无挂起点）；直接用 withTimeoutOrNull 包裹时，若某篇
                // 笔记的 SAF 操作挂起，IO 线程被阻塞，withContext 无法恢复，超时
                // 永不触发，导致一直转圈。改为 async 启动任务后对 await()（真正
                // 的挂起点）施加超时，超时即放弃该篇继续下一篇。
                val deferred = async {
                    runCatching { viewModel.resolveNoteImages(note) }.getOrDefault(emptyList())
                }
                val imagesForNote = withTimeoutOrNull(5000L) { deferred.await() } ?: emptyList()
                result.addAll(imagesForNote.map { image ->
                    GalleryImage(note = note, reference = image.reference, dataUri = image.dataUri)
                })
                images = result.toList()
            }
        } catch (_: Exception) {
            // 即使出错也不卡在加载状态
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("图片") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading && images.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                images.isEmpty() -> Text(
                    text = "当前笔记没有可显示的本地图片",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(images, key = { "${it.note.file.path}:${it.reference}:${it.note.lastModified.time}" }) { image ->
                        ImageGalleryCard(image = image, onClick = { onNoteClick(image.note) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGalleryCard(
    image: GalleryImage,
    onClick: () -> Unit,
) {
    val bitmap = remember(image.dataUri) { decodeDataUriBitmap(image.dataUri) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = image.reference,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("无法显示", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = image.note.title.ifBlank { image.note.file.nameWithoutExtension },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = image.reference,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactNoteRow(
    note: Note,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = note.title.ifBlank { note.file.nameWithoutExtension },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class GalleryImage(
    val note: Note,
    val reference: String,
    val dataUri: String,
)

private fun decodeDataUriBitmap(dataUri: String): android.graphics.Bitmap? =
    runCatching {
        val base64 = dataUri.substringAfter("base64,", "")
        if (base64.isBlank()) return@runCatching null
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

private fun firstDayOfMonth(timeMillis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun firstDayOfYearMonth(
    year: Int,
    month: Int,
): Long =
    Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun startOfDay(timeMillis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun addMonths(monthStart: Long, months: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = monthStart
        add(Calendar.MONTH, months)
    }.timeInMillis

private fun buildCalendarDays(monthStart: Long): List<Long> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = monthStart
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val leadingDays = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val weekCount = (leadingDays + daysInMonth + 6) / 7
    calendar.add(Calendar.DAY_OF_MONTH, -leadingDays)
    return List(weekCount * 7) {
        val day = startOfDay(calendar.timeInMillis)
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        day
    }
}

private fun buildWeekDays(dayStart: Long): List<Long> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = dayStart
        val offset = get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        add(Calendar.DAY_OF_MONTH, -offset)
    }
    return List(7) {
        val day = startOfDay(calendar.timeInMillis)
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        day
    }
}

private fun isSameMonth(
    left: Long,
    right: Long,
): Boolean {
    val leftCalendar = Calendar.getInstance().apply { timeInMillis = left }
    val rightCalendar = Calendar.getInstance().apply { timeInMillis = right }
    return leftCalendar.get(Calendar.YEAR) == rightCalendar.get(Calendar.YEAR) &&
        leftCalendar.get(Calendar.MONTH) == rightCalendar.get(Calendar.MONTH)
}
