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
}
