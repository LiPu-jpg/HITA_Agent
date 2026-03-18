package com.hita.agent.core.domain.model

import java.time.Instant

data class CampusSession(
    val campusId: CampusId,
    val bearerToken: String?,
    val cookiesByHost: Map<String, String>,
    val createdAt: Instant,
    val expiresAt: Instant?
)
