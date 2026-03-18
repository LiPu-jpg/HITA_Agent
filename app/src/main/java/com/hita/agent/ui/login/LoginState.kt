package com.hita.agent.ui.login

import com.hita.agent.core.domain.model.CampusId
import com.hita.agent.core.domain.model.CampusSession

enum class LoginStatus {
    LOGGED_OUT,
    LOGGING_IN,
    LOGGED_IN,
    STALE
}

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
