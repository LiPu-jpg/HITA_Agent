package com.hita.agent.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hita.agent.core.domain.repo.EasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScoresViewModel(
    private val repository: EasRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<ScoresUiState>(ScoresUiState.Loading)
    val uiState: StateFlow<ScoresUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val terms = repository.getTerms()
            val current = terms.firstOrNull { it.isCurrent } ?: terms.firstOrNull()
            if (current == null) {
                _uiState.value = ScoresUiState.Empty
                return@launch
            }
            val result = repository.getScores(current, qzqmFlag = "qm")
            val err = result.error
            _uiState.value = when {
                result.data.isEmpty() && err != null -> ScoresUiState.Error(err)
                result.data.isEmpty() -> ScoresUiState.Empty
                else -> ScoresUiState.Empty
            }
        }
    }

    companion object {
        fun factory(repository: EasRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ScoresViewModel(repository) as T
                }
            }
        }
    }
}
