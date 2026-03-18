package com.hita.agent.core.domain.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class AgentBackendConfig(
    @SerialName("base_url") val baseUrl: String,
    @SerialName("connect_timeout_ms") val connectTimeoutMs: Long = 8000,
    @SerialName("read_timeout_ms") val readTimeoutMs: Long = 15000,
    @SerialName("default_campus_id") val defaultCampusId: String? = null,
    @SerialName("planner_mode") val plannerMode: String = "rule",
    @SerialName("llm_base_url") val llmBaseUrl: String? = null,
    @SerialName("llm_model") val llmModel: String? = null,
    @SerialName("llm_api_key_env") val llmApiKeyEnv: String? = null
)

@Serializable
data class SkillInfo(
    val name: String,
    @SerialName("is_async") val isAsync: Boolean,
    val description: String? = null,
    @SerialName("input_schema") val inputSchema: JsonObject? = null,
    @SerialName("output_schema") val outputSchema: JsonObject? = null
)

@Serializable
data class SkillsResponse(
    val skills: List<SkillInfo> = emptyList()
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val retryable: Boolean? = null
)

@Serializable
data class InvokeRequest(
    val input: JsonElement,
    val trace: JsonElement? = null
)

@Serializable
data class InvokeResponse(
    val ok: Boolean,
    val output: JsonElement? = null,
    @SerialName("job_id") val jobId: String? = null,
    val error: ApiError? = null
)

@Serializable
data class JobResponse(
    val ok: Boolean,
    val job: JobInfo? = null,
    val error: ApiError? = null
)

@Serializable
data class JobInfo(
    val id: String,
    @SerialName("status") val status: JobStatus,
    @SerialName("skill_name") val skillName: String,
    @SerialName("input_json") val inputJson: JsonElement? = null,
    @SerialName("output_json") val outputJson: JsonElement? = null,
    val error: ApiError? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
enum class JobStatus {
    @SerialName("queued") QUEUED,
    @SerialName("running") RUNNING,
    @SerialName("succeeded") SUCCEEDED,
    @SerialName("failed") FAILED
}

@Serializable
data class RagQueryInput(
    val query: String,
    @SerialName("top_k") val topK: Int = 10,
    val filters: JsonObject? = null,
    @SerialName("campus_id") val campusId: String? = null
)

@Serializable
data class RagQueryOutput(
    val hits: List<RagQueryHit> = emptyList()
)

@Serializable
data class RagQueryHit(
    @SerialName("doc_id") val docId: String,
    @SerialName("chunk_id") val chunkId: String? = null,
    val title: String? = null,
    val url: String? = null,
    val snippet: String? = null,
    val score: Double? = null,
    val source: String? = null
)

@Serializable
data class SearchOutput(
    val query: String,
    val total: Int,
    val results: List<SearchResult> = emptyList(),
    val bySource: Map<String, Int> = emptyMap(),
    @SerialName("by_source_type") val bySourceType: Map<String, Int> = emptyMap(),
    val summary: String? = null,
    @SerialName("key_points") val keyPoints: List<String> = emptyList()
)

@Serializable
data class SearchResult(
    val source: String,
    @SerialName("source_type") val sourceType: String? = null,
    val title: String? = null,
    val content: String? = null,
    val url: String? = null,
    val score: Double? = null
)
