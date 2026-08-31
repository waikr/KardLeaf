package com.kangle.kardleaf.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardStableKeyViewportTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneRequestOverridesStableKeyAnchorAfterTopInsertion() {
        lateinit var state: LazyStaggeredGridState
        val values = mutableStateListOf<Int>()
        values += 0 until 30

        composeRule.setContent {
            state = remember { LazyStaggeredGridState() }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(1),
                state = state,
                modifier = Modifier.height(240.dp),
            ) {
                items(values, key = { it }) { value ->
                    Text("item $value", modifier = Modifier.height(80.dp))
                }
            }
        }

        composeRule.runOnIdle { state.requestDashboardScrollToItem(10) }
        composeRule.waitForIdle()
        val anchoredKey = state.layoutInfo.visibleItemsInfo.first().key

        composeRule.runOnIdle { values.add(0, -1) }
        composeRule.waitForIdle()
        assertEquals(anchoredKey, state.layoutInfo.visibleItemsInfo.first().key)

        composeRule.runOnIdle { state.requestDashboardScrollToItem(0) }
        composeRule.waitForIdle()
        assertEquals(-1, state.layoutInfo.visibleItemsInfo.first().key)
    }
}
