# HITA_Angent Login + Session Lifecycle Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Workbench login card and per-campus session lifecycle for Shenzhen (direct API) and Main (CAS WebView).

**Architecture:** Implement a LoginViewModel in app layer to orchestrate Shenzhen login via `EasRepository` and Main login via WebView cookie capture. Store sessions per campus using `FileSessionStore`; status computed per campus and surfaced in Workbench.

**Tech Stack:** Kotlin, Jetpack Compose, OkHttp (existing), Android WebView, coroutines, StateFlow.

---

### Task 1: Login state models + reducer

**Files:**
- Create: `app/src/main/java/com/hita/agent/ui/login/LoginState.kt`
- Test: `app/src/test/java/com/hita/agent/ui/login/LoginStateTest.kt`

**Step 1: Write the failing test**

```kotlin
class LoginStateTest {
    @Test
    fun resolveStatus_loggedOut_whenNoSession() {
        val status = resolveStatus(session = null, isValid = null)
        assertEquals(LoginStatus.LOGGED_OUT, status)
    }

    @Test
    fun resolveStatus_loggedIn_whenSessionValid() {
        val session = CampusSession(CampusId.SHENZHEN, null, emptyMap(), Instant.now(), null)
        val status = resolveStatus(session = session, isValid = true)
        assertEquals(LoginStatus.LOGGED_IN, status)
    }

    @Test
    fun resolveStatus_stale_whenSessionInvalid() {
        val session = CampusSession(CampusId.SHENZHEN, null, emptyMap(), Instant.now(), null)
        val status = resolveStatus(session = session, isValid = false)
        assertEquals(LoginStatus.STALE, status)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.hita.agent.ui.login.LoginStateTest`
Expected: FAIL (class/functions missing)

**Step 3: Write minimal implementation**

```kotlin
enum class LoginStatus { LOGGED_OUT, LOGGING_IN, LOGGED_IN, STALE }

data class LoginUiState(
    val campusId: CampusId,
    val username: String,
    val password: String,
    val status: LoginStatus,
    val message: String? = null
)

fun resolveStatus(session: CampusSession?, isValid: Boolean?): LoginStatus {
    return when {
        session == null -> LoginStatus.LOGGED_OUT
        isValid == true -> LoginStatus.LOGGED_IN
        isValid == false -> LoginStatus.STALE
        else -> LoginStatus.STALE
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.hita.agent.ui.login.LoginStateTest`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/hita/agent/ui/login/LoginState.kt \
        app/src/test/java/com/hita/agent/ui/login/LoginStateTest.kt
