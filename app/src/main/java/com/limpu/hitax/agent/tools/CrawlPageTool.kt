package com.limpu.hitax.agent.tools

import com.limpu.hitax.agent.remote.AgentBackendClient

class CrawlPageTool : ReActTool {
    override fun execute(input: ReActToolInput): String? {
        return try {
            val url = extractParam(input.actionInput)
            AgentBackendClient.crawlPageSync(url) ?: "无法获取页面内容"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun extractParam(actionInput: String): String {
        val regex = Regex(""""param"\s*:\s*"([^"]+)"""")
        return regex.find(actionInput)?.groupValues?.get(1) ?: actionInput.trim()
    }
}