package com.hita.agent.core.domain.agent

data class PlannerConfig(
    val mode: PlannerMode = PlannerMode.RULE,
    val llmBaseUrl: String? = null,
    val llmModel: String? = null,
    val llmApiKeyEnv: String? = null
) {
    fun isLlmConfigured(): Boolean {
        return mode == PlannerMode.LLM && !llmBaseUrl.isNullOrBlank() && !llmModel.isNullOrBlank()
    }

    companion object {
        fun from(config: AgentBackendConfig): PlannerConfig {
            val mode = when (config.plannerMode.lowercase()) {
                "llm", "model" -> PlannerMode.LLM
                else -> PlannerMode.RULE
            }
            return PlannerConfig(
                mode = mode,
                llmBaseUrl = config.llmBaseUrl,
                llmModel = config.llmModel,
                llmApiKeyEnv = config.llmApiKeyEnv
            )
        }
    }
}

enum class PlannerMode {
    RULE,
    LLM
}
