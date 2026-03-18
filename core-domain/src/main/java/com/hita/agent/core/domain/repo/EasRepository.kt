package com.hita.agent.core.domain.repo

import com.hita.agent.core.domain.model.CampusSession
import com.hita.agent.core.domain.model.EmptyRoomResult
import com.hita.agent.core.domain.model.UnifiedCourseItem
import com.hita.agent.core.domain.model.UnifiedScoreItem
import com.hita.agent.core.domain.model.UnifiedTerm

interface EasRepository {
    suspend fun login(username: String, password: String): CampusSession
    suspend fun validateSession(): Boolean
    suspend fun getTerms(): List<UnifiedTerm>
    suspend fun getTimetable(term: UnifiedTerm, forceRefresh: Boolean = false): CachedResult<List<UnifiedCourseItem>>
    suspend fun getScores(term: UnifiedTerm, qzqmFlag: String, forceRefresh: Boolean = false): CachedResult<List<UnifiedScoreItem>>
    suspend fun getEmptyRooms(
        date: String,
        buildingId: String,
        period: String,
        forceRefresh: Boolean = false
    ): EmptyRoomResult
}

data class CachedResult<T>(
    val data: T,
    val cachedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val stale: Boolean,
    val source: CacheSource,
    val error: String? = null
)

enum class CacheSource {
    CACHE,
    NETWORK
}
