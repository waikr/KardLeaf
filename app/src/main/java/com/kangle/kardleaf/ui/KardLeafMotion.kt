package com.kangle.kardleaf.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Shared motion tokens for KardLeaf.
 *
 * The screen transition follows Kori's shared-axis idea: move only a small
 * distance, fade the outgoing surface early, and fade the incoming surface
 * after the first third of the movement. Keeping these numbers together avoids
 * slow default springs and makes exit animations consistent across the app.
 */
internal object KardLeafMotion {
    const val ScreenDurationMillis = 300
    const val EditorExitDurationMillis = 180
    const val ContainerDurationMillis = 180
    const val MicroDurationMillis = 120
    const val SharedAxisOffsetFactor = 0.10f
    const val EditorExitOffsetFactor = 0.08f
    const val ContentOffsetFactor = 0.33f
    private const val OutgoingFraction = 0.35f

    fun outgoingDuration(durationMillis: Int): Int = (durationMillis * OutgoingFraction).toInt()

    fun incomingDuration(durationMillis: Int): Int = durationMillis - outgoingDuration(durationMillis)
}

private fun scaledOffset(fullSize: Int, factor: Float): Int = (fullSize * factor).toInt()

internal fun kardLeafSharedAxisXIn(
    initialOffsetX: (fullWidth: Int) -> Int,
    durationMillis: Int = KardLeafMotion.ScreenDurationMillis,
): EnterTransition = slideInHorizontally(
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing,
    ),
    initialOffsetX = initialOffsetX,
) + fadeIn(
    animationSpec = tween(
        durationMillis = KardLeafMotion.incomingDuration(durationMillis),
        delayMillis = KardLeafMotion.outgoingDuration(durationMillis),
        easing = LinearOutSlowInEasing,
    ),
)

internal fun kardLeafSharedAxisXOut(
    targetOffsetX: (fullWidth: Int) -> Int,
    durationMillis: Int = KardLeafMotion.ScreenDurationMillis,
): ExitTransition = slideOutHorizontally(
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing,
    ),
    targetOffsetX = targetOffsetX,
) + fadeOut(
    animationSpec = tween(
        durationMillis = KardLeafMotion.outgoingDuration(durationMillis),
        easing = FastOutLinearInEasing,
    ),
)

internal fun kardLeafSharedAxisYIn(
    initialOffsetY: (fullHeight: Int) -> Int,
    durationMillis: Int = KardLeafMotion.ContainerDurationMillis,
): EnterTransition = slideInVertically(
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing,
    ),
    initialOffsetY = initialOffsetY,
) + fadeIn(
    animationSpec = tween(
        durationMillis = KardLeafMotion.incomingDuration(durationMillis),
        delayMillis = KardLeafMotion.outgoingDuration(durationMillis),
        easing = LinearOutSlowInEasing,
    ),
)

internal fun kardLeafSharedAxisYOut(
    targetOffsetY: (fullHeight: Int) -> Int,
    durationMillis: Int = KardLeafMotion.ContainerDurationMillis,
): ExitTransition = slideOutVertically(
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing,
    ),
    targetOffsetY = targetOffsetY,
) + fadeOut(
    animationSpec = tween(
        durationMillis = KardLeafMotion.outgoingDuration(durationMillis),
        easing = FastOutLinearInEasing,
    ),
)

internal fun kardLeafFadeIn(
    durationMillis: Int = KardLeafMotion.ContainerDurationMillis,
): EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = LinearOutSlowInEasing,
    ),
)

internal fun kardLeafFadeOut(
    durationMillis: Int = KardLeafMotion.MicroDurationMillis,
): ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = FastOutLinearInEasing,
    ),
)

internal fun kardLeafScaleFadeIn(
    durationMillis: Int = KardLeafMotion.ContainerDurationMillis,
): EnterTransition = scaleIn(
    initialScale = 0.92f,
    transformOrigin = TransformOrigin(1f, 1f),
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing,
    ),
) + kardLeafFadeIn(durationMillis)

internal fun kardLeafScaleFadeOut(
    durationMillis: Int = KardLeafMotion.MicroDurationMillis,
): ExitTransition = scaleOut(
    targetScale = 0.96f,
    transformOrigin = TransformOrigin(1f, 1f),
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = FastOutLinearInEasing,
    ),
) + kardLeafFadeOut(durationMillis)

internal fun kardLeafFadeThroughContentTransform(
    durationMillis: Int = KardLeafMotion.ContainerDurationMillis,
): ContentTransform = fadeIn(
    animationSpec = tween(
        durationMillis = KardLeafMotion.incomingDuration(durationMillis),
        delayMillis = KardLeafMotion.outgoingDuration(durationMillis),
        easing = LinearOutSlowInEasing,
    ),
) togetherWith fadeOut(
    animationSpec = tween(
        durationMillis = KardLeafMotion.outgoingDuration(durationMillis),
        easing = FastOutLinearInEasing,
    ),
)

internal fun kardLeafHorizontalContentTransform(
    forward: Boolean,
    durationMillis: Int = 220,
    distanceFactor: Float = KardLeafMotion.ContentOffsetFactor,
): ContentTransform {
    val enterSign = if (forward) 1 else -1
    val exitSign = if (forward) -1 else 1
    return (
        slideInHorizontally(
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            initialOffsetX = { scaledOffset(it, distanceFactor) * enterSign },
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = KardLeafMotion.incomingDuration(durationMillis),
                delayMillis = KardLeafMotion.outgoingDuration(durationMillis),
                easing = LinearOutSlowInEasing,
            ),
        )
    ) togetherWith (
        slideOutHorizontally(
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            targetOffsetX = { scaledOffset(it, distanceFactor) * exitSign },
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = KardLeafMotion.outgoingDuration(durationMillis),
                easing = FastOutLinearInEasing,
            ),
        )
    )
}
