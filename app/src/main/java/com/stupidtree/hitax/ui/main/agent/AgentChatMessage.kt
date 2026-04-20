package com.stupidtree.hitax.ui.main.agent

data class AgentChatMessage(
    val role: Role,
    val text: String,
    val thinking: String? = null,
    val isThinkingExpanded: Boolean = false,
    val isPlaceholder: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    enum class Role {
        USER,
        ASSISTANT,
        TRACE,
    }
}
