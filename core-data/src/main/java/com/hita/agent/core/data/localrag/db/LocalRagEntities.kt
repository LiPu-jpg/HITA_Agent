package com.hita.agent.core.data.localrag.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "indexed_files")
data class IndexedFileEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModified: Long?,
    val addedAt: Long,
    val status: String
)

@Entity(tableName = "doc_chunks")
data class DocChunkEntity(
    @PrimaryKey val chunkId: String,
    val fileId: String,
    val startChar: Int,
    val endChar: Int
)

@Fts4
@Entity(tableName = "doc_chunk_fts")
data class DocChunkFtsEntity(
    val chunkId: String,
    val fileId: String,
    val content: String
)

data class LocalRagHitRow(
    val fileId: String,
    val chunkId: String,
    val content: String,
    val displayName: String?,
    val uri: String,
    val score: Double
)
