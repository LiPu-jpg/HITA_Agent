package com.limpu.hitax.agent.timetable

import android.util.Log
import com.limpu.hitax.agent.core.AgentTool
import com.limpu.hitax.agent.core.AgentToolResult
import com.limpu.hitax.data.repository.TimetableRepository
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

class GetLocalTimetableTool : AgentTool<TimetableAgentInput, TimetableAgentOutput> {
    override val name: String = "get_local_timetable"

    override fun execute(
        input: TimetableAgentInput,
        onResult: (AgentToolResult<TimetableAgentOutput>) -> Unit,
    ) {
        if (input.action != TimetableAgentInput.Action.GET_LOCAL_TIMETABLE) {
            onResult(AgentToolResult.failure("invalid action for $name"))
            return
        }

        thread(start = true) {
            try {
                val repository = TimetableRepository.getInstance(input.application)
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                Log.d("GetLocalTimetable", "Query range: from=${sdf.format(input.fromMs ?: 0)}, to=${sdf.format(input.toMs ?: 0)}, timetableId=${input.timetableId}")

                val events = if (input.timetableId != null) {
                    val timetable = repository.getTimetableByIdSync(input.timetableId)
                        ?: run {
                            onResult(AgentToolResult.failure("timetable ${input.timetableId} not found"))
                            return@thread
                        }
                    Log.d("GetLocalTimetable", "Single timetable: ${timetable.name}")
                    repository.getEventsOfTimetableSync(timetable.id, input.fromMs, input.toMs)
                } else {
                    Log.d("GetLocalTimetable", "No timetableId, querying ALL timetables")
                    repository.getEventsOfAllTimetablesSync(input.fromMs, input.toMs)
                }.sortedBy { it.from.time }

                Log.d("GetLocalTimetable", "Found ${events.size} events")
                events.take(5).forEach { ev ->
                    Log.d("GetLocalTimetable", "  event: ${ev.name}, from=${sdf.format(ev.from.time)}, to=${sdf.format(ev.to.time)}, type=${ev.type}, timetableId=${ev.timetableId}")
                }

                onResult(
                    AgentToolResult.success(
                        TimetableAgentOutput(
                            action = input.action,
                            timetableId = events.firstOrNull()?.timetableId ?: "",
                            timetableName = "全部课表",
                            events = events.map {
                                TimetableEventSnapshot(
                                    id = it.id,
                                    timetableId = it.timetableId,
                                    name = it.name,
                                    fromMs = it.from.time,
                                    toMs = it.to.time,
                                    place = it.place.orEmpty(),
                                    teacher = it.teacher.orEmpty(),
                                    type = it.type,
                                    source = it.source,
                                )
                            },
                        )
                    )
                )
            } catch (e: Exception) {
                onResult(AgentToolResult.failure(e.message ?: "get local timetable failed"))
            }
        }
    }
}
