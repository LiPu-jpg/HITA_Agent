package com.limpu.hitax.agent.timetable

import com.limpu.hitax.agent.core.AgentTool
import com.limpu.hitax.agent.core.AgentToolResult
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.utils.LogUtils
import java.sql.Timestamp
import java.util.Calendar
import kotlin.concurrent.thread

class AddTimetableArrangementTool : AgentTool<TimetableAgentInput, TimetableAgentOutput> {
    override val name: String = "add_timetable_arrangement"

    override fun execute(
        input: TimetableAgentInput,
        onResult: (AgentToolResult<TimetableAgentOutput>) -> Unit,
    ) {
        if (input.action != TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT) {
            onResult(AgentToolResult.failure("invalid action for $name"))
            return
        }

        val arrangement = input.arrangement
        if (arrangement == null) {
            onResult(AgentToolResult.failure("arrangement is required"))
            return
        }
        if (arrangement.name.isBlank()) {
            onResult(AgentToolResult.failure("arrangement name is required"))
            return
        }
        if (arrangement.toMs <= arrangement.fromMs) {
            onResult(AgentToolResult.failure("arrangement end time must be after start time"))
            return
        }

        thread(start = true) {
            try {
                LogUtils.d("[DEBUG] Starting tool execution")
                val repository = TimetableRepository(input.application)
                LogUtils.d("[DEBUG] Got repository instance")

                val timetable = input.timetableId
                    ?.let { repository.getTimetableByIdSync(it) }
                    ?: repository.getRecentTimetableSync()
                    ?: repository.ensureDefaultCustomTimetableSync()
                    LogUtils.d("[DEBUG] Got timetable: id=${timetable.id}, name=${timetable.name}")

                val event = EventItem().apply {
                    type = arrangement.type
                    source = EventItem.SOURCE_AGENT
                    name = arrangement.name
                    timetableId = timetable.id
                    subjectId = arrangement.subjectId
                    place = arrangement.place.orEmpty()
                    teacher = arrangement.teacher.orEmpty()
                    from = Timestamp(arrangement.fromMs)
                    to = Timestamp(arrangement.toMs)
                    fromNumber = 0
                    lastNumber = 0
                    color = -1
                }
                LogUtils.d("[DEBUG] Created event: id=${event.id}, name=${event.name}, timetableId=${event.timetableId}, from=${event.from}, to=${event.to}")

                repository.addEventsSync(listOf(event))
                LogUtils.d("[DEBUG] addEventsSync called successfully")

                if (event.to.time > timetable.endTime.time) {
                    val c = Calendar.getInstance()
                    c.timeInMillis = event.to.time
                    c.firstDayOfWeek = Calendar.MONDAY
                    c.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                    c.set(Calendar.HOUR_OF_DAY, 23)
                    c.set(Calendar.MINUTE, 59)
                    timetable.endTime.time = c.timeInMillis
                    repository.saveTimetableSync(timetable)
                    LogUtils.d("[DEBUG] Updated timetable endTime")
                }

                LogUtils.d("[DEBUG] Tool execution successful, returning result")
                onResult(
                    AgentToolResult.success(
                        TimetableAgentOutput(
                            action = input.action,
                            timetableId = timetable.id,
                            timetableName = timetable.name,
                            addedEventIds = listOf(event.id),
                        )
                    )
                )
            } catch (e: Exception) {
                LogUtils.e("[DEBUG] Tool execution failed", e)
                onResult(AgentToolResult.failure(e.message ?: "add timetable arrangement failed"))
            }
        }
    }
}
