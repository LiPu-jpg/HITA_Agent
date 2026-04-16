package com.stupidtree.hitax.agent.timetable

import com.stupidtree.hitax.agent.core.AgentOrchestrator
import com.stupidtree.hitax.agent.core.AgentProvider
import com.stupidtree.hitax.agent.core.AgentToolRegistry
import com.stupidtree.hitax.agent.core.OrchestratorAgentSession
import com.stupidtree.hitax.agent.core.SimpleAgentProvider

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
