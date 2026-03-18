package com.hita.agent.core.domain.localrag

enum class IndexedStatus {
    INDEXED,
    FAILED,
    PARTIAL
}

data class IndexedFile(
    val id: String,
    val uri: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModified: Long?,
    val addedAt: Long,
    val status: IndexedStatus
)

data class DocChunk(
    val chunkId: String,
    val fileId: String,
    val content: String,
    val startChar: Int,
    val endChar: Int
)

data class LocalRagHit(
    val fileId: String,
    val chunkId: String,
    val snippet: String,
    val score: Double,
    val displayName: String?,
    val uri: String
)
