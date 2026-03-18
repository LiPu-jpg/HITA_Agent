package com.hita.agent.core.data.store

import com.hita.agent.core.domain.model.CampusId
import com.hita.agent.core.domain.model.CampusSession

class CampusSessionStore {
    private val sessions = mutableMapOf<CampusId, CampusSession>()

    fun save(session: CampusSession) {
        sessions[session.campusId] = session
    }

    fun load(campusId: CampusId): CampusSession? {
        return sessions[campusId]
    }

    fun clear(campusId: CampusId) {
        sessions.remove(campusId)
    }
}
