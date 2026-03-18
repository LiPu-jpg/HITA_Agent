package com.hita.agent.ui.scores

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
import com.hita.agent.core.domain.model.UnifiedScoreItem
import com.hita.agent.core.ui.theme.HitaColors

sealed interface ScoresUiState {
    data object Loading : ScoresUiState
    data object Empty : ScoresUiState
    data class Error(val message: String) : ScoresUiState
    data class Content(val items: List<UnifiedScoreItem>) : ScoresUiState
}

@Composable
fun ScoresScreen(
    viewModel: ScoresViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    ScoresScreenContent(state = state, modifier = modifier)
}

@Composable
fun ScoresScreenContent(
    state: ScoresUiState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HitaColors.BackgroundBottom)
    ) {
        when (state) {
            is ScoresUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = HitaColors.Primary)
                }
            }
            is ScoresUiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无成绩",
                        fontSize = 16.sp,
                        color = HitaColors.TextSecondary
                    )
                }
            }
            is ScoresUiState.Error -> {
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
            is ScoresUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items) { score ->
                        ScoreCard(score = score)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreCard(score: UnifiedScoreItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = HitaColors.BackgroundSecond
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = score.courseName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = HitaColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${score.courseCode} · ${score.credit}学分",
                    fontSize = 13.sp,
                    color = HitaColors.TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val scoreValue = score.scoreValue
                Text(
                    text = score.scoreText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (scoreValue != null && scoreValue >= 60) {
                        HitaColors.Subject10
                    } else {
                        HitaColors.Subject6
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                score.status?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = HitaColors.TextSecondary
                    )
                }
            }
        }
    }
}