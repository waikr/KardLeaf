package com.kangle.kardleaf.ui

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.NoteRecordSummary
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.localizedText
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val SETTINGS_TRACE_TAG = "KardLeafSettingsTrace"

internal fun settingsPageTitle(page: String): String {
    if (Locale.getDefault().language == "en") {
        return when (page) {
            "layout" -> "Layout"
            "sort" -> "Sorting"
            "theme" -> "Theme"
            "image" -> "Image folder"
            "hiddenFolders" -> "Hidden folders"
            "density" -> "Card density"
            "autoFileName" -> "Automatic file names"
            "date" -> "Date format"
            "cardModifiedDateFormat" -> "Modified date format"
            "openNote" -> "Default editor mode"
            "sidePanelOpenMode" -> "Side panel trigger"
            "backup" -> "Data backup"
            "drawerStyle" -> "Sidebar"
            "interface" -> "Interface"
            "homeBottomToolbar" -> "Home toolbar"
            "drawerSettings" -> "Sidebar"
            "drawer" -> "Sidebar spacing"
            "historyLimit" -> "History limit"
            "trash" -> "Trash"
            "toolbar" -> "Format buttons"
            "editorTopToolbar" -> "Note top bar"
            "selectionToolbar" -> "Selection toolbar"
            "editorTypography" -> "Editor font"
            "appLanguage" -> "Language"
            "drawerEdit" -> "Customize sidebar"
            "imagePath" -> "Image path format"
            "security" -> "Security"
            "passwordMode" -> "Password type"
            "doubleTap" -> "Double-tap interval"
            "previewTheme" -> "Preview theme"
            "autoCodeMirrorThreshold" -> "Automatic editor threshold"
            "trashAutoClean" -> "Automatic trash cleanup"
            "webDav" -> "WebDAV"
            "autoBackup" -> "Auto backup"
            "taskReminders" -> "Tasks & reminders"
            "remarkRecords" -> "Remark records"
            "historyRecords" -> "Version history"
            "about" -> "About"
            else -> "Settings"
        }
    }
    if (page == "editorTypography") return "编辑器字体"
    if (page == "appLanguage") return "语言"
    return when (page) {
        "layout" -> "布局模式"
        "sort" -> "排序方式"
        "theme" -> "主题切换"
        "image" -> "图片保存位置"
        "hiddenFolders" -> "隐藏的文件夹"
        "density" -> "卡片密度"
        "autoFileName" -> "自动文件名"
        "date" -> "日期格式"
        "cardModifiedDateFormat" -> "修改日期格式"
        "openNote" -> "默认编辑器模式"
        "sidePanelOpenMode" -> "侧滑面板弹出方式"
        "backup" -> "数据备份"
        "drawerStyle" -> "侧边栏"
        "drawerSettings" -> "侧边栏"
        "drawer" -> "侧边栏距离"
        "historyLimit" -> "历史版本数量"
        "trash" -> "回收站"
        "toolbar" -> "字符按钮位置"
        "editorTopToolbar" -> "笔记顶部栏"
        "selectionToolbar" -> "长按选择栏"
        "homeBottomToolbar" -> "首页底部工具栏"
        "drawerEdit" -> "侧边栏调整"
        "interface" -> "应用界面"
        "imagePath" -> "图片路径格式"
        "security" -> "安全"
        "passwordMode" -> "密码类型"
        "doubleTap" -> "双击间隔"
        "previewTheme" -> "预览主题"
        "autoCodeMirrorThreshold" -> "自动切换字数"
        "trashAutoClean" -> "自动清理回收站"
        "webDav" -> "WebDAV 云同步"
        "autoBackup" -> "自动备份"
        "taskReminders" -> "任务与提醒"
        "remarkRecords" -> "备注记录"
        "historyRecords" -> "历史版本记录"
        "about" -> "关于"
        else -> "设置"
    }
}

internal fun previewThemeLabel(theme: PrefsManager.PreviewTheme): String =
    if (Locale.getDefault().language == "en") {
        when (theme) {
            PrefsManager.PreviewTheme.FOLLOW_APP -> "Follow app theme"
            PrefsManager.PreviewTheme.GITHUB -> "GitHub"
            PrefsManager.PreviewTheme.NEWSPRINT -> "Newsprint"
            PrefsManager.PreviewTheme.VUE -> "Vue"
            PrefsManager.PreviewTheme.ONE -> "One Light / Dark"
            PrefsManager.PreviewTheme.DRACULA -> "Dracula"
            PrefsManager.PreviewTheme.NORD -> "Nord"
            PrefsManager.PreviewTheme.SOLARIZED -> "Solarized"
        }
    } else {
        when (theme) {
            PrefsManager.PreviewTheme.FOLLOW_APP -> "跟随应用主题"
            PrefsManager.PreviewTheme.GITHUB -> "GitHub"
            PrefsManager.PreviewTheme.NEWSPRINT -> "报纸油墨"
            PrefsManager.PreviewTheme.VUE -> "Vue 文档绿"
            PrefsManager.PreviewTheme.ONE -> "One Light / Dark"
            PrefsManager.PreviewTheme.DRACULA -> "Dracula 霓彩夜"
            PrefsManager.PreviewTheme.NORD -> "Nord 极地蓝"
            PrefsManager.PreviewTheme.SOLARIZED -> "Solarized 护眼"
        }
    }

internal fun previewThemeSubtitle(theme: PrefsManager.PreviewTheme): String =
    if (Locale.getDefault().language == "en") {
        when (theme) {
            PrefsManager.PreviewTheme.FOLLOW_APP -> "Preview colors track the app theme and accent"
            PrefsManager.PreviewTheme.GITHUB -> "Classic github-markdown-css, auto light/dark"
            PrefsManager.PreviewTheme.NEWSPRINT -> "Typora Newsprint style with serif headings"
            PrefsManager.PreviewTheme.VUE -> "Vue docs green accent with orange inline code"
            PrefsManager.PreviewTheme.ONE -> "Atom One editor palette, auto light/dark"
            PrefsManager.PreviewTheme.DRACULA -> "Always-dark purple paper with pink code"
            PrefsManager.PreviewTheme.NORD -> "Arctic blue-gray palette, auto light/dark"
            PrefsManager.PreviewTheme.SOLARIZED -> "Low-contrast classic, auto light/dark"
        }
    } else {
        when (theme) {
            PrefsManager.PreviewTheme.FOLLOW_APP -> "预览配色实时跟随应用主题与强调色"
            PrefsManager.PreviewTheme.GITHUB -> "经典 github-markdown-css 风格，自动明暗"
            PrefsManager.PreviewTheme.NEWSPRINT -> "Typora 报纸风，衬线标题纸质底色"
            PrefsManager.PreviewTheme.VUE -> "Vue 文档绿强调，行内代码橙色高亮"
            PrefsManager.PreviewTheme.ONE -> "Atom 编辑器经典配色，自动明暗"
            PrefsManager.PreviewTheme.DRACULA -> "德古拉暗紫纸面，始终深色"
            PrefsManager.PreviewTheme.NORD -> "北极蓝灰冷色调，自动明暗"
            PrefsManager.PreviewTheme.SOLARIZED -> "低对比经典护眼配色，自动明暗"
        }
    }

