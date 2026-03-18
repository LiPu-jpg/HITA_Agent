package com.hita.agent.core.data.store

import com.hita.agent.core.domain.model.CampusId
import com.hita.agent.core.domain.model.CampusSession
import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileSessionStore(
    private val baseDir: File
) {
    private val json: Json = Json { ignoreUnknownKeys = true }
    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    @Serializable
    data class PersistedSession(
        val campusId: CampusId,
        val bearerToken: String?,
        val cookiesByHost: Map<String, String>,
        val createdAtEpochMs: Long,
        val expiresAtEpochMs: Long?
    )

    fun save(session: CampusSession) {
        val persisted = PersistedSession(
            campusId = session.campusId,
            bearerToken = session.bearerToken,
            cookiesByHost = session.cookiesByHost,
            createdAtEpochMs = session.createdAt.toEpochMilli(),
            expiresAtEpochMs = session.expiresAt?.toEpochMilli()
        )
        fileForCampus(session.campusId).writeText(json.encodeToString(persisted))
    }

    fun load(campusId: CampusId): CampusSession? {
        val file = fileForCampus(campusId)
        if (!file.exists()) return null
        val persisted = json.decodeFromString(PersistedSession.serializer(), file.readText())
        return CampusSession(
            campusId = persisted.campusId,
            bearerToken = persisted.bearerToken,
            cookiesByHost = persisted.cookiesByHost,
            createdAt = Instant.ofEpochMilli(persisted.createdAtEpochMs),
            expiresAt = persisted.expiresAtEpochMs?.let { Instant.ofEpochMilli(it) }
        )
    }

    fun clear(campusId: CampusId) {
        fileForCampus(campusId).delete()
    }

    private fun fileForCampus(campusId: CampusId): File {
        return File(baseDir, "session_${campusId.name.lowercase()}.json")
    }
}
