package com.hita.agent.ui.emptyrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hita.agent.core.ui.theme.HitaColors

sealed interface EmptyRoomsUiState {
    data object Loading : EmptyRoomsUiState
    data object Empty : EmptyRoomsUiState
    data class Error(val message: String) : EmptyRoomsUiState
    data class Content(val rooms: List<String>, val buildingName: String?) : EmptyRoomsUiState
}

@Composable
fun EmptyRoomsScreen(
    viewModel: EmptyRoomsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    EmptyRoomsScreenContent(state = state, modifier = modifier)
}

@Composable
fun EmptyRoomsScreenContent(
    state: EmptyRoomsUiState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HitaColors.BackgroundBottom)
    ) {
        when (state) {
            is EmptyRoomsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = HitaColors.Primary)
                }
            }
            is EmptyRoomsUiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无空教室",
                        fontSize = 16.sp,
                        color = HitaColors.TextSecondary
                    )
                }
            }
            is EmptyRoomsUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        fontSize = 16.sp,
                        color = HitaColors.Subject6
                    )
                }
            }
            is EmptyRoomsUiState.Content -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    state.buildingName?.let {
                        Text(
                            text = it,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = HitaColors.TextPrimary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.rooms.chunked(3)) { rowRooms ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowRooms.forEach { room ->
                                    RoomCard(
                                        room = room,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowRooms.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomCard(
    room: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = HitaColors.BackgroundSecond
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = room,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = HitaColors.TextPrimary
            )
        }
    }
}