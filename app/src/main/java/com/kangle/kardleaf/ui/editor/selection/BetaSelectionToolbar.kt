/* Adapted from MarkText Android. Copyright (c) 2026 Renakoni. MIT.
 * See app/src/main/codemirror-editor/src/marktext/LICENSE.
 * Android View binding of the same state table, paging and placement rules. */
package com.kangle.kardleaf.ui.editor.selection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.repository.PrefsManager
import org.qosp.notes.ui.utils.views.ExtendedEditText
import org.qosp.notes.ui.utils.views.OperationType

internal class BetaSelectionToolbar(
    private val edit: ExtendedEditText,
    private val mode: SelectionActionMode,
    private val execute: (String) -> Boolean,
) {
    private val prefs = PrefsManager(edit.context)
    private var settings = prefs.getTextSelectionToolbarSettings()
    private val content = LinearLayout(edit.context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
    private val popup = PopupWindow(content, -2, -2, false).apply {
        isClippingEnabled = true
        elevation = dp(5).toFloat()
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        isOutsideTouchable = true
        setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
    }
    private var active = false
    private var readOnly = false
    private var failed = false
    private var canPaste = false
    private var caret = -1
    private var generation = 0
    private var page = 0
    private var styleProperty: String? = null
    private var colorsDirty = true
    private var disposed = false
    private var busy = false
    private var snapshotStart = 0
    private var snapshotEnd = 0
    private var snapshotText = ""
    private var snapshotGeneration = -1
    private var snapshotAnchor = 0
    private var snapshotHead = 0
    private var dismissedSelection: Pair<Int, Int>? = null
    private var foreground = Color.BLACK
    private var background = Color.WHITE
    private var signature = ""
    private val refresh = Runnable { render() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { schedule() }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { schedule() }
    private val stopSettings = prefs.observeTextSelectionToolbarSettings {
        settings = prefs.getTextSelectionToolbarSettings(); page = 0; signature = ""; schedule()
    }
    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) { generation++; colorsDirty = true; caret = -1; dismissedSelection = null; schedule() }
    }
    init {
        mode.onContext = {
            if (active && !failed) { canPaste = clipboardText().isNotEmpty(); caret = if (edit.selectionStart == edit.selectionEnd) edit.selectionStart else -1; dismissedSelection = null; schedule() }
        }
        mode.onTap = { x, y ->
            if (popup.isShowing && !busy) {
                val pos = edit.getOffsetForPosition(x, y)
                dismiss()
                edit.setSelection(pos.coerceIn(0, edit.length()))
                mode.finish()
            }
        }
        mode.onFinished = { if (!busy) dismiss() }
        mode.onHideFailed = { failed = true; dismiss() }
        popup.setOnDismissListener {
            if (!busy) { caret = -1; generation++; dismissedSelection = edit.selectionStart to edit.selectionEnd }
        }
        edit.addTextChangedListener(watcher)
        edit.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        edit.viewTreeObserver.addOnScrollChangedListener(scrollListener)
    }
    private fun dp(value: Int) = (value * edit.resources.displayMetrics.density + .5f).toInt()
    fun configure(enabled: Boolean, isReadOnly: Boolean, textColor: Int, surfaceColor: Int) {
        val changed = foreground != textColor || background != surfaceColor
        foreground = textColor; background = surfaceColor
        if (changed) signature = ""
        active = enabled
        readOnly = isReadOnly
        mode.enabled = enabled && !failed
        if (!enabled) dismiss() else schedule()
    }
    fun selectionChanged() {
        generation++
        if (caret >= 0 && (edit.selectionStart != caret || edit.selectionEnd != caret)) { caret = -1; generation++ }
        if (dismissedSelection != (edit.selectionStart to edit.selectionEnd)) dismissedSelection = null
        schedule()
    }
    fun documentChanged() { dismiss(); dismissedSelection = null }
    private fun schedule() { if (!disposed) { edit.removeCallbacks(refresh); edit.post(refresh) } }
    private fun dismiss() { generation++; caret = -1; page = 0; styleProperty = null; popup.dismiss() }
    private fun clipboardText(): String = runCatching {
        val clip = (edit.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(edit.context).toString() else ""
    }.getOrDefault("")

    private fun render() {
        if (disposed || busy) return
        renderColors()
        val start = minOf(edit.selectionStart, edit.selectionEnd)
        val end = maxOf(edit.selectionStart, edit.selectionEnd)
        val hasSelection = start >= 0 && end > start && edit.text?.substring(start, end)?.trim()?.isNotEmpty() == true
        if (!hasSelection) styleProperty = null
        if (!active || failed || !edit.isShown || !edit.hasFocus() || start < 0 ||
            (!hasSelection && caret != start) || dismissedSelection == (edit.selectionStart to edit.selectionEnd)
        ) { popup.dismiss(); return }
        val layout = edit.layout ?: return
        val visible = Rect(); if (!edit.getGlobalVisibleRect(visible)) return
        val window = Rect(); edit.getWindowVisibleDisplayFrame(window)
        visible.intersect(window)
        if (visible.isEmpty) { popup.dismiss(); return }
        val location = IntArray(2); edit.getLocationOnScreen(location)
        val topLine = layout.getLineForOffset(start)
        val bottomLine = layout.getLineForOffset(end)
        val top = location[1] + edit.totalPaddingTop + layout.getLineTop(topLine) - edit.scrollY
        val x1 = location[0] + edit.totalPaddingLeft + layout.getPrimaryHorizontal(start) - edit.scrollX
        val x2 = location[0] + edit.totalPaddingLeft + layout.getPrimaryHorizontal(end) - edit.scrollX
        val center = if (topLine == bottomLine) (x1 + x2) / 2 else (visible.left + visible.right) / 2f
        snapshotStart = start; snapshotEnd = end; snapshotText = edit.text?.substring(start, end).orEmpty()
        snapshotGeneration = generation; snapshotAnchor = edit.selectionStart; snapshotHead = edit.selectionEnd
        val basic = buildList {
            if (hasSelection && !readOnly) add("cut")
            if (hasSelection) add("copy")
            if (!readOnly && canPaste) add("paste")
            add("selectAll")
        }
        val commands = if (hasSelection && !readOnly) {
            if (styleProperty == "fontSize") listOf("colorsBack", "fontSize:clear") + SelectionInlineStyles.fontSizePalette.map { "fontSize:$it" }
            else if (styleProperty != null) listOf("colorsBack", "color:clear") + SelectionInlineStyles.palette.map { "color:$it" }
            else settings.commands + listOf("color", "backgroundColor", "fontSize")
        } else emptyList()
        val pages = TextSelectionToolbarSettings.pages(commands,
            TextSelectionToolbarSettings.capacity(window.width() / edit.resources.displayMetrics.density), settings.rows == 1)
        val total = if (settings.rows == 2) maxOf(1, pages.size) else pages.size + 1
        page = page.coerceIn(0, total - 1)
        val nextSignature = "$basic/$pages/$page/${settings.rows}/$foreground/$background/$styleProperty"
        if (signature != nextSignature) {
            signature = nextSignature; content.removeAllViews()
            content.background = GradientDrawable().apply { setColor(background); cornerRadius = dp(10).toFloat(); setStroke(dp(1), (foreground and 0x00ffffff) or 0x33000000) }
            fun row() = LinearLayout(edit.context).also { content.addView(it) }
            if (settings.rows == 2 || page == 0) {
                val row = row(); basic.forEach { button(row, it) { run(it) } }
                if (settings.rows == 1 && total > 1) button(row, "next") { page++; schedule() }
            }
            if (commands.isNotEmpty() && (settings.rows == 2 || page > 0)) {
                val row = row()
                if (page > 0) button(row, "back") { page--; schedule() }
                pages.getOrNull(if (settings.rows == 2) page else page - 1)?.forEach { button(row, it) { run(it) } }
                if (page < total - 1) button(row, "next") { page++; schedule() }
            }
            content.contentDescription = "${if (styleProperty == "color") "字体颜色" else if (styleProperty == "backgroundColor") "字体背景色" else if (styleProperty == "fontSize") "字体大小" else "文本选择工具栏"}，第 ${page + 1} 页，共 $total 页"
        }
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = content.measuredWidth; val height = content.measuredHeight
        val x = (center - width / 2).toInt().coerceIn(window.left + dp(8), maxOf(window.left + dp(8), window.right - dp(8) - width))
        val y = (top - dp(10) - height).coerceAtLeast(visible.top + dp(8))
        val rootScreen = IntArray(2); val rootWindow = IntArray(2)
        edit.rootView.getLocationOnScreen(rootScreen); edit.rootView.getLocationInWindow(rootWindow)
        val wx = x - rootScreen[0] + rootWindow[0]; val wy = y - rootScreen[1] + rootWindow[1]
        if (popup.isShowing) popup.update(wx, wy, width, height)
        else popup.showAtLocation(edit, Gravity.TOP or Gravity.LEFT, wx, wy)
    }
    private fun button(row: LinearLayout, id: String, action: () -> Unit) {
        val colorIndex = id.takeIf { it.startsWith("color:") }?.let { SelectionInlineStyles.palette.indexOf(it.removePrefix("color:")) } ?: -1
        val fontSizeIndex = id.takeIf { it.startsWith("fontSize:") }?.let { SelectionInlineStyles.fontSizePalette.indexOf(it.removePrefix("fontSize:")) } ?: -1
        val label = when {
            colorIndex >= 0 -> "${if (styleProperty == "color") "字体颜色" else "字体背景色"}：${SelectionInlineStyles.names[colorIndex]}"
            fontSizeIndex >= 0 -> "字体大小：${SelectionInlineStyles.fontSizeNames[fontSizeIndex]}"
            else -> TextSelectionToolbarSettings.labels[id] ?: mapOf("copy" to "复制", "cut" to "剪切", "paste" to "粘贴", "selectAll" to "全选", "back" to "上一页", "next" to "下一页", "color" to "字体颜色", "backgroundColor" to "字体背景色", "fontSize" to "字体大小", "colorsBack" to "返回格式操作", "color:clear" to "清除当前颜色", "fontSize:clear" to "清除当前字号").getValue(id)
        }
        val glyph = mapOf("toggleBold" to "B", "toggleItalic" to "I", "toggleUnderline" to "U", "toggleStrike" to "S", "toggleCode" to "`", "toggleBlockquote" to "❯", "toggleOrderedList" to "1.", "toggleUnorderedList" to "•", "toggleCheckList" to "☑", "back" to "‹", "next" to "›", "color" to "字色", "backgroundColor" to "底色", "fontSize" to "字号", "colorsBack" to "↩", "color:clear" to "清除", "fontSize:clear" to "默认")
        row.addView(TextView(edit.context).apply {
            val iconResource = when (id) {
                "color" -> R.drawable.ic_text_selection_color
                "backgroundColor" -> R.drawable.ic_text_selection_background
                else -> null
            }
            text = if (iconResource == null) glyph[id] ?: "" else ""
            if (iconResource != null) {
                edit.context.getDrawable(iconResource)?.also { icon ->
                    icon.setBounds(0, 0, dp(20), dp(20))
                    setCompoundDrawables(icon, null, null, null)
                }
            } else if (id in setOf("copy", "cut", "paste", "selectAll")) {
                val icon = SelectionGlyph(id, this@BetaSelectionToolbar.foreground, dp(20)).apply { setBounds(0, 0, dp(20), dp(20)) }
                setCompoundDrawables(icon, null, null, null)
            }
            contentDescription = label; gravity = Gravity.CENTER
            textSize = if (id in glyph || fontSizeIndex >= 0) 20f else 13f; setTextColor(this@BetaSelectionToolbar.foreground)
            if (colorIndex >= 0) {
                val swatch = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.parseColor(SelectionInlineStyles.palette[colorIndex]))
                    setStroke(dp(1), this@BetaSelectionToolbar.foreground); setSize(dp(24), dp(24)); setBounds(0, 0, dp(24), dp(24))
                }
                setCompoundDrawables(swatch, null, null, null)
            }
            isClickable = true; isFocusable = true
            val typed = android.util.TypedValue(); context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typed, true)
            setBackgroundResource(typed.resourceId)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { if (row.childCount > 0) marginStart = dp(3) })
    }
    private fun run(id: String) {
        if (busy || !active || disposed || (readOnly && id != "copy" && id != "selectAll")) return
        val editable = edit.text ?: return
        if (snapshotGeneration != generation || edit.selectionStart != snapshotAnchor || edit.selectionEnd != snapshotHead ||
            snapshotEnd > editable.length || editable.substring(snapshotStart, snapshotEnd) != snapshotText) return
        busy = true
        try {
            edit.setSelection(snapshotAnchor, snapshotHead)
            when (id) {
                "color", "backgroundColor", "fontSize", "colorsBack" -> {
                    if (snapshotText.isBlank()) return
                    styleProperty = id.takeUnless { it == "colorsBack" }
                    page = if (settings.rows == 2) 0 else 1
                }
                "copy", "cut" -> {
                    val clipboard = edit.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("KardLeaf", snapshotText))
                    if (id == "cut") edit.editHistory(OperationType.REPLACE) {
                        editable.delete(snapshotStart, snapshotEnd); edit.setSelection(snapshotStart)
                    } else edit.setSelection(snapshotEnd)
                    mode.finish(); dismiss()
                }
                "paste" -> { edit.onTextContextMenuItem(android.R.id.paste); mode.finish(); dismiss() }
                "selectAll" -> { edit.onTextContextMenuItem(android.R.id.selectAll); dismissedSelection = null }
                else -> {
                    if (id.startsWith("color:") || id.startsWith("fontSize:")) {
                        val property = styleProperty ?: return
                        if (android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editable) >= 0) return
                        val change = SelectionInlineStyles.change(editable.toString(), snapshotAnchor, snapshotHead, property,
                            if (id.endsWith(":clear")) null else id.substringAfter(':')) ?: return
                        if (editable.substring(change.from, change.to) != change.text) edit.editHistory(OperationType.TOOLBAR) {
                            editable.replace(change.from, change.to, change.text)
                            edit.setSelection(change.start, change.end)
                        }
                        dismissedSelection = null
                        return
                    }
                    val change = selectionBlockChange(editable.toString(), snapshotStart, snapshotEnd, id)
                    if (change == null) execute(id) else edit.editHistory(OperationType.TOOLBAR) {
                        editable.replace(change.from, change.to, change.text)
                        edit.setSelection(change.start, change.end)
                    }
                    dismissedSelection = null
                }
            }
        } catch (_: RuntimeException) {
            com.kangle.kardleaf.data.utils.KardLeafLog.w("KardLeafSelection", "selection toolbar command failed: $id")
            // Clipboard failure leaves the original selection/document available for retry.
        } finally { busy = false; caret = -1; schedule() }
    }
    fun dispose() {
        disposed = true; dismiss(); mode.dispose(); stopSettings(); edit.removeCallbacks(refresh)
        edit.removeTextChangedListener(watcher)
        if (edit.viewTreeObserver.isAlive) {
            edit.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            edit.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        }
    }

    // Appearance-only spans; HTML stays visible because Editable/IME offsets are source offsets.
    private class TextColor(color: Int) : android.text.style.ForegroundColorSpan(color)
    private class TextBackground(color: Int) : android.text.style.BackgroundColorSpan(color)
    private class TextSize(size: Float) : android.text.style.RelativeSizeSpan(size)
    private fun renderColors() {
        if (!colorsDirty) return
        val editable = edit.text ?: return
        if (android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editable) >= 0) return
        colorsDirty = false
        editable.getSpans(0, editable.length, TextColor::class.java).forEach(editable::removeSpan)
        editable.getSpans(0, editable.length, TextBackground::class.java).forEach(editable::removeSpan)
        editable.getSpans(0, editable.length, TextSize::class.java).forEach(editable::removeSpan)
        val text = editable.toString()
        if (!text.contains("<span", ignoreCase = true)) return
        SelectionInlineStyles.scan(text).spans.sortedBy { it.from }.forEach { span ->
            if (span.openTo < span.closeFrom) {
                span.colors["color"]?.let { editable.setSpan(TextColor(Color.parseColor(it)), span.openTo, span.closeFrom, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                span.colors["backgroundColor"]?.let { editable.setSpan(TextBackground(Color.parseColor(it)), span.openTo, span.closeFrom, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                span.colors["fontSize"]?.removeSuffix("em")?.toFloatOrNull()?.let { editable.setSpan(TextSize(it), span.openTo, span.closeFrom, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
        }
    }
}

/** The four SVG glyphs from MobileSelectionToolbar.vue, drawn in their 24-unit viewport. */
private class SelectionGlyph(private val id: String, color: Int, private val size: Int) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f
        strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    override fun draw(canvas: android.graphics.Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat()); canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun path(value: String) { canvas.drawPath(androidx.core.graphics.PathParser.createPathFromPathData(value)!!, paint) }
        when (id) {
            "copy" -> { canvas.drawRoundRect(9f, 8f, 19f, 20f, 2f, 2f, paint); path("M5 16V6a2 2 0 0 1 2-2h8") }
            "cut" -> { canvas.drawCircle(6f, 7f, 2.3f, paint); canvas.drawCircle(6f, 17f, 2.3f, paint); path("M8.1 8.1 19 19M8.1 15.9 19 5") }
            "paste" -> path("M9 5h6M9 4.8A2.8 2.8 0 0 1 11.8 2h.4A2.8 2.8 0 0 1 15 4.8V6H9ZM8 5H6.8A2.8 2.8 0 0 0 4 7.8v10.4A2.8 2.8 0 0 0 6.8 21h10.4a2.8 2.8 0 0 0 2.8-2.8V7.8A2.8 2.8 0 0 0 17.2 5H16")
            else -> { paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2.4f, 2.4f), 0f); canvas.drawRoundRect(4f, 4f, 20f, 20f, 2f, 2f, paint) }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
}
