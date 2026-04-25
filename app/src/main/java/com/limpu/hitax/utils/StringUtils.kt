package com.limpu.hitax.utils

import android.content.Context
import androidx.annotation.StringRes

/**
 * 字符串资源工具类
 * 统一管理应用中的字符串资源，避免硬编码
 */
object StringUtils {

    /**
     * 获取字符串资源
     * 统一的字符串获取方式
     */
    fun getString(context: Context, @StringRes resId: Int): String {
        return context.getString(resId)
    }

    /**
     * 获取格式化字符串资源
     */
    fun getString(context: Context, @StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }

    /**
     * 检查字符串是否为空或空白
     */
    fun isBlank(text: String?): Boolean {
        return text.isNullOrBlank()
    }

    /**
     * 安全的字符串截取
     */
    fun safeSubstring(text: String?, maxLength: Int, suffix: String = "..."): String {
        if (text == null) return ""
        if (text.length <= maxLength) return text
        return text.substring(0, maxLength) + suffix
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * 格式化时间间隔
     */
    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}天${hours % 24}小时"
            hours > 0 -> "${hours}小时${minutes % 60}分钟"
            minutes > 0 -> "${minutes}分钟${seconds % 60}秒"
            else -> "${seconds}秒"
        }
    }

    /**
     * 验证邮箱格式
     */
    fun isValidEmail(email: String?): Boolean {
        return email?.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) ?: false
    }

    /**
     * 验证手机号格式（中国大陆）
     */
    fun isValidPhoneNumber(phone: String?): Boolean {
        return phone?.matches(Regex("^1[3-9]\\d{9}$")) ?: false
    }

    /**
     * 脱敏处理
     */
    fun maskSensitiveInfo(text: String?, maskChar: Char = '*'): String? {
        if (text == null) return null

        return when {
            // 手机号脱敏：保留前3后4位
            text.matches(Regex("^1[3-9]\\d{9}$")) -> {
                text.substring(0, 3) + maskChar.toString().repeat(4) + text.substring(7)
            }
            // 邮箱脱敏：保留前2后4位
            text.contains("@") -> {
                val parts = text.split("@")
                val name = parts[0]
                val domain = parts[1]
                if (name.length > 2) {
                    name.substring(0, 2) + maskChar.toString().repeat(name.length - 2) + "@" + domain
                } else {
                    text
                }
            }
            // 其他情况：中间脱敏
            text.length > 4 -> {
                text.substring(0, 2) + maskChar.toString().repeat(text.length - 4) + text.substring(text.length - 2)
            }
            else -> text
        }
    }

    /**
     * 清理用户输入
     */
    fun sanitizeInput(input: String?): String {
        if (input == null) return ""
        return input.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * 高亮关键词
     */
    fun highlightKeywords(text: String, keywords: List<String>, highlightColor: String = "#FFFF00"): String {
        var result = text
        keywords.forEach { keyword ->
            if (keyword.isNotEmpty()) {
                result = result.replace(
                    Regex(keyword, RegexOption.IGNORE_CASE),
                    "<span style='background-color:$highlightColor'>$0</span>"
                )
            }
        }
        return result
    }

    /**
     * 生成唯一标识符
     */
    fun generateUniqueId(prefix: String = ""): String {
        val timestamp = System.currentTimeMillis()
        val random = (0..9999).random()
        return "${prefix}_${timestamp}_$random"
    }

    /**
     * 比较版本号
     * @return 1 if version1 > version2, -1 if version1 < version2, 0 if equal
     */
    fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }
}
