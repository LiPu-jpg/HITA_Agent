package com.limpu.hitax.ui.main.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentChatViewModelTest {
    @Test
    fun `deleting current session switches to remaining latest session`() {
        assertEquals(
            "session-b",
            nextSessionIdAfterDeletion(
                deletedSessionId = "session-a",
                currentSessionId = "session-a",
                remainingLatestSessionId = "session-b",
            ),
        )
    }

    @Test
    fun `deleting non current session keeps current session`() {
        assertEquals(
            "session-a",
            nextSessionIdAfterDeletion(
                deletedSessionId = "session-b",
                currentSessionId = "session-a",
                remainingLatestSessionId = "session-c",
            ),
        )
    }

    @Test
    fun `deleting last current session returns null to signal fresh session`() {
        assertNull(
            nextSessionIdAfterDeletion(
                deletedSessionId = "session-a",
                currentSessionId = "session-a",
                remainingLatestSessionId = null,
            ),
        )
    }
}
