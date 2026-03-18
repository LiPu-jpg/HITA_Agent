package com.hita.agent.ui.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FeaturesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<FeaturesUiState>(FeaturesUiState.Loading)
    val uiState: StateFlow<FeaturesUiState> = _uiState

    init {
        loadFeaturesData()
    }

    private fun loadFeaturesData() {
        viewModelScope.launch {
            // Simulate loading data
            // In real implementation, this would fetch from repositories
            val userInfo = UserInfo(
                isLoggedIn = false,
                username = "未登录",
                nickname = "点击登录",
                avatarUrl = null,
                easUsername = null,
                isEasLoggedIn = false
            )

            val timetableInfo = TimetableInfo(
                recentTimetableName = "2024春季学期",
                timetableCount = 1
            )

            _uiState.value = FeaturesUiState.Content(
                userInfo = userInfo,
                timetableInfo = timetableInfo
            )
        }
    }

    fun refresh() {
        loadFeaturesData()
    }
}
