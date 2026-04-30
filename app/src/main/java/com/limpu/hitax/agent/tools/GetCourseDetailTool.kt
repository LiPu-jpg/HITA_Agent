package com.limpu.hitax.agent.tools

import com.limpu.hitax.agent.remote.AgentBackendClient

class GetCourseDetailTool : ReActTool {
    override fun execute(input: ReActToolInput): String {
        val courseCode = extractCourseCode(input.actionInput)
        if (courseCode.isBlank()) return "获取详情失败: 无法解析课程代码"

        return try {
            val response = AgentBackendClient.readCourseSync(courseCode)
            if (!response.ok) {
                return "获取详情失败: ${response.error?.message ?: "未知错误"}"
            }

            val course = when (val raw = response.course ?: response.result) {
                is Map<*, *> -> raw
                else -> null
            } ?: return "课程详情（$courseCode）：\n暂无详细信息"

            val courseName = valueOf(course, "course_name", "name", "title")
            val teacher = extractTeacherSummary(course)
            val markdown = extractMarkdown(course)
            val summary = valueOf(course, "summary", "description", "intro")

            buildString {
                append("课程详情（")
                append(if (courseName.isNotBlank()) courseName else courseCode)
                append("）")
                if (teacher.isNotBlank()) {
                    append("\n教师：")
                    append(teacher)
                }
                append("\n\n")
                when {
                    markdown.isNotBlank() -> append(markdown.take(4000))
                    summary.isNotBlank() -> append(summary)
                    else -> append("暂无详细信息")
                }
            }
        } catch (e: Exception) {
            "获取详情失败: ${e.message}"
        }
    }

    private fun extractCourseCode(actionInput: String): String {
        val keys = listOf("course_code", "code", "param")
        keys.forEach { key ->
            val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
            val value = regex.find(actionInput)?.groupValues?.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return actionInput.trim().removeSurrounding("{", "}")
    }

    private fun extractMarkdown(course: Map<*, *>): String {
        val rawContent = course["raw_content"] as? Map<*, *>
        return listOf(
            rawContent?.get("fit_markdown"),
            rawContent?.get("content"),
            course["readme_md"],
            course["markdown"],
        ).firstNotNullOfOrNull {
            it?.toString()?.trim()?.takeIf { value -> value.isNotBlank() }
        }.orEmpty()
    }

    private fun extractTeacherSummary(course: Map<*, *>): String {
        val teachers = course["teachers"]
        return when (teachers) {
            is List<*> -> teachers.joinToString("/") { it.toString() }.trim('/')
            else -> valueOf(course, "teacher", "lecturer_name")
        }
    }

    private fun valueOf(map: Map<*, *>, vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            map[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }
}
