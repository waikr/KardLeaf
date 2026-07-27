package com.kangle.kardleaf.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class KardLeafAiAssistantTest {
    @Test
    fun `trial action ids match Gateway protocol`() {
        assertEquals(
            listOf("summary", "polish", "expand", "continue", "shorten", "proofread", "translate", "explain", "key_points", "todos", "title", "custom"),
            KardLeafAiAction.values().map { it.gatewayActionId() },
        )
    }

    @Test
    fun `trial errors are short Chinese messages without upstream body`() {
        assertEquals("请求过于频繁，请稍后再试", trialErrorMessage("""{"error":{"code":"RATE_LIMITED","message":"secret upstream body"}}""", 429))
        assertEquals("AI 试用服务暂时不可用", trialErrorMessage("not json", 502))
    }
}
