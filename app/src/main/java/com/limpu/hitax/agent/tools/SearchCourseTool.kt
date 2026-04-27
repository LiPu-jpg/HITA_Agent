package com.limpu.hitax.agent.tools

import org.jsoup.Connection
import org.json.JSONObject

class SearchCourseTool : ReActTool {
    override fun execute(input: ReActToolInput): String {
        val queryRegex = Regex("""query"\s*:\s*"([^"]+)""")
        val match = queryRegex.find(input.actionInput) ?: return "搜索失败: 无法解析查询条件"
        val keyword = match.groupValues[1]

        val body = JSONObject().apply {
            put("keyword", keyword.trim())
            put("campus", "shenzhen")
        }

        return try {
            val resp = ToolHelper.prServerRequest("https://example.com/v1/courses:search")
                .requestBody(body.toString())
                .method(Connection.Method.POST)
                .execute()
            val json = JSONObject(resp.body())
            val results = json.optJSONObject("data")?.optJSONArray("results") ?: return "未找到相关课程"

            if (results.length() == 0) return "未找到相关课程"

            val formatted = (0 until results.length()).map { i ->
                val item = results.getJSONObject(i)
                val code = item.optString("code", "")
                val name = item.optString("name", "")
                val campus = item.optString("campus", "shenzhen")
                "• $code - $name ($campus)"
            }

            "找到 ${results.length()} 门相关课程:\n${formatted.joinToString("\n")}"
        } catch (e: Exception) {
            "搜索失败: ${e.message}"
        }
    }
}