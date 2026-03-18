package com.hita.agent.core.data.store

import com.hita.agent.core.domain.model.CampusId
import com.hita.agent.core.domain.model.CampusSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files
import java.time.Instant

class FileSessionStoreTest {
    @Test
    fun saveAndLoadSession() {
        val dir = Files.createTempDirectory("session-store").toFile()
        val store = FileSessionStore(dir)
        val session = CampusSession(
            campusId = CampusId.SHENZHEN,
            bearerToken = "token",
            cookiesByHost = mapOf("example.com" to "route=abc"),
            createdAt = Instant.now(),
            expiresAt = null
        )

        store.save(session)
        val loaded = store.load(CampusId.SHENZHEN)

        assertNotNull(loaded)
        assertEquals("token", loaded?.bearerToken)
        assertEquals("route=abc", loaded?.cookiesByHost?.get("example.com"))
    }
}
