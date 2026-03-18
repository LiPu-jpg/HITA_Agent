package com.hita.agent.core.data.repo

import com.hita.agent.core.data.eas.CampusEasAdapter
import com.hita.agent.core.data.store.FileCacheStore
import com.hita.agent.core.data.store.FileSessionStore
import com.hita.agent.core.domain.model.CampusSession
import com.hita.agent.core.domain.model.EmptyRoomResult
import com.hita.agent.core.domain.model.UnifiedCourseItem
import com.hita.agent.core.domain.model.UnifiedScoreItem
import com.hita.agent.core.domain.model.UnifiedTerm
import com.hita.agent.core.domain.repo.CacheSource
import com.hita.agent.core.domain.repo.CachedResult
import com.hita.agent.core.domain.repo.EasRepository
import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer

class EasRepositoryImpl(
    private val adapter: CampusEasAdapter,
    private val sessionStore: FileSessionStore,
    private val cacheStore: FileCacheStore,
    private val timetableTtlSeconds: Long = 24 * 3600,
    private val scoresTtlSeconds: Long = 6 * 3600,
    private val emptyRoomsTtlSeconds: Long = 2 * 3600
) : EasRepository {
    override suspend fun login(username: String, password: String): CampusSession {
        val session = adapter.login(username, password)
        sessionStore.save(session)
        return session
    }

    override suspend fun validateSession(): Boolean {
        val session = sessionStore.load(com.hita.agent.core.domain.model.CampusId.SHENZHEN) ?: return false
        return adapter.validateSession(session)
    }

    override suspend fun getTerms(): List<UnifiedTerm> {
        val session = sessionStore.load(com.hita.agent.core.domain.model.CampusId.SHENZHEN)
            ?: return emptyList()
        return adapter.fetchTerms(session)
    }

    override suspend fun getTimetable(
        term: UnifiedTerm,
        forceRefresh: Boolean
    ): CachedResult<List<UnifiedCourseItem>> {
        val key = "timetable_${term.termId}"
        val cached = cacheStore.load(key, ListSerializer(UnifiedCourseItem.serializer()))
        val now = Instant.now()
        if (!forceRefresh && cached != null && !cacheStore.isExpired(cached.expiresAtEpochMs, now)) {
            return CachedResult(
                data = cached.data,
                cachedAtEpochMs = cached.cachedAtEpochMs,
                expiresAtEpochMs = cached.expiresAtEpochMs,
                stale = false,
                source = CacheSource.CACHE
            )
        }
        val session = sessionStore.load(com.hita.agent.core.domain.model.CampusId.SHENZHEN)
        return try {
            val data = adapter.fetchTimetable(term, session ?: return cachedListToResult(cached, true, "NO_SESSION"))
            val saved = cacheStore.save(
                key,
                data,
                timetableTtlSeconds,
                ListSerializer(UnifiedCourseItem.serializer()),
                now
            )
            CachedResult(
                data = saved.data,
                cachedAtEpochMs = saved.cachedAtEpochMs,
                expiresAtEpochMs = saved.expiresAtEpochMs,
                stale = false,
                source = CacheSource.NETWORK
            )
        } catch (e: Exception) {
            cachedListToResult(cached, true, e.message ?: "FETCH_FAILED")
        }
    }

    override suspend fun getScores(
        term: UnifiedTerm,
        qzqmFlag: String,
        forceRefresh: Boolean
    ): CachedResult<List<UnifiedScoreItem>> {
        val key = "scores_${term.termId}_$qzqmFlag"
        val cached = cacheStore.load(key, ListSerializer(UnifiedScoreItem.serializer()))
        val now = Instant.now()
        if (!forceRefresh && cached != null && !cacheStore.isExpired(cached.expiresAtEpochMs, now)) {
            return CachedResult(
                data = cached.data,
                cachedAtEpochMs = cached.cachedAtEpochMs,
                expiresAtEpochMs = cached.expiresAtEpochMs,
                stale = false,
                source = CacheSource.CACHE
            )
        }
        val session = sessionStore.load(com.hita.agent.core.domain.model.CampusId.SHENZHEN)
        return try {
            val data = adapter.fetchScores(term, session ?: return cachedListToResult(cached, true, "NO_SESSION"), qzqmFlag)
            val saved = cacheStore.save(
                key,
                data,
                scoresTtlSeconds,
                ListSerializer(UnifiedScoreItem.serializer()),
                now
            )
            CachedResult(
                data = saved.data,
                cachedAtEpochMs = saved.cachedAtEpochMs,
                expiresAtEpochMs = saved.expiresAtEpochMs,
                stale = false,
                source = CacheSource.NETWORK
            )
        } catch (e: Exception) {
            cachedListToResult(cached, true, e.message ?: "FETCH_FAILED")
        }
    }

    override suspend fun getEmptyRooms(
        date: String,
        buildingId: String,
        period: String,
        forceRefresh: Boolean
    ): EmptyRoomResult {
        val key = "rooms_${date}_${buildingId}_$period"
        val cached = cacheStore.load(key, ListSerializer(com.hita.agent.core.domain.model.EmptyRoomItem.serializer()))
        val now = Instant.now()
        if (!forceRefresh && cached != null && !cacheStore.isExpired(cached.expiresAtEpochMs, now)) {
            return EmptyRoomResult(
                rooms = cached.data,
                cachedAt = Instant.ofEpochMilli(cached.cachedAtEpochMs),
                expiresAt = Instant.ofEpochMilli(cached.expiresAtEpochMs),
                stale = false,
                source = com.hita.agent.core.domain.model.DataSource.CACHE,
                error = null
            )
        }
        val session = sessionStore.load(com.hita.agent.core.domain.model.CampusId.SHENZHEN)
        return try {
            val result = adapter.fetchEmptyRooms(session ?: return EmptyRoomResult(
                rooms = cached?.data.orEmpty(),
                cachedAt = Instant.ofEpochMilli(cached?.cachedAtEpochMs ?: now.toEpochMilli()),
                expiresAt = Instant.ofEpochMilli(cached?.expiresAtEpochMs ?: now.toEpochMilli()),
                stale = true,
                source = com.hita.agent.core.domain.model.DataSource.CACHE,
                error = com.hita.agent.core.domain.model.ErrorInfo("NO_SESSION", "no session", false)
            ), date, buildingId, period)
            cacheStore.save(
                key,
                result.rooms,
                emptyRoomsTtlSeconds,
                ListSerializer(com.hita.agent.core.domain.model.EmptyRoomItem.serializer()),
                now
            )
            result
        } catch (e: Exception) {
            EmptyRoomResult(
                rooms = cached?.data.orEmpty(),
                cachedAt = Instant.ofEpochMilli(cached?.cachedAtEpochMs ?: now.toEpochMilli()),
                expiresAt = Instant.ofEpochMilli(cached?.expiresAtEpochMs ?: now.toEpochMilli()),
                stale = true,
                source = com.hita.agent.core.domain.model.DataSource.CACHE,
                error = com.hita.agent.core.domain.model.ErrorInfo("FETCH_FAILED", e.message ?: "error", true)
            )
        }
    }

    private fun <T> cachedListToResult(
        cached: FileCacheStore.CacheResult<List<T>>?,
        stale: Boolean,
        error: String
    ): CachedResult<List<T>> {
        if (cached == null) {
            return CachedResult(
                data = emptyList(),
                cachedAtEpochMs = 0,
                expiresAtEpochMs = 0,
                stale = true,
                source = CacheSource.CACHE,
                error = error
            )
        }
        return CachedResult(
            data = cached.data,
            cachedAtEpochMs = cached.cachedAtEpochMs,
            expiresAtEpochMs = cached.expiresAtEpochMs,
            stale = stale,
            source = CacheSource.CACHE,
            error = error
        )
    }
}
