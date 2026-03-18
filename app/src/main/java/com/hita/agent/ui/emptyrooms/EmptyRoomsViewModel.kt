package com.hita.agent.ui.emptyrooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hita.agent.core.domain.repo.EasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmptyRoomsViewModel(
    private val repository: EasRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<EmptyRoomsUiState>(EmptyRoomsUiState.Loading)
    val uiState: StateFlow<EmptyRoomsUiState> = _uiState.asStateFlow()

    fun load(date: String, buildingId: String, period: String) {
        viewModelScope.launch {
            val result = repository.getEmptyRooms(date, buildingId, period)
            val err = result.error
            _uiState.value = if (result.rooms.isEmpty()) {
                if (err != null) EmptyRoomsUiState.Error(err.message)
                else EmptyRoomsUiState.Empty
            } else {
                EmptyRoomsUiState.Empty
            }
        }
    }

    companion object {
        fun factory(repository: EasRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EmptyRoomsViewModel(repository) as T
                }
            }
        }
    }
}
