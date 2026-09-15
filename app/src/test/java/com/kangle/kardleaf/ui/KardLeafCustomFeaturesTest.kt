package com.kangle.kardleaf.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class KardLeafCustomFeaturesTest {
    @Test
    fun normalizesCustomSymbols() {
        assertEquals(
            listOf("→", "★", "多字"),
            KardLeafCustomFeatures.normalizeCustomSymbols("  →  \n★\n→\n\n多字"),
        )
    }

    @Test
    fun appliesToolbarItemFallbacks() {
        assertEquals(
            KardLeafCustomFeatures.QuickTextItem(name = "**bold**", content = "**bold**"),
            KardLeafCustomFeatures.normalizeQuickText("", "**bold**"),
        )
        assertEquals(
            KardLeafCustomFeatures.CustomFunctionItem(name = "→", svg = "→", content = "→"),
            KardLeafCustomFeatures.normalizeCustomFunctionItem("", "", "→"),
        )
        assertEquals(
            KardLeafCustomFeatures.CustomFunctionItem(name = "加粗", svg = "加粗", content = "**"),
            KardLeafCustomFeatures.normalizeCustomFunctionItem("加粗", "", "**"),
        )
    }

    @Test
    fun usesRequestedDefaultToolbarOrderWithoutDefaultQuickTexts() {
        assertEquals(
            listOf(
                KardLeafCustomFeatures.ToolbarItem.UNDO,
                KardLeafCustomFeatures.ToolbarItem.REDO,
                KardLeafCustomFeatures.ToolbarItem.IMAGE,
                KardLeafCustomFeatures.ToolbarItem.SYMBOLS,
                KardLeafCustomFeatures.ToolbarItem.HEADING,
                KardLeafCustomFeatures.ToolbarItem.BOLD,
                KardLeafCustomFeatures.ToolbarItem.PREVIEW,
            ),
            KardLeafCustomFeatures.DefaultToolbarOrder.take(7),
        )
        assertEquals(emptyList<KardLeafCustomFeatures.QuickTextItem>(), KardLeafCustomFeatures.DefaultQuickTexts)
    }

    @Test
    fun removesOldDefaultQuickTextsAndKeepsUserItems() {
        val custom = KardLeafCustomFeatures.QuickTextItem(name = "我的文本", content = "我的内容")
        assertEquals(
            listOf(custom),
            KardLeafCustomFeatures.removeLegacyDefaultQuickTexts(
                listOf(
                    KardLeafCustomFeatures.QuickTextItem(name = "→", content = "→"),
                    custom,
                ),
            ),
        )
    }

    @Test
    fun keepsCustomFunctionIdentityForToolbarOrdering() {
        val item = KardLeafCustomFeatures.normalizeCustomFunctionItems(
            listOf(KardLeafCustomFeatures.CustomFunctionItem(name = "→", svg = "→", content = "→")),
        ).single()

        assertEquals(
            item,
            KardLeafCustomFeatures.normalizeCustomFunctionItems(listOf(item)).single(),
        )
        assertEquals(
            "CUSTOM_FUNCTION:${item.id}",
            KardLeafCustomFeatures.EditorToolbarEntry.CustomFunction(item).key,
        )
    }
}