internal fun drawerStyleLabel(style: PrefsManager.DrawerStyle): String =
    if (Locale.getDefault().language == "en") {
        when (style) {
            PrefsManager.DrawerStyle.MINIMAL_TEXT -> "Option 1: Minimal text"
            PrefsManager.DrawerStyle.ICON_BOX -> "Option 2: Icon boxes"
            PrefsManager.DrawerStyle.GROUPED_CARD -> "Option 3: Grouped cards"
            PrefsManager.DrawerStyle.DATA_CARD -> "Option 2: Data cards"
        }
    } else {
        when (style) {
            PrefsManager.DrawerStyle.MINIMAL_TEXT -> "方案一：极简文字式"
            PrefsManager.DrawerStyle.ICON_BOX -> "方案二：图标盒子式"
            PrefsManager.DrawerStyle.GROUPED_CARD -> "方案三：分组卡片式"
            PrefsManager.DrawerStyle.DATA_CARD -> "方案二：数据卡片式"
        }
    }

internal fun drawerStyleSubtitle(style: PrefsManager.DrawerStyle): String =
    when (style) {
        PrefsManager.DrawerStyle.MINIMAL_TEXT -> "文字更克制，弱化图标背景"
        PrefsManager.DrawerStyle.ICON_BOX -> "保留图标块，入口更明显"
        PrefsManager.DrawerStyle.GROUPED_CARD -> "按自定义分组显示，不显示分组名"
        PrefsManager.DrawerStyle.DATA_CARD -> "更像数据卡片布局，按自定义分组显示"
    }

internal fun drawerStyleIcon(style: PrefsManager.DrawerStyle): ImageVector =
    when (style) {
        PrefsManager.DrawerStyle.MINIMAL_TEXT -> Icons.Outlined.Description
        PrefsManager.DrawerStyle.ICON_BOX -> Icons.Outlined.Folder
        PrefsManager.DrawerStyle.GROUPED_CARD -> Icons.Outlined.AccountTree
        PrefsManager.DrawerStyle.DATA_CARD -> Icons.Outlined.Functions
    }


internal fun sortSummary(
    order: PrefsManager.SortOrder,
    direction: PrefsManager.SortDirection,
): String {
    if (Locale.getDefault().language == "en") {
        val orderText =
            when (order) {
                PrefsManager.SortOrder.DATE_MODIFIED -> "Modified"
                PrefsManager.SortOrder.TITLE -> "Title"
                PrefsManager.SortOrder.CUSTOM -> "Custom"
            }
        val directionText = if (direction == PrefsManager.SortDirection.DESCENDING) "descending" else "ascending"
        return "$orderText ($directionText)"
    }
    val orderText =
        when (order) {
            PrefsManager.SortOrder.DATE_MODIFIED -> "修改日期"
            PrefsManager.SortOrder.TITLE -> "标题"
            PrefsManager.SortOrder.CUSTOM -> "自定义"
        }
    val directionText = if (direction == PrefsManager.SortDirection.DESCENDING) "降序" else "升序"
    return "$orderText（$directionText）"
}

internal fun toolbarItemIcon(item: KardLeafCustomFeatures.ToolbarItem): ImageVector =
    when (item) {
        KardLeafCustomFeatures.ToolbarItem.PREVIEW -> Icons.Outlined.Visibility
        KardLeafCustomFeatures.ToolbarItem.UNDO -> Icons.Outlined.Undo
        KardLeafCustomFeatures.ToolbarItem.REDO -> Icons.Outlined.Redo
        KardLeafCustomFeatures.ToolbarItem.IMAGE -> Icons.Outlined.Image
        KardLeafCustomFeatures.ToolbarItem.DRAWING -> Icons.Outlined.Palette
        KardLeafCustomFeatures.ToolbarItem.HEADING -> Icons.Outlined.Title
        KardLeafCustomFeatures.ToolbarItem.HEADING2 -> Icons.Outlined.TextIncrease
        KardLeafCustomFeatures.ToolbarItem.HEADING3 -> Icons.Outlined.TextDecrease
        KardLeafCustomFeatures.ToolbarItem.RULE -> Icons.Outlined.HorizontalRule
        KardLeafCustomFeatures.ToolbarItem.BOLD -> Icons.Outlined.FormatBold
        KardLeafCustomFeatures.ToolbarItem.ITALIC -> Icons.Outlined.FormatItalic
        KardLeafCustomFeatures.ToolbarItem.UNDERLINE -> Icons.Outlined.FormatUnderlined
        KardLeafCustomFeatures.ToolbarItem.STRIKE -> Icons.Outlined.StrikethroughS
        KardLeafCustomFeatures.ToolbarItem.LINK -> Icons.Outlined.Link
        KardLeafCustomFeatures.ToolbarItem.CODE -> Icons.Outlined.Code
        KardLeafCustomFeatures.ToolbarItem.CODE_BLOCK -> Icons.Outlined.Terminal
        KardLeafCustomFeatures.ToolbarItem.QUOTE -> Icons.Outlined.FormatQuote
        KardLeafCustomFeatures.ToolbarItem.MATH -> Icons.Outlined.Functions
        KardLeafCustomFeatures.ToolbarItem.BULLET -> Icons.Outlined.FormatListBulleted
        KardLeafCustomFeatures.ToolbarItem.NUMBERED -> Icons.Outlined.FormatListNumbered
        KardLeafCustomFeatures.ToolbarItem.INDENT -> Icons.Outlined.FormatIndentIncrease
        KardLeafCustomFeatures.ToolbarItem.OUTDENT -> Icons.Outlined.FormatIndentDecrease
        KardLeafCustomFeatures.ToolbarItem.CHECKBOX -> Icons.Outlined.CheckBox
        KardLeafCustomFeatures.ToolbarItem.CHECKBOX_DONE -> Icons.Outlined.CheckBoxOutlineBlank
        KardLeafCustomFeatures.ToolbarItem.TABLE -> Icons.Outlined.TableChart
    }

