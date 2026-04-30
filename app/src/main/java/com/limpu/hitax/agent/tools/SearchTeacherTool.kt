package com.limpu.hitax.agent.tools

import com.limpu.hitax.agent.remote.AgentBackendClient

class SearchTeacherTool : ReActTool {
    override fun execute(input: ReActToolInput): String? {
        return try {
            val query = extractParam(input.actionInput)
            if (query.isBlank()) {
                return "教师搜索失败: 缺少教师姓名"
            }
            AgentBackendClient.searchTeacherSync(query) ?: "无法执行教师搜索"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun extractParam(actionInput: String): String {
        val keys = listOf("name", "teacher_name", "param")
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
