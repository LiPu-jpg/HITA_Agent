package com.hita.agent.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class EmptyRoomQuery(
    val date: String,
    val period: String,
    val buildingId: String,
    val buildingName: String? = null
)

@Serializable
enum class RoomStatus {
    FREE,
    OCCUPIED
}

@Serializable
data class EmptyRoomItem(
    val name: String,
    val nameEn: String? = null,
    val status: RoomStatus
)

data class EmptyRoomResult(
    val rooms: List<EmptyRoomItem>,
    val buildingName: String? = null,
    val cachedAt: java.time.Instant,
    val expiresAt: java.time.Instant,
    val stale: Boolean,
    val source: DataSource,
    val error: ErrorInfo?
)