@Composable
internal fun SettingsToolbarGrid(
    items: List<KardLeafCustomFeatures.ToolbarItem>,
    onOrderChange: (List<KardLeafCustomFeatures.ToolbarItem>) -> Unit,
) {
    val columns = 4
    val spacing = 10.dp
    val density = LocalDensity.current
    var draggingItem by remember { mutableStateOf<KardLeafCustomFeatures.ToolbarItem?>(null) }
    var draggingStartIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }

    fun clearDragState() {
        draggingItem = null
        draggingStartIndex = -1
        dragOffset = Offset.Zero
        dragTargetIndex = null
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemSize = (maxWidth - spacing * (columns - 1).toFloat()) / columns
        val itemSizePx = with(density) { itemSize.toPx() }
        val spacingPx = with(density) { spacing.toPx() }
        val rows = items.chunked(columns)

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    rowItems.forEachIndexed { columnIndex, item ->
                        val index = rowIndex * columns + columnIndex
                        val isDragging = draggingItem == item
                        val targetIndex = dragTargetIndex
                        val isDropTarget = targetIndex == index && draggingItem != null && !isDragging
                        val avoidanceOffset = if (!isDragging && targetIndex != null) {
                            calculateToolbarAvoidanceOffset(
                                index = index,
                                fromIndex = draggingStartIndex,
                                toIndex = targetIndex,
                                columns = columns,
                                cellSizePx = itemSizePx + spacingPx,
                            )
                        } else {
                            IntOffset.Zero
                        }

                        SettingsToolbarGridItem(
                            icon = toolbarItemIcon(item),
                            title = item.label,
                            isDragging = isDragging,
                            isDropTarget = isDropTarget,
                            modifier = Modifier
                                .size(itemSize)
                                .zIndex(if (isDragging) 1f else 0f)
                                .offset {
                                    if (isDragging) {
                                        IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                                    } else {
                                        avoidanceOffset
                                    }
                                }
                                .pointerInput(item, items.size, itemSizePx, spacingPx) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingItem = item
                                            draggingStartIndex = index
                                            dragOffset = Offset.Zero
                                            dragTargetIndex = index
                                        },
                                        onDragCancel = { clearDragState() },
                                        onDragEnd = {
                                            val dragged = draggingItem
                                            val fromIndex = if (dragged == null) -1 else items.indexOf(dragged)
                                            val toIndex = dragTargetIndex
                                            if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                                val newOrder = items.toMutableList().also { list ->
                                                    val moved = list.removeAt(fromIndex)
                                                    list.add(toIndex.coerceIn(0, list.size), moved)
                                                }
                                                onOrderChange(newOrder)
                                            }
                                            clearDragState()
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount
                                            dragTargetIndex = calculateToolbarDragTarget(
                                                startIndex = draggingStartIndex,
                                                dragOffset = dragOffset,
                                                columns = columns,
                                                itemSizePx = itemSizePx,
                                                spacingPx = spacingPx,
                                                itemCount = items.size,
                                            )
                                        },
                                    )
                                },
                        )
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.size(itemSize))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsToolbarGridItem(
    icon: ImageVector,
    title: String,
    isDragging: Boolean,
    isDropTarget: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val active = isDragging || isDropTarget
    val borderColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val backgroundColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun calculateToolbarDragTarget(
    startIndex: Int,
    dragOffset: Offset,
    columns: Int,
    itemSizePx: Float,
    spacingPx: Float,
    itemCount: Int,
): Int {
    if (startIndex !in 0 until itemCount) return 0

    val cellSizePx = itemSizePx + spacingPx
    val startColumn = startIndex % columns
    val startRow = startIndex / columns
    val centerX = startColumn * cellSizePx + itemSizePx / 2f + dragOffset.x
    val centerY = startRow * cellSizePx + itemSizePx / 2f + dragOffset.y
    val targetColumn = (centerX / cellSizePx).toInt().coerceIn(0, columns - 1)
    val targetRow = (centerY / cellSizePx).toInt().coerceIn(0, (itemCount - 1) / columns)

    return (targetRow * columns + targetColumn).coerceIn(0, itemCount - 1)
}

internal fun calculateToolbarAvoidanceOffset(
    index: Int,
    fromIndex: Int,
    toIndex: Int,
    columns: Int,
    cellSizePx: Float,
): IntOffset {
    if (fromIndex == toIndex || fromIndex < 0) return IntOffset.Zero

    val visualIndex = when {
        fromIndex < toIndex && index in (fromIndex + 1)..toIndex -> index - 1
        fromIndex > toIndex && index in toIndex until fromIndex -> index + 1
        else -> index
    }
    if (visualIndex == index) return IntOffset.Zero

    val originalColumn = index % columns
    val originalRow = index / columns
    val visualColumn = visualIndex % columns
    val visualRow = visualIndex / columns
    return IntOffset(
        x = ((visualColumn - originalColumn) * cellSizePx).roundToInt(),
        y = ((visualRow - originalRow) * cellSizePx).roundToInt(),
    )
}

internal fun editorTopToolbarAvailableItems(
    noteSidePanelsEnabled: Boolean,
    noteSidePanelOpenMode: PrefsManager.NoteSidePanelOpenMode,
): List<PrefsManager.EditorTopToolbarItemId> {
    val showPanelItems = noteSidePanelsEnabled && noteSidePanelOpenMode == PrefsManager.NoteSidePanelOpenMode.TOOLBAR
    return PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER.filter { item ->
        showPanelItems || (item != PrefsManager.EditorTopToolbarItemId.OUTLINE && item != PrefsManager.EditorTopToolbarItemId.REMARKS)
    }
}

internal fun normalizeEditorTopToolbarOrder(
    order: List<PrefsManager.EditorTopToolbarItemId>,
    availableItems: List<PrefsManager.EditorTopToolbarItemId>,
): List<PrefsManager.EditorTopToolbarItemId> {
    val result = order.filter { it in availableItems }.distinct().toMutableList()
    availableItems.forEach { if (it !in result) result.add(it) }
    return result
}

internal fun editorTopToolbarItemLabel(item: PrefsManager.EditorTopToolbarItemId): String =
    when (item) {
        PrefsManager.EditorTopToolbarItemId.MINDMAP -> localizedText("思维导图", "Mind map")
        PrefsManager.EditorTopToolbarItemId.LABEL -> localizedText("目录", "Folder")
        PrefsManager.EditorTopToolbarItemId.OUTLINE -> localizedText("大纲", "Outline")
        PrefsManager.EditorTopToolbarItemId.REMARKS -> localizedText("属性备注", "Properties & remarks")
        PrefsManager.EditorTopToolbarItemId.SEARCH -> localizedText("搜索", "Search")
        PrefsManager.EditorTopToolbarItemId.EDIT -> localizedText("编辑", "Edit")
        PrefsManager.EditorTopToolbarItemId.HISTORY -> localizedText("历史版本", "Version history")
        PrefsManager.EditorTopToolbarItemId.PRIVACY -> localizedText("保护", "Protect")
        PrefsManager.EditorTopToolbarItemId.ARCHIVE -> localizedText("归档", "Archive")
        PrefsManager.EditorTopToolbarItemId.DELETE -> localizedText("删除", "Delete")
        PrefsManager.EditorTopToolbarItemId.MORE -> localizedText("更多", "More")
    }

internal fun editorTopToolbarItemIcon(item: PrefsManager.EditorTopToolbarItemId): ImageVector =
    when (item) {
        PrefsManager.EditorTopToolbarItemId.MINDMAP -> Icons.Outlined.AccountTree
        PrefsManager.EditorTopToolbarItemId.LABEL -> Icons.Outlined.FolderOpen
        PrefsManager.EditorTopToolbarItemId.OUTLINE -> Icons.Outlined.Toc
        PrefsManager.EditorTopToolbarItemId.REMARKS -> Icons.Outlined.StickyNote2
        PrefsManager.EditorTopToolbarItemId.SEARCH -> Icons.Outlined.Search
        PrefsManager.EditorTopToolbarItemId.EDIT -> Icons.Outlined.Edit
        PrefsManager.EditorTopToolbarItemId.HISTORY -> Icons.Outlined.History
        PrefsManager.EditorTopToolbarItemId.PRIVACY -> Icons.Outlined.Shield
        PrefsManager.EditorTopToolbarItemId.ARCHIVE -> Icons.Outlined.Inventory2
        PrefsManager.EditorTopToolbarItemId.DELETE -> Icons.Outlined.DeleteOutline
        PrefsManager.EditorTopToolbarItemId.MORE -> Icons.Outlined.MoreVert
    }

@Composable
internal fun SettingsEditorTopToolbarDragList(
    items: List<PrefsManager.EditorTopToolbarItemId>,
    moreItems: Set<PrefsManager.EditorTopToolbarItemId>,
    hiddenItems: Set<PrefsManager.EditorTopToolbarItemId>,
    onOrderChange: (List<PrefsManager.EditorTopToolbarItemId>) -> Unit,
    onToggleArea: (PrefsManager.EditorTopToolbarItemId) -> Unit,
    onToggleHidden: (PrefsManager.EditorTopToolbarItemId) -> Unit,
) {
    val rowHeight = 64.dp
    val rowSpacing = 6.dp
    val rowStepPx = with(LocalDensity.current) { (rowHeight + rowSpacing).toPx() }
    var draggingItem by remember { mutableStateOf<PrefsManager.EditorTopToolbarItemId?>(null) }
    var draggingStartIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }

    fun clearDragState() {
        draggingItem = null
        draggingStartIndex = -1
        dragOffset = Offset.Zero
        dragTargetIndex = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        items.forEachIndexed { index, itemId ->
            val itemIsDragging = draggingItem == itemId
            val targetIndex = dragTargetIndex
            val isDropTarget = targetIndex == index && draggingItem != null && !itemIsDragging
            val avoidanceOffset = if (!itemIsDragging && targetIndex != null) {
                calculateDrawerAvoidanceOffset(
                    index = index,
                    fromIndex = draggingStartIndex,
                    toIndex = targetIndex,
                    rowStepPx = rowStepPx,
                )
            } else {
                IntOffset.Zero
            }

            SettingsEditorTopToolbarEditRow(
                icon = editorTopToolbarItemIcon(itemId),
                title = editorTopToolbarItemLabel(itemId),
                isMore = itemId in moreItems,
                isHidden = itemId in hiddenItems,
                canToggleArea = itemId != PrefsManager.EditorTopToolbarItemId.MORE && itemId !in hiddenItems,
                canToggleHidden = itemId != PrefsManager.EditorTopToolbarItemId.MORE,
                isDragging = itemIsDragging,
                isDropTarget = isDropTarget,
                onToggleArea = { onToggleArea(itemId) },
                onToggleHidden = { onToggleHidden(itemId) },
                modifier = Modifier
                    .zIndex(if (itemIsDragging) 1f else 0f)
                    .offset {
                        if (itemIsDragging) {
                            IntOffset(0, dragOffset.y.roundToInt())
                        } else {
                            avoidanceOffset
                        }
                    }
                    .pointerInput(itemId, items.size, rowStepPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingItem = itemId
                                draggingStartIndex = index
                                dragOffset = Offset.Zero
                                dragTargetIndex = index
                            },
                            onDragCancel = { clearDragState() },
                            onDragEnd = {
                                val dragged = draggingItem
                                val fromIndex = if (dragged == null) -1 else items.indexOf(dragged)
                                val toIndex = dragTargetIndex
                                if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                    val newOrder = items.toMutableList().also { list ->
                                        val moved = list.removeAt(fromIndex)
                                        list.add(toIndex.coerceIn(0, list.size), moved)
                                    }
                                    onOrderChange(newOrder)
                                }
                                clearDragState()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                                dragTargetIndex = calculateDrawerDragTarget(
                                    startIndex = draggingStartIndex,
                                    dragOffset = dragOffset,
                                    rowHeightPx = rowStepPx,
                                    itemCount = items.size,
                                )
                            },
                        )
                    },
            )
        }
    }
}

