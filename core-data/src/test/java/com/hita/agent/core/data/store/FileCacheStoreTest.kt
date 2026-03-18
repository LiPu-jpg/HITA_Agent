package com.hita.agent.core.data.store

import com.hita.agent.core.domain.model.UnifiedCourseItem
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FileCacheStoreTest {
    @Test
    fun saveAndLoadCache() {
        val dir = Files.createTempDirectory("cache-store").toFile()
        val store = FileCacheStore(dir)
        val items = listOf(
            UnifiedCourseItem(
                courseCode = "AUTO1001",
                courseName = "Test",
                weekday = 1,
                startPeriod = 1,
                endPeriod = 2,
                weeks = listOf(1, 2)
            )
        )
        val cached = store.save(
            key = "timetable",
            data = items,
            ttlSeconds = 3600,
            serializer = ListSerializer(UnifiedCourseItem.serializer())
        )
        val loaded = store.load(
            key = "timetable",
            serializer = ListSerializer(UnifiedCourseItem.serializer())
        )

        assertEquals(1, loaded?.data?.size)
        assertTrue(cached.expiresAtEpochMs > cached.cachedAtEpochMs)
    }
}
