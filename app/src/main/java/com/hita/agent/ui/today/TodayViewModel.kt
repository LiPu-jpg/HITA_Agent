package com.hita.agent.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class TodayViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
    val uiState: StateFlow<TimelineUiState> = _uiState

    init {
        loadTodayData()
    }

    private fun loadTodayData() {
        viewModelScope.launch {
            // Simulate loading data
            val currentTime = Calendar.getInstance()
            val headerState = getHeaderState(currentTime)
            
            val events = listOf(
                TimelineEvent(
                    id = "1",
                    name = "高等数学",
                    from = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0) }.time,
                    to = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 45) }.time,
                    place = "A101",
                    type = EventType.CLASS,
                    isPassed = true
                ),
                TimelineEvent(
                    id = "2",
                    name = "大学英语",
                    from = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0) }.time,
                    to = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 11); set(Calendar.MINUTE, 45) }.time,
                    place = "B203",
                    type = EventType.CLASS,
                    isPassed = false
                ),
                TimelineEvent(
                    id = "3",
                    name = "计算机基础",
                    from = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 14); set(Calendar.MINUTE, 0) }.time,
                    to = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 15); set(Calendar.MINUTE, 45) }.time,
                    place = "C305",
                    type = EventType.CLASS,
                    isPassed = false
                )
            )

            _uiState.value = TimelineUiState.Content(
                headerState = headerState,
                events = events,
                currentEvent = events.find { !it.isPassed },
                nextEvent = events.find { !it.isPassed },
                progress = 65f
            )
        }
    }

    private fun getHeaderState(currentTime: Calendar): HeaderState {
        val hour = currentTime.get(Calendar.HOUR_OF_DAY)
        val minute = currentTime.get(Calendar.MINUTE)
        val timeInDay = hour * 60 + minute

        return when {
            timeInDay < 300 -> HeaderState(
                title = "晚安",
                subtitle = "早点休息，明天见",
                mode = HeaderMode.GOOD_NIGHT
            )
            timeInDay < 495 -> HeaderState(
                title = "早上好",
                subtitle = "今天有3节课",
                mode = HeaderMode.GOOD_MORNING
            )
            timeInDay in 735..780 -> HeaderState(
                title = "午餐时间",
                subtitle = "记得按时吃饭",
                mode = HeaderMode.LUNCH
            )
            timeInDay in 1030..1090 -> HeaderState(
                title = "晚餐时间",
                subtitle = "吃点好的",
                mode = HeaderMode.DINNER
            )
            timeInDay > 1380 || timeInDay < 300 -> HeaderState(
                title = "晚安",
                subtitle = "早点休息",
                mode = HeaderMode.GOOD_NIGHT
            )
            else -> HeaderState(
                title = "大学英语",
                subtitle = "正在进行中",
                mode = HeaderMode.ONGOING,
                progress = 65,
                nextEventName = "计算机基础",
                nextEventTime = "14:00开始",
                classroom = "B203"
            )
        }
    }
}
