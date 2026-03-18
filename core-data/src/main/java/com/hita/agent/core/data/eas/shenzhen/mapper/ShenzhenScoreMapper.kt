package com.hita.agent.core.data.eas.shenzhen.mapper

import com.hita.agent.core.data.eas.shenzhen.dto.ScoreListResponse
import com.hita.agent.core.data.eas.shenzhen.dto.ScoreSummaryResponse
import com.hita.agent.core.domain.model.UnifiedScoreItem
import com.hita.agent.core.domain.model.UnifiedScoreSummary
import kotlinx.serialization.json.Json

object ShenzhenScoreMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun mapScores(payload: String): List<UnifiedScoreItem> {
        val response = json.decodeFromString<ScoreListResponse>(payload)
        return response.content.map { item ->
            val scoreText = item.score?.trim().orEmpty()
            val scoreValue = scoreText.toDoubleOrNull()
            UnifiedScoreItem(
                courseCode = item.courseCode.orEmpty(),
                courseName = item.courseName.orEmpty(),
                credit = item.credit ?: 0.0,
                scoreValue = scoreValue,
                scoreText = scoreText,
                status = null,
                termId = item.termCode ?: item.termName.orEmpty()
            )
        }
    }

    fun mapSummary(payload: String): UnifiedScoreSummary? {
        val response = json.decodeFromString<ScoreSummaryResponse>(payload)
        val summary = response.content?.summary ?: return null
        if (summary.gpa.isNullOrBlank() && summary.rank.isNullOrBlank() && summary.total.isNullOrBlank()) {
            return null
        }
        return UnifiedScoreSummary(
            gpa = summary.gpa,
            rank = summary.rank,
            total = summary.total
        )
    }
}