@Composable
internal fun SettingsEditorTopToolbarEditRow(
    icon: ImageVector,
    title: String,
    isMore: Boolean,
    isHidden: Boolean,
    canToggleArea: Boolean,
    canToggleHidden: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    onToggleArea: () -> Unit,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = isDragging || isDropTarget

    LaunchedEffect(title, active, isMore, isHidden, isDragging, isDropTarget) {
        KardLeafLog.d(
            SETTINGS_TRACE_TAG,
            "EditorTopToolbarEditRow title=$title active=$active isMore=$isMore isHidden=$isHidden " +
                "dragging=$isDragging dropTarget=$isDropTarget",
        )
    }

    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = "",
        selected = active,
        onClick = {},
        modifier = modifier,
        contentHorizontalPadding = 14.dp,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canToggleHidden) {
                    TextButton(onClick = onToggleHidden) { Text(if (isHidden) "显示" else "隐藏") }
                }
                if (!isHidden) {
                    if (canToggleArea) {
                        TextButton(onClick = onToggleArea) { Text(if (isMore) "顶部" else "更多") }
                    } else {
                        Text(
                            text = "固定",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

internal fun selectionToolbarItemLabel(item: PrefsManager.SelectionToolbarItemId): String =
    when (item) {
        PrefsManager.SelectionToolbarItemId.MOVE -> localizedText("移动", "Move")
        PrefsManager.SelectionToolbarItemId.COPY -> localizedText("复制", "Copy")
        PrefsManager.SelectionToolbarItemId.PIN -> localizedText("置顶", "Pin")
        PrefsManager.SelectionToolbarItemId.FAVORITE -> localizedText("收藏", "Favorite")
        PrefsManager.SelectionToolbarItemId.TAG -> localizedText("标签", "Tag")
        PrefsManager.SelectionToolbarItemId.ARCHIVE -> localizedText("归档", "Archive")
        PrefsManager.SelectionToolbarItemId.PROPERTIES -> localizedText("属性", "Properties")
        PrefsManager.SelectionToolbarItemId.SHARE -> localizedText("分享", "Share")
        PrefsManager.SelectionToolbarItemId.PRIVACY -> localizedText("保护", "Protect")
        PrefsManager.SelectionToolbarItemId.DELETE -> localizedText("删除", "Delete")
    }

internal fun selectionToolbarItemIcon(item: PrefsManager.SelectionToolbarItemId): ImageVector =
    when (item) {
        PrefsManager.SelectionToolbarItemId.MOVE -> Icons.AutoMirrored.Outlined.DriveFileMove
        PrefsManager.SelectionToolbarItemId.COPY -> Icons.Outlined.ContentCopy
        PrefsManager.SelectionToolbarItemId.PIN -> Icons.Outlined.PushPin
        PrefsManager.SelectionToolbarItemId.FAVORITE -> Icons.Outlined.BookmarkBorder
        PrefsManager.SelectionToolbarItemId.TAG -> Icons.Outlined.Label
        PrefsManager.SelectionToolbarItemId.ARCHIVE -> Icons.Outlined.Archive
        PrefsManager.SelectionToolbarItemId.PROPERTIES -> Icons.Outlined.Info
        PrefsManager.SelectionToolbarItemId.SHARE -> Icons.Outlined.Share
        PrefsManager.SelectionToolbarItemId.PRIVACY -> Icons.Outlined.Lock
        PrefsManager.SelectionToolbarItemId.DELETE -> Icons.Outlined.Delete
    }

@Composable
internal fun SettingsSelectionToolbarDragList(
    items: List<PrefsManager.SelectionToolbarItemId>,
    moreItems: Set<PrefsManager.SelectionToolbarItemId>,
    hiddenItems: Set<PrefsManager.SelectionToolbarItemId>,
    onOrderChange: (List<PrefsManager.SelectionToolbarItemId>) -> Unit,
    onToggleArea: (PrefsManager.SelectionToolbarItemId) -> Unit,
    onToggleHidden: (PrefsManager.SelectionToolbarItemId) -> Unit,
) {
    val rowHeight = 64.dp
    val rowSpacing = 6.dp
    val rowStepPx = with(LocalDensity.current) { (rowHeight + rowSpacing).toPx() }
    var draggingItem by remember { mutableStateOf<PrefsManager.SelectionToolbarItemId?>(null) }
    var draggingStartIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }

    fun clearDragState() {
        draggingItem = null
        draggingStartIndex = -1
        dragOffset = Offset.Zero
        dragTargetIndex = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        items.forEachIndexed { index, itemId ->
            val itemIsDragging = draggingItem == itemId
            val targetIndex = dragTargetIndex
            val isDropTarget = targetIndex == index && draggingItem != null && !itemIsDragging
            val avoidanceOffset = if (!itemIsDragging && targetIndex != null) {
                calculateDrawerAvoidanceOffset(
                    index = index,
                    fromIndex = draggingStartIndex,
                    toIndex = targetIndex,
                    rowStepPx = rowStepPx,
                )
            } else {
                IntOffset.Zero
            }

            SettingsSelectionToolbarEditRow(
                icon = selectionToolbarItemIcon(itemId),
                title = selectionToolbarItemLabel(itemId),
                isMore = itemId in moreItems,
                isHidden = itemId in hiddenItems,
                isDragging = itemIsDragging,
                isDropTarget = isDropTarget,
                onToggleArea = { onToggleArea(itemId) },
                onToggleHidden = { onToggleHidden(itemId) },
                modifier = Modifier
                    .zIndex(if (itemIsDragging) 1f else 0f)
                    .offset {
                        if (itemIsDragging) {
                            IntOffset(0, dragOffset.y.roundToInt())
                        } else {
                            avoidanceOffset
                        }
                    }
                    .pointerInput(itemId, items.size, rowStepPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingItem = itemId
                                draggingStartIndex = index
                                dragOffset = Offset.Zero
                                dragTargetIndex = index
                            },
                            onDragCancel = { clearDragState() },
                            onDragEnd = {
                                val dragged = draggingItem
                                val fromIndex = if (dragged == null) -1 else items.indexOf(dragged)
                                val toIndex = dragTargetIndex
                                if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                    val newOrder = items.toMutableList().also { list ->
                                        val moved = list.removeAt(fromIndex)
                                        list.add(toIndex.coerceIn(0, list.size), moved)
                                    }
                                    onOrderChange(newOrder)
                                }
                                clearDragState()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                                dragTargetIndex = calculateDrawerDragTarget(
                                    startIndex = draggingStartIndex,
                                    dragOffset = dragOffset,
                                    rowHeightPx = rowStepPx,
                                    itemCount = items.size,
                                )
                            },
                        )
                    },
            )
        }
    }
}

