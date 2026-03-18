# Local RAG (On-Device) Design

Date: 2026-03-14
Owner: Client (HITA_Angent)
Status: Draft (approved verbally)

## 1. Goal
Implement a **local, on-device RAG pipeline** for files the user selects in Workbench:
- **Index** selected files locally.
- **Persist** index across sessions.
- **Retrieve** relevant chunks on query.
- **Read on demand** (local parsing only, no upload).

This enables privacy-preserving document Q&A without sending content to server.

## 2. Scope
**In scope**
- Workbench file picker → local index
- Local parsing for `txt/md/pdf/doc/docx/ppt/pptx`
- Local full-text retrieval with ranking
- Persisted index in app storage
- AgenticLoop action `local_rag` (preferred before `rag.query`)

**Out of scope**
- Server upload / COS integration
- Cloud embedding / vector search
- Automatic folder crawling (only user-selected files)
- OCR for images/scans (future)

## 3. Architecture (High Level)

```
Workbench
  └─ OpenDocument picker
       └─ LocalRagRepository.indexFile(uri)
            ├─ ContentResolver stream
            ├─ Parser (txt/md/pdf/doc/docx/ppt/pptx)
            ├─ Chunker (fixed size)
            └─ Room + FTS5 persist

AgenticLoop
  └─ local_rag(query)
       └─ LocalRagRepository.search(query)
            └─ FTS5 match + bm25 rank → top-k chunks
```

## 4. Data Model

### Domain models (core-domain)
- `IndexedFile`
  - `id: String`
  - `uri: String`
  - `display_name: String?`
  - `mime_type: String?`
  - `size_bytes: Long?`
  - `last_modified: Long?` (epoch ms)
  - `added_at: Long` (epoch ms)
  - `status: IndexedStatus` (`INDEXED|FAILED|PARTIAL`)

- `DocChunk`
  - `chunk_id: String`
  - `file_id: String`
  - `content: String`
  - `start_char: Int`
  - `end_char: Int`

- `LocalRagHit`
  - `file_id: String`
  - `chunk_id: String`
  - `snippet: String`
  - `score: Double`
  - `display_name: String?`
  - `uri: String`

### Storage models (core-data / Room)
- `IndexedFileEntity`
- `DocChunkEntity`
- `DocChunkFtsEntity` (FTS5 table indexing `content`)

## 5. Storage & Persistence
- Use **Room + FTS5**.
- Database lives in `context.filesDir` (persisted).
- Keep file metadata in normal tables; chunk content in FTS for search.

FTS schema:
- `DocChunkFtsEntity` with `content`, `chunk_id`, `file_id`.
- Query with `MATCH` + `bm25()` for ranking.

## 6. Indexing Pipeline

1. **User selects file** via `OpenDocument`.
2. Persist URI permission (takePersistableUriPermission).
3. Read metadata via `ContentResolver`:
   - `display_name`, `mime_type`, `size`, `last_modified`.
4. Parse content (best-effort):
   - `txt/md`: direct stream decode
   - `pdf`: pdfbox-android
   - `doc/docx/ppt/pptx`: Apache POI (text extraction)
5. Chunk content:
   - Fixed chunk size (e.g., 800–1200 chars)
   - Overlap optional (default: no overlap)
6. Insert into Room:
   - `IndexedFileEntity`
   - `DocChunkEntity`
   - `DocChunkFtsEntity`
7. Update status:
   - `INDEXED` on success
   - `PARTIAL` if parse returns empty but metadata stored
   - `FAILED` if exception occurs

## 7. Retrieval Pipeline

1. Query input from Workbench / AgenticLoop.
2. `LocalRagRepository.search(query, topK)`
3. SQL:
   - `SELECT ... FROM doc_chunk_fts WHERE doc_chunk_fts MATCH :query ORDER BY bm25(doc_chunk_fts) LIMIT :k`
4. Join with `IndexedFileEntity` to include `display_name`, `uri`.
5. Return `LocalRagHit[]`.

## 8. AgenticLoop Integration
- Add `local_rag` action.
- Planner priority:
  1. local scores / timetable / empty rooms
  2. **local_rag**
  3. public `rag.query`
- For `local_rag`, return a short summary:
  - Top 3 hits with filenames + snippets.
- Never upload local content.

## 9. Privacy & Security
- All content stays on-device.
- Only URI + metadata stored in local DB.
- No trace/logging of file contents.
- P0 data never leaves device.

## 10. Error Handling & UX
- If parsing fails: still keep metadata, mark `FAILED`.
- Search on empty index → “No local documents indexed”.
- If URI permission revoked → mark status `FAILED` and prompt re-select.

## 11. Dependencies
- Room + FTS5
- pdfbox-android
- Apache POI (for doc/docx/ppt/pptx)

Notes:
- POI may increase APK size; accept for v1.0.
- If POI fails on large PPT, mark `PARTIAL`.

## 12. Testing
- Unit tests:
  - Chunker boundaries
  - FTS query returns results
  - Parser fallback when unsupported
- Instrumentation tests:
  - Index a local fixture file
  - Search returns expected hit

## 13. Rollout Plan
- Phase 1: index + search for txt/md/pdf
- Phase 2: enable doc/docx/ppt/pptx parsers
- Phase 3: UI enhancements (file manager, reindex)

---
**Approved verbally by user. Ready for implementation plan.**
