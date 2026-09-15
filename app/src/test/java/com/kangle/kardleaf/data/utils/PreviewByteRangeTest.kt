package com.kangle.kardleaf.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewByteRangeTest {
    @Test fun chromiumSeekingRanges() {
        assertEquals(0L..99L, previewByteRange("bytes=0-", 100))
        assertEquals(20L..39L, previewByteRange("bytes=20-39", 100))
        assertEquals(90L..99L, previewByteRange("bytes=90-999", 100))
        assertEquals(90L..99L, previewByteRange("bytes=-10", 100))
        assertEquals(0L..99L, previewByteRange("bytes=-999", 100))
        for (header in listOf("bytes=100-", "bytes=40-20", "bytes=-0", "bytes=-", "bytes=0-1,3-4", "bytes=999999999999999999999-", "items=0-1")) {
            assertNull(header, previewByteRange(header, 100))
        }
        assertNull(previewByteRange("bytes=0-", 0))
    }
}
