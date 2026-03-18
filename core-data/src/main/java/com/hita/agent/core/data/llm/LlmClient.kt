package com.hita.agent.core.data.llm

import com.hita.agent.core.domain.agent.PlannerConfig
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class LlmClient(
    private val config: PlannerConfig,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun isAvailable(): Boolean {
        return config.isLlmConfigured() && !resolveApiKey().isNullOrBlank()
    }

    fun completeStream(system: String, user: String): String? {
        if (!isAvailable()) return null
        val apiKey = resolveApiKey() ?: return null
        val url = config.llmBaseUrl?.toHttpUrl()?.newBuilder()
            ?.addPathSegments("chat/completions")
            ?.build()
            ?: return null

        val bodyObject = buildJsonObject {
            put("model", JsonPrimitive(config.llmModel))
            put("temperature", JsonPrimitive(0))
            put("stream", JsonPrimitive(true))
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("system"))
                            put("content", JsonPrimitive(system))
                        }
                    )
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", JsonPrimitive(user))
                        }
                    )
                }
            )
        }
        val bodyJson = json.encodeToString(JsonObject.serializer(), bodyObject)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val source = response.body?.source() ?: return null
            val builder = StringBuilder()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                val delta = extractDeltaContent(payload)
                if (!delta.isNullOrBlank()) builder.append(delta)
            }
            builder.toString().takeIf { it.isNotBlank() }
        }
    }

    private fun resolveApiKey(): String? {
        val envKey = config.llmApiKeyEnv?.trim().orEmpty()
        if (envKey.isBlank()) return null
        return System.getenv(envKey)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private fun extractDeltaContent(payload: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(payload).jsonObject
            val choices = root["choices"]?.jsonArray ?: JsonArray(emptyList())
            val first = choices.firstOrNull()?.jsonObject ?: JsonObject(emptyMap())
            val delta = first["delta"]?.jsonObject
            delta?.get("content")?.jsonPrimitive?.content
        }.getOrNull()
    }
}
