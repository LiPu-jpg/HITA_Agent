package com.limpu.hitax.agent.tools

import org.jsoup.Connection
import org.json.JSONObject

class GetCourseDetailTool : ReActTool {
    override fun execute(input: ReActToolInput): String {
        val codeRegex = Regex("""course_code"\s*:\s*"([^"]+)""")
        val match = codeRegex.find(input.actionInput) ?: return "获取详情失败: 无法解析课程代码"
        val courseCode = match.groupValues[1]

        val body = JSONObject().apply {
            put("target", JSONObject().apply {
                put("campus", "shenzhen")
                put("course_code", courseCode)
            })
        }

        return try {
            val resp = ToolHelper.prServerRequest("https://example.com/v1/course:read")
                .requestBody(body.toString())
                .method(Connection.Method.POST)
                .execute()
            val json = JSONObject(resp.body())
            val readmeMd = json.optJSONObject("data")
                ?.optJSONObject("result")
                ?.optString("readme_md")
                ?: ""

            if (readmeMd.isBlank()) return "课程详情（$courseCode）：\n暂无详细信息"

            "课程详情（$courseCode）：\n$readmeMd"
        } catch (e: Exception) {
            "获取详情失败: ${e.message}"
        }
    }
}