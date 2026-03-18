# HITA_Angent Refactor Progress

**Date:** 2026-03-13

## Completed
- Wrote design doc: `docs/plans/2026-03-13-hita-agent-refactor-design.md`
- Defined layered architecture and Compose-first migration strategy
- Captured data flow, privacy constraints, orchestration model
- Added campus-aware public skill requirement
- Updated design with Shenzhen v0 scope (login/session + timetable + scores + empty rooms)
- Added unified local schemas + TTL rules + Shenzhen data flows
- Scaffolded multi-module Gradle project (JDK17 + Compose)
- Added core-domain unified models + result wrappers
- Added Shenzhen DTOs + parsing tests (sample JSON in test resources)
- Implemented Shenzhen mappers (timetable/scores/empty rooms)
- Added cache/session store stubs
- Wired minimal Compose screens (timetable/scores/empty rooms)
- Shenzhen API smoke test: login OK, term list OK, scores OK (0 items), empty rooms OK (6 items). No credentials recorded.
- Added file-based cache/session stores and repository implementation for Shenzhen
- Wired Timetable/Scores/EmptyRooms viewmodels with repository
- Added agent-backend config asset and loader (app assets)
- Added agent-backend API client (invoke/jobs) and domain models
- Added Workbench UI (agent chat + tool actions) and bottom navigation
- Build: `./gradlew :app:assembleDebug` OK
- Public skills default campus_id injection via config
- Workbench trace metadata added (SSOT-aligned, redacted)
- Workbench file upload/download hooks wired to system pickers (no backend integration yet)
- Added minimal Agentic Loop (Planner/Executor) with local vs public skill steps
- Planner upgraded to intelligent selection with fallback to rules; need-input for missing empty-room parameters
- Added LLM streaming planner (SSE) with env-key config; fallback to rule on failure
- Planner now includes redacted local summary (P1) in LLM routing prompt
- Updated agent backend config asset with LLM base URL/model/env key name
- Build: `./gradlew :app:assembleDebug` OK (after LLM changes)
- Added login UI card in Workbench (HITA_L style)
- Implemented per-campus session lifecycle (Shenzhen direct login + Main CAS WebView)
- Added Main CAS WebView login activity and cookie capture
- Added login state/viewmodel tests (app module)
- Workbench MCP tools entry: `/mcp list` and `/mcp call` routed to agent-backend
- Tools button now lists MCP tools via `mcp.list_tools`
- Added local RAG design + implementation plan (on-device indexing)
- Added Room FTS local index + parsers (txt/md/pdf/doc/docx/ppt/pptx)
- Added LocalRagRepository with persistent index + search
- Workbench picker now indexes local files and injects metadata into chat
- AgenticLoop supports `local_rag` action before public `rag.query`
- Synced shenzhen-v0-plan worktree into main `HITA_Angent`
- Added debug logging (agent loop, backend API, local RAG, login/WebView)

## Next
- Replace file-based cache/session with Room/DataStore + encryption
- Connect Workbench to real rag.query responses (server)
- Add LLM integration test or mock for planner routing
- Replace file-based session store with encrypted DataStore/Room
- Local RAG: add file manager UI (list/reindex/delete)

## Notes
- Reference for main-campus API interaction flow: HITSchedule repo (CAS/VPN/RSA/captcha patterns) for adapter guidance.
