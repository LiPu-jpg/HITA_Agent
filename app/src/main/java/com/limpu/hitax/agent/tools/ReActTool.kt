package com.limpu.hitax.agent.tools

import android.app.Application
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentTraceEvent
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput

data class ReActToolInput(
    val actionInput: String,
    val userMessage: String,
    val application: Application,
    val timetableId: String?,
    val agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
    val onTrace: (AgentTraceEvent) -> Unit,
)

fun interface ReActTool {
    fun execute(input: ReActToolInput): String?
}
