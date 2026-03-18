package com.hita.agent.core.data.eas.shenzhen.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScoreListResponse(
    val code: Int,
    val msg: String? = null,
    @SerialName("msg_en") val msgEn: String? = null,
    val content: List<ScoreItemDto> = emptyList()
)

@Serializable
data class ScoreItemDto(
    val id: String? = null,
    @SerialName("xnxq") val termName: String? = null,
    @SerialName("xnxqdm") val termCode: String? = null,
    @SerialName("kcdm") val courseCode: String? = null,
    @SerialName("kcmc") val courseName: String? = null,
    @SerialName("kcmc_en") val courseNameEn: String? = null,
    @SerialName("xf") val credit: Double? = null,
    @SerialName("zf") val score: String? = null,
    @SerialName("kcxz") val courseProperty: String? = null,
    @SerialName("kclb") val courseCategory: String? = null,
    @SerialName("khfs") val assessMethod: String? = null
)

@Serializable
data class ScoreSummaryResponse(
    val code: Int,
    val msg: String? = null,
    @SerialName("msg_en") val msgEn: String? = null,
    val content: ScoreSummaryContent? = null
)

@Serializable
data class ScoreSummaryContent(
    @SerialName("xfj") val summary: ScoreSummaryDto? = null
)

@Serializable
data class ScoreSummaryDto(
    @SerialName("XFJ") val gpa: String? = null,
    @SerialName("RANK") val rank: String? = null,
    @SerialName("ZYZRS") val total: String? = null
)
