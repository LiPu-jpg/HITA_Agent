package com.hita.agent.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hita.agent.core.domain.model.UnifiedCourseItem
import com.hita.agent.core.domain.repo.EasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TimetableUiState {
    data object Loading : TimetableUiState
    data object Empty : TimetableUiState
    data class Content(val items: List<UnifiedCourseItem>) : TimetableUiState
    data class Error(val message: String) : TimetableUiState
}

class TimetableViewModel(
    private val repository: EasRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<TimetableUiState>(TimetableUiState.Loading)
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val terms = repository.getTerms()
            val current = terms.firstOrNull { it.isCurrent } ?: terms.firstOrNull()
            if (current == null) {
                _uiState.value = TimetableUiState.Empty
                return@launch
            }
            val result = repository.getTimetable(current)
            val err = result.error
            _uiState.value = when {
                result.data.isEmpty() && err != null -> TimetableUiState.Error(err)
                result.data.isEmpty() -> TimetableUiState.Empty
                else -> TimetableUiState.Content(result.data)
            }
        }
    }

    companion object {
        fun factory(repository: EasRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TimetableViewModel(repository) as T
                }
            }
        }
    }
}
