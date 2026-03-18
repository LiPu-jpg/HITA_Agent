package com.hita.agent.core.domain.model

import java.time.Instant

enum class DataSource {
    CACHE,
    NETWORK
}

data class ErrorInfo(
    val code: String,
    val message: String,
    val retryable: Boolean? = null
)

data class UnifiedTimetableResult(
    val data: List<UnifiedCourseItem>,
    val cachedAt: Instant,
    val expiresAt: Instant,
    val stale: Boolean,
    val source: DataSource,
    val error: ErrorInfo?
)

data class UnifiedScoreSummary(
    val gpa: String? = null,
    val rank: String? = null,
    val total: String? = null
)

data class UnifiedScoreResult(
    val data: List<UnifiedScoreItem>,
    val summary: UnifiedScoreSummary? = null,
    val cachedAt: Instant,
    val expiresAt: Instant,
    val stale: Boolean,
    val source: DataSource,
    val error: ErrorInfo?
)
