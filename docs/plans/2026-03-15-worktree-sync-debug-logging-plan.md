# Worktree Sync + Debug Logging Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Sync `shenzhen-v0-plan` worktree changes into main and add non-sensitive debug logging across core flows.

**Architecture:** Use `rsync` to copy source/doc files into main while excluding build artifacts. Add a shared debug logger (Android `Log`) and instrument agent loop, backend client, local RAG, and login flows without leaking credentials.

**Tech Stack:** Android (Kotlin, Compose), OkHttp, Room, WebView.

---

### Task 1: Cross-check `new_api` docs for mismatches

**Files:**
- Read: `new_api/index.md`
- Read: `new_api/school-api/main.md`

**Step 1: Scan docs**
Run: `sed -n '1,160p' new_api/index.md`
Expected: See latest architecture summary.

**Step 2: Note any mismatches**
If any mismatch is found, add a short note to `HITA_Angent/docs/plans/2026-03-13-hita-agent-refactor-progress.md` under “Notes”.

**Step 3: Commit note (optional)**
If note added:
```bash
git add HITA_Angent/docs/plans/2026-03-13-hita-agent-refactor-progress.md
git commit -m "docs: note new_api cross-check"
```

---

### Task 2: Sync worktree to main

**Files:**
- Copy from: `HITA_Angent/.worktrees/shenzhen-v0-plan/`
- Copy to: `HITA_Angent/`

**Step 1: rsync (exclude build and local configs)**
Run:
```bash
rsync -a \
  --exclude '.git' \
  --exclude '.gradle' \
  --exclude '.tmp' \
  --exclude '.idea' \
  --exclude 'local.properties' \
  --exclude '*/build' \
  HITA_Angent/.worktrees/shenzhen-v0-plan/ HITA_Angent/
```
Expected: Source + docs copied into main.

**Step 2: Inspect status**
Run: `git -C HITA_Angent status -sb`
Expected: Modified/added files reflecting sync.

---

### Task 3: Add shared debug logger

**Files:**
- Create: `HITA_Angent/core-data/src/main/java/com/hita/agent/core/data/DebugLog.kt`

**Step 1: Write implementation**
```kotlin
package com.hita.agent.core.data

import android.util.Log

object DebugLog {
    private const val PREFIX = "HITA"
    var enabled: Boolean = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (enabled) Log.d("$PREFIX/$tag", message)
    }

    fun i(tag: String, message: String) {
        if (enabled) Log.i("$PREFIX/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) Log.w("$PREFIX/$tag", message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) Log.e("$PREFIX/$tag", message, throwable)
    }
}
```

**Step 2: Commit**
```bash
git add HITA_Angent/core-data/src/main/java/com/hita/agent/core/data/DebugLog.kt
git commit -m "chore: add debug logger"
```

---

### Task 4: Instrument AgentBackendApi

**Files:**
- Modify: `HITA_Angent/core-data/src/main/java/com/hita/agent/core/data/agent/AgentBackendApi.kt`

**Step 1: Add logging around network calls**
Example changes:
```kotlin
val start = System.currentTimeMillis()
DebugLog.d("AgentBackendApi", "invokeSkill name=$name")
val response = client.newCall(request).execute()
DebugLog.d("AgentBackendApi", "invokeSkill name=$name status=${response.code} ms=${System.currentTimeMillis()-start}")
```

**Step 2: Commit**
```bash
git add HITA_Angent/core-data/src/main/java/com/hita/agent/core/data/agent/AgentBackendApi.kt
git commit -m "chore: log agent-backend api calls"
```

---

### Task 5: Instrument AgenticLoop

**Files:**
- Modify: `HITA_Angent/agent/workbench/src/main/java/com/hita/agent/workbench/AgenticLoop.kt`

**Step 1: Add logs for planning + execution**
Examples:
```kotlin
DebugLog.d("AgenticLoop", "plan start len=${prompt.length}")
DebugLog.d("AgenticLoop", "plan steps=${plan.steps.size}")
DebugLog.d("AgenticLoop", "step=${step.describe()}")
```

**Step 2: Commit**
```bash
git add HITA_Angent/agent/workbench/src/main/java/com/hita/agent/workbench/AgenticLoop.kt
git commit -m "chore: log agent loop execution"
```

---

### Task 6: Instrument WorkbenchViewModel

**Files:**
- Modify: `HITA_Angent/agent/workbench/src/main/java/com/hita/agent/workbench/WorkbenchViewModel.kt`

**Step 1: Add logs for sendQuery, MCP, file index**
Examples:
```kotlin
DebugLog.d("WorkbenchVM", "sendQuery len=${query.length}")
DebugLog.i("WorkbenchVM", "mcp command")
DebugLog.d("WorkbenchVM", "indexed file status=${indexed.status}")
```

**Step 2: Commit**
```bash
git add HITA_Angent/agent/workbench/src/main/java/com/hita/agent/workbench/WorkbenchViewModel.kt
git commit -m "chore: log workbench actions"
```

---

### Task 7: Instrument LocalRagRepository

**Files:**
- Modify: `HITA_Angent/core-data/src/main/java/com/hita/agent/core/data/localrag/LocalRagRepository.kt`

**Step 1: Add logs for indexing + search**
Examples:
```kotlin
DebugLog.d("LocalRag", "index start name=${metadata.displayName} mime=${metadata.mimeType}")
DebugLog.d("LocalRag", "index chunks=${chunks.size}")
DebugLog.d("LocalRag", "search len=${query.length} topK=$topK")
```

**Step 2: Commit**
```bash
git add HITA_Angent/core-data/src/main/java/com/hita/agent/core/data/localrag/LocalRagRepository.kt
git commit -m "chore: log local rag indexing"
```

---

### Task 8: Instrument login flow

**Files:**
- Modify: `HITA_Angent/app/src/main/java/com/hita/agent/ui/login/LoginViewModel.kt`
- Modify: `HITA_Angent/app/src/main/java/com/hita/agent/ui/login/MainWebLoginActivity.kt`

**Step 1: Add logs for login and session lifecycle**
Examples:
```kotlin
DebugLog.i("LoginVM", "login shenzhen start")
DebugLog.i("LoginVM", "session saved campus=${session.campusId}")
```

**Step 2: Commit**
```bash
git add HITA_Angent/app/src/main/java/com/hita/agent/ui/login/LoginViewModel.kt \
  HITA_Angent/app/src/main/java/com/hita/agent/ui/login/MainWebLoginActivity.kt
git commit -m "chore: log login/session lifecycle"
```

---

### Task 9: Update progress log

**Files:**
- Modify: `HITA_Angent/docs/plans/2026-03-13-hita-agent-refactor-progress.md`

**Step 1: Add note about sync + debug logs**

**Step 2: Commit**
```bash
git add HITA_Angent/docs/plans/2026-03-13-hita-agent-refactor-progress.md
git commit -m "docs: update progress log"
```

---

### Task 10: Verify build

**Step 1: Assemble debug**
Run: `cd HITA_Angent && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Commit any leftover changes**
```bash
git -C HITA_Angent status -sb
```

---

Plan complete and saved to `docs/plans/2026-03-15-worktree-sync-debug-logging-plan.md`. Two execution options:

1. Subagent-Driven (this session) — I dispatch fresh subagent per task, review between tasks.
2. Parallel Session (separate) — Open new session and run tasks with executing-plans.

Which approach?
