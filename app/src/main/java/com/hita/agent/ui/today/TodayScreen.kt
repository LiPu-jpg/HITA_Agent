package com.hita.agent.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hita.agent.core.ui.theme.HitaColors
import java.util.*

// Data Models
data class TimelineEvent(
    val id: String,
    val name: String,
    val from: Date,
    val to: Date,
    val place: String?,
    val type: EventType,
    val isPassed: Boolean = false
)

enum class EventType {
    CLASS, EXAM, NORMAL, HINT
}

sealed class TimelineUiState {
    object Loading : TimelineUiState()
    data class Content(
        val headerState: HeaderState,
        val events: List<TimelineEvent>,
        val currentEvent: TimelineEvent?,
        val nextEvent: TimelineEvent?,
        val progress: Float
    ) : TimelineUiState()
}

data class HeaderState(
    val title: String,
    val subtitle: String,
    val mode: HeaderMode,
    val nextEventName: String? = null,
    val nextEventTime: String? = null,
    val classroom: String? = null,
    val progress: Int = 0
)

enum class HeaderMode {
    FREE, ONGOING, NEXT_SOON, NORMAL, GOOD_MORNING, LUNCH, DINNER, GOOD_NIGHT, FINISH
}

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    TodayScreenContent(state = state, modifier = modifier)
}

@Composable
fun TodayScreenContent(
    state: TimelineUiState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HitaColors.BackgroundBottom)
    ) {
        when (state) {
            is TimelineUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = HitaColors.Primary)
                }
            }
            is TimelineUiState.Content -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Header Card
                    item {
                        TimelineHeader(
                            headerState = state.headerState,
                            onExpandClick = { /* TODO */ }
                        )
                    }

                    // Events with timeline
                    items(state.events) { event ->
                        TimelineEventItem(
                            event = event,
                            isFirst = state.events.firstOrNull() == event,
                            isLast = state.events.lastOrNull() == event,
                            onClick = { /* TODO */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    headerState: HeaderState,
    onExpandClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = HitaColors.Primary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Main header content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = headerState.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = headerState.subtitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }

            // Center icon/content based on mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentAlignment = Alignment.Center
            ) {
                when (headerState.mode) {
                    HeaderMode.ONGOING -> {
                        // Circular progress
                        CircularProgressIndicator(
                            progress = { headerState.progress / 100f },
                            modifier = Modifier.size(100.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.4f),
                            strokeWidth = 12.dp
                        )
                        Text(
                            text = "${headerState.progress}%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    HeaderMode.NEXT_SOON -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.White
                                )
                            }
                            headerState.classroom?.let {
                                Text(
                                    text = it,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        // Default icon
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Expand button
            IconButton(
                onClick = {
                    expanded = !expanded
                    onExpandClick()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    modifier = Modifier.rotate(rotation),
                    tint = Color.White
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            headerState.nextEventTime?.let {
                                Text(
                                    text = it,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            headerState.nextEventName?.let {
                                Text(
                                    text = it,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEventItem(
    event: TimelineEvent,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
    ) {
        // Timeline indicator
        TimelineIndicator(
            isFirst = isFirst,
            isLast = isLast,
            isPassed = event.isPassed,
            isImportant = event.type == EventType.CLASS || event.type == EventType.EXAM
        )

        // Event card
        if (event.isPassed) {
            PassedEventCard(
                event = event,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                onClick = onClick
            )
        } else {
            ImportantEventCard(
                event = event,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                onClick = onClick
            )
        }
    }
}

@Composable
private fun TimelineIndicator(
    isFirst: Boolean,
    isLast: Boolean,
    isPassed: Boolean,
    isImportant: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(24.dp)
    ) {
        // Top line (hidden for first item)
        if (!isFirst) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .background(
                        if (isPassed) HitaColors.BackgroundIconBottom
                        else HitaColors.Primary.copy(alpha = 0.1f)
                    )
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Dot
        Box(
            modifier = Modifier
                .size(if (isImportant) 12.dp else 8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isPassed -> HitaColors.BackgroundIconBottom
                        isImportant -> HitaColors.Primary
                        else -> HitaColors.Primary.copy(alpha = 0.5f)
                    }
                )
        )

        // Bottom line (hidden for last item)
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(
                        if (isPassed) HitaColors.BackgroundIconBottom
                        else HitaColors.Primary.copy(alpha = 0.1f)
                    )
            )
        }
    }
}

@Composable
private fun ImportantEventCard(
    event: TimelineEvent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = HitaColors.BackgroundSecond
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = HitaColors.TextPrimary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Time row
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = HitaColors.TextSecondary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${formatTime(event.from)}-${formatTime(event.to)}",
                        fontSize = 14.sp,
                        color = HitaColors.TextSecondary.copy(alpha = 0.8f)
                    )
                }
            }

            // Location badge
            if (!event.place.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = HitaColors.Primary.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = HitaColors.Primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = event.place,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = HitaColors.Primary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Progress bar for current event
        if (!event.isPassed) {
            LinearProgressIndicator(
                progress = { 0.5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .padding(horizontal = 8.dp, vertical = 0.dp),
                color = HitaColors.Primary.copy(alpha = 0.3f),
                trackColor = HitaColors.Primary.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
private fun PassedEventCard(
    event: TimelineEvent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = HitaColors.BackgroundSecond
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = event.name,
                fontSize = 14.sp,
                color = HitaColors.TextSecondary.copy(alpha = 0.8f),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatTime(date: Date): String {
    val calendar = Calendar.getInstance().apply { time = date }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format("%02d:%02d", hour, minute)
}