@Composable
internal fun SettingsSelectionToolbarEditRow(
    icon: ImageVector,
    title: String,
    isMore: Boolean,
    isHidden: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    onToggleArea: () -> Unit,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = isDragging || isDropTarget

    LaunchedEffect(title, active, isMore, isHidden, isDragging, isDropTarget) {
        KardLeafLog.d(
            SETTINGS_TRACE_TAG,
            "SelectionToolbarEditRow title=$title active=$active isMore=$isMore isHidden=$isHidden " +
                "dragging=$isDragging dropTarget=$isDropTarget",
        )
    }

    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = "",
        selected = active,
        onClick = {},
        modifier = modifier,
        contentHorizontalPadding = 14.dp,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleHidden) { Text(if (isHidden) "显示" else "隐藏") }
                if (!isHidden) {
                    TextButton(onClick = onToggleArea) { Text(if (isMore) "顶部" else "更多") }
                }
            }
        },
    )
}

@Composable
internal fun SettingsDrawerDragList(
    items: List<PrefsManager.DrawerItemId>,
    hiddenItems: Set<PrefsManager.DrawerItemId>,
    prefsManager: PrefsManager,
    onOrderChange: (List<PrefsManager.DrawerItemId>) -> Unit,
    onRename: (PrefsManager.DrawerItemId, String) -> Unit,
    onToggleVisible: (PrefsManager.DrawerItemId) -> Unit,
    groupStartItems: Set<PrefsManager.DrawerItemId> = emptySet(),
    onMoveGroupStart: (PrefsManager.DrawerItemId, PrefsManager.DrawerItemId?) -> Unit = { _, _ -> },
) {
    val rowHeight = 64.dp
    val rowSpacing = 6.dp
    val rowStepPx = with(LocalDensity.current) { (rowHeight + rowSpacing).toPx() }
    var draggingItem by remember { mutableStateOf<PrefsManager.DrawerItemId?>(null) }
    var draggingStartIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var draggingGroupStart by remember { mutableStateOf<PrefsManager.DrawerItemId?>(null) }
    var groupDragStartIndex by remember { mutableStateOf(-1) }
    var groupDragOffset by remember { mutableStateOf(Offset.Zero) }
    var groupDragTargetIndex by remember { mutableStateOf<Int?>(null) }

    fun clearDragState() {
        draggingItem = null
        draggingStartIndex = -1
        dragOffset = Offset.Zero
        dragTargetIndex = null
    }

    fun clearGroupDragState() {
        draggingGroupStart = null
        groupDragStartIndex = -1
        groupDragOffset = Offset.Zero
        groupDragTargetIndex = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        items.forEachIndexed { index, itemId ->
            val title = prefsManager.getDrawerItemLabel(itemId, drawerItemLabel(itemId))
            val itemIsDragging = draggingItem == itemId
            val targetIndex = dragTargetIndex
            val isDropTarget = targetIndex == index && draggingItem != null && !itemIsDragging
            val avoidanceOffset = if (!itemIsDragging && targetIndex != null) {
                calculateDrawerAvoidanceOffset(
                    index = index,
                    fromIndex = draggingStartIndex,
                    toIndex = targetIndex,
                    rowStepPx = rowStepPx,
                )
            } else {
                IntOffset.Zero
            }

            Box {
                SettingsDrawerEditRow(
                    icon = drawerItemIcon(itemId),
                    title = title,
                    isHidden = itemId in hiddenItems,
                    isDragging = itemIsDragging,
                    isDropTarget = isDropTarget,
                    onRename = { onRename(itemId, title) },
                    canToggleVisible = itemId != PrefsManager.DrawerItemId.SETTINGS,
                    onToggleVisible = { onToggleVisible(itemId) },
                    modifier = Modifier
                        .zIndex(if (itemIsDragging) 1f else 0f)
                        .offset {
                            if (itemIsDragging) {
                                IntOffset(0, dragOffset.y.roundToInt())
                            } else {
                                avoidanceOffset
                            }
                        }
                        .pointerInput(itemId, items.size, rowStepPx) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingItem = itemId
                                    draggingStartIndex = index
                                    dragOffset = Offset.Zero
                                    dragTargetIndex = index
                                },
                                onDragCancel = { clearDragState() },
                                onDragEnd = {
                                    val dragged = draggingItem
                                    val fromIndex = if (dragged == null) -1 else items.indexOf(dragged)
                                    val toIndex = dragTargetIndex
                                    if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                        val newOrder = items.toMutableList().also { list ->
                                            val moved = list.removeAt(fromIndex)
                                            list.add(toIndex.coerceIn(0, list.size), moved)
                                        }
                                        onOrderChange(newOrder)
                                    }
                                    clearDragState()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount
                                    dragTargetIndex = calculateDrawerDragTarget(
                                        startIndex = draggingStartIndex,
                                        dragOffset = dragOffset,
                                        rowHeightPx = rowStepPx,
                                        itemCount = items.size,
                                    )
                                },
                            )
                        },
                )

                if (index > 0 && itemId in groupStartItems) {
                    val groupLineIsDragging = draggingGroupStart == itemId
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(2f)
                            .offset(y = (-12).dp)
                            .offset {
                                if (groupLineIsDragging) {
                                    IntOffset(0, groupDragOffset.y.roundToInt())
                                } else {
                                    IntOffset.Zero
                                }
                            }
                            .fillMaxWidth()
                            .height(18.dp)
                            .pointerInput(itemId, items.size, rowStepPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingGroupStart = itemId
                                        groupDragStartIndex = index
                                        groupDragOffset = Offset.Zero
                                        groupDragTargetIndex = index
                                    },
                                    onDragCancel = { clearGroupDragState() },
                                    onDragEnd = {
                                        val dragged = draggingGroupStart
                                        val targetIndex = groupDragTargetIndex
                                        if (dragged != null && targetIndex != null) {
                                            onMoveGroupStart(
                                                dragged,
                                                items.getOrNull(targetIndex)?.takeIf { targetIndex > 0 },
                                            )
                                        }
                                        clearGroupDragState()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        groupDragOffset += dragAmount
                                        groupDragTargetIndex = calculateDrawerGroupLineTarget(
                                            startIndex = groupDragStartIndex,
                                            dragOffset = groupDragOffset,
                                            rowHeightPx = rowStepPx,
                                            itemCount = items.size,
                                        )
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            thickness = 1.dp,
                            color = if (groupLineIsDragging) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
@Composable
internal fun SettingsDrawerEditRow(
    icon: ImageVector,
    title: String,
    isHidden: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    onRename: () -> Unit,
    canToggleVisible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = isDragging || isDropTarget
    LaunchedEffect(title, active, isHidden, isDragging, isDropTarget) {
        KardLeafLog.d(
            SETTINGS_TRACE_TAG,
            "DrawerEditRow title=$title active=$active isHidden=$isHidden " +
                "dragging=$isDragging dropTarget=$isDropTarget",
        )
    }

    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = "",
        selected = active,
        onClick = {},
        modifier = modifier,
        contentHorizontalPadding = 14.dp,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRename) { Text("改名") }
                if (canToggleVisible) {
                    TextButton(onClick = onToggleVisible) { Text(if (isHidden) "显示" else "隐藏") }
                }
            }
        },
    )
}

