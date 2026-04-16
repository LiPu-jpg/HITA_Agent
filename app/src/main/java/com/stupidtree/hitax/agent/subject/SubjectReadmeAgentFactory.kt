package com.stupidtree.hitax.agent.subject

import com.stupidtree.hitax.agent.core.AgentProvider
import com.stupidtree.hitax.agent.core.AgentOrchestrator
import com.stupidtree.hitax.agent.core.AgentToolRegistry
import com.stupidtree.hitax.agent.core.OrchestratorAgentSession
import com.stupidtree.hitax.agent.core.SimpleAgentProvider

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
