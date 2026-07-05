package com.kangle.kardleaf.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ToolbarIconButton(
    text: String,
    icon: ImageVector? = null,
    contentDescription: String? = text,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strikethrough: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val themeStyle = LocalKardLeafThemeStyle.current
    val isModern = themeStyle != PrefsManager.AppThemeStyle.CLASSIC
    val isDracula = themeStyle == PrefsManager.AppThemeStyle.DRACULA
    @Composable
    fun ButtonContent() {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = text,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration =
                            when {
                                underline && strikethrough ->
                                    TextDecoration.combine(
                                        listOf(TextDecoration.Underline, TextDecoration.LineThrough),
                                    )
                                underline -> TextDecoration.Underline
                                strikethrough -> TextDecoration.LineThrough
                                else -> null
                            },
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }

    if (!isModern) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .alpha(if (enabled) 1f else 0.38f)
                    .clip(RoundedCornerShape(22.dp))
                    .combinedClickable(
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            ButtonContent()
        }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "ToolbarIconButtonPressedScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDracula) 1f else 0.86f)
        } else if (isDracula) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
        },
        label = "ToolbarIconButtonContainerColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.primary.copy(alpha = if (isDracula) 0.9f else 0.34f)
        } else if (isDracula) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
        },
        label = "ToolbarIconButtonBorderColor",
    )
    val shape = RoundedCornerShape(if (isDracula) 8.dp else 18.dp)
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .alpha(if (enabled) 1f else 0.38f)
                .graphicsLayer {
                    scaleX = pressedScale
                    scaleY = pressedScale
                }
                .clip(shape)
                .background(containerColor)
                .border(1.dp, borderColor, shape)
                .combinedClickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent()
    }
}
