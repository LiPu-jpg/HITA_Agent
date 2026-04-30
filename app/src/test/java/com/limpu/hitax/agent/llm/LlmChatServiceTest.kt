package com.limpu.hitax.agent.llm

import com.limpu.hitax.agent.tools.ReActToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LlmChatServiceTest {

    @Test
    fun `registry contains all 12 tools`() {
        val registry = ReActToolRegistry.createDefault()
        val tools = listOf(
            "get_timetable", "add_activity", "search_course", "get_course_detail",
            "search_teacher", "web_search", "brave_answer", "rag_search",
            "crawl_page", "crawl_site", "crawl_status", "submit_review",
        )
        tools.forEach { name ->
            assertNotNull("Tool $name should be registered", registry.get(name))
        }
    }

    @Test
    fun `registry is case-insensitive`() {
        val registry = ReActToolRegistry.createDefault()
        assertNotNull(registry.get("GET_TIMETABLE"))
        assertNotNull(registry.get("Get_Timetable"))
    }

    @Test
    fun `unknown tool returns null`() {
        val registry = ReActToolRegistry.createDefault()
        assertNull(registry.get("nonexistent_tool"))
    }

    @Test
    fun `registry returns same instance for same name`() {
        val registry = ReActToolRegistry.createDefault()
        assertEquals(registry.get("get_timetable"), registry.get("GET_TIMETABLE"))
    }

    @Test
    fun `registry does not contain unregistered tool`() {
        val registry = ReActToolRegistry.createDefault()
        assertNull(registry.get("delete_timetable"))
        assertNull(registry.get("update_course"))
    }
}
