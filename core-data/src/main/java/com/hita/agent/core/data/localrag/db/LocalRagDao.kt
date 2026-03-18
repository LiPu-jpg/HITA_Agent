package com.hita.agent.core.data.localrag.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalRagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFile(entity: IndexedFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChunks(chunks: List<DocChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFts(chunks: List<DocChunkFtsEntity>)

    @Query(
        """
        SELECT doc_chunk_fts.fileId AS fileId,
               doc_chunk_fts.chunkId AS chunkId,
               doc_chunk_fts.content AS content,
               indexed_files.displayName AS displayName,
               indexed_files.uri AS uri,
               0.0 AS score
        FROM doc_chunk_fts
        JOIN indexed_files ON indexed_files.id = doc_chunk_fts.fileId
        WHERE doc_chunk_fts MATCH :query
        LIMIT :limit
        """
    )
    fun search(query: String, limit: Int): List<LocalRagHitRow>
}
