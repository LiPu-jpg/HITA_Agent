# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HITA Agent is an Android app for Harbin Institute of Technology (HIT) students across three campuses (深圳/本部/威海). It provides timetable management, grade queries, course resources, and an AI assistant based on ReAct framework. Package name: `com.limpu.hitax`.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew testDebugUnitTest      # Unit tests
./gradlew connectedAndroidTest   # Instrumentation tests (requires device/emulator)
./gradlew testClasses            # Alias for testDebugUnitTest
```

**Environment**: JDK 17 required (configured in `gradle.properties` via `org.gradle.java.home`). Gradle 8.7, AGP 8.5.2, Kotlin 1.9.22.

**Maven mirrors**: All repos use Chinese mirrors (Aliyun, Tencent, JitPack) — see `build.gradle`.

## Architecture

### Multi-Module Structure

```
HITA_Agent/
├── app/          # Main application — UI, business logic, agent system, data layer
├── component/    # Shared base classes: DataState, Result, BaseWebSource, ApiResponse
├── hitauser/     # User module — auth, profile, login, isolated Room DB (UserDatabase)
├── style/        # Base UI classes (BaseActivity, BaseFragment, BaseListAdapter), widgets, theme tools
├── sync/         # Data sync (legacy, not actively included in settings.gradle)
```

`settings.gradle` includes: `app`, `hitauser`, `component`, `style`.

### Dependency Injection: Hilt

- `app/di/` contains `AgentModule`, `DatabaseModule`, `RepositoryModule`
- `hitauser/di/HitaUserModule` provides user-related dependencies
- `@Singleton` scope via `SingletonComponent`
- Fragment/Activity inject via `@AndroidEntryPoint`

### Data Layer Pattern (MVVM + Repository)

```
data/
├── model/           # Data classes (course, score, timetable, chat, resource, etc.)
├── repository/      # Business logic & data coordination
├── source/
│   ├── dao/         # Room DAOs (ChatDao, SubjectDao, TimetableDao, EventItemDao)
│   ├── preference/  # SharedPreferences wrappers
│   └── web/         # Retrofit services + HTML parsers (Jsoup)
│       ├── eas/     # EAS (教务系统) parsers: BenbuEASWebSource, WeihaiEASWebSource
│       └── service/ # Retrofit service interfaces
└── work/            # WorkManager workers (CourseReminder, ScoreReminder)
```

Two Room databases: `AppDatabase` (app module) and `UserDatabase` (hitauser module).

### Agent / AI System (ReAct Framework)

The AI assistant lives under `app/.../agent/`:

```
agent/
├── core/        # AgentEngine, AgentOrchestrator, AgentTool (interface), AgentToolRegistry
├── llm/         # LlmClient, LlmChatService, ReactPromptBuilder (MiniMax API)
├── remote/      # AgentBackendClient (agent-backend HTTP), PrServerClient (course resource HTTP)
├── tools/       # Concrete tools: WebSearch, RagSearch, CrawlPage, SearchCourse, etc.
├── subject/     # Subject README agent (course detail fetcher)
├── timetable/   # Timetable agent (local timetable queries + calendar add)
└── document/    # File parsers: PDF, DOCX, XLSX, PPTX, TXT — Strategy pattern via FileParserDispatcher
```

**Key design**: Tools implement `AgentTool` interface, registered in `ReActToolRegistry`. The `AgentOrchestrator` drives the ReAct loop (thought → action → observation). LLM calls go through `LlmClient` → MiniMax API. Course resource queries hit `PrServerClient`; other tools route through `AgentBackendClient`.

### Network Clients

- **PrServerClient**: GitHub HOA repo interactions (course resources, README, PR submission)
- **AgentBackendClient**: AI tool backend (Brave search, RAG, web crawling)
- **EASWebSource**: 教务系统 HTML scraping with Jsoup (BenbuEASWebSource for 本部, WeihaiEASWebSource for 威海)
- Retrofit services defined in `data/source/web/service/`

### UI Layer

- ViewBinding enabled (no Compose in production despite Compose dependencies being present)
- Fragments extend `BaseFragment` / `BaseFragmentClassic` from style module
- Adapters extend `BaseListAdapter` / `BaseListAdapterClassic`
- Markdown rendering via Markwon library

### Key BuildConfig Fields

Defined in `app/build.gradle`:
- `HOA_BASE_URL` — course resource server
- `AGENT_BACKEND_BASE_URL` — AI agent backend
- `HOA_API_KEY` — API key (empty in code, injected at build)

## Coding Conventions

- Kotlin style: `kotlin.code.style=official`
- Package prefix: `com.limpu.hitax` (app), `com.limpu.component`, `com.limpu.hitauser`, `com.limpu.style`
- Commit messages follow conventional commits: `feat:`, `fix:`, `refactor:`, `docs:`, `perf:`, `test:`, `chore:`
- Null safety: prefer `?.` and `?.let{}` over `!!`
- Coroutines: use `viewModelScope.launch`, never `GlobalScope`
- Room schema exports to `app/schemas/`
- ProGuard enabled for release builds (`minifyEnabled true`, `shrinkResources true`)
