package com.hita.agent.core.data.store

import java.io.File
import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonElement

class FileCacheStore(
    private val baseDir: File
) {
    private val json: Json = Json { ignoreUnknownKeys = true }
    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    @Serializable
    data class CacheEnvelope(
        val cachedAtEpochMs: Long,
        val expiresAtEpochMs: Long,
        val data: JsonElement
    )

    data class CacheResult<T>(
        val data: T,
        val cachedAtEpochMs: Long,
        val expiresAtEpochMs: Long
    )

    fun <T> save(
        key: String,
        data: T,
        ttlSeconds: Long,
        serializer: KSerializer<T>,
        now: Instant = Instant.now()
    ): CacheResult<T> {
        val cachedAt = now.toEpochMilli()
        val expiresAt = now.plusSeconds(ttlSeconds).toEpochMilli()
        val element = json.encodeToJsonElement(serializer, data)
        val envelope = CacheEnvelope(cachedAt, expiresAt, element)
        val file = fileForKey(key)
        file.writeText(json.encodeToString(CacheEnvelope.serializer(), envelope))
        return CacheResult(data, cachedAt, expiresAt)
    }

    fun <T> load(
        key: String,
        serializer: KSerializer<T>
    ): CacheResult<T>? {
        val file = fileForKey(key)
        if (!file.exists()) return null
        val envelope = json.decodeFromString(CacheEnvelope.serializer(), file.readText())
        val data = json.decodeFromJsonElement(serializer, envelope.data)
        return CacheResult(data, envelope.cachedAtEpochMs, envelope.expiresAtEpochMs)
    }

    fun isExpired(expiresAtEpochMs: Long, now: Instant = Instant.now()): Boolean {
        return now.toEpochMilli() > expiresAtEpochMs
    }

    private fun fileForKey(key: String): File {
        val safe = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(baseDir, "$safe.json")
    }
}
