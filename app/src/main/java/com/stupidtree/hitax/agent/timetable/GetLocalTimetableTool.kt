package com.stupidtree.hitax.agent.timetable

import com.stupidtree.hitax.agent.core.AgentTool
import com.stupidtree.hitax.agent.core.AgentToolResult
import com.stupidtree.hitax.data.repository.TimetableRepository
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
                val timetable = input.timetableId
                    ?.let { repository.getTimetableByIdSync(it) }
                    ?: repository.getRecentTimetableSync()
                    ?: run {
                        onResult(AgentToolResult.failure("no timetable found"))
                        return@thread
                    }

                val events = repository.getEventsOfTimetableSync(
                    timetableId = timetable.id,
                    fromMs = input.fromMs,
                    toMs = input.toMs,
                ).sortedBy { it.from.time }

                onResult(
                    AgentToolResult.success(
                        TimetableAgentOutput(
                            action = input.action,
                            timetableId = timetable.id,
                            timetableName = timetable.name,
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
