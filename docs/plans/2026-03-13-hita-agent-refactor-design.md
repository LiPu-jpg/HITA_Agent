# HITA_Angent Refactor Design (v0)

**Goal:** Migrate HITA_L into a new, layered, Compose-first Android app (HITA_Angent) with JDK 17 and an Agent Workbench that aligns with SSOT for public/private skill separation.

**Scope:**
- Source of truth for features: HITA_L (HITA_X only for optional webui login if reused).
- Compose-first UI migration.
- Layered modules: `app`, `core-data`, `core-domain`, `core-ui`, `agent/workbench`.
- Privacy: all EAS sessions and private skills remain on-device.

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

## Section 2 — Data Flow + Privacy + Orchestration

### 2.1 Data Flow (Orchestrator view)
**Flow A — Refresh scores (Private → Local → Optional summary upload)**
1) User/manual refresh or background worker triggers.
2) Orchestrator reads local `UnifiedScoreResult` cache.
3) If `now < expires_at` and not forced → return cache (`source=cache`).
4) Else call private EAS skill `eas.scores.fetch`.
5) Normalize to `UnifiedScoreItem[]`, persist with `cached_at` and `expires_at` (TTL from `cached_at + ttl_seconds`).
6) If user enabled optional upload → upload redacted summary (P1 only).

**Flow B — PR submit (Public skill)**
1) Orchestrator builds PR payload (no credentials).
2) Call server public skill (async).
3) Poll `/v1/jobs/{id}` with backoff.
4) Store summary locally.

**Flow C — RAG query (Public skill)**
1) Orchestrator decides if public knowledge is required.
2) Call `rag.query` and merge hits into response.

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

## Section 3 — Compose UI Migration + Design System

### 3.1 Compose-first原则
- All new UI is Compose; no new XML.
- Migrate high-frequency flows first.

### 3.2 Design System (core-ui)
- Tokens: colors, typography, spacing.
- Components: AppBar, Tabs, Card, ListItem, ScoreBadge, CourseChip, StatusPill, Loading/Error/Empty.
- Preserve HITA_L visual language and palette.

### 3.3 Navigation (app)
- Main routes: `/timetable`, `/scores`, `/resources`, `/profile`, `/workbench`.
- Subroutes for detail screens.

### 3.4 Migration order (UI)
1) Tokens + base components
2) Business components
3) Feature screens

### 3.5 Campus awareness in UI
- Campus switcher triggers cache invalidation and refresh.
- Public skill calls always pass `campus_id`.

---

## Section 4 — Data Layer Migration & API Adapters

### 4.1 EAS Adapter Interface (v0)
- `login()` → `CampusSession`
- `validateSession()`
- `fetchTerms()`
- `fetchTimetable(term)`
- `fetchScores(term, qzqmFlag)`

### 4.2 Campus adapters
- HITSZ: incoSpringBoot bearer + cookie (existing baseline)
- HITH: iVPN/CAS WebView
- HITWH: webvpn QR login

### 4.3 Session store
- `CampusSession` persisted locally, encrypted with Android Keystore.
- TTL controlled locally; no session data uploaded.

### 4.4 Public skills client
- `/v1/skills`, `/v1/skills/{name}:invoke`, `/v1/jobs/{id}`
- Always include `campus_id`.
- Must follow SSOT invoke/jobs semantics.

---

## Section 5 — Phased Migration + Tests + Risks

### 5.1 Phases
**Phase 0**: module skeleton, Compose baseline, JDK17, Design System v0
**Phase 1**: core features (timetable, scores, resources)
**Phase 2**: academic tools (GPA, empty rooms, course snatching)
**Phase 3**: Agent Workbench

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
