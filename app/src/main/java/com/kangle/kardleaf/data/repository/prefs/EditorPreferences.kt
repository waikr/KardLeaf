package com.kangle.kardleaf.data.repository.prefs

import android.content.SharedPreferences
import com.kangle.kardleaf.data.repository.PrefsManager

internal class EditorPreferences(private val prefs: SharedPreferences) {
    fun saveKernel(kernel: PrefsManager.EditorKernel) {
        prefs.edit().putString(KEY_KERNEL, kernel.name).apply()
    }

    fun getKernel(): PrefsManager.EditorKernel {
        val name = prefs.getString(KEY_KERNEL, PrefsManager.DEFAULT_EDITOR_KERNEL)
        val kernel = runCatching {
            PrefsManager.EditorKernel.valueOf(name ?: PrefsManager.DEFAULT_EDITOR_KERNEL)
        }.getOrDefault(PrefsManager.EditorKernel.QUILLPAD_STYLE)
        return if (kernel == PrefsManager.EditorKernel.AUTO) PrefsManager.EditorKernel.QUILLPAD_STYLE else kernel
    }

    fun saveCodeMirrorLivePreviewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CODEMIRROR_LIVE_PREVIEW_ENABLED, enabled).apply()
    }

    fun isCodeMirrorLivePreviewEnabled(): Boolean =
        prefs.getBoolean(KEY_CODEMIRROR_LIVE_PREVIEW_ENABLED, PrefsManager.DEFAULT_CODEMIRROR_LIVE_PREVIEW_ENABLED)

    fun saveEditingImagePreviewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EDITING_IMAGE_PREVIEW_ENABLED, enabled).apply()
    }

    fun isEditingImagePreviewEnabled(): Boolean =
        prefs.getBoolean(KEY_EDITING_IMAGE_PREVIEW_ENABLED, PrefsManager.DEFAULT_EDITING_IMAGE_PREVIEW_ENABLED)

    fun saveAutoCodeMirrorThresholdChars(chars: Int) {
        prefs.edit().putInt(
            KEY_AUTO_CODEMIRROR_THRESHOLD_CHARS,
            chars.coerceIn(PrefsManager.MIN_AUTO_CODEMIRROR_THRESHOLD_CHARS, PrefsManager.MAX_AUTO_CODEMIRROR_THRESHOLD_CHARS),
        ).apply()
    }

    fun getAutoCodeMirrorThresholdChars(): Int =
        prefs.getInt(KEY_AUTO_CODEMIRROR_THRESHOLD_CHARS, PrefsManager.DEFAULT_AUTO_CODEMIRROR_THRESHOLD_CHARS)
            .coerceIn(PrefsManager.MIN_AUTO_CODEMIRROR_THRESHOLD_CHARS, PrefsManager.MAX_AUTO_CODEMIRROR_THRESHOLD_CHARS)

    fun saveFontSizeSp(value: Float) = saveFloat(KEY_FONT_SIZE_SP, value, PrefsManager.MIN_EDITOR_FONT_SIZE_SP, PrefsManager.MAX_EDITOR_FONT_SIZE_SP)
    fun getFontSizeSp(): Float = getFloat(KEY_FONT_SIZE_SP, PrefsManager.DEFAULT_EDITOR_FONT_SIZE_SP, PrefsManager.MIN_EDITOR_FONT_SIZE_SP, PrefsManager.MAX_EDITOR_FONT_SIZE_SP)
    fun saveLineHeightMultiplier(value: Float) = saveFloat(KEY_LINE_HEIGHT_MULTIPLIER, value, PrefsManager.MIN_EDITOR_LINE_HEIGHT_MULTIPLIER, PrefsManager.MAX_EDITOR_LINE_HEIGHT_MULTIPLIER)
    fun getLineHeightMultiplier(): Float = getFloat(KEY_LINE_HEIGHT_MULTIPLIER, PrefsManager.DEFAULT_EDITOR_LINE_HEIGHT_MULTIPLIER, PrefsManager.MIN_EDITOR_LINE_HEIGHT_MULTIPLIER, PrefsManager.MAX_EDITOR_LINE_HEIGHT_MULTIPLIER)
    fun saveLetterSpacingSp(value: Float) = saveFloat(KEY_LETTER_SPACING_SP, value, PrefsManager.MIN_EDITOR_LETTER_SPACING_SP, PrefsManager.MAX_EDITOR_LETTER_SPACING_SP)
    fun getLetterSpacingSp(): Float = getFloat(KEY_LETTER_SPACING_SP, PrefsManager.DEFAULT_EDITOR_LETTER_SPACING_SP, PrefsManager.MIN_EDITOR_LETTER_SPACING_SP, PrefsManager.MAX_EDITOR_LETTER_SPACING_SP)
    fun saveParagraphSpacingDp(value: Float) = saveFloat(KEY_PARAGRAPH_SPACING_DP, value, PrefsManager.MIN_EDITOR_PARAGRAPH_SPACING_DP, PrefsManager.MAX_EDITOR_PARAGRAPH_SPACING_DP)
    fun getParagraphSpacingDp(): Float = getFloat(KEY_PARAGRAPH_SPACING_DP, PrefsManager.DEFAULT_EDITOR_PARAGRAPH_SPACING_DP, PrefsManager.MIN_EDITOR_PARAGRAPH_SPACING_DP, PrefsManager.MAX_EDITOR_PARAGRAPH_SPACING_DP)

    fun saveFontFamily(fontFamily: String) {
        prefs.edit().putString(KEY_FONT_FAMILY, fontFamily.trim().ifBlank { PrefsManager.DEFAULT_EDITOR_FONT_FAMILY }).apply()
    }

    fun getFontFamily(): String =
        prefs.getString(KEY_FONT_FAMILY, PrefsManager.DEFAULT_EDITOR_FONT_FAMILY)?.takeIf { it.isNotBlank() }
            ?: PrefsManager.DEFAULT_EDITOR_FONT_FAMILY

    fun saveBottomToolbarAlwaysVisible(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BOTTOM_TOOLBAR_ALWAYS_VISIBLE, enabled).apply()
    }

    fun isBottomToolbarAlwaysVisible(): Boolean =
        prefs.getBoolean(KEY_BOTTOM_TOOLBAR_ALWAYS_VISIBLE, PrefsManager.DEFAULT_EDITOR_BOTTOM_TOOLBAR_ALWAYS_VISIBLE)

    fun getTopToolbarItemOrder(): List<PrefsManager.EditorTopToolbarItemId> {
        migrateTopToolbarDefaultsV2IfNeeded()
        val raw = prefs.getString(KEY_TOP_TOOLBAR_ITEM_ORDER, null)
            ?: return PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER
        val ids = raw.split(",").mapNotNull { runCatching { PrefsManager.EditorTopToolbarItemId.valueOf(it) }.getOrNull() }
        return ids.toMutableList().apply {
            PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER.forEach { if (it !in this) add(it) }
        }
    }

    fun saveTopToolbarItemOrder(order: List<PrefsManager.EditorTopToolbarItemId>) {
        migrateTopToolbarDefaultsV2IfNeeded()
        val normalized = order.distinct().toMutableList()
        PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER.forEach { if (it !in normalized) normalized.add(it) }
        prefs.edit().putString(KEY_TOP_TOOLBAR_ITEM_ORDER, normalized.joinToString(",") { it.name }).apply()
    }

    fun getTopToolbarMoreItems(): Set<PrefsManager.EditorTopToolbarItemId> {
        migrateTopToolbarDefaultsV2IfNeeded()
        val storedItems = prefs.getStringSet(KEY_TOP_TOOLBAR_MORE_ITEMS, null)
            ?.mapNotNull { runCatching { PrefsManager.EditorTopToolbarItemId.valueOf(it) }.getOrNull() }
            ?.filter { it != PrefsManager.EditorTopToolbarItemId.MORE }
            ?.toSet()
            ?: return PrefsManager.EditorTopToolbarItemId.DEFAULT_MORE_ITEMS
        if (prefs.getBoolean(KEY_TOP_TOOLBAR_MORE_DEFAULT_MIGRATED, false)) return storedItems
        val migratedItems = storedItems + PrefsManager.EditorTopToolbarItemId.DEFAULT_MORE_ITEMS
        prefs.edit()
            .putBoolean(KEY_TOP_TOOLBAR_MORE_DEFAULT_MIGRATED, true)
            .putStringSet(KEY_TOP_TOOLBAR_MORE_ITEMS, migratedItems.map { it.name }.toSet())
            .apply()
        return migratedItems
    }

    fun saveTopToolbarMoreItems(items: Set<PrefsManager.EditorTopToolbarItemId>) {
        migrateTopToolbarDefaultsV2IfNeeded()
        val safeItems = items.filter { it != PrefsManager.EditorTopToolbarItemId.MORE }
        prefs.edit()
            .putBoolean(KEY_TOP_TOOLBAR_MORE_DEFAULT_MIGRATED, true)
            .putStringSet(KEY_TOP_TOOLBAR_MORE_ITEMS, safeItems.map { it.name }.toSet())
            .apply()
    }

    fun getTopToolbarHiddenItems(): Set<PrefsManager.EditorTopToolbarItemId> {
        migrateTopToolbarDefaultsV2IfNeeded()
        return prefs.getStringSet(KEY_TOP_TOOLBAR_HIDDEN_ITEMS, null)
            ?.mapNotNull { runCatching { PrefsManager.EditorTopToolbarItemId.valueOf(it) }.getOrNull() }
            ?.filter { it != PrefsManager.EditorTopToolbarItemId.MORE }
            ?.toSet() ?: PrefsManager.EditorTopToolbarItemId.DEFAULT_HIDDEN_ITEMS
    }

    fun saveTopToolbarHiddenItems(items: Set<PrefsManager.EditorTopToolbarItemId>) {
        migrateTopToolbarDefaultsV2IfNeeded()
        val safeItems = items.filter { it != PrefsManager.EditorTopToolbarItemId.MORE }
        prefs.edit().putStringSet(KEY_TOP_TOOLBAR_HIDDEN_ITEMS, safeItems.map { it.name }.toSet()).apply()
    }

    private fun migrateTopToolbarDefaultsV2IfNeeded() {
        if (prefs.getBoolean(KEY_TOP_TOOLBAR_DEFAULTS_V2_MIGRATED, false)) return

        val storedOrder = prefs.getString(KEY_TOP_TOOLBAR_ITEM_ORDER, null)
            ?.split(",")
            ?.mapNotNull { runCatching { PrefsManager.EditorTopToolbarItemId.valueOf(it) }.getOrNull() }
        val storedMoreItems = prefs.getStringSet(KEY_TOP_TOOLBAR_MORE_ITEMS, null)
            ?.mapNotNull { runCatching { PrefsManager.EditorTopToolbarItemId.valueOf(it) }.getOrNull() }
            ?.filter { it != PrefsManager.EditorTopToolbarItemId.MORE }
            ?.toSet()
        val storedHiddenItems = prefs.getStringSet(KEY_TOP_TOOLBAR_HIDDEN_ITEMS, null)
            ?.mapNotNull { runCatching { PrefsManager.EditorTopToolbarItemId.valueOf(it) }.getOrNull() }
            ?.filter { it != PrefsManager.EditorTopToolbarItemId.MORE }
            ?.toSet()
        val usesPreviousDefaults =
            (storedOrder == null || storedOrder == PREVIOUS_DEFAULT_ORDER) &&
                (storedMoreItems == null || storedMoreItems == PREVIOUS_DEFAULT_MORE_ITEMS) &&
                (storedHiddenItems == null || storedHiddenItems == PREVIOUS_DEFAULT_HIDDEN_ITEMS)

        prefs.edit().apply {
            if (usesPreviousDefaults) {
                putString(
                    KEY_TOP_TOOLBAR_ITEM_ORDER,
                    PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER.joinToString(",") { it.name },
                )
                putStringSet(
                    KEY_TOP_TOOLBAR_MORE_ITEMS,
                    PrefsManager.EditorTopToolbarItemId.DEFAULT_MORE_ITEMS.map { it.name }.toSet(),
                )
                putStringSet(
                    KEY_TOP_TOOLBAR_HIDDEN_ITEMS,
                    PrefsManager.EditorTopToolbarItemId.DEFAULT_HIDDEN_ITEMS.map { it.name }.toSet(),
                )
                putBoolean(KEY_TOP_TOOLBAR_MORE_DEFAULT_MIGRATED, true)
            }
            putBoolean(KEY_TOP_TOOLBAR_DEFAULTS_V2_MIGRATED, true)
        }.apply()
    }

    private fun saveFloat(key: String, value: Float, min: Float, max: Float) {
        prefs.edit().putFloat(key, value.coerceIn(min, max)).apply()
    }

    private fun getFloat(key: String, default: Float, min: Float, max: Float): Float =
        prefs.getFloat(key, default).coerceIn(min, max)

    private companion object {
        const val KEY_KERNEL = "editor_kernel"
        const val KEY_CODEMIRROR_LIVE_PREVIEW_ENABLED = "codemirror_live_preview_enabled"
        const val KEY_EDITING_IMAGE_PREVIEW_ENABLED = "editing_image_preview_enabled"
        const val KEY_AUTO_CODEMIRROR_THRESHOLD_CHARS = "auto_codemirror_threshold_chars"
        const val KEY_FONT_SIZE_SP = "editor_font_size_sp"
        const val KEY_LINE_HEIGHT_MULTIPLIER = "editor_line_height_multiplier"
        const val KEY_LETTER_SPACING_SP = "editor_letter_spacing_sp"
        const val KEY_PARAGRAPH_SPACING_DP = "editor_paragraph_spacing_dp"
        const val KEY_FONT_FAMILY = "editor_font_family"
        const val KEY_BOTTOM_TOOLBAR_ALWAYS_VISIBLE = "editor_bottom_toolbar_always_visible"
        const val KEY_TOP_TOOLBAR_ITEM_ORDER = "editor_top_toolbar_item_order"
        const val KEY_TOP_TOOLBAR_MORE_ITEMS = "editor_top_toolbar_more_items"
        const val KEY_TOP_TOOLBAR_HIDDEN_ITEMS = "editor_top_toolbar_hidden_items"
        const val KEY_TOP_TOOLBAR_MORE_DEFAULT_MIGRATED = "editor_top_toolbar_more_default_migrated"
        const val KEY_TOP_TOOLBAR_DEFAULTS_V2_MIGRATED = "editor_top_toolbar_defaults_v2_migrated"

        val PREVIOUS_DEFAULT_ORDER = listOf(
            PrefsManager.EditorTopToolbarItemId.MINDMAP,
            PrefsManager.EditorTopToolbarItemId.LABEL,
            PrefsManager.EditorTopToolbarItemId.SEARCH,
            PrefsManager.EditorTopToolbarItemId.EDIT,
            PrefsManager.EditorTopToolbarItemId.OUTLINE,
            PrefsManager.EditorTopToolbarItemId.REMARKS,
            PrefsManager.EditorTopToolbarItemId.HISTORY,
            PrefsManager.EditorTopToolbarItemId.PRIVACY,
            PrefsManager.EditorTopToolbarItemId.ARCHIVE,
            PrefsManager.EditorTopToolbarItemId.DELETE,
            PrefsManager.EditorTopToolbarItemId.MORE,
        )
        val PREVIOUS_DEFAULT_MORE_ITEMS = setOf(
            PrefsManager.EditorTopToolbarItemId.HISTORY,
            PrefsManager.EditorTopToolbarItemId.PRIVACY,
            PrefsManager.EditorTopToolbarItemId.ARCHIVE,
            PrefsManager.EditorTopToolbarItemId.DELETE,
        )
        val PREVIOUS_DEFAULT_HIDDEN_ITEMS = emptySet<PrefsManager.EditorTopToolbarItemId>()
    }
}
