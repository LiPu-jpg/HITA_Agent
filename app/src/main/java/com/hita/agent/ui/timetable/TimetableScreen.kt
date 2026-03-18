package com.hita.agent.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hita.agent.core.ui.theme.HitaColors
import java.util.*

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    TimetableScreenContent(
        state = state,
        onWeekChange = { viewModel.changeWeek(it) },
        onEventClick = { /* TODO */ },
        onBackToToday = { viewModel.backToToday() },
        modifier = modifier
    )
}

@Composable
fun TimetableScreenContent(
    state: TimetableUiState,
    onWeekChange: (Int) -> Unit,
    onEventClick: (TimetableEvent) -> Unit,
    onBackToToday: () -> Unit,
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
                        text = (state as TimetableUiState.Empty).message,
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
                        text = state.message,
                        fontSize = 16.sp,
                        color = HitaColors.Subject6
                    )
                }
            }
            is TimetableUiState.Content -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Week header with month and day indicators
                    WeekHeader(
                        month = state.currentMonth,
                        days = state.days,
                        onSwipeLeft = { onWeekChange(1) },
                        onSwipeRight = { onWeekChange(-1) }
                    )

                    // Timetable grid
                    TimetableGrid(
                        days = state.days,
                        onEventClick = onEventClick,
                        modifier = Modifier.weight(1f)
                    )
                }

                // FAB to return to today
                FloatingActionButton(
                    onClick = onBackToToday,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = HitaColors.Primary,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "回到今天"
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(
    month: String,
    days: List<TimetableDay>,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HitaColors.BackgroundSecond)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount < -50) onSwipeLeft()
                    else if (dragAmount > 50) onSwipeRight()
                }
            }
    ) {
        // Month and week navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = month,
                fontSize = 12.sp,
                color = HitaColors.TextPrimary,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )

            // Day indicators
            days.forEachIndexed { index, day ->
                val calendar = Calendar.getInstance().apply { time = day.date }
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                val isToday = isToday(day.date)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isToday) HitaColors.Primary
                                else HitaColors.BackgroundBottom
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayNames[index],
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) Color.White else HitaColors.TextPrimary
                        )
                    }
                }
            }
        }

        // Date numbers row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(32.dp))

            days.forEach { day ->
                val calendar = Calendar.getInstance().apply { time = day.date }
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                val isToday = isToday(day.date)

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayOfMonth.toString(),
                        fontSize = 11.sp,
                        color = if (isToday) HitaColors.Primary else HitaColors.TextSecondary,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetableGrid(
    days: List<TimetableDay>,
    onEventClick: (TimetableEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // Time labels column
        TimeLabelsColumn()

        // Days columns
        days.forEach { day ->
            DayColumn(
                day = day,
                onEventClick = onEventClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimeLabelsColumn() {
    Column(
        modifier = Modifier.width(32.dp)
    ) {
        // Period labels (1-12)
        for (period in 1..12) {
            Box(
                modifier = Modifier
                    .height(60.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = period.toString(),
                    fontSize = 11.sp,
                    color = HitaColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: TimetableDay,
    onEventClick: (TimetableEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = isToday(day.date)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                if (isToday) HitaColors.Primary.copy(alpha = 0.05f)
                else Color.Transparent
            )
    ) {
        // Grid lines
        Column {
            for (period in 1..12) {
                Box(
                    modifier = Modifier
                        .height(60.dp)
                        .fillMaxWidth()
                        .background(
                            if (period % 2 == 0) HitaColors.BackgroundBottom.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                )
            }
        }

        // Events
        day.events.forEach { event ->
            EventCard(
                event = event,
                onClick = { onEventClick(event) },
                modifier = Modifier.padding(2.dp)
            )
        }
    }
}

@Composable
private fun EventCard(
    event: TimetableEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = event.endPeriod - event.startPeriod + 1
    val topOffset = (event.startPeriod - 1) * 60

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height((duration * 60 - 4).dp)
            .offset(y = topOffset.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = event.color.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Text(
                text = event.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 2,
                lineHeight = 12.sp
            )
            event.place?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}

private fun isToday(date: Date): Boolean {
    val today = Calendar.getInstance()
    val checkDate = Calendar.getInstance().apply { time = date }
    return today.get(Calendar.YEAR) == checkDate.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == checkDate.get(Calendar.DAY_OF_YEAR)
}
