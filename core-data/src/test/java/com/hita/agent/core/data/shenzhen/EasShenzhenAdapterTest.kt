package com.hita.agent.core.data.shenzhen

import com.hita.agent.core.data.eas.shenzhen.EasShenzhenAdapter
import com.hita.agent.core.domain.model.CampusId
import com.hita.agent.core.domain.model.CampusSession
import com.hita.agent.core.domain.model.RoomStatus
import com.hita.agent.core.domain.model.UnifiedTerm
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class EasShenzhenAdapterTest {
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
    fun login_returnsSessionWithCookies() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Set-Cookie", "route=abc; Path=/"))
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Set-Cookie", "JSESSIONID=xyz; Path=/"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"access_token\":\"token123\",\"refresh_token\":\"r1\"}")
        )

        val adapter = EasShenzhenAdapter(baseUrl = server.url("/incoSpringBoot").toString())
        val session = adapter.login("user", "pass")

        assertEquals("token123", session.bearerToken)
        val cookieHeader = session.cookiesByHost[server.hostName].orEmpty()
        assertTrue(cookieHeader.contains("route=abc"))
        assertTrue(cookieHeader.contains("JSESSIONID=xyz"))
    }

    @Test
    fun fetchTerms_mapsUnifiedTerm() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\n" +
                    "\"code\":200,\n" +
                    "\"content\":[{\"SFDQXQ\":\"1\",\"XN\":\"2025-2026\",\"XQ\":\"2\",\"XNXQ\":\"2025-20262\",\"XNXQMC\":\"2026春季\"}]\n" +
                    "}"
            )
        )

        val adapter = EasShenzhenAdapter(baseUrl = server.url("/incoSpringBoot").toString())
        val session = CampusSession(
            campusId = CampusId.SHENZHEN,
            bearerToken = "token",
            cookiesByHost = mapOf(server.hostName to "route=abc"),
            createdAt = Instant.now(),
            expiresAt = null
        )

        val terms = adapter.fetchTerms(session)
        assertEquals(1, terms.size)
        val term = terms.first()
        assertEquals("2025-20262", term.termId)
        assertTrue(term.isCurrent)
    }

    @Test
    fun fetchTimetable_usesWeekListAndMatrix() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\n" +
                    "\"code\":200,\n" +
                    "\"content\":[{\"ZCMC\":\"第1周\",\"ZC\":1},{\"ZCMC\":\"第2周\",\"ZC\":2}]\n" +
                    "}"
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\n" +
                    "\"code\":200,\n" +
                    "\"content\":[{\"kcxxList\":[{\"XQJ\":1,\"KCDM\":\"COMP2054\",\"KCMC\":\"机器学习\",\"KBXX\":\"机器学习\\n[A309]\",\"DJ\":3,\"XB\":2}]}]\n" +
                    "}"
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\n" +
                    "\"code\":200,\n" +
                    "\"content\":[{\"kcxxList\":[{\"XQJ\":2,\"KCDM\":\"COMP2054\",\"KCMC\":\"机器学习\",\"KBXX\":\"机器学习\\n[A309]\",\"DJ\":3,\"XB\":2}]}]\n" +
                    "}"
            )
        )

        val adapter = EasShenzhenAdapter(baseUrl = server.url("/incoSpringBoot").toString())
        val session = CampusSession(
            campusId = CampusId.SHENZHEN,
            bearerToken = "token",
            cookiesByHost = mapOf(server.hostName to "route=abc"),
            createdAt = Instant.now(),
            expiresAt = null
        )
        val term = UnifiedTerm(
            termId = "2025-20262",
            year = "2025-2026",
            term = "2",
            name = "2026春季",
            isCurrent = true
        )

        val courses = adapter.fetchTimetable(term, session)
        assertEquals(2, courses.size)
        assertTrue(courses.any { it.weeks.contains(1) })
        assertTrue(courses.any { it.weeks.contains(2) })
    }

    @Test
    fun fetchScores_mapsScores() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\n" +
                    "\"code\":200,\n" +
                    "\"content\":[{\"kcdm\":\"22MX44001\",\"kcmc\":\"劳动教育概论\",\"xf\":2,\"zf\":\"99\",\"xnxqdm\":\"2025-20261\"}]\n" +
                    "}"
            )
        )

        val adapter = EasShenzhenAdapter(baseUrl = server.url("/incoSpringBoot").toString())
        val session = CampusSession(
            campusId = CampusId.SHENZHEN,
            bearerToken = "token",
            cookiesByHost = mapOf(server.hostName to "route=abc"),
            createdAt = Instant.now(),
            expiresAt = null
        )
        val term = UnifiedTerm(
            termId = "2025-20262",
            year = "2025-2026",
            term = "2",
            name = "2026春季",
            isCurrent = true
        )

        val scores = adapter.fetchScores(term, session, "qm")
        assertEquals(1, scores.size)
        assertEquals("劳动教育概论", scores.first().courseName)
    }

    @Test
    fun fetchEmptyRooms_filtersByPeriod() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\n" +
                    "\"code\":200,\n" +
                    "\"content\":[{\"CDMC\":\"T2102\",\"CDMC_EN\":\"T2102\",\"DJ1\":\"1\",\"DJ2\":\"0\"}]\n" +
                    "}"
            )
        )

        val adapter = EasShenzhenAdapter(baseUrl = server.url("/incoSpringBoot").toString())
        val session = CampusSession(
            campusId = CampusId.SHENZHEN,
            bearerToken = "token",
            cookiesByHost = mapOf(server.hostName to "route=abc"),
            createdAt = Instant.now(),
            expiresAt = null
        )

        val result = adapter.fetchEmptyRooms(session, "2026-03-14", "14", "DJ2")
        assertEquals(1, result.rooms.size)
        assertEquals(RoomStatus.FREE, result.rooms.first().status)
    }
}
