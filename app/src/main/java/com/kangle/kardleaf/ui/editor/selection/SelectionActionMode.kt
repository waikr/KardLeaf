/* Adapted from MarkText Android, Copyright (c) 2026 Renakoni, MIT.
 * See app/src/main/codemirror-editor/src/marktext/LICENSE. */
package com.kangle.kardleaf.ui.editor.selection

import android.graphics.Rect
import android.os.SystemClock
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/** Keeps the real platform selection session/handles alive, hiding only its menu. */
internal class SelectionActionMode(private val view: View) {
    var enabled = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) view.removeCallbacks(rearm) else hide()
        }
    var onContext: () -> Unit = {}
    var onTap: (Float, Float) -> Unit = { _, _ -> }
    var onFinished: () -> Unit = {}
    var onHideFailed: () -> Unit = {}
    private var mode: ActionMode? = null
    private var callback: ActionMode.Callback? = null
    private var startedAt = 0L
    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var maxTravel = 0f
    private val rearm = Runnable { hide() }

    fun wrap(delegate: ActionMode.Callback): ActionMode.Callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(created: ActionMode, menu: Menu): Boolean {
            if (!delegate.onCreateActionMode(created, menu)) return false
            mode = created
            callback = delegate
            startedAt = SystemClock.uptimeMillis()
            view.post {
                if (enabled && mode === created) {
                    hide()
                    if (enabled) onContext()
                }
            }
            return true
        }
        override fun onPrepareActionMode(current: ActionMode, menu: Menu): Boolean {
            val changed = delegate.onPrepareActionMode(current, menu)
            if (mode === current) hide()
            return changed
        }
        override fun onActionItemClicked(current: ActionMode, item: MenuItem) = delegate.onActionItemClicked(current, item)
        override fun onDestroyActionMode(current: ActionMode) {
            if (mode === current) {
                mode = null
                callback = null
                view.removeCallbacks(rearm)
                onFinished()
            }
            delegate.onDestroyActionMode(current)
        }
        override fun onGetContentRect(current: ActionMode, origin: View, rect: Rect) {
            if (delegate is ActionMode.Callback2) delegate.onGetContentRect(current, origin, rect)
            else super.onGetContentRect(current, origin, rect)
        }
    }

    private fun hide() {
        val current = mode ?: return
        if (!enabled) return
        val duration = ViewConfiguration.getDefaultActionModeHideDuration().takeIf { it > 100L } ?: 2000L
        try {
            current.hide(duration)
            view.removeCallbacks(rearm)
            view.postDelayed(rearm, maxOf(100L, duration - 100L))
        } catch (_: RuntimeException) {
            enabled = false
            onHideFailed()
        }
    }

    fun selectAll(): Boolean {
        val current = mode ?: return false
        val delegate = callback ?: return false
        val expected = view.context.getText(android.R.string.selectAll).toString().trim()
        val menu = current.menu
        val item = menu.findItem(android.R.id.selectAll) ?: (0 until menu.size())
            .map(menu::getItem).firstOrNull { it.title?.toString()?.trim().equals(expected, ignoreCase = true) }
            ?: return false
        return delegate.onActionItemClicked(current, item)
    }

    fun finish(): Boolean {
        val current = mode ?: return false
        current.finish()
        return true
    }

    /** Observation only: never consume a scroll, long press or native handle gesture. */
    fun observeTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downAt = event.eventTime
                downX = event.x
                downY = event.y
                maxTravel = 0f
            }
            MotionEvent.ACTION_MOVE -> maxTravel = maxOf(maxTravel, kotlin.math.hypot(event.x - downX, event.y - downY))
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> downAt = 0L
            MotionEvent.ACTION_UP -> {
                maxTravel = maxOf(maxTravel, kotlin.math.hypot(event.x - downX, event.y - downY))
                if (enabled && mode != null && downAt >= startedAt + 250L &&
                    event.eventTime - downAt <= 500L && maxTravel <= 24f * view.resources.displayMetrics.density
                ) onTap(event.x, event.y)
                downAt = 0L
            }
        }
    }

    fun dispose() {
        enabled = false
        finish()
        onContext = {}
        onTap = { _, _ -> }
        onFinished = {}
        onHideFailed = {}
    }
}
