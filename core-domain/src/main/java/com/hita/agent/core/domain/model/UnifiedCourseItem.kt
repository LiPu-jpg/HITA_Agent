package com.hita.agent.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UnifiedCourseItem(
    val courseCode: String,
    val courseName: String,
    val teacher: String? = null,
    val classroom: String? = null,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val weeksText: String? = null
)
