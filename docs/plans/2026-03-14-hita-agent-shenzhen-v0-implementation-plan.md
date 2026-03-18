# HITA_Angent Shenzhen v0 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the Shenzhen v0 core (login/session, timetable, scores, empty rooms) in a modular Compose-first Android app with unified local schemas.

**Architecture:** Multi-module Android app (`app`, `core-domain`, `core-data`, `core-ui`, `agent/workbench`). Shenzhen-specific adapters in `core-data` map API responses to unified domain models. All private data stays on device with cache/TTL rules; UI consumes repositories only.

**Tech Stack:** Kotlin 1.9.x, JDK 17, Android Gradle Plugin 8.x, Jetpack Compose, OkHttp, kotlinx.serialization, Room/SQLite (local cache), DataStore (prefs), JUnit.

---

### Task 1: Scaffold the multi-module Gradle project (JDK17 + Compose)

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Create: `core-domain/build.gradle.kts`
- Create: `core-data/build.gradle.kts`
- Create: `core-ui/build.gradle.kts`
- Create: `agent/workbench/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/hita/agent/MainActivity.kt`

**Step 1: Create Gradle wrapper + module skeleton**
- Add Gradle wrapper files and minimal module build scripts (JDK 17, Compose enabled in `app`).

**Step 2: Build to verify baseline**
- Run: `./gradlew :app:assembleDebug`
- Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/wrapper/gradle-wrapper.properties app core-domain core-data core-ui agent/workbench
git commit -m "chore: scaffold multi-module Compose project"
```

---

### Task 2: Define unified domain models + cache result wrappers

**Files:**
- Create: `core-domain/src/main/java/com/hita/agent/core/domain/model/CampusId.kt`
- Create: `core-domain/src/main/java/com/hita/agent/core/domain/model/CampusSession.kt`
- Create: `core-domain/src/main/java/com/hita/agent/core/domain/model/UnifiedTerm.kt`
- Create: `core-domain/src/main/java/com/hita/agent/core/domain/model/UnifiedCourseItem.kt`
- Create: `core-domain/src/main/java/com/hita/agent/core/domain/model/UnifiedScoreItem.kt`
- Create: `core-domain/src/main/java/com/hita/agent/core/domain/model/EmptyRoomModels.kt`
- Create: `core-domain/src/main/java/com/hita/agent/core/domain/model/ResultWrappers.kt`
- Create: `core-domain/src/test/java/com/hita/agent/core/domain/UnifiedModelsTest.kt`

**Step 1: Write failing tests**
```kotlin
@Test
fun unifiedCourseItem_inclusiveEndPeriod() {
    val item = UnifiedCourseItem(
        courseCode = "AUTO1001",
        courseName = "Test",
        weekday = 1,
        startPeriod = 1,
        endPeriod = 2,
        weeks = listOf(1, 2)
    )
    assertEquals(2, item.endPeriod)
}
```

**Step 2: Run tests to verify failure**
- Run: `./gradlew :core-domain:test`
- Expected: FAIL (missing classes)

**Step 3: Implement models**
- Add data classes matching SSOT-unified schema (see design doc Section 3).

**Step 4: Re-run tests**
- Run: `./gradlew :core-domain:test`
- Expected: PASS

**Step 5: Commit**
```bash
git add core-domain
git commit -m "feat(core-domain): add unified models and result wrappers"
```

---

### Task 3: Add Shenzhen API DTOs + JSON parsing tests

**Files:**
- Create: `core-data/src/main/java/com/hita/agent/core/data/eas/shenzhen/dto/TermDtos.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/eas/shenzhen/dto/TimetableDtos.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/eas/shenzhen/dto/ScoreDtos.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/eas/shenzhen/dto/EmptyRoomDtos.kt`
- Create: `core-data/src/test/java/com/hita/agent/core/data/shenzhen/ShenzhenDtoParsingTest.kt`
- Create: `core-data/src/test/resources/shenzhen/` (sample JSON copied from `api_bundle/深圳API清单.md`)

**Step 1: Write failing parsing tests**
```kotlin
@Test
fun parseEmptyRoomOccupancy() {
    val json = loadJson("shenzhen/empty_room_occupancy.json")
    val dto = json.decodeTo<EmptyRoomOccupancyResponse>()
    assertTrue(dto.content.isNotEmpty())
}
```

**Step 2: Run tests to verify failure**
- Run: `./gradlew :core-data:test`
- Expected: FAIL (DTOs missing)

**Step 3: Implement DTOs + serializers**
- Use `kotlinx.serialization` with `@Serializable`.

**Step 4: Re-run tests**
- Run: `./gradlew :core-data:test`
- Expected: PASS

**Step 5: Commit**
```bash
git add core-data
git commit -m "feat(core-data): add Shenzhen DTOs and parsing tests"
```

---

### Task 4: Shenzhen adapter (login/session + timetable)

**Files:**
- Create: `core-data/src/main/java/com/hita/agent/core/data/eas/CampusEasAdapter.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/eas/shenzhen/EasShenzhenAdapter.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/net/HttpClient.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/net/AuthInterceptors.kt`
- Create: `core-data/src/test/java/com/hita/agent/core/data/shenzhen/ShenzhenTimetableMappingTest.kt`

**Step 1: Write failing tests for timetable mapping**
```kotlin
@Test
fun mapWeeklyMatrixToUnifiedCourseItems() {
    val json = loadJson("shenzhen/weekly_matrix.json")
    val items = ShenzhenTimetableMapper.map(json)
    assertTrue(items.any { it.courseName.isNotBlank() })
}
```

**Step 2: Run tests to verify failure**
- Run: `./gradlew :core-data:test`
- Expected: FAIL (adapter/mapper missing)

**Step 3: Implement adapter + mapper**
- Call `queryxnxqlist`, `queryzclistbyxnxq`, `Kbcx/query`.
- Normalize to `UnifiedCourseItem` (weeks array, inclusive end period).

**Step 4: Re-run tests**
- Run: `./gradlew :core-data:test`
- Expected: PASS

**Step 5: Commit**
```bash
git add core-data
git commit -m "feat(core-data): implement Shenzhen timetable adapter"
```

---

### Task 5: Shenzhen adapter (scores + summary)

**Files:**
- Modify: `core-data/src/main/java/com/hita/agent/core/data/eas/shenzhen/EasShenzhenAdapter.kt`
- Create: `core-data/src/test/java/com/hita/agent/core/data/shenzhen/ShenzhenScoresMappingTest.kt`

**Step 1: Write failing tests for score mapping**
```kotlin
@Test
fun mapScoresToUnifiedScoreItems() {
    val json = loadJson("shenzhen/scores_list.json")
    val items = ShenzhenScoreMapper.map(json)
    assertTrue(items.any { it.scoreText.isNotBlank() })
}
```

**Step 2: Run tests to verify failure**
- Run: `./gradlew :core-data:test`
- Expected: FAIL

**Step 3: Implement score mapping**
- `score_value` nullable; always set `score_text`.
- Parse summary from `/xfj`.

**Step 4: Re-run tests**
- Run: `./gradlew :core-data:test`
- Expected: PASS

**Step 5: Commit**
```bash
git add core-data
git commit -m "feat(core-data): add Shenzhen scores adapter"
```

---

### Task 6: Shenzhen adapter (empty rooms)

**Files:**
- Modify: `core-data/src/main/java/com/hita/agent/core/data/eas/shenzhen/EasShenzhenAdapter.kt`
- Create: `core-data/src/test/java/com/hita/agent/core/data/shenzhen/ShenzhenEmptyRoomsTest.kt`

**Step 1: Write failing tests for empty room filtering**
```kotlin
@Test
fun filterEmptyRoomsByPeriod() {
    val json = loadJson("shenzhen/empty_room_occupancy.json")
    val rooms = ShenzhenEmptyRoomMapper.filter(json, period = "DJ2")
    assertTrue(rooms.all { it.status == "free" || it.status == "occupied" })
}
```

**Step 2: Run tests to verify failure**
- Run: `./gradlew :core-data:test`
- Expected: FAIL

**Step 3: Implement mapping + filtering**
- `DJ1..DJ6` => `free` when value == "0".
- Return `EmptyRoomResult` with cache wrapper.

**Step 4: Re-run tests**
- Run: `./gradlew :core-data:test`
- Expected: PASS

**Step 5: Commit**
```bash
git add core-data
git commit -m "feat(core-data): add Shenzhen empty rooms adapter"
```

---

### Task 7: Local cache + session store

**Files:**
- Create: `core-data/src/main/java/com/hita/agent/core/data/store/CampusSessionStore.kt`
- Create: `core-data/src/main/java/com/hita/agent/core/data/store/CacheStore.kt`
- Create: `core-data/src/test/java/com/hita/agent/core/data/store/CacheStoreTest.kt`

**Step 1: Write failing tests for TTL rules**
```kotlin
@Test
fun cacheExpiresByCachedAt() {
    val cachedAt = Instant.parse("2026-03-14T00:00:00Z")
    val expiresAt = cachedAt.plusSeconds(3600)
    assertTrue(expiresAt.isAfter(cachedAt))
}
```

**Step 2: Run tests to verify failure**
- Run: `./gradlew :core-data:test`
- Expected: FAIL (store missing)

**Step 3: Implement stores**
- Persist sessions encrypted (Keystore). Cache results in Room/SQLite.

**Step 4: Re-run tests**
- Run: `./gradlew :core-data:test`
- Expected: PASS

**Step 5: Commit**
```bash
git add core-data
git commit -m "feat(core-data): add session and cache stores"
```

---

### Task 8: Compose UI wiring (timetable/scores/empty rooms)

**Files:**
- Modify: `app/src/main/java/com/hita/agent/ui/MainNavHost.kt`
- Modify: `app/src/main/java/com/hita/agent/ui/Screens.kt`
- Create: `app/src/main/java/com/hita/agent/ui/timetable/TimetableScreen.kt`
- Create: `app/src/main/java/com/hita/agent/ui/scores/ScoresScreen.kt`
- Create: `app/src/main/java/com/hita/agent/ui/emptyrooms/EmptyRoomsScreen.kt`
- Create: `app/src/test/java/com/hita/agent/ui/TimetableScreenTest.kt`

**Step 1: Write failing Compose UI tests**
```kotlin
@Test
fun timetableScreen_showsEmptyState() {
    composeTestRule.setContent { TimetableScreen(state = TimetableUiState.Empty) }
    composeTestRule.onNodeWithText("无课表").assertExists()
}
```

**Step 2: Run tests to verify failure**
- Run: `./gradlew :app:test`
- Expected: FAIL (screens missing)

**Step 3: Implement screens + basic view models**
- Wire to repositories, show loading/empty/error states.

**Step 4: Re-run tests**
- Run: `./gradlew :app:test`
- Expected: PASS

**Step 5: Commit**
```bash
git add app
git commit -m "feat(app): wire timetable, scores, empty rooms screens"
```

---

### Task 9: End-to-end sanity build

**Files:**
- Modify: `docs/plans/2026-03-13-hita-agent-refactor-progress.md`

**Step 1: Run full build**
- Run: `./gradlew :app:assembleDebug`
- Expected: BUILD SUCCESSFUL

**Step 2: Update progress log**
- Note completion of Shenzhen v0 core.

**Step 3: Commit**
```bash
git add docs/plans/2026-03-13-hita-agent-refactor-progress.md
git commit -m "docs: update progress after Shenzhen v0 core"
```
