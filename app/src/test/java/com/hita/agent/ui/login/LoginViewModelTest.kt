package com.hita.agent.ui.login

import com.hita.agent.core.data.store.FileSessionStore
import com.hita.agent.core.domain.model.CampusId
import com.hita.agent.core.domain.model.CampusSession
import com.hita.agent.core.domain.repo.CachedResult
import com.hita.agent.core.domain.repo.EasRepository
import com.hita.agent.core.domain.model.EmptyRoomResult
import com.hita.agent.core.domain.model.UnifiedCourseItem
import com.hita.agent.core.domain.model.UnifiedScoreItem
import com.hita.agent.core.domain.model.UnifiedTerm
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loginShenzhen_updatesStateToLoggedIn() = runTest {
        val repo = FakeEasRepository(alwaysValid = true)
        val dir = Files.createTempDirectory("session-store").toFile()
        val store = FileSessionStore(dir)
        val vm = LoginViewModel(repo, store)

        vm.updateUsername("user")
        vm.updatePassword("pass")
        vm.loginShenzhen()

        advanceUntilIdle()
        assertEquals(LoginStatus.LOGGED_IN, vm.state.value.status)
    }

    private class FakeEasRepository(
        private val alwaysValid: Boolean
    ) : EasRepository {
        override suspend fun login(username: String, password: String): CampusSession {
            return CampusSession(
                campusId = CampusId.SHENZHEN,
                bearerToken = "token",
                cookiesByHost = mapOf("example.com" to "a=b"),
                createdAt = Instant.now(),
                expiresAt = null
            )
        }

        override suspend fun validateSession(): Boolean {
            return alwaysValid
        }

        override suspend fun getTerms(): List<UnifiedTerm> = emptyList()

        override suspend fun getTimetable(
            term: UnifiedTerm,
            forceRefresh: Boolean
        ): CachedResult<List<UnifiedCourseItem>> {
            throw UnsupportedOperationException()
        }

        override suspend fun getScores(
            term: UnifiedTerm,
            qzqmFlag: String,
            forceRefresh: Boolean
        ): CachedResult<List<UnifiedScoreItem>> {
            throw UnsupportedOperationException()
        }

        override suspend fun getEmptyRooms(
            date: String,
            buildingId: String,
            period: String,
            forceRefresh: Boolean
        ): EmptyRoomResult {
            throw UnsupportedOperationException()
        }
    }
}
