# Local RAG (On-Device) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a local on-device RAG pipeline that indexes user-selected files, persists across sessions, and serves local search results in the Workbench agent loop.

**Architecture:** Room + FTS (FTS4 via Room) for local full-text search; content parsed on-device (txt/md/pdf/doc/docx/ppt/pptx), chunked, and stored locally. AgenticLoop adds a `local_rag` action before public `rag.query`.

**Tech Stack:** Android (Compose), Kotlin, Room + FTS, pdfbox-android, Apache POI (doc/docx/ppt/pptx).

---

### Task 1: Add dependencies (Room + parsers + testing)

**Files:**
- Modify: `build.gradle.kts`
- Modify: `core-data/build.gradle.kts`

**Step 1: Add KSP plugin and Room version to root build**

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
```

**Step 2: Add Room + parser deps to core-data**

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml-lite:5.2.5")

    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.12.2")
}
```

**Step 3: Run a build to verify deps resolve**

Run: `./gradlew :core-data:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add build.gradle.kts core-data/build.gradle.kts
git commit -m "build(core-data): add room and local rag parser deps"
```

---

### Task 2: Add local RAG domain models & planner action

**Files:**
- Create: `core-domain/src/main/java/com/hita/agent/core/domain/localrag/LocalRagModels.kt`
- Modify: `core-domain/src/main/java/com/hita/agent/core/domain/agent/PlannerDecision.kt`
- Test: `core-domain/src/test/java/com/hita/agent/core/domain/localrag/LocalRagModelsTest.kt`

**Step 1: Write failing test**

```kotlin
class LocalRagModelsTest {
    @Test
    fun localRagHit_hasRequiredFields() {
        val hit = LocalRagHit(
            fileId = "f1",
            chunkId = "c1",
            snippet = "hello",
            score = 1.0,
            displayName = "doc.txt",
            uri = "content://doc"
        )
        assertEquals("f1", hit.fileId)
        assertEquals("c1", hit.chunkId)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :core-domain:testDebugUnitTest`
Expected: FAIL (class not found)

**Step 3: Add models + planner action**

```kotlin
package com.hita.agent.core.domain.localrag

enum class IndexedStatus { INDEXED, FAILED, PARTIAL }

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
```

And update `PlannerAction` enum:

```kotlin
enum class PlannerAction {
    LOCAL_SCORES,
    LOCAL_TIMETABLE,
    LOCAL_EMPTY_ROOMS,
    LOCAL_RAG,
    PUBLIC_RAG
}
```

**Step 4: Run tests**

Run: `./gradlew :core-domain:testDebugUnitTest`
Expected: PASS

**Step 5: Commit**

```bash
git add core-domain/src/main/java/com/hita/agent/core/domain/localrag/LocalRagModels.kt \
  core-domain/src/main/java/com/hita/agent/core/domain/agent/PlannerDecision.kt \
  core-domain/src/test/java/com/hita/agent/core/domain/localrag/LocalRagModelsTest.kt
git commit -m "feat(core-domain): add local rag models and planner action"
```

---

### Task 3: Implement chunker + parser interfaces

**Files:**
- Create: `core-data/src/main/java/com/hita/agent/core/data/localrag/Chunker.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/localrag/DocumentParser.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/localrag/ParserRegistry.kt`
- Test: `core-data/src/test/java/com/hita/agent/core/data/localrag/ChunkerTest.kt`

**Step 1: Write failing test (chunker)**

```kotlin
class ChunkerTest {
    @Test
    fun chunker_splitsTextIntoFixedSizes() {
        val text = "a".repeat(2500)
        val chunks = Chunker(chunkSize = 1000).chunk(text)
        assertEquals(3, chunks.size)
        assertEquals(0, chunks[0].start)
        assertEquals(1000, chunks[0].end)
    }
}
```

**Step 2: Run test (expect fail)**

Run: `./gradlew :core-data:testDebugUnitTest`
Expected: FAIL (class not found)

**Step 3: Add implementation**

```kotlin
data class TextChunk(val content: String, val start: Int, val end: Int)

class Chunker(private val chunkSize: Int = 1000) {
    fun chunk(text: String): List<TextChunk> {
        if (text.isBlank()) return emptyList()
        val chunks = mutableListOf<TextChunk>()
        var start = 0
        while (start < text.length) {
            val end = minOf(text.length, start + chunkSize)
            val slice = text.substring(start, end)
            chunks.add(TextChunk(slice, start, end))
            start = end
        }
        return chunks
    }
}
```

And a parser interface:

```kotlin
interface DocumentParser {
    fun canHandle(mimeType: String?, name: String?): Boolean
    fun parse(input: InputStream): String
}
```

