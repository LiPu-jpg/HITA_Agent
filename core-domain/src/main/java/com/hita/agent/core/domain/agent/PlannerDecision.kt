package com.hita.agent.core.domain.agent

import kotlinx.serialization.json.JsonElement

data class PlannerDecision(
    val action: PlannerAction,
    val date: String? = null,
    val period: String? = null,
    val buildingId: String? = null,
    val skillName: String? = null,
    val skillInput: JsonElement? = null
)

enum class PlannerAction {
    LOCAL_SCORES,
    LOCAL_TIMETABLE,
    LOCAL_EMPTY_ROOMS,
    LOCAL_RAG,
    PUBLIC_RAG,
    PUBLIC_SKILL  // 新增：支持调用任意 public skill
}
