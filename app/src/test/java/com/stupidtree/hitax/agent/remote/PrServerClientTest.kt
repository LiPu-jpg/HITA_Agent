package com.stupidtree.hitax.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PrServerClientTest {
    @Test
    fun `builds branch safe submit idempotency key`() {
        val key = buildSubmitIdempotencyKey("COMP3003", uniqueSuffix = 123456L)
        assertEquals("android-comp3003-123456", key)
    }

    @Test
    fun `sanitizes submit idempotency key input`() {
        val key = buildSubmitIdempotencyKey("Comp:3003 / PR", uniqueSuffix = 42L)
        assertEquals("android-comp-3003-pr-42", key)
    }

    @Test
    fun `prefers nested error message from json body`() {
        val error = buildPrHttpError(500, "{" +
                "\"error\":{\"code\":\"INTERNAL\",\"message\":\"github create pr failed\"}}")
        assertEquals("HTTP_500", error.code)
        assertEquals("HTTP 500: github create pr failed", error.message)
    }

    @Test
    fun `falls back to detail field when error object missing`() {
        val error = buildPrHttpError(400, "{\"detail\":\"invalid course code\"}")
        assertEquals("HTTP 400: invalid course code", error.message)
    }

    @Test
    fun `falls back to raw body for plain text responses`() {
        val error = buildPrHttpError(500, "internal server error")
        assertEquals("HTTP 500: internal server error", error.message)
    }

    @Test
    fun `keeps plain status when body missing`() {
        val error = buildPrHttpError(500, null)
        assertEquals("HTTP 500", error.message)
    }
}
