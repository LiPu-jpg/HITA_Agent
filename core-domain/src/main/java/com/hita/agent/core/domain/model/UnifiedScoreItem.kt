package com.hita.agent.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UnifiedScoreItem(
    val courseCode: String,
    val courseName: String,
    val credit: Double,
    val scoreValue: Double?,
    val scoreText: String,
    val status: String? = null,
    val termId: String
)
