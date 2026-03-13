# HITA_Angent Refactor Design (v0)

**Goal:** Migrate HITA_L into a new, layered, Compose-first Android app (HITA_Angent) with JDK 17, starting with Shenzhen v0 core features and a unified multi-campus data model.

**Scope (v0 Shenzhen-first):**
- Source of truth for features: HITA_L (HITA_X only for optional webui login if reused).
- Compose-first UI migration.
- Layered modules: `app`, `core-data`, `core-domain`, `core-ui`, `agent/workbench`.
- Privacy: all EAS sessions and private skills remain on-device.
- Core only: **login/session + timetable + scores + empty rooms**.
- Not in v0: course selection, course resources (reserved for agent/pr workflows).

## Rules / Constraints (Contractual)

1) **Backend SSOT reference (hard link)**  
   The protocol SSOT is `hoa-agent-backend/docs/plans/2026-03-13-agentic-architecture-design.md`.  
   Android must not change field names/semantics locally; any changes must follow the `/v2` contract process.

2) **Public skills always send `campus_id`**  
   Client must always include `campus_id` in public skill requests.  
   `rag.query` currently treats `campus_id` as optional server-side; client still sends it for future multi-tenant filters (server may ignore without breaking).

---

## Section 1 — Architecture & Module Map

### 1.1 Modules
- `app`
  - Compose navigation, screen composition, app shell, entrypoints, feature routing.
- `core-domain`
  - Entities (`CampusSession`, `Unified*`), use cases, repository contracts, state machine interfaces.
- `core-data`
  - Repository implementations, EAS adapters, agent-backend client wrapper, Room/SQLite storage.
- `core-ui`
  - Compose Design System: colors/typography/components based on HITA_L style.
- `agent/workbench`
  - Orchestrator, skill registry, workflow state machine, local memory abstraction.

### 1.2 Dependencies
- `app` → `core-ui`, `core-domain`, `core-data`, `agent/workbench`
- `core-data` → `core-domain`
- `agent/workbench` → `core-domain`
- `core-ui` → (no deps)

### 1.3 Migration focus
- HITA_L is the functional baseline.
- HITA_X only contributes the WebUI login (if still needed) and will be rebuilt in Compose.

---

## Section 2 — Shenzhen v0 Scope & API Mapping

### 2.1 Shenzhen v0 Scope
- Login/Session
- Timetable (weekly matrix, HITA_L behavior)
- Scores (list + summary)
- Empty rooms (date + period + building/area)

### 2.2 Shenzhen API Mapping (HITA_L mode)
**Login / Session**
- `/component/queryApplicationSetting/rsa` → `/c_raskey` → `/authentication/ldap`

**Timetable (HITA_L weekly matrix)**
- Term list: `/app/commapp/queryxnxqlist`
- Week list: `/app/commapp/queryzclistbyxnxq`
- Weekly matrix: `/app/Kbcx/query`
- Schedule structure from `jcList` in weekly matrix response

**Scores**
- Scores list: `/app/cjgl/xscjList?_lang=zh_CN`
- Summary (GPA/rank): `/app/cjgl/xfj`

**Empty Rooms**
- Building list: `/app/commapp/queryjxllist`
- Occupancy by date + building: `/app/kbrcbyapp/querycdzyxx`

### 2.2 Privacy & Data Classification
- **P0 Private:** credentials, session cookies/tokens, student ID, grades, raw timetable. Never leave device.
- **P1 Derived Local:** redacted summaries only, optional upload via explicit user consent.
- **P2 Public:** PR metadata, public resources, public search results.

**Rules**
- P0 never uploaded.
- Trace is metadata only; no request/response bodies or personal fields.
- Public-but-Intranet resources may be fetched by server but never linked to personal sessions.

### 2.3 Orchestration & State Machine
- **Planner** may run on-device or cloud.
- **Executor (Orchestrator)** runs on-device for any sensitive action.

**States (v0)**
- `Idle` → `PlanReady` → `Acting` → `Waiting` → `Done`
- `Failed` on unrecoverable error

**Failure strategy**
- Private skill failure: return stale cache with `stale=true`, include error.
- Public skill failure: backoff retry (0.5s → 1s → 2s → 5s, cap 2 minutes).

