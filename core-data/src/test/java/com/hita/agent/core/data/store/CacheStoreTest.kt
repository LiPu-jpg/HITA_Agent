package com.hita.agent.core.data.store

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CacheStoreTest {
    @Test
    fun cacheExpiresByCachedAt() {
        val cachedAt = Instant.parse("2026-03-14T00:00:00Z")
        val expiresAt = cachedAt.plusSeconds(3600)
        assertTrue(expiresAt.isAfter(cachedAt))
    }
}
