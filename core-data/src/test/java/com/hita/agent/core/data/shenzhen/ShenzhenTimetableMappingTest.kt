package com.hita.agent.core.data.shenzhen

import com.hita.agent.core.data.eas.shenzhen.mapper.ShenzhenTimetableMapper
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenTimetableMappingTest {
    @Test
    fun mapWeeklyMatrixToUnifiedCourseItems() {
        val json = loadJson("shenzhen/weekly_matrix.json")
        val items = ShenzhenTimetableMapper.map(json)
        assertTrue(items.any { it.courseName.isNotBlank() })
    }

    private fun loadJson(path: String): String {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Missing resource: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
