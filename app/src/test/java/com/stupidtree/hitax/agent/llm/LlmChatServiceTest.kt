package com.stupidtree.hitax.agent.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmChatServiceTest {
    private fun callMethod(name: String, arg: String): Boolean {
        val method = LlmChatService::class.java.getDeclaredMethod(name, String::class.java)
        method.isAccessible = true
        return method.invoke(LlmChatService, arg) as Boolean
    }

    @Test
    fun `detects timetable query intent`() {
        assertTrue(callMethod("isLocalTimetableQuery", "帮我查一下课表"))
        assertTrue(callMethod("isLocalTimetableQuery", "今天有什么课"))
        assertTrue(callMethod("isLocalTimetableQuery", "明天上什么课"))
        assertTrue(callMethod("isLocalTimetableQuery", "后天有什么课"))
        assertTrue(callMethod("isLocalTimetableQuery", "4月25日有什么课"))
        assertTrue(callMethod("isLocalTimetableQuery", "下周三有什么课"))
    }

    @Test
    fun `rejects non timetable query intent`() {
        assertFalse(callMethod("isLocalTimetableQuery", "计算机网络这门课怎么样"))
        assertFalse(callMethod("isLocalTimetableQuery", "帮我搜一下课程评价"))
        assertFalse(callMethod("isLocalTimetableQuery", "帮我搜索 OpenAI"))
    }

    @Test
    fun `detects add activity intent`() {
        assertTrue(callMethod("isLocalAddActivity", "帮我添加明天下午3点的组会"))
        assertTrue(callMethod("isLocalAddActivity", "添加自由活动"))
        assertTrue(callMethod("isLocalAddActivity", "帮我安排一个活动"))
    }

    @Test
    fun `rejects non add activity intent`() {
        assertFalse(callMethod("isLocalAddActivity", "帮我查一下课表"))
        assertFalse(callMethod("isLocalAddActivity", "搜索课程"))
    }

    @Test
    fun `parses day offset correctly`() {
        val method = LlmChatService::class.java.getDeclaredMethod("parseDayOffset", String::class.java)
        method.isAccessible = true
        assertEquals(0, (method.invoke(LlmChatService, "今天有什么课") as Int))
        assertEquals(1, (method.invoke(LlmChatService, "明天有什么课") as Int))
        assertEquals(2, (method.invoke(LlmChatService, "后天有什么课") as Int))
        assertEquals(3, (method.invoke(LlmChatService, "大后天有什么课") as Int))
    }

    @Test
    fun `parses timestamp string`() {
        val method = LlmChatService::class.java.getDeclaredMethod("parseTimestampOrIso", String::class.java)
        method.isAccessible = true
        assertEquals(1768806000000L, method.invoke(LlmChatService, "1768806000000"))
    }
}
