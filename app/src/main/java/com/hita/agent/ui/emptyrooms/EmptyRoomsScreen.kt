package com.hita.agent.ui.emptyrooms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

sealed interface EmptyRoomsUiState {
    data object Loading : EmptyRoomsUiState
    data object Empty : EmptyRoomsUiState
    data class Error(val message: String) : EmptyRoomsUiState
}

@Composable
fun EmptyRoomsScreen(viewModel: EmptyRoomsViewModel) {
    val state by viewModel.uiState.collectAsState()
    EmptyRoomsScreen(state = state)
}

@Composable
fun EmptyRoomsScreen(state: EmptyRoomsUiState) {
    when (state) {
        EmptyRoomsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "加载中…")
            }
        }
        EmptyRoomsUiState.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无空教室")
            }
        }
        is EmptyRoomsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = state.message)
            }
        }
    }
}
