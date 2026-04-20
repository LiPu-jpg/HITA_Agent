package com.stupidtree.hitax.ui.main.agent

data class AgentChatMessage(
    val role: Role,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    enum class Role {
        USER,
        ASSISTANT,
        TRACE,
    }
}
