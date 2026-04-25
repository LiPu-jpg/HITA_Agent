package com.limpu.hitax.agent.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

interface AgentSession<I, O> {
    fun run(
        input: I,
        onTrace: (AgentTraceEvent) -> Unit,
        onResult: (AgentToolResult<O>) -> Unit,
    )

    fun dispose() {}
}

interface AgentProvider<I, O> {
    fun createSession(): AgentSession<I, O>
}

class OrchestratorAgentSession<I, O>(
    private val orchestrator: AgentOrchestrator<I, O>,
) : AgentSession<I, O> {
    private val disposed = AtomicBoolean(false)
    private val runVersion = AtomicInteger(0)

    override fun run(
        input: I,
        onTrace: (AgentTraceEvent) -> Unit,
        onResult: (AgentToolResult<O>) -> Unit,
    ) {
        if (disposed.get()) return
        val currentVersion = runVersion.incrementAndGet()
        orchestrator.run(
            input = input,
            onTrace = { trace ->
                if (!disposed.get() && runVersion.get() == currentVersion) {
                    onTrace(trace)
                }
            },
            onResult = { result ->
                if (!disposed.get() && runVersion.get() == currentVersion) {
                    onResult(result)
                }
            }
        )
    }

    override fun dispose() {
        disposed.set(true)
        runVersion.incrementAndGet()
    }
}

class SimpleAgentProvider<I, O>(
    private val sessionFactory: () -> AgentSession<I, O>,
) : AgentProvider<I, O> {
    override fun createSession(): AgentSession<I, O> = sessionFactory()
}
