package com.limpu.hitax.agent.core

interface AgentTool<I, O> {
    val name: String

    fun execute(input: I, onResult: (AgentToolResult<O>) -> Unit)
}

data class AgentToolResult<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: String? = null,
) {
    companion object {
        fun <T> success(data: T): AgentToolResult<T> = AgentToolResult(ok = true, data = data)
        fun <T> failure(error: String): AgentToolResult<T> = AgentToolResult(ok = false, error = error)
    }
}
