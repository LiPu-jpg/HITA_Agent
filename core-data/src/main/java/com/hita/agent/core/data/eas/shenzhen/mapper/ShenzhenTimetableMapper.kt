package com.hita.agent.core.data.eas.shenzhen.mapper

import com.hita.agent.core.data.eas.shenzhen.dto.WeeklyMatrixResponse
import com.hita.agent.core.domain.model.UnifiedCourseItem
import kotlinx.serialization.json.Json

object ShenzhenTimetableMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun map(payload: String, week: Int = 1): List<UnifiedCourseItem> {
        val response = json.decodeFromString<WeeklyMatrixResponse>(payload)
        val jcList = response.content.firstOrNull { it.jcList.isNotEmpty() }?.jcList.orEmpty()
        val items = response.content
            .flatMap { it.kcxxList }
            .filter { it.courseCode != null || it.rawInfo != null }

        return items.mapNotNull { kc ->
            val weekday = kc.weekday ?: return@mapNotNull null
            val bigSection = kc.bigSection ?: return@mapNotNull null
            val schedule = jcList.firstOrNull { it.bigSection == bigSection }
            val start = schedule?.startPeriod ?: if (bigSection > 0) (bigSection - 1) * 2 + 1 else null
            val end = schedule?.endPeriod ?: if (start != null) start + (kc.length ?: 1) - 1 else null
            if (start == null || end == null) return@mapNotNull null

            val name = kc.courseName?.ifBlank { null }
                ?: extractCourseName(kc.rawInfo)
                ?: return@mapNotNull null
            val classroom = extractClassroom(kc.rawInfo)

            UnifiedCourseItem(
                courseCode = kc.courseCode.orEmpty(),
                courseName = name,
                teacher = null,
                classroom = classroom,
                weekday = weekday,
                startPeriod = start,
                endPeriod = end,
                weeks = listOf(week)
            )
        }
    }

    private fun extractCourseName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val first = raw.lineSequence().firstOrNull()?.trim().orEmpty()
        return first.replace(Regex("\\[.*?]"), "").trim().ifBlank { null }
    }

    private fun extractClassroom(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val matches = Regex("\\[([^\\]]+)]").findAll(raw).map { it.groupValues[1].trim() }
        return matches.lastOrNull()
    }
}
