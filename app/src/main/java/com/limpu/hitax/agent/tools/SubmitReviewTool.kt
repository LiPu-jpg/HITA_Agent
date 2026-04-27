package com.limpu.hitax.agent.tools

import com.limpu.hitax.agent.remote.PrServerClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubmitReviewTool : ReActTool {
    override fun execute(input: ReActToolInput): String? {
        val courseCode = Regex(""""course_code"\s*:\s*"([^"]+)"""").find(input.actionInput)?.groupValues?.get(1) ?: ""
        val content = Regex(""""content"\s*:\s*"([^"]+)"""").find(input.actionInput)?.groupValues?.get(1) ?: ""
        val authorName = Regex(""""author_name"\s*:\s*"([^"]*)"""").find(input.actionInput)?.groupValues?.get(1) ?: "匿名"
        val lecturerName = Regex(""""lecturer_name"\s*:\s*"([^"]*)"""").find(input.actionInput)?.groupValues?.get(1) ?: ""
        val teacherName = Regex(""""teacher_name"\s*:\s*"([^"]*)"""").find(input.actionInput)?.groupValues?.get(1) ?: ""
        val courseName = Regex(""""course_name"\s*:\s*"([^"]*)"""").find(input.actionInput)?.groupValues?.get(1) ?: ""
        val reviewType = Regex(""""review_type"\s*:\s*"([^"]*)"""").find(input.actionInput)?.groupValues?.get(1) ?: "lecturer"
        val title = Regex(""""title"\s*:\s*"([^"]*)"""").find(input.actionInput)?.groupValues?.get(1) ?: "课程评价"

        if (courseCode.isBlank() || content.isBlank()) {
            return "提交评价失败：缺少课程代码或评价内容"
        }

        val author = mapOf(
            "name" to authorName,
            "link" to "",
            "date" to SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date()),
        )

        val ops = when (reviewType) {
            "course" -> {
                if (courseName.isBlank()) {
                    return "提交评价失败：多项目课程评价需要 course_name"
                }
                listOf(
                    mapOf(
                        "op" to "add_section_item",
                        "course_name" to courseName,
                        "title" to title,
                        "content" to content,
                        "author" to author,
                    )
                )
            }
            "course_teacher" -> {
                if (courseName.isBlank() || teacherName.isBlank()) {
                    return "提交评价失败：课程教师评价需要 course_name 和 teacher_name"
                }
                listOf(
                    mapOf(
                        "op" to "add_course_teacher_review",
                        "course_name" to courseName,
                        "teacher_name" to teacherName,
                        "content" to content,
                        "author" to author,
                    )
                )
            }
            "section" -> {
                if (title.isBlank()) {
                    return "提交评价失败：章节内容需要 title"
                }
                listOf(
                    mapOf(
                        "op" to "add_section_item",
                        "title" to title,
                        "content" to content,
                        "author" to author,
                    )
                )
            }
            else -> {
                if (lecturerName.isBlank()) {
                    return "提交评价失败：缺少教师姓名（lecturer_name）"
                }
                listOf(
                    mapOf(
                        "op" to "add_lecturer_review",
                        "lecturer_name" to lecturerName,
                        "content" to content,
                        "author" to author,
                    )
                )
            }
        }

        val result = PrServerClient.submitSync(courseCode, ops)
        if (result.ok) {
            val prUrl = result.data?.pr?.url
            if (!prUrl.isNullOrBlank()) {
                return "评价提交成功！PR 链接：$prUrl"
            } else {
                return "评价提交成功"
            }
        } else {
            return "评价提交失败：${result.error?.message ?: "未知错误"}"
        }
    }
}