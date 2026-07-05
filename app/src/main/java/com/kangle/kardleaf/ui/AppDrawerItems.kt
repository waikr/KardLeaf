package com.kangle.kardleaf.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
@Composable
internal fun drawerItemColors() =
    NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
        unselectedContainerColor = Color.Transparent,
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        selectedBadgeColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedBadgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

@Composable
internal fun ThemedDrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefsManager = remember(context) { PrefsManager(context) }
    val drawerStyle = prefsManager.getDrawerStyle()
    val themeStyle = LocalKardLeafThemeStyle.current
    val isModern = themeStyle != PrefsManager.AppThemeStyle.CLASSIC
    val isDracula = themeStyle == PrefsManager.AppThemeStyle.DRACULA
    val isCleanList = themeStyle == PrefsManager.AppThemeStyle.CLEAN_LIST
    val cleanListFeatureIconStyle = prefsManager.getCleanListFeatureIconStyle()
    if (!isModern) {
        NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(icon, contentDescription = null) },
            selected = selected,
            onClick = onClick,
            modifier = modifier.padding(horizontal = 12.dp),
            colors = drawerItemColors(),
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val itemShape = when (drawerStyle) {
        PrefsManager.DrawerStyle.MINIMAL_TEXT -> RoundedCornerShape(12.dp)
        PrefsManager.DrawerStyle.ICON_BOX -> RoundedCornerShape(if (isDracula) 10.dp else 22.dp)
        PrefsManager.DrawerStyle.GROUPED_CARD -> RoundedCornerShape(if (isDracula) 10.dp else 16.dp)
        PrefsManager.DrawerStyle.DATA_CARD -> RoundedCornerShape(16.dp)
    }
    val iconSize = when (drawerStyle) {
        PrefsManager.DrawerStyle.MINIMAL_TEXT -> 22.dp
        PrefsManager.DrawerStyle.ICON_BOX -> if (isDracula) 34.dp else 36.dp
        PrefsManager.DrawerStyle.GROUPED_CARD -> 30.dp
        PrefsManager.DrawerStyle.DATA_CARD -> if (isCleanList) 40.dp else 30.dp
    }
    val iconCorner = when (drawerStyle) {
        PrefsManager.DrawerStyle.ICON_BOX -> if (isDracula) 8.dp else 14.dp
        PrefsManager.DrawerStyle.GROUPED_CARD -> 12.dp
        PrefsManager.DrawerStyle.DATA_CARD -> if (isCleanList) 14.dp else 999.dp
        PrefsManager.DrawerStyle.MINIMAL_TEXT -> 0.dp
    }
    val showIconBox = drawerStyle != PrefsManager.DrawerStyle.MINIMAL_TEXT
    val itemVerticalPadding = when (drawerStyle) {
        PrefsManager.DrawerStyle.MINIMAL_TEXT -> 8.dp
        PrefsManager.DrawerStyle.GROUPED_CARD -> 8.dp
        else -> if (isDracula) 9.dp else 10.dp
    }
    val itemHorizontalPadding = when (drawerStyle) {
        PrefsManager.DrawerStyle.MINIMAL_TEXT -> 10.dp
        PrefsManager.DrawerStyle.GROUPED_CARD -> 10.dp
        else -> if (isDracula) 10.dp else 12.dp
    }
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            when (drawerStyle) {
                PrefsManager.DrawerStyle.MINIMAL_TEXT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                PrefsManager.DrawerStyle.DATA_CARD -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDracula) 0.92f else 0.86f)
            }
        } else if (isPressed) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDracula) 0.76f else 0.58f)
        } else {
            Color.Transparent
        },
        label = "DrawerItemBackground",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected && drawerStyle == PrefsManager.DrawerStyle.ICON_BOX) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            Color.Transparent
        },
        label = "DrawerItemBorder",
    )
    val iconBackgroundColor by animateColorAsState(
        targetValue = if (isCleanList && drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) {
            Color.Transparent
        } else if (selected) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
        label = "DrawerItemIconBackground",
    )
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        label = "DrawerItemPressedScale",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (drawerStyle == PrefsManager.DrawerStyle.MINIMAL_TEXT) 2.dp else 4.dp)
            .graphicsLayer {
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .clip(itemShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, itemShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(if (selected && drawerStyle != PrefsManager.DrawerStyle.DATA_CARD) 4.dp else 0.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected && drawerStyle != PrefsManager.DrawerStyle.DATA_CARD) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
        if (drawerStyle != PrefsManager.DrawerStyle.DATA_CARD) {
            Spacer(modifier = Modifier.width(if (selected || drawerStyle == PrefsManager.DrawerStyle.ICON_BOX) 10.dp else 4.dp))
        }
        if (showIconBox) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(iconCorner))
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isCleanList && drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) {
                        if (cleanListFeatureIconStyle == PrefsManager.CleanListFeatureIconStyle.MODERN) {
                            cleanListDrawerIconColor(label)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    } else if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(if (isCleanList && drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) 24.dp else 21.dp),
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = if (drawerStyle == PrefsManager.DrawerStyle.MINIMAL_TEXT) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val CleanListDrawerIconPalette = listOf(
    Color(0xFF3B82F6),
    Color(0xFF16A34A),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFF8B5CF6),
    Color(0xFF0EA5E9),
    Color(0xFFEF4444),
)

