package com.hita.agent.core.data.eas.shenzhen.mapper

import com.hita.agent.core.data.eas.shenzhen.dto.EmptyRoomOccupancyResponse
import com.hita.agent.core.domain.model.EmptyRoomItem
import com.hita.agent.core.domain.model.RoomStatus
import kotlinx.serialization.json.Json

object ShenzhenEmptyRoomMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun filter(payload: String, period: String): List<EmptyRoomItem> {
        val response = json.decodeFromString<EmptyRoomOccupancyResponse>(payload)
        return response.content.map { room ->
            val value = when (period) {
                "DJ1" -> room.dj1
                "DJ2" -> room.dj2
                "DJ3" -> room.dj3
                "DJ4" -> room.dj4
                "DJ5" -> room.dj5
                "DJ6" -> room.dj6
                else -> null
            }
            val status = if (value == "0") RoomStatus.FREE else RoomStatus.OCCUPIED
            EmptyRoomItem(
                name = room.roomName.orEmpty(),
                nameEn = room.roomNameEn,
                status = status
            )
        }
    }
}