### 2.4 Campus in Public Skills
- All public skill requests must include `campus_id` by default.
- Only truly global resources can omit `campus_id` (explicitly documented per skill).

---

## Section 3 — Unified Schema + Cache/TTL + Data Flow

### 3.1 Unified Local Schemas (multi-campus)
- `CampusSession`: `campus_id`, `bearer_token?`, `cookies_by_host`, `created_at`, `expires_at?`
- `UnifiedTerm`: `term_id`, `year`, `term`, `name`, `is_current`
- `UnifiedCourseItem`: `course_code`, `course_name`, `teacher?`, `classroom?`, `weekday`, `start_period`, `end_period` (inclusive), `weeks: int[]`, `weeks_text?`
- `UnifiedScoreItem`: `course_code`, `course_name`, `credit`, `score_value: number|null`, `score_text`, `status?`, `term_id`
- `UnifiedTimetableResult`: `data`, `cached_at`, `expires_at`, `stale`, `source`, `error: Error|null`
- `UnifiedScoreResult`: `data`, `summary?`, `cached_at`, `expires_at`, `stale`, `source`, `error: Error|null`
- `EmptyRoomQuery`: `date`, `period` (DJ1..DJ6), `building_id`, `building_name?`
- `EmptyRoomResult`: `rooms`, `cached_at`, `expires_at`, `stale`, `source`, `error: Error|null`

### 3.2 Cache / TTL Rules
- `expires_at = cached_at + ttl_seconds` (single source of truth)
- Force refresh triggers:
  - manual refresh
  - background worker
  - term/session change
- Failure strategy: if cache exists → return `stale=true` + error; else return error
- Suggested TTLs:
  - Timetable: 24h
  - Scores: 6h–12h
  - Empty rooms: 2h–4h

### 3.3 Shenzhen v0 Data Flows
**Login / Session**
1) RSA init → RSA key → LDAP login
2) Persist `CampusSession` locally (bearer + cookie)

**Timetable**
1) `queryxnxqlist` → terms
2) `queryzclistbyxnxq` → week count
3) Loop `Kbcx/query` per week → normalize to `UnifiedCourseItem[]`

**Scores**
1) `xscjList` → `UnifiedScoreItem[]`
2) `xfj` → summary (optional)

**Empty Rooms**
1) `queryjxllist` → buildings
2) `querycdzyxx` → DJ1..DJ6 occupancy
3) Local filter by `period`

---

## Section 4 — Module Responsibilities + Migration Order + Minimum v0

### 4.1 Module Responsibilities
- `core-domain`
  - Unified schemas, result wrappers, repository contracts
- `core-data`
  - `EasShenzhenAdapter` (login/session + timetable + scores + empty rooms)
  - Local cache + TTL
  - `CampusSessionStore` (Keystore encryption)
- `core-ui`
  - tokens + reusable components for timetable/scores/empty rooms
- `app`
  - Compose routes/screens + ViewModels
- `agent/workbench`
  - Stub only in v0 (no functional dependency)

### 4.2 Migration Order (v0 core only)
1) Shenzhen session + adapter skeleton
2) Timetable weekly matrix pipeline
3) Scores list + summary
4) Empty rooms (buildings + occupancy)
5) UI wiring for timetable/scores/empty rooms
6) Background refresh + error handling

### 4.3 Minimum v0 Checklist
- Login/Session (RSA init → RSA key → LDAP login)
- Timetable (term → week list → weekly matrix)
- Scores (list + summary)
- Empty rooms (building list + occupancy, filter by period)
- UI: Timetable + Scores + Empty Rooms + Profile login card

---

## Section 5 — Compose UI + Tests + Risks

### 5.1 Navigation (app)
- Main routes (v0): `/timetable`, `/scores`, `/empty-rooms`, `/profile`, `/workbench`
- Home remains Timetable; Workbench is present but stubbed

### 5.2 Tests
- Domain: use-case unit tests
- Data: adapter + cache integration tests
- UI: Compose UI tests on core flows

### 5.3 Risks
- Multi-campus API divergence → mitigate by unified schemas and staged rollout.
- Compose + JDK17 upgrades → lock versions, migrate module by module.
- Parallel dev drift → SSOT + client proposal as single contracts.

---

**Status:** Approved for implementation planning.
