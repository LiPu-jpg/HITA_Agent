package com.limpu.hitax.agent.tools

import com.limpu.hitax.agent.remote.AgentBackendClient

class BraveAnswerTool : ReActTool {
    override fun execute(input: ReActToolInput): String? {
        return try {
            val query = extractParam(input.actionInput)
            val result = AgentBackendClient.braveAnswerSync(query)
            if (!result.ok) {
                return "回答失败: ${result.error?.message ?: "未知错误"}"
            }
            result.answer.ifBlank { "未找到相关回答" }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun extractParam(actionInput: String): String {
        val regex = Regex(""""param"\s*:\s*"([^"]+)"""")
        return regex.find(actionInput)?.groupValues?.get(1) ?: actionInput.trim()
    }
}