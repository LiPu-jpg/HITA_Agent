package com.stupidtree.hitax.agent.core

interface AgentEngine<I, O> {
    fun run(
        input: I,
        onTrace: (AgentTraceEvent) -> Unit,
        onResult: (AgentToolResult<O>) -> Unit,
    )
}
