package com.hita.agent.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hita.agent.AppContainer
import com.hita.agent.core.data.DebugLog
import com.hita.agent.core.data.store.FileSessionStore
import com.hita.agent.core.domain.model.CampusId
import com.hita.agent.core.domain.model.CampusSession
import com.hita.agent.core.domain.repo.EasRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: EasRepository,
    private val sessionStore: FileSessionStore
) : ViewModel() {
    private val _state = MutableStateFlow(
        LoginUiState(
            campusId = CampusId.SHENZHEN,
            username = "",
            password = "",
            status = LoginStatus.LOGGED_OUT
        )
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun setCampus(campusId: CampusId) {
        DebugLog.d("LoginVM", "setCampus ${campusId.value}")
        _state.update { it.copy(campusId = campusId, message = null) }
        refreshStatus()
    }

    fun updateUsername(value: String) {
        _state.update { it.copy(username = value) }
    }

    fun updatePassword(value: String) {
        _state.update { it.copy(password = value) }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val campus = _state.value.campusId
            val session = sessionStore.load(campus)
            val valid = if (campus == CampusId.SHENZHEN && session != null) {
                repository.validateSession()
            } else {
                session != null
            }
            DebugLog.d("LoginVM", "refreshStatus campus=${campus.value} hasSession=${session != null} valid=$valid")
            _state.update { it.copy(status = resolveStatus(session, valid), message = null) }
        }
    }

    fun loginShenzhen() {
        viewModelScope.launch {
            DebugLog.i("LoginVM", "login shenzhen start")
            _state.update { it.copy(status = LoginStatus.LOGGING_IN, message = null) }
            runCatching { repository.login(_state.value.username, _state.value.password) }
                .onSuccess {
                    DebugLog.i("LoginVM", "login shenzhen success")
                    _state.update {
                        it.copy(
                            status = LoginStatus.LOGGED_IN,
                            password = "",
                            message = null
                        )
                    }
                }
                .onFailure { ex ->
                    DebugLog.w("LoginVM", "login shenzhen failed", ex)
                    _state.update {
                        it.copy(
                            status = LoginStatus.LOGGED_OUT,
                            message = ex.message ?: "login failed"
                        )
                    }
                }
        }
    }

    fun saveMainSession(cookiesByHost: Map<String, String>) {
        DebugLog.i("LoginVM", "save main session hosts=${cookiesByHost.keys.size}")
        val session = CampusSession(
            campusId = CampusId.MAIN,
            bearerToken = null,
            cookiesByHost = cookiesByHost,
            createdAt = Instant.now(),
            expiresAt = null
        )
        sessionStore.save(session)
        _state.update { it.copy(status = LoginStatus.LOGGED_IN, message = null) }
    }

    fun logout() {
        DebugLog.i("LoginVM", "logout campus=${_state.value.campusId.value}")
        sessionStore.clear(_state.value.campusId)
        _state.update { it.copy(status = LoginStatus.LOGGED_OUT, message = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LoginViewModel(
                        repository = container.easRepository,
                        sessionStore = container.sessionStore
                    ) as T
                }
            }
        }
    }
}
