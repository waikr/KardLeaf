package com.kangle.kardleaf.data.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryUtilsTest {
    @Test
    fun escapesSqlLikeWildcards() {
        assertEquals("\\\\foo\\%\\_", SearchQueryUtils.escapeLikePattern("\\foo%_"))
    }
}
