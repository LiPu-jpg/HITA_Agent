package com.limpu.hitax.agent.core

class AgentOrchestrator<I, O>(
    private val engine: AgentEngine<I, O>,
) {
    fun run(
        input: I,
        onTrace: (AgentTraceEvent) -> Unit,
        onResult: (AgentToolResult<O>) -> Unit,
    ) {
        engine.run(input, onTrace, onResult)
    }
}