internal fun calculateDrawerDragTarget(
    startIndex: Int,
    dragOffset: Offset,
    rowHeightPx: Float,
    itemCount: Int,
): Int {
    if (startIndex !in 0 until itemCount) return 0

    val centerY = startIndex * rowHeightPx + rowHeightPx / 2f + dragOffset.y
    return (centerY / rowHeightPx).toInt().coerceIn(0, itemCount - 1)
}

internal fun calculateDrawerGroupLineTarget(
    startIndex: Int,
    dragOffset: Offset,
    rowHeightPx: Float,
    itemCount: Int,
): Int {
    if (startIndex !in 0 until itemCount) return 0
    return (startIndex + (dragOffset.y / rowHeightPx).roundToInt()).coerceIn(0, itemCount - 1)
}

internal fun calculateDrawerAvoidanceOffset(
    index: Int,
    fromIndex: Int,
    toIndex: Int,
    rowStepPx: Float,
): IntOffset {
    if (fromIndex == toIndex || fromIndex < 0) return IntOffset.Zero

    val visualIndex = when {
        fromIndex < toIndex && index in (fromIndex + 1)..toIndex -> index - 1
        fromIndex > toIndex && index in toIndex until fromIndex -> index + 1
        else -> index
    }
    if (visualIndex == index) return IntOffset.Zero

    return IntOffset(
        x = 0,
        y = ((visualIndex - index) * rowStepPx).roundToInt(),
    )
}