git commit -m "feat(app): add login state model"
```

---

### Task 2: LoginViewModel (Shenzhen + Main session flow)

**Files:**
- Create: `app/src/main/java/com/hita/agent/ui/login/LoginViewModel.kt`
- Test: `app/src/test/java/com/hita/agent/ui/login/LoginViewModelTest.kt`

**Step 1: Write the failing test**

```kotlin
class LoginViewModelTest {
    @Test
    fun loginShenzhen_updatesStateToLoggedIn() = runTest {
        val repo = FakeEasRepository(alwaysValid = true)
        val store = InMemorySessionStore()
        val vm = LoginViewModel(repo, store)

        vm.updateUsername("user")
        vm.updatePassword("pass")
        vm.loginShenzhen()

        assertEquals(LoginStatus.LOGGED_IN, vm.state.value.status)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.hita.agent.ui.login.LoginViewModelTest`
Expected: FAIL

**Step 3: Write minimal implementation**

```kotlin
class LoginViewModel(
    private val repository: EasRepository,
    private val sessionStore: FileSessionStore
) : ViewModel() {
    private val _state = MutableStateFlow(
        LoginUiState(campusId = CampusId.SHENZHEN, username = "", password = "", status = LoginStatus.LOGGED_OUT)
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun setCampus(campusId: CampusId) { _state.update { it.copy(campusId = campusId, message = null) } }
    fun updateUsername(value: String) { _state.update { it.copy(username = value) } }
    fun updatePassword(value: String) { _state.update { it.copy(password = value) } }

    fun refreshStatus() = viewModelScope.launch {
        val campus = _state.value.campusId
        val session = sessionStore.load(campus)
        val valid = if (campus == CampusId.SHENZHEN && session != null) repository.validateSession() else session != null
        _state.update { it.copy(status = resolveStatus(session, valid), message = null) }
    }

    fun loginShenzhen() = viewModelScope.launch {
        _state.update { it.copy(status = LoginStatus.LOGGING_IN, message = null) }
        runCatching { repository.login(_state.value.username, _state.value.password) }
            .onSuccess { _state.update { it.copy(status = LoginStatus.LOGGED_IN) } }
            .onFailure { _state.update { it.copy(status = LoginStatus.LOGGED_OUT, message = it.message) } }
    }

    fun saveMainSession(cookiesByHost: Map<String, String>) {
        val session = CampusSession(CampusId.MAIN, null, cookiesByHost, Instant.now(), null)
        sessionStore.save(session)
        _state.update { it.copy(status = LoginStatus.LOGGED_IN, message = null) }
    }

    fun logout() {
        sessionStore.clear(_state.value.campusId)
        _state.update { it.copy(status = LoginStatus.LOGGED_OUT, message = null) }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.hita.agent.ui.login.LoginViewModelTest`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/hita/agent/ui/login/LoginViewModel.kt \
        app/src/test/java/com/hita/agent/ui/login/LoginViewModelTest.kt
git commit -m "feat(app): add login viewmodel"
```

---

### Task 3: Main campus WebView login activity (CAS)

**Files:**
- Create: `app/src/main/java/com/hita/agent/ui/login/MainWebLoginActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/hita/agent/ui/login/MainWebLoginActivityTest.kt`

**Step 1: Write the failing test**

```kotlin
class MainWebLoginActivityTest {
    @Test
    fun filterCookies_dropsBlankHosts() {
        val input = mapOf("a.com" to "", "b.com" to "k=v")
        val output = filterCookies(input)
        assertEquals(mapOf("b.com" to "k=v"), output)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.hita.agent.ui.login.MainWebLoginActivityTest`
Expected: FAIL

**Step 3: Write minimal implementation**

```kotlin
private const val EXTRA_COOKIES = "extra_cookies"
private const val EXTRA_START_URL = "extra_start_url"
private val DEFAULT_START_URL = "https://ids-hit-edu-cn-s.ivpn.hit.edu.cn/authserver/login?service=https%3A%2F%2Fjwts-hit-edu-cn.ivpn.hit.edu.cn%2FloginCAS"
private val MAIN_COOKIE_HOSTS = listOf(
    "ids-hit-edu-cn-s.ivpn.hit.edu.cn",
    "ivpn.hit.edu.cn",
    "i-hit-edu-cn.ivpn.hit.edu.cn",
    "jwts-hit-edu-cn.ivpn.hit.edu.cn"
)

fun filterCookies(raw: Map<String, String>): Map<String, String> {
    return raw.filterValues { it.isNotBlank() }
}

// In Activity: use WebView + CookieManager
// On success (detect loginCAS redirect), collect cookies by host and return via setResult(RESULT_OK).
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.hita.agent.ui.login.MainWebLoginActivityTest`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/hita/agent/ui/login/MainWebLoginActivity.kt \
        app/src/main/AndroidManifest.xml \
        app/src/test/java/com/hita/agent/ui/login/MainWebLoginActivityTest.kt
git commit -m "feat(app): add main campus webview login"
```

---

### Task 4: Workbench login card UI (HITA_L style) + integration

**Files:**
- Create: `app/src/main/java/com/hita/agent/ui/workbench/WorkbenchRoute.kt`
- Create: `app/src/main/java/com/hita/agent/ui/login/LoginCard.kt`
- Modify: `app/src/main/java/com/hita/agent/ui/MainNavHost.kt`
- Modify: `app/src/main/java/com/hita/agent/AppContainer.kt`

**Step 1: Add WorkbenchRoute + LoginCard**

```kotlin
@Composable
fun WorkbenchRoute(container: AppContainer) {
    val loginVm: LoginViewModel = viewModel(factory = LoginViewModel.factory(container))
    Column {
        LoginCard(viewModel = loginVm)
        Spacer(Modifier.height(12.dp))
        val agentVm: WorkbenchViewModel = viewModel(
            factory = WorkbenchViewModel.factory(container.agentBackendApi, container.easRepository, container.plannerConfig)
        )
        WorkbenchScreen(viewModel = agentVm)
    }
}
```

**Step 2: Wire campus selector + button actions**

- Shenzhen: username/password + Login button
- Main: WebView login button → start `MainWebLoginActivity`
- Show status text + logout button

**Step 3: Run app build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/hita/agent/ui/workbench/WorkbenchRoute.kt \
        app/src/main/java/com/hita/agent/ui/login/LoginCard.kt \
        app/src/main/java/com/hita/agent/ui/MainNavHost.kt \
        app/src/main/java/com/hita/agent/AppContainer.kt
git commit -m "feat(app): add workbench login card"
```

---

### Task 5: Update progress log

**Files:**
- Modify: `docs/plans/2026-03-13-hita-agent-refactor-progress.md`

**Step 1: Append completed items**
- Login card UI added
- Shenzhen + Main session lifecycle implemented
- WebView CAS login added (Main)

**Step 2: Commit**

```bash
git add docs/plans/2026-03-13-hita-agent-refactor-progress.md
git commit -m "docs: update progress for login/session"
```