**Step 4: Run tests**

Run: `./gradlew :core-data:testDebugUnitTest`
Expected: PASS

**Step 5: Commit**

```bash
git add core-data/src/main/java/com/hita/agent/core/data/localrag/Chunker.kt \
  core-data/src/main/java/com/hita/agent/core/data/localrag/DocumentParser.kt \
  core-data/src/main/java/com/hita/agent/core/data/localrag/ParserRegistry.kt \
  core-data/src/test/java/com/hita/agent/core/data/localrag/ChunkerTest.kt
git commit -m "feat(core-data): add local rag chunker and parser interfaces"
```

---

### Task 4: Add Room database (FTS) for local RAG

**Files:**
- Create: `core-data/src/main/java/com/hita/agent/core/data/localrag/db/LocalRagEntities.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/localrag/db/LocalRagDao.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/localrag/db/LocalRagDatabase.kt`
- Test: `core-data/src/test/java/com/hita/agent/core/data/localrag/LocalRagDaoTest.kt`

**Step 1: Write failing DAO test (Robolectric)**

```kotlin
@RunWith(RobolectricTestRunner::class)
class LocalRagDaoTest {
    private lateinit var db: LocalRagDatabase
    private lateinit var dao: LocalRagDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LocalRagDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun insertAndSearch_returnsHits() {
        dao.insertFile(IndexedFileEntity("f1", "content://doc", "doc.txt", "text/plain", 10, null, 0L, "INDEXED"))
        dao.insertChunks(listOf(DocChunkEntity("c1", "f1", 0, 5)))
        dao.insertFts(listOf(DocChunkFtsEntity("c1", "f1", "hello world")))

        val hits = dao.search("hello", 5)
        assertTrue(hits.isNotEmpty())
    }
}
```

**Step 2: Run test (expect fail)**

Run: `./gradlew :core-data:testDebugUnitTest`
Expected: FAIL (entities/dao missing)

**Step 3: Implement entities/dao/db**

- `IndexedFileEntity`
- `DocChunkEntity`
- `DocChunkFtsEntity` (Room `@Fts4`)
- `LocalRagDao.search(query, limit)` returning joined hits

**Step 4: Run tests**

Run: `./gradlew :core-data:testDebugUnitTest`
Expected: PASS

**Step 5: Commit**

```bash
git add core-data/src/main/java/com/hita/agent/core/data/localrag/db \
  core-data/src/test/java/com/hita/agent/core/data/localrag/LocalRagDaoTest.kt
git commit -m "feat(core-data): add local rag room database"
```

---

### Task 5: Implement LocalRagRepository (index + search)

**Files:**
- Create: `core-data/src/main/java/com/hita/agent/core/data/localrag/LocalRagRepository.kt`
- Modify: `core-data/src/main/java/com/hita/agent/core/data/localrag/ParserRegistry.kt`
- Test: `core-data/src/test/java/com/hita/agent/core/data/localrag/LocalRagRepositoryTest.kt`

**Step 1: Write failing test**

```kotlin
@RunWith(RobolectricTestRunner::class)
class LocalRagRepositoryTest {
    @Test
    fun indexAndSearch_returnsHit() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LocalRagDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = LocalRagRepository(db, context.contentResolver, FakeParser("hello world"))
        val uri = Uri.parse("content://fake")

        repo.indexFile(uri, "doc.txt", "text/plain", 10, null)
        val hits = repo.search("hello", 3)
        assertTrue(hits.isNotEmpty())
    }
}
```

**Step 2: Run test (expect fail)**

Run: `./gradlew :core-data:testDebugUnitTest`
Expected: FAIL

**Step 3: Implement repository**

- `indexFile(uri)`
  - take persistable read permission
  - read metadata
  - parse via registry
  - chunk + insert entities
- `search(query, topK)`
  - DAO search
  - map to `LocalRagHit`

**Step 4: Run tests**

Run: `./gradlew :core-data:testDebugUnitTest`
Expected: PASS

**Step 5: Commit**

```bash
git add core-data/src/main/java/com/hita/agent/core/data/localrag/LocalRagRepository.kt \
  core-data/src/main/java/com/hita/agent/core/data/localrag/ParserRegistry.kt \
  core-data/src/test/java/com/hita/agent/core/data/localrag/LocalRagRepositoryTest.kt
git commit -m "feat(core-data): add local rag repository"
```

---

### Task 6: Wire AppContainer with LocalRagRepository

**Files:**
- Modify: `app/src/main/java/com/hita/agent/AppContainer.kt`

**Step 1: Add LocalRagDatabase + LocalRagRepository**