internal fun homeBottomToolbarItemLabel(itemId: PrefsManager.HomeBottomToolbarItemId): String =
    when (itemId) {
        PrefsManager.HomeBottomToolbarItemId.TASKS -> localizedText("任务", "Tasks")
        PrefsManager.HomeBottomToolbarItemId.NEW_NOTE -> localizedText("新建", "New")
        PrefsManager.HomeBottomToolbarItemId.NEW_DRAFT -> localizedText("新建速记", "New quick note")
        PrefsManager.HomeBottomToolbarItemId.NEW_DRAWING -> localizedText("新建绘图", "New drawing")
        PrefsManager.HomeBottomToolbarItemId.NEW_FOLDER -> localizedText("新建分类", "New folder")
        PrefsManager.HomeBottomToolbarItemId.ALL_NOTES -> localizedText("全部笔记", "All notes")
        PrefsManager.HomeBottomToolbarItemId.RECENT -> localizedText("最近修改", "Recent")
        PrefsManager.HomeBottomToolbarItemId.FAVORITES -> localizedText("收藏", "Favorites")
        PrefsManager.HomeBottomToolbarItemId.DRAFTS -> localizedText("速记", "Quick notes")
        PrefsManager.HomeBottomToolbarItemId.TAGS -> localizedText("标签", "Tags")
        PrefsManager.HomeBottomToolbarItemId.FILES -> localizedText("分类", "Folders")
        PrefsManager.HomeBottomToolbarItemId.DATES -> localizedText("日期", "Dates")
        PrefsManager.HomeBottomToolbarItemId.IMAGES -> localizedText("图片", "Images")
        PrefsManager.HomeBottomToolbarItemId.ARCHIVE -> localizedText("归档", "Archive")
        PrefsManager.HomeBottomToolbarItemId.TRASH -> localizedText("废弃", "Trash")
        PrefsManager.HomeBottomToolbarItemId.PRIVACY -> localizedText("隐私", "Privacy")
        PrefsManager.HomeBottomToolbarItemId.SETTINGS -> localizedText("设置", "Settings")
    }

internal fun homeBottomToolbarItemIcon(itemId: PrefsManager.HomeBottomToolbarItemId): ImageVector =
    when (itemId) {
        PrefsManager.HomeBottomToolbarItemId.TASKS -> Icons.Outlined.Checklist
        PrefsManager.HomeBottomToolbarItemId.NEW_NOTE -> Icons.Filled.Add
        PrefsManager.HomeBottomToolbarItemId.NEW_DRAFT -> Icons.Outlined.PostAdd
        PrefsManager.HomeBottomToolbarItemId.NEW_DRAWING -> Icons.Outlined.Palette
        PrefsManager.HomeBottomToolbarItemId.NEW_FOLDER -> Icons.Outlined.CreateNewFolder
        PrefsManager.HomeBottomToolbarItemId.ALL_NOTES -> Icons.Outlined.Article
        PrefsManager.HomeBottomToolbarItemId.RECENT -> Icons.Outlined.History
        PrefsManager.HomeBottomToolbarItemId.FAVORITES -> Icons.Outlined.StarBorder
        PrefsManager.HomeBottomToolbarItemId.DRAFTS -> Icons.Outlined.EditNote
        PrefsManager.HomeBottomToolbarItemId.TAGS -> Icons.Outlined.Sell
        PrefsManager.HomeBottomToolbarItemId.FILES -> Icons.Outlined.FolderOpen
        PrefsManager.HomeBottomToolbarItemId.DATES -> Icons.Outlined.EventNote
        PrefsManager.HomeBottomToolbarItemId.IMAGES -> Icons.Outlined.PhotoLibrary
        PrefsManager.HomeBottomToolbarItemId.ARCHIVE -> Icons.Outlined.Inventory2
        PrefsManager.HomeBottomToolbarItemId.TRASH -> Icons.Outlined.DeleteOutline
        PrefsManager.HomeBottomToolbarItemId.PRIVACY -> Icons.Outlined.Shield
        PrefsManager.HomeBottomToolbarItemId.SETTINGS -> Icons.Outlined.Settings
    }

