package com.hita.agent.core.data.store

import java.time.Instant

class CacheStore {
    fun computeExpiresAt(cachedAt: Instant, ttlSeconds: Long): Instant {
        return cachedAt.plusSeconds(ttlSeconds)
    }

    fun isExpired(cachedAt: Instant, ttlSeconds: Long, now: Instant = Instant.now()): Boolean {
        val expiresAt = computeExpiresAt(cachedAt, ttlSeconds)
        return now.isAfter(expiresAt)
    }
}