```kotlin
val localRagDb = LocalRagDatabase.build(context)
val localRagRepository = LocalRagRepository(localRagDb, context.contentResolver)
```

**Step 2: Run build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/hita/agent/AppContainer.kt
git commit -m "feat(app): wire local rag repository"
```

---

### Task 7: Workbench file picker → index + message

**Files:**
- Modify: `agent/workbench/src/main/java/com/hita/agent/workbench/WorkbenchViewModel.kt`
- Modify: `agent/workbench/src/main/java/com/hita/agent/workbench/WorkbenchScreen.kt`
- Modify: `app/src/main/java/com/hita/agent/ui/workbench/WorkbenchRoute.kt`
- Test: `agent/workbench/src/test/java/com/hita/agent/workbench/WorkbenchViewModelTest.kt`

**Step 1: Add test for indexing message**

```kotlin
@Test
fun onUploadSelected_indexesFileAndReports() = runTest {
    val repo = FakeLocalRagRepository()
    val vm = WorkbenchViewModel(api, easRepo, plannerConfig, repo)
    vm.onUploadSelected("content://doc")
    assertTrue(vm.state.value.messages.last().content.contains("Indexed"))
}
```

**Step 2: Run test (expect fail)**

Run: `./gradlew :agent:workbench:testDebugUnitTest`
Expected: FAIL

**Step 3: Implement**
- Change `WorkbenchScreen` to use `OpenDocument()` with MIME types:
  - `application/pdf`, `text/plain`, `text/markdown`,
  - `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`,
  - `application/vnd.ms-powerpoint`, `application/vnd.openxmlformats-officedocument.presentationml.presentation`.
- Update `WorkbenchViewModel` to call `localRagRepository.indexFile(...)` and append a message with `display_name`, `mime_type`, `size`.
- Inject repository from `AppContainer` in `WorkbenchRoute`.

**Step 4: Run tests**

Run: `./gradlew :agent:workbench:testDebugUnitTest`
Expected: PASS

**Step 5: Commit**

```bash
git add agent/workbench/src/main/java/com/hita/agent/workbench/WorkbenchViewModel.kt \
  agent/workbench/src/main/java/com/hita/agent/workbench/WorkbenchScreen.kt \
  app/src/main/java/com/hita/agent/ui/workbench/WorkbenchRoute.kt \
  agent/workbench/src/test/java/com/hita/agent/workbench/WorkbenchViewModelTest.kt
git commit -m "feat(workbench): index local files from picker"
```

---

### Task 8: AgenticLoop local_rag action

**Files:**
- Modify: `agent/workbench/src/main/java/com/hita/agent/workbench/AgenticLoop.kt`
- Test: `agent/workbench/src/test/java/com/hita/agent/workbench/AgenticLoopTest.kt`

**Step 1: Write failing test**

```kotlin
@Test
fun rulePlanner_routesToLocalRag() {
    val plan = AgenticLoop.RulePlanner().plan("帮我找这份文档", api)
    assertTrue(plan.steps.first() is AgentStep.LocalRag)
}
```

**Step 2: Run test (expect fail)**

Run: `./gradlew :agent:workbench:testDebugUnitTest`
Expected: FAIL

**Step 3: Implement**
- Add `LocalRag` step.
- In RulePlanner, if query includes `文件/文档/资料/笔记` → local_rag.
- In LLM planner, add `local_rag` action.
- Implement `runLocalRag` to call repository search and format results.

**Step 4: Run tests**

Run: `./gradlew :agent:workbench:testDebugUnitTest`
Expected: PASS

**Step 5: Commit**

```bash
git add agent/workbench/src/main/java/com/hita/agent/workbench/AgenticLoop.kt \
  agent/workbench/src/test/java/com/hita/agent/workbench/AgenticLoopTest.kt
git commit -m "feat(agent): add local rag action"
```

---

### Task 9: Update progress log

**Files:**
- Modify: `docs/plans/2026-03-13-hita-agent-refactor-progress.md`

**Step 1: Add progress entries**
- Local RAG indexed file support
- Parser integrations
- AgenticLoop local_rag

**Step 2: Commit**

```bash
git add docs/plans/2026-03-13-hita-agent-refactor-progress.md
git commit -m "docs: update progress for local rag"
```

---

## Notes / Risks
- Apache POI may increase APK size; accept for v1.
- Room FTS uses `@Fts4` (Room does not expose FTS5); ranking is basic.
- PDF parsing best-effort; if parsing fails, store metadata only.

---

**Plan complete and saved to `docs/plans/2026-03-14-local-rag-implementation-plan.md`. Two execution options:**

**1. Subagent-Driven (this session)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open a new session with executing-plans, batch execution with checkpoints

**Which approach?**
