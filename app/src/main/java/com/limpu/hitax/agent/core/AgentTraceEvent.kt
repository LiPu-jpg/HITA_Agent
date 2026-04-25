package com.limpu.hitax.agent.core

data class AgentTraceEvent(
    val stage: String,
    val message: String,
    val payload: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
)
