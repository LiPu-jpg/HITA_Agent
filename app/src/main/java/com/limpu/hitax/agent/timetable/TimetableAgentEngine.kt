package com.limpu.hitax.agent.timetable

import com.limpu.hitax.agent.core.AgentEngine
import com.limpu.hitax.agent.core.AgentToolExecutionPolicy
import com.limpu.hitax.agent.core.AgentToolExecutor
import com.limpu.hitax.agent.core.AgentToolResult
import com.limpu.hitax.agent.core.AgentToolRegistry
import com.limpu.hitax.agent.core.AgentTraceEvent
import com.limpu.hitax.agent.core.AgentTraceSanitizer

class TimetableAgentEngine(
    private val toolRegistry: AgentToolRegistry,
    private val toolExecutor: AgentToolExecutor = AgentToolExecutor(),
) : AgentEngine<TimetableAgentInput, TimetableAgentOutput> {

    override fun run(
        input: TimetableAgentInput,
        onTrace: (AgentTraceEvent) -> Unit,
        onResult: (AgentToolResult<TimetableAgentOutput>) -> Unit,
    ) {
        onTrace(
            AgentTraceEvent(
                stage = "start",
                message = "Running timetable agent",
                payload = AgentTraceSanitizer.sanitizePayload("action=${input.action},timetableId=${input.timetableId.orEmpty()}"),
            )
        )

        val toolName = when (input.action) {
            TimetableAgentInput.Action.GET_LOCAL_TIMETABLE -> "get_local_timetable"
            TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT -> "add_timetable_arrangement"
        }

        val tool = toolRegistry.get<TimetableAgentInput, TimetableAgentOutput>(toolName)
        if (tool == null) {
            onResult(AgentToolResult.failure("tool $toolName not found"))
            return
        }

        val policy = AgentToolExecutionPolicy(
            timeoutMs = 5000L,
            retryCount = 1,
            retryDelayMs = 180L,
        )

        toolExecutor.execute(
            tool = tool,
            input = input,
            policy = policy,
            onTrace = onTrace,
        ) { result ->
            if (!result.ok) {
                onResult(AgentToolResult.failure(result.error ?: "timetable agent tool failed"))
                return@execute
            }

            val output = result.data
            if (output == null) {
                onResult(AgentToolResult.failure("timetable agent returned empty output"))
                return@execute
            }

            onTrace(
                AgentTraceEvent(
                    stage = "result",
                    message = "Timetable agent finished",
                    payload = AgentTraceSanitizer.sanitizePayload(
                        "action=${output.action},eventCount=${output.events.size},addedCount=${output.addedEventIds.size}"
                    ),
                )
            )
            onResult(AgentToolResult.success(output))
        }
    }
}
