package com.kangle.kardleaf.data.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryUtilsTest {
    @Test
    fun allowsChineseSingleCharacter() {
        assertTrue(SearchQueryUtils.isMeaningfulSearchQuery("账"))
    }

    @Test
    fun allowsChineseMultipleCharacters() {
        assertTrue(SearchQueryUtils.isMeaningfulSearchQuery("账单"))
    }

    @Test
    fun rejectsEnglishSingleCharacter() {
        assertFalse(SearchQueryUtils.isMeaningfulSearchQuery("a"))
    }

    @Test
    fun allowsEnglishMultipleCharacters() {
        assertTrue(SearchQueryUtils.isMeaningfulSearchQuery("car"))
    }

    @Test
    fun rejectsBlankQuery() {
        assertFalse(SearchQueryUtils.isMeaningfulSearchQuery("  "))
    }

    @Test
    fun rejectsPunctuationOnlyQuery() {
        assertFalse(SearchQueryUtils.isMeaningfulSearchQuery("？！"))
    }
}