private fun cleanListDrawerIconColor(label: String): Color =
    CleanListDrawerIconPalette[(label.hashCode() and Int.MAX_VALUE) % CleanListDrawerIconPalette.size]

@Composable
internal fun DrawerEntry(
    itemId: PrefsManager.DrawerItemId,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onScreenSelect: (MainViewModel.Screen) -> Unit,
    onShowOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    val label = prefsManager.getDrawerItemLabel(
        itemId,
        if (prefsManager.getAppLanguage() == "en") englishDrawerItemLabel(itemId) else defaultDrawerItemLabel(itemId),
    )
    when (itemId) {
        PrefsManager.DrawerItemId.ALL_NOTES -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.Article,
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.All,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.All) },
        )
        PrefsManager.DrawerItemId.RECENT -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.History,
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Recent,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.Recent) },
        )
        PrefsManager.DrawerItemId.TASKS -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.Checklist,
            selected = currentScreen is MainViewModel.Screen.Tasks,
            onClick = { onScreenSelect(MainViewModel.Screen.Tasks) },
        )
        PrefsManager.DrawerItemId.FAVORITES -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.StarBorder,
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Favorites,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.Favorites) },
        )
        PrefsManager.DrawerItemId.DRAFTS -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.EditNote,
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Drafts,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.Drafts) },
        )
        PrefsManager.DrawerItemId.TAGS -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.Sell,
            selected = currentScreen is MainViewModel.Screen.Tags,
            onClick = { onScreenSelect(MainViewModel.Screen.Tags) },
        )
        PrefsManager.DrawerItemId.FILES -> Unit
        PrefsManager.DrawerItemId.DATES -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.EventNote,
            selected = currentScreen is MainViewModel.Screen.Dates,
            onClick = { onScreenSelect(MainViewModel.Screen.Dates) },
        )
        PrefsManager.DrawerItemId.IMAGES -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.PhotoLibrary,
            selected = currentScreen is MainViewModel.Screen.Images,
            onClick = { onScreenSelect(MainViewModel.Screen.Images) },
        )
        PrefsManager.DrawerItemId.ARCHIVE -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.Inventory2,
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Archive,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.Archive) },
        )
        PrefsManager.DrawerItemId.TRASH -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.DeleteOutline,
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Trash,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.Trash) },
        )
        PrefsManager.DrawerItemId.PRIVACY -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.Shield,
            selected = false,
            onClick = { onOpenPrivacy() },
        )
        PrefsManager.DrawerItemId.ONBOARDING -> ThemedDrawerItem(
            label = label,
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            selected = false,
            onClick = { onShowOnboarding() },
        )
        PrefsManager.DrawerItemId.SETTINGS -> ThemedDrawerItem(
            label = label,
            icon = Icons.Outlined.Settings,
            selected = false,
            onClick = { onOpenSettings() },
        )
    }
}

internal fun defaultDrawerItemLabel(itemId: PrefsManager.DrawerItemId): String =
    when (itemId) {
        PrefsManager.DrawerItemId.ALL_NOTES -> "全部笔记"
        PrefsManager.DrawerItemId.RECENT -> "最近修改"
        PrefsManager.DrawerItemId.TASKS -> "任务"
        PrefsManager.DrawerItemId.FAVORITES -> "收藏"
        PrefsManager.DrawerItemId.DRAFTS -> "草稿"
        PrefsManager.DrawerItemId.TAGS -> "标签"
        PrefsManager.DrawerItemId.FILES -> "分类"
        PrefsManager.DrawerItemId.DATES -> "日期"
        PrefsManager.DrawerItemId.IMAGES -> "图片"
        PrefsManager.DrawerItemId.ARCHIVE -> "归档"
        PrefsManager.DrawerItemId.TRASH -> "废弃"
        PrefsManager.DrawerItemId.PRIVACY -> "隐私"
        PrefsManager.DrawerItemId.ONBOARDING -> "介绍"
        PrefsManager.DrawerItemId.SETTINGS -> "设置"
    }

internal fun englishDrawerItemLabel(itemId: PrefsManager.DrawerItemId): String =
    when (itemId) {
        PrefsManager.DrawerItemId.ALL_NOTES -> "All notes"
        PrefsManager.DrawerItemId.RECENT -> "Recent"
        PrefsManager.DrawerItemId.TASKS -> "Tasks"
        PrefsManager.DrawerItemId.FAVORITES -> "Favorites"
        PrefsManager.DrawerItemId.DRAFTS -> "Drafts"
        PrefsManager.DrawerItemId.TAGS -> "Tags"
        PrefsManager.DrawerItemId.FILES -> "Folders"
        PrefsManager.DrawerItemId.DATES -> "Dates"
        PrefsManager.DrawerItemId.IMAGES -> "Images"
        PrefsManager.DrawerItemId.ARCHIVE -> "Archive"
        PrefsManager.DrawerItemId.TRASH -> "Trash"
        PrefsManager.DrawerItemId.PRIVACY -> "Privacy"
        PrefsManager.DrawerItemId.ONBOARDING -> "Intro"
        PrefsManager.DrawerItemId.SETTINGS -> "Settings"
    }
