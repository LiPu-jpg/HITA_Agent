package com.hita.agent.core.data.eas.shenzhen.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeeklyMatrixResponse(
    val code: Int,
    val msg: String? = null,
    @SerialName("msg_en") val msgEn: String? = null,
    val content: List<WeeklyMatrixBlockDto> = emptyList()
)

@Serializable
data class WeeklyMatrixBlockDto(
    val rqList: List<RqItemDto> = emptyList(),
    val jcList: List<JcItemDto> = emptyList(),
    val kcxxList: List<KcxxItemDto> = emptyList()
)

@Serializable
data class RqItemDto(
    @SerialName("XQMC") val weekdayName: String? = null,
    @SerialName("XQMC_EN") val weekdayNameEn: String? = null,
    @SerialName("XQDM") val weekday: Int? = null,
    @SerialName("RQ") val date: String? = null
)

@Serializable
data class JcItemDto(
    @SerialName("JSJC") val endPeriod: Int? = null,
    @SerialName("KSJC") val startPeriod: Int? = null,
    @SerialName("DJ") val bigSection: Int? = null,
    @SerialName("SJ") val timeRange: String? = null
)

@Serializable
data class KcxxItemDto(
    @SerialName("XQJ") val weekday: Int? = null,
    @SerialName("KCDM") val courseCode: String? = null,
    @SerialName("KCMC") val courseName: String? = null,
    @SerialName("KBXX") val rawInfo: String? = null,
    @SerialName("KBXX_EN") val rawInfoEn: String? = null,
    @SerialName("DJ") val bigSection: Int? = null,
    @SerialName("XB") val length: Int? = null,
    @SerialName("SYLB") val useType: String? = null
)
