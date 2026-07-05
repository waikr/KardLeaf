package com.kangle.kardleaf.ui

import androidx.compose.ui.tooling.preview.Preview
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

private const val DATE_SCOPE_MENU_REOPEN_GUARD_MS = 250L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
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
    var lastDateScopeMenuDismissAt by remember { mutableStateOf(0L) }
    var dateScopeMode by remember { mutableStateOf(DateScopeMode.TODAY) }
    var calendarSlideDirection by remember { mutableStateOf(1) }

    fun resetToTodayScope() {
        val today = startOfDay(System.currentTimeMillis())
        selectedDay = today
        visibleMonth = firstDayOfMonth(today)
        dateScopeMode = DateScopeMode.TODAY
        showDateScopeMenu = false
    }

    fun switchCalendarMonth(forward: Boolean) {
        calendarSlideDirection = if (forward) 1 else -1
        visibleMonth = addMonths(visibleMonth, if (forward) 1 else -1)
        dateScopeMode = DateScopeMode.MONTH
    }

    BackHandler(enabled = showDateScopeMenu || dateScopeMode != DateScopeMode.TODAY) {
        if (showDateScopeMenu) {
            showDateScopeMenu = false
        } else {
            resetToTodayScope()
        }
    }
    val monthTitleFormat = remember { SimpleDateFormat("yyyy 年 M 月", Locale.getDefault()) }
    val dayTitleFormat = remember { SimpleDateFormat("yyyy年M月d日", Locale.getDefault()) }
    val notesByDay = remember(activeNotes) {
        activeNotes.groupBy { startOfDay(it.lastModified.time) }
    }
    val scopeStart = remember(selectedDay, visibleMonth, dateScopeMode) {
        when (dateScopeMode) {
            DateScopeMode.TODAY -> selectedDay
            DateScopeMode.WEEK -> buildWeekDays(selectedDay).firstOrNull() ?: selectedDay
            DateScopeMode.MONTH -> firstDayOfMonth(visibleMonth)
        }
    }
    val scopeEnd = remember(scopeStart, dateScopeMode) {
        when (dateScopeMode) {
            DateScopeMode.TODAY -> addDays(scopeStart, 1)
            DateScopeMode.WEEK -> addDays(scopeStart, 7)
            DateScopeMode.MONTH -> addMonths(scopeStart, 1)
        }
    }
    val selectedNotes = remember(activeNotes, scopeStart, scopeEnd) {
        activeNotes
            .filter { note -> note.lastModified.time >= scopeStart && note.lastModified.time < scopeEnd }
            .sortedByDescending { it.lastModified }
    }

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
                    IconButton(onClick = { switchCalendarMonth(forward = false) }) {
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
                    IconButton(onClick = { switchCalendarMonth(forward = true) }) {
                        Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }
            }
            item {
                var dragTotal by remember { mutableStateOf(0f) }
                val swipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
                AnimatedContent(
                    targetState = visibleMonth,
                    transitionSpec = {
                        kardLeafHorizontalContentTransform(
                            forward = calendarSlideDirection > 0,
                            durationMillis = 220,
                            distanceFactor = 0.18f,
                        )
                    },
                    label = "calendarMonthSwitch",
                ) { month ->
                    val panelDays = if (dateScopeMode == DateScopeMode.WEEK && month == visibleMonth) {
                        buildWeekDays(selectedDay)
                    } else {
                        buildCalendarDays(month)
                    }
                    CalendarMonthGrid(
                        days = panelDays,
                        visibleMonth = month,
                        selectedDay = selectedDay,
                        notesByDay = notesByDay,
                        modifier = Modifier.pointerInput(month, dateScopeMode) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragTotal = 0f },
                                onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                                onDragEnd = {
                                    when {
                                        dragTotal <= -swipeThreshold -> switchCalendarMonth(forward = true)
                                        dragTotal >= swipeThreshold -> switchCalendarMonth(forward = false)
                                    }
                                    dragTotal = 0f
                                },
                                onDragCancel = { dragTotal = 0f },
                            )
                        },
                        onSelectDay = { day ->
                            selectedDay = day
                            visibleMonth = firstDayOfMonth(day)
                            dateScopeMode = DateScopeMode.TODAY
                            showDateScopeMenu = false
                        },
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = scopeTitleText(
                            mode = dateScopeMode,
                            selectedDay = selectedDay,
                            visibleMonth = visibleMonth,
                            count = selectedNotes.size,
                            dayTitleFormat = dayTitleFormat,
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box {
                        DateScopeButton(
                            text = dateScopeMode.label,
                            selected = true,
                            onClick = {
                                val now = SystemClock.uptimeMillis()
                                val ignoreReopen = !showDateScopeMenu &&
                                    now - lastDateScopeMenuDismissAt < DATE_SCOPE_MENU_REOPEN_GUARD_MS
                                if (showDateScopeMenu) {
                                    lastDateScopeMenuDismissAt = now
                                    showDateScopeMenu = false
                                } else if (!ignoreReopen) {
                                    showDateScopeMenu = true
                                }
                            },
                        )
                        if (showDateScopeMenu) {
                            Popup(
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, with(LocalDensity.current) { 44.dp.roundToPx() }),
                                onDismissRequest = {
                                    lastDateScopeMenuDismissAt = SystemClock.uptimeMillis()
                                    showDateScopeMenu = false
                                },
                                properties = PopupProperties(focusable = false),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    DateScopeMode.values()
                                        .filter { mode ->
                                            mode != dateScopeMode &&
                                                (dateScopeMode == DateScopeMode.TODAY || mode != DateScopeMode.TODAY)
                                        }
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
                        Text(emptyScopeText(dateScopeMode), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun scopeTitleText(
    mode: DateScopeMode,
    selectedDay: Long,
    visibleMonth: Long,
    count: Int,
    dayTitleFormat: SimpleDateFormat,
): String =
    when (mode) {
        DateScopeMode.TODAY -> "${dayTitleFormat.format(Date(selectedDay))} · $count 篇"
        DateScopeMode.WEEK -> "本周 · $count 篇"
        DateScopeMode.MONTH -> SimpleDateFormat("yyyy年M月", Locale.getDefault()).format(Date(visibleMonth)) + " · $count 篇"
    }

private fun emptyScopeText(mode: DateScopeMode): String =
    when (mode) {
        DateScopeMode.TODAY -> "这一天没有笔记"
        DateScopeMode.WEEK -> "本周没有笔记"
        DateScopeMode.MONTH -> "本月没有笔记"
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
    modifier: Modifier = Modifier,
    onSelectDay: (Long) -> Unit,
) {
    val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val content =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
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
                modifier = Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

@Composable
private fun CompactNoteRow(
    note: Note,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.title.ifBlank { note.file.nameWithoutExtension },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.contentPreview.isNotBlank()) {
                Text(
                    text = note.contentPreview.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
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

private fun addDays(dayStart: Long, days: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_MONTH, days)
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
