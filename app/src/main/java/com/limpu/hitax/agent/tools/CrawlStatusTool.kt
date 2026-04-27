package com.limpu.hitax.agent.tools

import com.limpu.hitax.agent.remote.AgentBackendClient

class CrawlStatusTool : ReActTool {
    override fun execute(input: ReActToolInput): String? {
        return try {
            val taskId = extractTaskId(input.actionInput)
            AgentBackendClient.crawlStatusSync(taskId) ?: "无法获取爬取状态"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun extractTaskId(actionInput: String): String {
        val regex = Regex(""""task_id"\s*:\s*"([^"]+)"""")
        return regex.find(actionInput)?.groupValues?.get(1) ?: actionInput.trim()
    }
}