@Composable
internal fun SettingsHomeBottomToolbarDragList(
    items: List<PrefsManager.HomeBottomToolbarItemId>,
    hiddenItems: Set<PrefsManager.HomeBottomToolbarItemId>,
    onOrderChange: (List<PrefsManager.HomeBottomToolbarItemId>) -> Unit,
    onToggleVisible: (PrefsManager.HomeBottomToolbarItemId) -> Unit,
) {
    val rowHeight = 64.dp
    val rowSpacing = 6.dp
    val rowStepPx = with(LocalDensity.current) { (rowHeight + rowSpacing).toPx() }
    var draggingItem by remember { mutableStateOf<PrefsManager.HomeBottomToolbarItemId?>(null) }
    var draggingStartIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }

    fun clearDragState() {
        draggingItem = null
        draggingStartIndex = -1
        dragOffset = Offset.Zero
        dragTargetIndex = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        items.forEachIndexed { index, itemId ->
            val title = homeBottomToolbarItemLabel(itemId)
            val itemIsDragging = draggingItem == itemId
            val targetIndex = dragTargetIndex
            val isDropTarget = targetIndex == index && draggingItem != null && !itemIsDragging
            val avoidanceOffset = if (!itemIsDragging && targetIndex != null) {
                calculateDrawerAvoidanceOffset(
                    index = index,
                    fromIndex = draggingStartIndex,
                    toIndex = targetIndex,
                    rowStepPx = rowStepPx,
                )
            } else {
                IntOffset.Zero
            }

            SettingsHomeBottomToolbarEditRow(
                icon = homeBottomToolbarItemIcon(itemId),
                title = title,
                subtitle = if (itemId.name.startsWith("NEW_")) "新建功能" else "侧边栏功能",
                isHidden = itemId in hiddenItems,
                isDragging = itemIsDragging,
                isDropTarget = isDropTarget,
                onToggleVisible = { onToggleVisible(itemId) },
                modifier = Modifier
                    .zIndex(if (itemIsDragging) 1f else 0f)
                    .offset {
                        if (itemIsDragging) {
                            IntOffset(0, dragOffset.y.roundToInt())
                        } else {
                            avoidanceOffset
                        }
                    }
                    .pointerInput(itemId, items.size, rowStepPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingItem = itemId
                                draggingStartIndex = index
                                dragOffset = Offset.Zero
                                dragTargetIndex = index
                            },
                            onDragCancel = { clearDragState() },
                            onDragEnd = {
                                val dragged = draggingItem
                                val fromIndex = if (dragged == null) -1 else items.indexOf(dragged)
                                val toIndex = dragTargetIndex
                                if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                    val newOrder = items.toMutableList().also { list ->
                                        val moved = list.removeAt(fromIndex)
                                        list.add(toIndex.coerceIn(0, list.size), moved)
                                    }
                                    onOrderChange(newOrder)
                                }
                                clearDragState()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                                dragTargetIndex = calculateDrawerDragTarget(
                                    startIndex = draggingStartIndex,
                                    dragOffset = dragOffset,
                                    rowHeightPx = rowStepPx,
                                    itemCount = items.size,
                                )
                            },
                        )
                    },
            )
        }
    }
}

@Composable
internal fun SettingsHomeBottomToolbarEditRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isHidden: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = isDragging || isDropTarget

    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        selected = active,
        onClick = {},
        modifier = modifier,
        contentHorizontalPadding = 14.dp,
        trailing = {
            TextButton(onClick = onToggleVisible) { Text(if (isHidden) "添加" else "隐藏") }
        },
    )
}

internal fun drawerItemLabel(itemId: PrefsManager.DrawerItemId): String =
    when (itemId) {
        PrefsManager.DrawerItemId.ALL_NOTES -> localizedText("全部笔记", "All notes")
        PrefsManager.DrawerItemId.RECENT -> localizedText("最近修改", "Recent")
        PrefsManager.DrawerItemId.TASKS -> localizedText("任务", "Tasks")
        PrefsManager.DrawerItemId.NEW_DRAWING -> localizedText("新建绘图", "New drawing")
        PrefsManager.DrawerItemId.FAVORITES -> localizedText("收藏", "Favorites")
        PrefsManager.DrawerItemId.DRAFTS -> localizedText("速记", "Quick notes")
        PrefsManager.DrawerItemId.TAGS -> localizedText("标签", "Tags")
        PrefsManager.DrawerItemId.RANDOM -> localizedText("随机", "Random")
        PrefsManager.DrawerItemId.FILES -> localizedText("分类", "Folders")
        PrefsManager.DrawerItemId.DATES -> localizedText("日期", "Dates")
        PrefsManager.DrawerItemId.IMAGES -> localizedText("图片", "Images")
        PrefsManager.DrawerItemId.RELATIONSHIP_GRAPH -> localizedText("关系", "Relations")
        PrefsManager.DrawerItemId.ARCHIVE -> localizedText("归档", "Archive")
        PrefsManager.DrawerItemId.TRASH -> localizedText("废弃", "Trash")
        PrefsManager.DrawerItemId.PRIVACY -> localizedText("隐私", "Privacy")
        PrefsManager.DrawerItemId.ONBOARDING -> localizedText("介绍", "Intro")
        PrefsManager.DrawerItemId.SETTINGS -> localizedText("设置", "Settings")
    }

internal fun drawerItemIcon(itemId: PrefsManager.DrawerItemId): ImageVector =
    when (itemId) {
        PrefsManager.DrawerItemId.ALL_NOTES -> Icons.Outlined.Article
        PrefsManager.DrawerItemId.RECENT -> Icons.Outlined.History
        PrefsManager.DrawerItemId.TASKS -> Icons.Outlined.Checklist
        PrefsManager.DrawerItemId.NEW_DRAWING -> Icons.Outlined.Palette
        PrefsManager.DrawerItemId.FAVORITES -> Icons.Outlined.StarBorder
        PrefsManager.DrawerItemId.DRAFTS -> Icons.Outlined.EditNote
        PrefsManager.DrawerItemId.TAGS -> Icons.Outlined.Sell
        PrefsManager.DrawerItemId.RANDOM -> Icons.Outlined.Shuffle
        PrefsManager.DrawerItemId.FILES -> Icons.Outlined.FolderOpen
        PrefsManager.DrawerItemId.DATES -> Icons.Outlined.EventNote
        PrefsManager.DrawerItemId.IMAGES -> Icons.Outlined.PhotoLibrary
        PrefsManager.DrawerItemId.RELATIONSHIP_GRAPH -> Icons.Outlined.AccountTree
        PrefsManager.DrawerItemId.ARCHIVE -> Icons.Outlined.Inventory2
        PrefsManager.DrawerItemId.TRASH -> Icons.Outlined.DeleteOutline
        PrefsManager.DrawerItemId.PRIVACY -> Icons.Outlined.Shield
        PrefsManager.DrawerItemId.ONBOARDING -> Icons.AutoMirrored.Outlined.MenuBook
        PrefsManager.DrawerItemId.SETTINGS -> Icons.Outlined.Settings
    }

internal fun displayRootPath(uriString: String?): String {
    if (uriString.isNullOrBlank()) return "未选择笔记库"
    val treePath = runCatching {
        Uri.decode(Uri.parse(uriString).lastPathSegment.orEmpty())
    }.getOrNull().orEmpty()

    if (treePath.contains(":")) {
        val volume = treePath.substringBefore(":")
        val relativePath = treePath.substringAfter(":").trim('/')
        val rootPath = if (volume.equals("primary", ignoreCase = true)) {
            "/storage/emulated/0"
        } else {
            "/storage/$volume"
        }
        return listOf(rootPath, relativePath)
            .filter { it.isNotBlank() }
            .joinToString("/")
            .replace("//", "/")
    }

    return uriString
}
