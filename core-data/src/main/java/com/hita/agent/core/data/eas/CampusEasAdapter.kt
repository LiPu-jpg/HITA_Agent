package com.hita.agent.core.data.eas

import com.hita.agent.core.domain.model.CampusSession
import com.hita.agent.core.domain.model.UnifiedCourseItem
import com.hita.agent.core.domain.model.UnifiedScoreItem
import com.hita.agent.core.domain.model.UnifiedTerm
import com.hita.agent.core.domain.model.EmptyRoomResult

interface CampusEasAdapter {
    suspend fun login(username: String, password: String): CampusSession
    suspend fun validateSession(session: CampusSession): Boolean

    suspend fun fetchTerms(session: CampusSession): List<UnifiedTerm>
    suspend fun fetchTimetable(term: UnifiedTerm, session: CampusSession): List<UnifiedCourseItem>
    suspend fun fetchScores(term: UnifiedTerm, session: CampusSession, qzqmFlag: String): List<UnifiedScoreItem>
    suspend fun fetchEmptyRooms(
        session: CampusSession,
        date: String,
        buildingId: String,
        period: String
    ): EmptyRoomResult
}
