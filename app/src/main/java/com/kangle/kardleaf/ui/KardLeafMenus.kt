package com.kangle.kardleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private object KardLeafAboveAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX)
        val yAbove = anchorBounds.top - popupContentSize.height
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val y = if (yAbove >= 0) yAbove else anchorBounds.bottom.coerceIn(0, maxY)
        return IntOffset(x, y)
    }
}

@Composable
internal fun KardLeafDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    forceAboveAnchor: Boolean = false,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val menuContent: @Composable ColumnScope.() -> Unit = {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
    if (forceAboveAnchor && expanded) {
        Popup(
            popupPositionProvider = KardLeafAboveAnchorPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = properties,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp,
            ) {
                Column(
                    modifier = modifier
                        .padding(vertical = 8.dp)
                        .width(IntrinsicSize.Max)
                        .verticalScroll(rememberScrollState()),
                    content = menuContent,
                )
            }
        }
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            properties = properties,
            content = menuContent,
        )
    }
}
