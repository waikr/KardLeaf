package com.kangle.kardleaf.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max

/** Lightweight metrics shared by the editor and preview fast-scroll overlay. */
data class EditorFastScrollMetrics(
    val canScroll: Boolean = false,
    val ratio: Float = 0f,
    val thumbFraction: Float = 1f,
)

/** Lets native scroll containers ask the overlay to show briefly after normal scrolling. */
class EditorFastScrollSignal {
    private var listener: (() -> Unit)? = null

    fun setListener(listener: (() -> Unit)?) {
        this.listener = listener
    }

    fun notifyScrollChanged() {
        listener?.invoke()
    }
}

class EditorFastScrollEdgeView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val thumbRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbWidthPx = 3f * density
    private val activeThumbWidthPx = 7f * density
    private val thumbInsetEndPx = 4f * density
    private val minThumbHeightPx = 28f * density
    private val fastScrollDelayMs = 220L
    private val hideDelayMs = 1000L

    private var metricsProvider: () -> EditorFastScrollMetrics = { EditorFastScrollMetrics() }
    private var onScrollToRatio: (Float) -> Unit = {}
    private var onFastScrollInteraction: () -> Unit = {}
    private var sidePanelDragEnabled: () -> Boolean = { false }
    private var onSidePanelDragStart: () -> Unit = {}
    private var onSidePanelDragBy: (Float) -> Boolean = { false }
    private var onSidePanelDragEnd: () -> Unit = {}
    private var onSidePanelDragCancel: () -> Unit = {}

    private var isVisible = false
    private var pendingFastScroll = false
    private var isFastScrolling = false
    private var isSidePanelDragging = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragStartY = 0f
    private var dragStartRatio = 0f

    private val beginFastScrollRunnable = Runnable {
        if (pendingFastScroll && metricsProvider().canScroll) {
            startFastScroll(downY, centerThumbOnTouch = true, haptic = true)
        }
    }

    private val hideRunnable = Runnable {
        if (!isFastScrolling && !isSidePanelDragging) {
            isVisible = false
            postInvalidateOnAnimation()
        }
    }

    init {
        setWillNotDraw(false)
        isClickable = true
    }

    fun configure(
        metricsProvider: () -> EditorFastScrollMetrics,
        onScrollToRatio: (Float) -> Unit,
        onFastScrollInteraction: () -> Unit,
        sidePanelDragEnabled: () -> Boolean,
        onSidePanelDragStart: () -> Unit,
        onSidePanelDragBy: (Float) -> Boolean,
        onSidePanelDragEnd: () -> Unit,
        onSidePanelDragCancel: () -> Unit,
    ) {
        this.metricsProvider = metricsProvider
        this.onScrollToRatio = onScrollToRatio
        this.onFastScrollInteraction = onFastScrollInteraction
        this.sidePanelDragEnabled = sidePanelDragEnabled
        this.onSidePanelDragStart = onSidePanelDragStart
        this.onSidePanelDragBy = onSidePanelDragBy
        this.onSidePanelDragEnd = onSidePanelDragEnd
        this.onSidePanelDragCancel = onSidePanelDragCancel
        postInvalidateOnAnimation()
    }

    fun showForScroll() {
        if (!metricsProvider().canScroll || isFastScrolling || isSidePanelDragging) return
        isVisible = true
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, hideDelayMs)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val metrics = metricsProvider()
        if (height <= 0 || !metrics.canScroll || (!isVisible && !isFastScrolling)) return

        updateThumbRect(metrics, if (isFastScrolling) activeThumbWidthPx else thumbWidthPx)

        if (isFastScrolling) {
            val thumbRight = width - thumbInsetEndPx
            trackPaint.color = AndroidColor.argb(24, 128, 128, 128)
            canvas.drawRoundRect(
                thumbRight - activeThumbWidthPx,
                0f,
                thumbRight,
                height.toFloat(),
                activeThumbWidthPx / 2f,
                activeThumbWidthPx / 2f,
                trackPaint,
            )
        }

        thumbPaint.color = AndroidColor.argb(
            if (isFastScrolling) 190 else 115,
            128,
            128,
            128,
        )
        canvas.drawRoundRect(
            thumbRect,
            thumbRect.width() / 2f,
            thumbRect.width() / 2f,
            thumbPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val canScroll = metricsProvider().canScroll
        val canDragSidePanel = sidePanelDragEnabled()
        if (!canScroll && !canDragSidePanel) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                isFastScrolling = false
                isSidePanelDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                removeCallbacks(beginFastScrollRunnable)
                removeCallbacks(hideRunnable)
                if (canScroll && isTouchOnThumb(event.y)) {
                    startFastScroll(event.y, centerThumbOnTouch = false, haptic = false)
                } else {
                    pendingFastScroll = canScroll
                    if (canScroll) postDelayed(beginFastScrollRunnable, fastScrollDelayMs)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isFastScrolling) {
                    scrollByDragTo(event.y)
                    return true
                }
                if (isSidePanelDragging) {
                    val dx = event.x - lastX
                    lastX = event.x
                    onSidePanelDragBy(dx)
                    return true
                }

                val totalDx = event.x - downX
                val totalDy = event.y - downY
                val absDx = abs(totalDx)
                val absDy = abs(totalDy)
                if (canDragSidePanel && absDx > touchSlop && absDx > absDy * 1.5f) {
                    removeCallbacks(beginFastScrollRunnable)
                    pendingFastScroll = false
                    isSidePanelDragging = true
                    lastX = event.x
                    onSidePanelDragStart()
                    onSidePanelDragBy(totalDx)
                    postInvalidateOnAnimation()
                    return true
                }
                if (canScroll && absDy > touchSlop && absDy > absDx * 1.2f) {
                    removeCallbacks(beginFastScrollRunnable)
                    startFastScroll(downY, centerThumbOnTouch = true, haptic = false)
                    scrollByDragTo(event.y)
                    return true
                }
                if (absDx > touchSlop && absDx > absDy) {
                    removeCallbacks(beginFastScrollRunnable)
                    pendingFastScroll = false
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                when {
                    isFastScrolling -> {
                        scrollByDragTo(event.y)
                        performClick()
                    }
                    isSidePanelDragging -> onSidePanelDragEnd()
                    else -> onSidePanelDragCancel()
                }
                endTouchState()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isSidePanelDragging) onSidePanelDragCancel()
                endTouchState()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun startFastScroll(
        touchY: Float,
        centerThumbOnTouch: Boolean,
        haptic: Boolean,
    ) {
        pendingFastScroll = false
        isFastScrolling = true
        isVisible = true
        removeCallbacks(hideRunnable)
        if (haptic) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onFastScrollInteraction()
        if (centerThumbOnTouch) centerThumbAt(touchY)
        dragStartY = touchY
        dragStartRatio = metricsProvider().ratio.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    private fun endTouchState() {
        removeCallbacks(beginFastScrollRunnable)
        pendingFastScroll = false
        isFastScrolling = false
        isSidePanelDragging = false
        parent?.requestDisallowInterceptTouchEvent(false)
        if (metricsProvider().canScroll) {
            isVisible = true
            removeCallbacks(hideRunnable)
            postDelayed(hideRunnable, hideDelayMs)
        }
        postInvalidateOnAnimation()
    }

    private fun centerThumbAt(touchY: Float) {
        val metrics = metricsProvider()
        if (height <= 0 || !metrics.canScroll) return

        val thumbHeight = thumbHeight(metrics)
        val travel = (height - thumbHeight).coerceAtLeast(1f)
        val targetThumbTop = (touchY - thumbHeight / 2f).coerceIn(0f, travel)
        onScrollToRatio((targetThumbTop / travel).coerceIn(0f, 1f))
    }

    private fun scrollByDragTo(touchY: Float) {
        val metrics = metricsProvider()
        if (height <= 0 || !metrics.canScroll) return

        val travel = (height - thumbHeight(metrics)).coerceAtLeast(1f)
        val targetRatio = dragStartRatio + (touchY - dragStartY) / travel
        onScrollToRatio(targetRatio.coerceIn(0f, 1f))
        postInvalidateOnAnimation()
    }

    private fun isTouchOnThumb(touchY: Float): Boolean {
        val metrics = metricsProvider()
        if (height <= 0 || !metrics.canScroll) return false
        updateThumbRect(metrics, activeThumbWidthPx)
        return touchY >= thumbRect.top - touchSlop && touchY <= thumbRect.bottom + touchSlop
    }

    private fun updateThumbRect(
        metrics: EditorFastScrollMetrics,
        thumbWidth: Float,
    ) {
        val thumbHeight = thumbHeight(metrics)
        val travel = (height - thumbHeight).coerceAtLeast(1f)
        val thumbTop = metrics.ratio.coerceIn(0f, 1f) * travel
        val thumbRight = width - thumbInsetEndPx
        val thumbLeft = thumbRight - thumbWidth
        thumbRect.set(thumbLeft, thumbTop, thumbRight, thumbTop + thumbHeight)
    }

    private fun thumbHeight(metrics: EditorFastScrollMetrics): Float =
        max(minThumbHeightPx, height * metrics.thumbFraction.coerceIn(0.02f, 1f))
            .coerceAtMost(height.toFloat())
}
