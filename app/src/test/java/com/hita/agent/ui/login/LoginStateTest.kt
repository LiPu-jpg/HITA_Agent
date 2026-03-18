package com.hita.agent.ui.login

import com.hita.agent.core.domain.model.CampusId
import com.hita.agent.core.domain.model.CampusSession
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginStateTest {
    @Test
    fun resolveStatus_loggedOut_whenNoSession() {
        val status = resolveStatus(session = null, isValid = null)
        assertEquals(LoginStatus.LOGGED_OUT, status)
    }

    @Test
    fun resolveStatus_loggedIn_whenSessionValid() {
        val session = CampusSession(
            campusId = CampusId.SHENZHEN,
            bearerToken = null,
            cookiesByHost = emptyMap(),
            createdAt = Instant.now(),
            expiresAt = null
        )
        val status = resolveStatus(session = session, isValid = true)
        assertEquals(LoginStatus.LOGGED_IN, status)
    }

    @Test
    fun resolveStatus_stale_whenSessionInvalid() {
        val session = CampusSession(
            campusId = CampusId.SHENZHEN,
            bearerToken = null,
            cookiesByHost = emptyMap(),
            createdAt = Instant.now(),
            expiresAt = null
        )
        val status = resolveStatus(session = session, isValid = false)
        assertEquals(LoginStatus.STALE, status)
    }
}
