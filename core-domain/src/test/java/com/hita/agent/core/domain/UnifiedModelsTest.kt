package com.hita.agent.core.domain

import com.hita.agent.core.domain.model.UnifiedCourseItem
import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedModelsTest {
    @Test
    fun unifiedCourseItem_inclusiveEndPeriod() {
        val item = UnifiedCourseItem(
            courseCode = "AUTO1001",
            courseName = "Test",
            weekday = 1,
            startPeriod = 1,
            endPeriod = 2,
            weeks = listOf(1, 2)
        )
        assertEquals(2, item.endPeriod)
    }
}
