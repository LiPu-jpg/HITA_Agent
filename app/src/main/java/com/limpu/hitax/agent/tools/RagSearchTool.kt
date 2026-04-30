package com.limpu.hitax.agent.tools

import com.limpu.hitax.agent.remote.AgentBackendClient

class RagSearchTool : ReActTool {
    override fun execute(input: ReActToolInput): String? {
        return try {
            val query = extractParam(input.actionInput)
            if (query.isBlank()) {
                return "RAG 查询失败: 缺少查询词"
            }
            AgentBackendClient.ragQuerySync(query) ?: "无法执行 RAG 查询"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun extractParam(actionInput: String): String {
        val keys = listOf("query", "keyword", "param")
        keys.forEach { key ->
            val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
            val value = regex.find(actionInput)?.groupValues?.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return actionInput.trim().removeSurrounding("{", "}")
    }
}
