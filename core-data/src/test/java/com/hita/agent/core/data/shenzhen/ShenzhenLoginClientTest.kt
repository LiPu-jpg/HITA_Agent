package com.hita.agent.core.data.shenzhen

import com.hita.agent.core.data.eas.shenzhen.ShenzhenLoginClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShenzhenLoginClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun login_returnsAccessToken() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "route=abc; Path=/")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "JSESSIONID=xyz; Path=/")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    "{\"access_token\":\"token123\",\"refresh_token\":\"r1\"}"
                )
        )

        val client = ShenzhenLoginClient(server.url("/incoSpringBoot").toString())
        val result = client.login("user", "pass")

        assertEquals("token123", result.accessToken)
        val cookieHeader = result.cookiesByHost[server.hostName].orEmpty()
        assertTrue(cookieHeader.contains("route=abc"))
        assertTrue(cookieHeader.contains("JSESSIONID=xyz"))
        assertEquals(3, server.requestCount)
    }
}
