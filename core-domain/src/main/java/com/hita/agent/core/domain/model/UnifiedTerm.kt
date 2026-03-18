package com.hita.agent.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UnifiedTerm(
    val termId: String,
    val year: String,
    val term: String,
    val name: String,
    val isCurrent: Boolean
)
