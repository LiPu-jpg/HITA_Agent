package com.limpu.hitax.agent.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentBackendClientTest {
    @Test
    fun `brave search request serializes query instead of keyword`() {
        val json = Gson().toJson(BraveSearchRequest(query = "哈尔滨工业大学 最新消息", count = 3))
        assertTrue(json.contains("\"query\":\"哈尔滨工业大学 最新消息\""))
        assertTrue(json.contains("\"count\":3"))
        assertFalse(json.contains("\"keyword\""))
    }

    @Test
    fun `extracts backend error message from json body`() {
        val error = buildAgentBackendHttpError(400, "{\"ok\":false,\"error\":{\"code\":\"HTTP_ERROR\",\"message\":\"query is required\"}}")
        assertEquals("HTTP_400", error.code)
        assertEquals("query is required", error.message)
    }

    @Test
    fun `falls back to raw backend body when message is absent`() {
        val error = buildAgentBackendHttpError(500, "internal server error")
        assertEquals("HTTP_500", error.code)
        assertEquals("internal server error", error.message)
    }
}
