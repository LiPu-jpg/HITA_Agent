package com.limpu.hitax.utils

import android.util.Log
import java.io.File
import java.io.FileWriter

/**
 * 日志迁移辅助工具
 * 帮助将旧式日志调用迁移到统一的LogUtils
 */
object LogMigrationHelper {

    private const val MIGRATION_REPORT = "log_migration_report.txt"

    /**
     * 分析文件中的日志调用
     * @param file 要分析的Kotlin文件
     * @return 分析结果
     */
    fun analyzeLogCalls(file: File): LogAnalysisResult {
        val content = file.readText()
        val lines = content.lines()

        var totalLogCalls = 0
        var logWithExplicitTag = 0
        var logWithImplicitTag = 0
        val logCallsByType = mutableMapOf(
            "Log.d" to 0,
            "Log.i" to 0,
            "Log.w" to 0,
            "Log.e" to 0,
            "Log.v" to 0
        )
        val problematicLines = mutableListOf<Int>()

        lines.forEachIndexed { index, line ->
            when {
                line.contains("Log.d(") || line.contains("Log.i(") ||
                line.contains("Log.w(") || line.contains("Log.e(") ||
                line.contains("Log.v(") -> {
                    totalLogCalls++

                    val logType = when {
                        line.contains("Log.d(") -> "Log.d"
                        line.contains("Log.i(") -> "Log.i"
                        line.contains("Log.w(") -> "Log.w"
                        line.contains("Log.e(") -> "Log.e"
                        line.contains("Log.v(") -> "Log.v"
                        else -> "Unknown"
                    }
                    logCallsByType[logType] = (logCallsByType[logType] ?: 0) + 1

                    if (line.contains("\".*\",.*\\)")) {
                        logWithExplicitTag++
                    } else {
                        logWithImplicitTag++
                    }

                    // 标记可能需要特殊处理的行
                    if (line.contains("Log.e") && !line.contains("exception")) {
                        problematicLines.add(index + 1)
                    }
                }
            }
        }

        return LogAnalysisResult(
            fileName = file.name,
            totalLogCalls = totalLogCalls,
            logWithExplicitTag = logWithExplicitTag,
            logWithImplicitTag = logWithImplicitTag,
            logCallsByType = logCallsByType,
            problematicLines = problematicLines
        )
    }

    /**
     * 生成迁移建议
     * @param analysis 分析结果
     * @return 迁移建议
     */
    fun generateMigrationSuggestion(analysis: LogAnalysisResult): String {
        val suggestions = StringBuilder()

        suggestions.appendLine("=== ${analysis.fileName} 迁移建议 ===\n")
        suggestions.appendLine("总日志调用: ${analysis.totalLogCalls}")
        suggestions.appendLine("显式TAG: ${analysis.logWithExplicitTag}")
        suggestions.appendLine("隐式TAG: ${analysis.logWithImplicitTag}")
        suggestions.appendLine("")

        // 按类型统计
        suggestions.appendLine("日志类型分布:")
        analysis.logCallsByType.forEach { (type, count) ->
            suggestions.appendLine("  $type: $count")
        }
        suggestions.appendLine("")

        // 生成迁移示例
        suggestions.appendLine("迁移示例:")
        suggestions.appendLine("----------------")

        if (analysis.problematicLines.isNotEmpty()) {
            suggestions.appendLine("需要注意的行: ${analysis.problematicLines.joinToString(", ")}")
            suggestions.appendLine("")
        }

        return suggestions.toString()
    }

    /**
     * 批量分析项目中的日志调用
     * @param projectDir 项目目录
     * @return 所有分析结果
     */
    fun analyzeProjectLogs(projectDir: File): List<LogAnalysisResult> {
        val kotlinFiles = projectDir.walk()
            .filter { it.extension == "kt" }
            .filter { !it.path.contains("/build/") }
            .toList()

        return kotlinFiles.map { analyzeLogCalls(it) }
    }

    /**
     * 生成迁移报告
     * @param results 分析结果列表
     * @param reportFile 报告文件路径
     */
    fun generateMigrationReport(
        results: List<LogAnalysisResult>,
        reportFile: String = MIGRATION_REPORT
    ) {
        FileWriter(reportFile).use { writer ->
            writer.appendLine("HITA Agent 日志迁移报告")
            writer.appendLine("生成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            writer.appendLine("")
            writer.appendLine("=".repeat(60))
            writer.appendLine("")

            var totalLogCalls = 0

            results.forEach { result ->
                totalLogCalls += result.totalLogCalls
                writer.appendLine(generateMigrationSuggestion(result))
                writer.appendLine("")
            }

            // 按日志调用数排序
            val filesByIssueCount = results
                .map { it.fileName to it.totalLogCalls }
                .sortedByDescending { it.second }

            writer.appendLine("============================================================")
            writer.appendLine("")
            writer.appendLine("项目统计:")
            writer.appendLine("  总文件数: ${results.size}")
            writer.appendLine("  总日志调用: $totalLogCalls")
            writer.appendLine("")
            writer.appendLine("需要迁移的文件 (按日志调用数排序):")
            filesByIssueCount.forEach { (fileName, count) ->
                writer.appendLine("  $fileName: $count 个日志调用")
            }
        }
    }

    /**
     * 日志分析结果数据类
     */
    data class LogAnalysisResult(
        val fileName: String,
        val totalLogCalls: Int,
        val logWithExplicitTag: Int,
        val logWithImplicitTag: Int,
        val logCallsByType: Map<String, Int>,
        val problematicLines: List<Int>
    )
}
