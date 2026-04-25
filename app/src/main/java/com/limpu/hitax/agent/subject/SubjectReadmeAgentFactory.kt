package com.limpu.hitax.agent.subject

import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentOrchestrator
import com.limpu.hitax.agent.core.AgentToolRegistry
import com.limpu.hitax.agent.core.OrchestratorAgentSession
import com.limpu.hitax.agent.core.SimpleAgentProvider

object SubjectReadmeAgentFactory {
    fun create(): AgentOrchestrator<SubjectReadmeAgentInput, SubjectReadmeAgentOutput> {
        val registry = AgentToolRegistry().apply {
            register(ResolveCourseCandidatesTool())
        }
        return AgentOrchestrator(SubjectReadmeAgentEngine(registry))
    }

    fun createProvider(): AgentProvider<SubjectReadmeAgentInput, SubjectReadmeAgentOutput> {
        return SimpleAgentProvider {
            OrchestratorAgentSession(create())
        }
    }
}
