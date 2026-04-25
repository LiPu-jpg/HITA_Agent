package com.limpu.hitax.agent.timetable

import android.app.Application
import com.limpu.hitax.data.model.timetable.EventItem

data class TimetableAgentInput(
    val application: Application,
    val action: Action,
    val timetableId: String? = null,
    val fromMs: Long? = null,
    val toMs: Long? = null,
    val arrangement: ArrangementInput? = null,
) {
    enum class Action {
        GET_LOCAL_TIMETABLE,
        ADD_TIMETABLE_ARRANGEMENT,
    }
}

data class ArrangementInput(
    val name: String,
    val fromMs: Long,
    val toMs: Long,
    val place: String? = "",
    val teacher: String? = "",
    val type: EventItem.TYPE = EventItem.TYPE.OTHER,
    val subjectId: String = "",
)

data class TimetableEventSnapshot(
    val id: String,
    val timetableId: String,
    val name: String,
    val fromMs: Long,
    val toMs: Long,
    val place: String,
    val teacher: String,
    val type: EventItem.TYPE,
    val source: String,
)

data class TimetableAgentOutput(
    val action: TimetableAgentInput.Action,
    val timetableId: String,
    val timetableName: String?,
    val events: List<TimetableEventSnapshot> = emptyList(),
    val addedEventIds: List<String> = emptyList(),
)
