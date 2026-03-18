package com.hita.agent.core.data.localrag

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.hita.agent.core.data.DebugLog
import com.hita.agent.core.data.localrag.db.DocChunkEntity
import com.hita.agent.core.data.localrag.db.DocChunkFtsEntity
import com.hita.agent.core.data.localrag.db.IndexedFileEntity
import com.hita.agent.core.data.localrag.db.LocalRagDatabase
import com.hita.agent.core.domain.localrag.IndexedFile
import com.hita.agent.core.domain.localrag.IndexedStatus
import com.hita.agent.core.domain.localrag.LocalRagHit
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalRagRepository(
    private val db: LocalRagDatabase,
    private val contentResolver: ContentResolver,
    private val parserRegistry: ParserRegistry = ParserRegistry.default(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val chunker = Chunker(chunkSize = 1000)

    suspend fun indexFile(uri: Uri): IndexedFile = withContext(ioDispatcher) {
        val metadata = readMetadata(uri)
        val fileId = stableId(uri)
        val parser = parserRegistry.resolve(metadata.mimeType, metadata.displayName)
        DebugLog.d(
            "LocalRag",
            "index start name=${metadata.displayName ?: "unknown"} mime=${metadata.mimeType ?: "unknown"}"
        )
        if (parser == null) {
            val entity = buildFileEntity(fileId, uri, metadata, IndexedStatus.PARTIAL)
            db.dao().insertFile(entity)
            DebugLog.d("LocalRag", "index partial (no parser)")
            return@withContext entity.toDomain()
        }
        val parsed = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                parser.parse(input)
            }
        }
        if (parsed.isFailure) {
            val entity = buildFileEntity(fileId, uri, metadata, IndexedStatus.FAILED)
            db.dao().insertFile(entity)
            DebugLog.d("LocalRag", "index failed parse")
            return@withContext entity.toDomain()
        }
        val content = parsed.getOrNull().orEmpty()
        if (content.isBlank()) {
            val entity = buildFileEntity(fileId, uri, metadata, IndexedStatus.PARTIAL)
            db.dao().insertFile(entity)
            DebugLog.d("LocalRag", "index partial (empty content)")
            return@withContext entity.toDomain()
        }
        val chunks = chunker.chunk(content)
        val fileEntity = buildFileEntity(fileId, uri, metadata, IndexedStatus.INDEXED)
        db.dao().insertFile(fileEntity)
        db.dao().insertChunks(
            chunks.map { chunk ->
                DocChunkEntity(
                    chunkId = chunkId(fileId, chunk),
                    fileId = fileId,
                    startChar = chunk.start,
                    endChar = chunk.end
                )
            }
        )
        db.dao().insertFts(
            chunks.map { chunk ->
                DocChunkFtsEntity(
                    chunkId = chunkId(fileId, chunk),
                    fileId = fileId,
                    content = chunk.content
                )
            }
        )
        DebugLog.d("LocalRag", "index ok chunks=${chunks.size}")
        fileEntity.toDomain()
    }

    suspend fun search(query: String, topK: Int): List<LocalRagHit> =
        withContext(ioDispatcher) {
            DebugLog.d("LocalRag", "search len=${query.length} topK=$topK")
            db.dao().search(query, topK).map { row ->
                LocalRagHit(
                    fileId = row.fileId,
                    chunkId = row.chunkId,
                    snippet = row.content.replace("\n", " ").take(160),
                    score = row.score,
                    displayName = row.displayName,
                    uri = row.uri
                )
            }
        }

    private fun chunkId(fileId: String, chunk: TextChunk): String {
        return "$fileId-${chunk.start}"
    }

    private fun buildFileEntity(
        fileId: String,
        uri: Uri,
        metadata: FileMetadata,
        status: IndexedStatus
    ): IndexedFileEntity {
        return IndexedFileEntity(
            id = fileId,
            uri = uri.toString(),
            displayName = metadata.displayName,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            lastModified = metadata.lastModified,
            addedAt = System.currentTimeMillis(),
            status = status.name
        )
    }

    private fun IndexedFileEntity.toDomain(): IndexedFile {
        return IndexedFile(
            id = id,
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            lastModified = lastModified,
            addedAt = addedAt,
            status = IndexedStatus.valueOf(status)
        )
    }

    private fun stableId(uri: Uri): String {
        return UUID.nameUUIDFromBytes(uri.toString().toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private data class FileMetadata(
        val displayName: String?,
        val mimeType: String?,
        val sizeBytes: Long?,
        val lastModified: Long?
    )

    private fun readMetadata(uri: Uri): FileMetadata {
        var displayName: String? = null
        var size: Long? = null
        var lastModified: Long? = null
        val projection = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val cursor = runCatching { contentResolver.query(uri, projection, null, null, null) }.getOrNull()
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) displayName = it.getString(nameIndex)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) size = it.getLong(sizeIndex)
                val modIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (modIndex >= 0) lastModified = it.getLong(modIndex)
            }
        }
        val mimeType = contentResolver.getType(uri)
        return FileMetadata(displayName, mimeType, size, lastModified)
    }
}
