package com.hita.agent.ui.timetable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun TimetableScreen(viewModel: TimetableViewModel) {
    val state by viewModel.uiState.collectAsState()
    TimetableScreen(state = state)
}

@Composable
fun TimetableScreen(state: TimetableUiState) {
    when (state) {
        is TimetableUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "加载中…")
            }
        }
        is TimetableUiState.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "无课表")
            }
        }
        is TimetableUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = (state as TimetableUiState.Error).message)
            }
        }
        is TimetableUiState.Content -> {
            val count = (state as TimetableUiState.Content).items.size
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "课表条目: $count")
            }
        }
    }
}
