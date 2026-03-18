package com.hita.agent.core.data.agent

import com.hita.agent.core.data.DebugLog
import com.hita.agent.core.domain.agent.AgentBackendConfig
import com.hita.agent.core.domain.agent.ApiError
import com.hita.agent.core.domain.agent.InvokeRequest
import com.hita.agent.core.domain.agent.InvokeResponse
import com.hita.agent.core.domain.agent.JobResponse
import com.hita.agent.core.domain.agent.RagQueryOutput
import com.hita.agent.core.domain.agent.SearchOutput
import com.hita.agent.core.domain.agent.SkillsResponse
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class AgentBackendApi(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient,
    val json: Json,
    private val defaultCampusId: String?
) {
    fun listSkills(): SkillsResponse {
        val url = baseUrl.newBuilder()
            .addPathSegments("v1/skills")
            .build()
        val request = Request.Builder().url(url).get().build()
        val start = System.currentTimeMillis()
        DebugLog.d("AgentBackendApi", "listSkills request")
        val response = client.newCall(request).execute()
        DebugLog.d(
            "AgentBackendApi",
            "listSkills status=${response.code} ms=${System.currentTimeMillis() - start}"
        )
        return decodeBody(response) { body ->
            json.decodeFromString(SkillsResponse.serializer(), body)
        }
    }

    fun invokeSkill(name: String, input: JsonElement, trace: JsonElement? = null): InvokeResponse {
        val enrichedInput = injectCampusId(input)
        val url = baseUrl.newBuilder()
            .addPathSegments("v1/skills")
            .addEncodedPathSegment("${name}:invoke")
            .build()
        val payload = InvokeRequest(input = enrichedInput, trace = trace)
        val body = json.encodeToString(InvokeRequest.serializer(), payload)
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val start = System.currentTimeMillis()
        DebugLog.d("AgentBackendApi", "invokeSkill name=$name body_len=${body.length}")
        val response = client.newCall(request).execute()
        DebugLog.d(
            "AgentBackendApi",
            "invokeSkill name=$name status=${response.code} ms=${System.currentTimeMillis() - start}"
        )
        return decodeBody(response) { responseBody ->
            json.decodeFromString(InvokeResponse.serializer(), responseBody)
        }
    }

    fun getJob(jobId: String): JobResponse {
        val url = baseUrl.newBuilder()
            .addPathSegments("v1/jobs")
            .addPathSegment(jobId)
            .build()
        val request = Request.Builder().url(url).get().build()
        val start = System.currentTimeMillis()
        DebugLog.d("AgentBackendApi", "getJob id=$jobId")
        val response = client.newCall(request).execute()
        DebugLog.d(
            "AgentBackendApi",
            "getJob id=$jobId status=${response.code} ms=${System.currentTimeMillis() - start}"
        )
        if (response.code == 404) {
            val error = parseErrorOrDefault(response, "JOB_NOT_FOUND", "Job not found")
            return JobResponse(ok = false, error = error)
        }
        return decodeBody(response) { responseBody ->
            json.decodeFromString(JobResponse.serializer(), responseBody)
        }
    }

    fun decodeRagQueryOutput(output: JsonElement): RagQueryOutput {
        return json.decodeFromJsonElement(RagQueryOutput.serializer(), output)
    }

    fun decodeSearchOutput(output: JsonElement): SearchOutput {
        return json.decodeFromJsonElement(SearchOutput.serializer(), output)
    }

    private fun parseErrorOrDefault(response: Response, code: String, message: String): ApiError {
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) {
            return ApiError(code = code, message = message, retryable = false)
        }
        return runCatching {
            val element = json.parseToJsonElement(body).jsonObject
            val error = element["error"]
            if (error is JsonObject) {
                json.decodeFromJsonElement(ApiError.serializer(), error)
            } else {
                ApiError(code = code, message = message, retryable = false)
            }
        }.getOrElse {
            ApiError(code = code, message = message, retryable = false)
        }
    }

    private fun injectCampusId(input: JsonElement): JsonElement {
        val campusId = defaultCampusId?.trim().orEmpty()
        if (campusId.isBlank()) return input
        val obj = input as? JsonObject ?: return input
        val existing = obj["campus_id"]
        if (existing != null && existing !is JsonNull) return input
        return JsonObject(obj + ("campus_id" to JsonPrimitive(campusId)))
    }

    private fun <T> decodeBody(response: Response, parser: (String) -> T): T {
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) {
            throw IllegalStateException("Empty response body")
        }
        return parser(body)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun create(config: AgentBackendConfig): AgentBackendApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
            val json = Json { ignoreUnknownKeys = true }
            return AgentBackendApi(
                baseUrl = config.baseUrl.toHttpUrl(),
                client = client,
                json = json,
                defaultCampusId = config.defaultCampusId
            )
        }
    }
}
