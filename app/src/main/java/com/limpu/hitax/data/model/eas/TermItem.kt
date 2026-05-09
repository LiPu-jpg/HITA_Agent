package com.limpu.hitax.data.model.eas

/**
 * 学年学期 - 统一的学期数据结构
 *
 * 重要：这是整个EAS系统（考试、成绩、课表等）统一使用的学期数据模型
 *
 * 数据格式说明：
 * - yearCode: 学年代码，格式 "YYYY-YYYY+1"，如 "2025-2026"
 * - termCode: 学期代码，"1"=秋季, "2"=春季, "3"=夏季
 * - id: 统一标识符，格式 "yearCode-termCode"，如 "2025-2026-2"
 *
 * 注意事项：
 * 1. 三个校区（本部、深圳、威海）返回的学期数据格式可能不同
 * 2. 需要在数据获取时统一转换为这个格式
 * 3. id字段用于所有学期匹配逻辑，不要使用name或termName进行匹配
 *
 * 修改此数据结构时需要同步修改的地方：
 * - EASWebSource.kt: 深圳校区学期解析
 * - BenbuEASWebSource.kt: 本部学期解析
 * - WeihaiEASWebSource.kt: 威海校区学期解析
 * - ExamViewModel.kt, ScoreViewModel.kt 等所有使用学期的ViewModel
 */
class TermItem(
     var yearCode: String,  // 学年代码，如 "2025-2026" [统一格式]
     var yearName: String,  // 学年名称，如 "2025-2026" [统一格式]
     var termCode: String,  // 学期代码，如 "1", "2", "3" [统一格式]
     var termName: String   // 学期名称，如 "春季", "秋季", "夏季" [统一格式]
) {
    var name: String = yearName+termName  // 显示名称，如 "2025-2026春季"
    var isCurrent:Boolean = false  // 是否当前学期
    var id: String = ""  // 统一ID，格式 "yearCode-termCode"，如 "2025-2026-2"

    init {
        // 自动生成统一ID
        generateId()
    }

    private fun generateId() {
        id = "$yearCode-$termCode"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TermItem

        // 使用统一ID进行比较
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun getCode():String{
        return yearCode+termCode
    }

}