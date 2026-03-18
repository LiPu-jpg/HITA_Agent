package com.hita.agent.core.data.eas.shenzhen.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TermListResponse(
    val code: Int,
    val msg: String? = null,
    @SerialName("msg_en") val msgEn: String? = null,
    val content: List<TermItemDto> = emptyList()
)

@Serializable
data class TermItemDto(
    @SerialName("SFDQXQ") val isCurrent: String? = null,
    @SerialName("XN") val year: String? = null,
    @SerialName("XQ") val term: String? = null,
    @SerialName("XNXQ") val yearTermCode: String? = null,
    @SerialName("XNXQMC") val nameCn: String? = null,
    @SerialName("XNXQMC_EN") val nameEn: String? = null
)

@Serializable
data class WeekListResponse(
    val code: Int,
    val msg: String? = null,
    @SerialName("msg_en") val msgEn: String? = null,
    val content: List<WeekItemDto> = emptyList()
)

@Serializable
data class WeekItemDto(
    @SerialName("ZCMC") val name: String? = null,
    @SerialName("ZC") val week: Int? = null
)
