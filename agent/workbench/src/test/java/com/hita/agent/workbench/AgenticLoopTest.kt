package com.hita.agent.workbench

import com.hita.agent.core.data.agent.AgentBackendApi
import com.hita.agent.core.domain.agent.AgentBackendConfig
import com.hita.agent.core.domain.agent.PlannerConfig
import com.hita.agent.core.domain.agent.PlannerMode
import org.junit.Assert.assertTrue
import org.junit.Test

class AgenticLoopTest {
    @Test
    fun planner_withoutLlm_fallsBackToPublicRag() {
        val api = AgentBackendApi.create(AgentBackendConfig(baseUrl = "http://localhost"))
        val planner = AgenticLoop.Planner(
            PlannerConfig(
                mode = PlannerMode.LLM,
                llmBaseUrl = null,
                llmModel = null,
                llmApiKeyEnv = null
            )
        )
        val plan = planner.plan("帮我找这份文档", api, null)
        assertTrue(plan.steps.last() is AgentStep.PublicSkill)
    }
}
