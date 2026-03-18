package com.hita.agent.ui.scores

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

sealed interface ScoresUiState {
    data object Loading : ScoresUiState
    data object Empty : ScoresUiState
    data class Error(val message: String) : ScoresUiState
}

@Composable
fun ScoresScreen(viewModel: ScoresViewModel) {
    val state by viewModel.uiState.collectAsState()
    ScoresScreen(state = state)
}

@Composable
fun ScoresScreen(state: ScoresUiState) {
    when (state) {
        ScoresUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "加载中…")
            }
        }
        ScoresUiState.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无成绩")
            }
        }
        is ScoresUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = state.message)
            }
        }
    }
}
