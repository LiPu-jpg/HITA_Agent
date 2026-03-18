package com.hita.agent.core.data.eas.shenzhen.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BuildingListResponse(
    val code: Int,
    val msg: String? = null,
    @SerialName("msg_en") val msgEn: String? = null,
    val content: List<BuildingDto> = emptyList()
)

@Serializable
data class BuildingDto(
    @SerialName("MC") val name: String? = null,
    @SerialName("DM") val code: String? = null,
    @SerialName("MC_EN") val nameEn: String? = null
)

@Serializable
data class EmptyRoomOccupancyResponse(
    val code: Int,
    val msg: String? = null,
    @SerialName("msg_en") val msgEn: String? = null,
    val content: List<RoomOccupancyDto> = emptyList()
)

@Serializable
data class RoomOccupancyDto(
    @SerialName("CDMC") val roomName: String? = null,
    @SerialName("CDMC_EN") val roomNameEn: String? = null,
    @SerialName("DJ1") val dj1: String? = null,
    @SerialName("DJ2") val dj2: String? = null,
    @SerialName("DJ3") val dj3: String? = null,
    @SerialName("DJ4") val dj4: String? = null,
    @SerialName("DJ5") val dj5: String? = null,
    @SerialName("DJ6") val dj6: String? = null
)
