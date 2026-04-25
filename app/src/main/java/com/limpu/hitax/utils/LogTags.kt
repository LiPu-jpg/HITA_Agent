package com.limpu.hitax.utils

/**
 * 统一的TAG常量管理
 * 确保日志记录的一致性和可过滤性
 */
object LogTags {

    // ========== 核心组件TAG ==========
    const val APP = "HITA_App"
    const val DATABASE = "HITA_DB"
    const val NETWORK = "HITA_Net"
    const val UI = "HITA_UI"
    const val SYNC = "HITA_Sync"

    // ========== 功能模块TAG ==========
    const val AGENT = "HITA_Agent"
    const val TIMETABLE = "HITA_Timetable"
    const val SUBJECT = "HITA_Subject"
    const val EAS = "HITA_EAS"
    const val NEWS = "HITA_News"
    const val RESOURCE = "HITA_Resource"
    const val USER = "HITA_User"

    // ========== UI组件TAG ==========
    const val WIDGET = "HITA_Widget"
    const val ADAPTER = "HITA_Adapter"
    const val FRAGMENT = "HITA_Fragment"
    const val DIALOG = "HITA_Dialog"

    // ========== 数据处理TAG ==========
    const val PARSER = "HITA_Parser"
    const val CONVERTER = "HITA_Converter"
    const val SERIALIZER = "HITA_Serializer"

    // ========== 性能监控TAG ==========
    const val PERFORMANCE = "HITA_Perf"
    const val MEMORY = "HITA_Memory"
    const val CACHE = "HITA_Cache"

    // ========== 调试专用TAG ==========
    const val DEBUG = "HITA_Debug"
    const val TEST = "HITA_Test"

    /**
     * 为类生成标准的TAG
     * 格式：HITA_ClassName
     */
    fun forClass(className: String): String {
        return "HITA_$className"
    }

    /**
     * 为类生成标准的TAG
     * 重载版本，接受任意类型
     */
    inline fun <reified T : Any> forClass(): String {
        return "HITA_${T::class.java.simpleName}"
    }

    /**
     * 获取模块化的TAG
     * 根据包名自动生成TAG
     */
    fun getModuleTag(packageName: String): String {
        return when {
            packageName.contains("agent") -> AGENT
            packageName.contains("timetable") -> TIMETABLE
            packageName.contains("subject") -> SUBJECT
            packageName.contains("eas") -> EAS
            packageName.contains("news") -> NEWS
            packageName.contains("resource") -> RESOURCE
            packageName.contains("user") -> USER
            packageName.contains("widget") -> WIDGET
            packageName.contains("adapter") -> ADAPTER
            packageName.contains("fragment") -> FRAGMENT
            else -> APP
        }
    }
}
