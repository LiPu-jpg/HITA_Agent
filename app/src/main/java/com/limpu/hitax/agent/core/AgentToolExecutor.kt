package com.limpu.hitax.agent.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

enum class AgentToolErrorType {
    TIMEOUT,
    TOOL_FAILURE,
}

data class AgentToolExecutionPolicy(
    val timeoutMs: Long = 4500L,
    val retryCount: Int = 1,
    val retryDelayMs: Long = 150L,
)

class AgentToolExecutor(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    fun <I, O> execute(
        tool: AgentTool<I, O>,
        input: I,
        policy: AgentToolExecutionPolicy = AgentToolExecutionPolicy(),
        onTrace: (AgentTraceEvent) -> Unit,
        onResult: (AgentToolResult<O>) -> Unit,
    ) {
        val maxAttempts = (policy.retryCount + 1).coerceAtLeast(1)

        fun runAttempt(attempt: Int) {
            val finished = AtomicBoolean(false)
            onTrace(
                AgentTraceEvent(
                    stage = "tool_attempt",
                    message = "Executing ${tool.name}",
                    payload = "attempt=$attempt/$maxAttempts",
                )
            )

            val timeoutTask = Runnable {
                if (!finished.compareAndSet(false, true)) return@Runnable
                val isRetryable = attempt < maxAttempts
                onTrace(
                    AgentTraceEvent(
                        stage = "tool_timeout",
                        message = "Tool timed out",
                        payload = "tool=${tool.name},attempt=$attempt/$maxAttempts,retry=$isRetryable",
                    )
                )
                if (isRetryable) {
                    handler.postDelayed({ runAttempt(attempt + 1) }, policy.retryDelayMs)
                } else {
                    onResult(
                        AgentToolResult.failure(
                            "[${AgentToolErrorType.TIMEOUT}] tool ${tool.name} timed out after ${policy.timeoutMs}ms"
                        )
                    )
                }
            }

            handler.postDelayed(timeoutTask, policy.timeoutMs)
            tool.execute(input) { result ->
                if (!finished.compareAndSet(false, true)) return@execute
                handler.removeCallbacks(timeoutTask)
                if (result.ok) {
                    onResult(result)
                    return@execute
                }

                val isRetryable = attempt < maxAttempts
                val rawMessage = result.error?.ifBlank { null } ?: "tool ${tool.name} failed"
                onTrace(
                    AgentTraceEvent(
                        stage = "tool_failure",
                        message = "Tool returned failure",
                        payload = "tool=${tool.name},attempt=$attempt/$maxAttempts,error=${AgentTraceSanitizer.sanitizeError(rawMessage)}",
                    )
                )
                if (isRetryable) {
                    handler.postDelayed({ runAttempt(attempt + 1) }, policy.retryDelayMs)
                } else {
                    onResult(
                        AgentToolResult.failure(
                            "[${AgentToolErrorType.TOOL_FAILURE}] ${AgentTraceSanitizer.sanitizeError(rawMessage)}"
                        )
                    )
                }
            }
        }

        runAttempt(1)
    }
}
