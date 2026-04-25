package com.limpu.hitax.agent.timetable

import com.limpu.hitax.agent.core.AgentOrchestrator
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentToolRegistry
import com.limpu.hitax.agent.core.OrchestratorAgentSession
import com.limpu.hitax.agent.core.SimpleAgentProvider

object TimetableAgentFactory {
    fun create(): AgentOrchestrator<TimetableAgentInput, TimetableAgentOutput> {
        val registry = AgentToolRegistry().apply {
            register(GetLocalTimetableTool())
            register(AddTimetableArrangementTool())
        }
        return AgentOrchestrator(TimetableAgentEngine(registry))
    }

    fun createProvider(): AgentProvider<TimetableAgentInput, TimetableAgentOutput> {
        return SimpleAgentProvider {
            OrchestratorAgentSession(create())
        }
    }
}
