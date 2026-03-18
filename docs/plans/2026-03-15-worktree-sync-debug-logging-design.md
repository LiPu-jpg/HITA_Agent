# Worktree Sync + Debug Logging Design

**Goal**
Sync the current implementation from the `shenzhen-v0-plan` worktree into the main `HITA_Angent` directory, then add non-sensitive runtime debug logs to improve local debugging in Android Studio.

**Scope**
- Copy source/doc changes from `HITA_Angent/.worktrees/shenzhen-v0-plan` to `HITA_Angent/`.
- Exclude build outputs and local machine configs.
- Add debug logs (no credentials/PII) across agent loop, backend client, local RAG, and login flows.
- Quick cross-check against `new_api` docs for obvious mismatch notes (no large refactor).

**Non-Goals**
- No API redesign.
- No backend changes.
- No new features beyond logging and sync.

**Approach**
1. Use `rsync` to copy all source and doc files from the worktree into main, excluding `.git`, `.gradle`, `*/build`, `.tmp`, `.idea`, `local.properties`.
2. Add a lightweight debug logger utility (Android `Log`) to keep logging consistent and easy to disable later.
3. Instrument key flows with logs that include counts/timing/status only, never user credentials or raw content.

**Logging Targets**
- `AgenticLoop`: plan start/end, step execution, public skill invocation, job polling result, local RAG hit count.
- `AgentBackendApi`: invokeSkill/getJob/listSkills request + status + latency.
- `LocalRagRepository`: index start/end, parser selection, chunk count, search query length + hit count.
- `WorkbenchViewModel`: sendQuery, /mcp command dispatch, file index completion, download target chosen.
- `LoginViewModel` + `MainWebLoginActivity`: login start/success/failure, session saved, CAS callback detected (no cookies or passwords).

**Risks**
- Excessive logging in release builds. Mitigation: central logger gate (toggle or BuildConfig guard).
- Local file names appear in logs. Mitigation: log display name only, avoid full paths.

**Success Criteria**
- Main repo contains all worktree functionality.
- Logs appear in Android Studio Logcat and do not expose sensitive data.
- No build outputs or machine configs are copied into main.
