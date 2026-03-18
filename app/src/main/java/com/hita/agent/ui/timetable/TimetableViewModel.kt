package com.hita.agent.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hita.agent.core.domain.model.UnifiedCourseItem
import com.hita.agent.core.domain.repo.EasRepository
import com.hita.agent.core.ui.theme.HitaColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

// Enhanced UI State for HITA_L style week view
sealed interface TimetableUiState {
    data object Loading : TimetableUiState
    data class Empty(val message: String = "暂无课表") : TimetableUiState
    data class Error(val message: String) : TimetableUiState
    data class Content(
        val currentWeek: Int,
        val totalWeeks: Int,
        val weekStartDate: Date,
        val days: List<TimetableDay>,
        val currentMonth: String
    ) : TimetableUiState
}

data class TimetableDay(
    val dayOfWeek: Int,
    val date: Date,
    val events: List<TimetableEvent>
)

data class TimetableEvent(
    val id: String,
    val name: String,
    val startPeriod: Int,
    val endPeriod: Int,
    val place: String?,
    val color: androidx.compose.ui.graphics.Color
)

class TimetableViewModel(
    private val repository: EasRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<TimetableUiState>(TimetableUiState.Loading)
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    private var currentWeekIndex = 0

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val terms = repository.getTerms()
            val current = terms.firstOrNull { it.isCurrent } ?: terms.firstOrNull()
            if (current == null) {
                _uiState.value = TimetableUiState.Empty("暂无学期数据")
                return@launch
            }
            val result = repository.getTimetable(current)
            val err = result.error
            
            if (result.data.isEmpty() && err != null) {
                _uiState.value = TimetableUiState.Error(err.toString())
            } else if (result.data.isEmpty()) {
                _uiState.value = TimetableUiState.Empty("暂无课表数据")
            } else {
                // Convert to week view format
                val today = Calendar.getInstance()
                val currentWeek = calculateCurrentWeek(today)
                val days = generateWeekDays(today, result.data)
                
                _uiState.value = TimetableUiState.Content(
                    currentWeek = currentWeek,
                    totalWeeks = 20,
                    weekStartDate = days.first().date,
                    days = days,
                    currentMonth = "${today.get(Calendar.MONTH) + 1}月"
                )
            }
        }
    }

    fun changeWeek(delta: Int) {
        val currentState = _uiState.value
        if (currentState !is TimetableUiState.Content) return

        viewModelScope.launch {
            currentWeekIndex += delta
            val newCalendar = Calendar.getInstance().apply {
                time = currentState.weekStartDate
                add(Calendar.WEEK_OF_YEAR, delta)
            }

            // Reload data for new week
            val terms = repository.getTerms()
            val current = terms.firstOrNull { it.isCurrent } ?: terms.firstOrNull()
            val result = if (current != null) repository.getTimetable(current) else null
            val days = generateWeekDays(newCalendar, result?.data ?: emptyList())
            
            _uiState.value = currentState.copy(
                currentWeek = currentState.currentWeek + delta,
                weekStartDate = days.first().date,
                days = days,
                currentMonth = "${newCalendar.get(Calendar.MONTH) + 1}月"
            )
        }
    }

    fun backToToday() {
        currentWeekIndex = 0
        load()
    }

    private fun calculateCurrentWeek(calendar: Calendar): Int {
        // Simplified week calculation - would normally calculate from term start
        return 8
    }

    private fun generateWeekDays(
        calendar: Calendar,
        courses: List<UnifiedCourseItem>
    ): List<TimetableDay> {
        val days = mutableListOf<TimetableDay>()
        val weekStart = calendar.clone() as Calendar

        // Set to Monday of current week
        weekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        weekStart.set(Calendar.HOUR_OF_DAY, 0)
        weekStart.set(Calendar.MINUTE, 0)

        val subjectColors = listOf(
            HitaColors.Subject1,
            HitaColors.Subject3,
            HitaColors.Subject4,
            HitaColors.Subject5,
            HitaColors.Subject6,
            HitaColors.Subject7,
            HitaColors.Subject8,
            HitaColors.Subject9,
            HitaColors.Subject10
        )

        for (dayOfWeek in 1..7) {
            val dayCalendar = weekStart.clone() as Calendar
            dayCalendar.add(Calendar.DAY_OF_YEAR, dayOfWeek - 1)

            // Map courses to events for this day
            val events = courses
                .filter { it.weekday == dayOfWeek }
                .mapIndexed { index, course ->
                    TimetableEvent(
                        id = "${course.courseCode}_${index}",
                        name = course.courseName,
                        startPeriod = course.startPeriod,
                        endPeriod = course.endPeriod,
                        place = course.classroom,
                        color = subjectColors[index % subjectColors.size]
                    )
                }

            days.add(TimetableDay(dayOfWeek, dayCalendar.time, events))
        }

        return days
    }

    companion object {
        fun factory(repository: EasRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TimetableViewModel(repository) as T
                }
            }
        }
    }
}
