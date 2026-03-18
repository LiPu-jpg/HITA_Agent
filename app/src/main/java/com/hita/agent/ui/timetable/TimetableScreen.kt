package com.hita.agent.ui.timetable

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
import com.hita.agent.core.domain.model.UnifiedCourseItem
import com.hita.agent.core.ui.theme.HitaColors

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    TimetableScreenContent(state = state, modifier = modifier)
}

@Composable
fun TimetableScreenContent(
    state: TimetableUiState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HitaColors.BackgroundBottom)
    ) {
        when (state) {
            is TimetableUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = HitaColors.Primary)
                }
            }
            is TimetableUiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无课表",
                        fontSize = 16.sp,
                        color = HitaColors.TextSecondary
                    )
                }
            }
            is TimetableUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (state as TimetableUiState.Error).message,
                        fontSize = 16.sp,
                        color = HitaColors.Subject6
                    )
                }
            }
            is TimetableUiState.Content -> {
                val items = (state as TimetableUiState.Content).items
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items) { course ->
                        CourseCard(course = course)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: UnifiedCourseItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = HitaColors.BackgroundSecond
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = course.courseName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = HitaColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${course.classroom ?: "未知教室"} · 第${course.startPeriod}-${course.endPeriod}节",
                    fontSize = 14.sp,
                    color = HitaColors.TextSecondary
                )
                Text(
                    text = "周${course.weekday}",
                    fontSize = 14.sp,
                    color = HitaColors.Primary
                )
            }
            course.teacher?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = HitaColors.TextSecondary
                )
            }
        }
    }
}
