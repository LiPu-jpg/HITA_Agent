package com.limpu.hitax.data.model.eas

import com.google.gson.Gson

/**
 * 考试条目 - 统一的考试数据结构
 *
 * 重要：这是三个校区（本部、深圳、威海）统一使用的考试数据模型
 *
 * 字段说明：
 * - termId: 关联的学期ID，格式 "yearCode-termCode"，用于学期匹配
 * - termName: 学期显示名称，如 "2026春季"，仅用于显示
 * - campusName: 校区名称，如 "深圳校区"、"本部"、"威海校区"
 *
 * 数据匹配规则：
 * 1. 使用 termId 与 TermItem.id 进行精确匹配
 * 2. 不要使用 termName 进行匹配，因为不同校区的命名可能不同
 *
 * 修改此数据结构时需要同步修改的地方：
 * - EASWebSource.kt: 深圳校区考试解析，设置termId
 * - BenbuEASWebSource.kt: 本部考试解析，设置termId
 * - WeihaiEASWebSource.kt: 威海校区考试解析（暂不支持）
 * - ExamViewModel.kt: 考试筛选逻辑
 */
class ExamItem {
    var courseName:String? = null  // 课程名称
    var examDate:String? = null  // 考试日期，格式 "YYYY-MM-DD"
    var examTime:String? = null  // 考试时间，格式 "HH:MM-HH:MM"
    var examType:String? = null  // 考试类型，如 "期中"、"期末"
    var examLocation:String? = null  // 考试地点，如 "教学楼V T5507"
    var termName:String? = null  // 学期名称（显示用），如 "2026春季"
    var termId:String? = null  // **重要**学期ID（匹配用），格式 "2025-2026-2"
    var campusName:String? = null  // 校区名称，如 "深圳校区"

    override fun toString(): String {
        return Gson().toJson(this)
    }
}
