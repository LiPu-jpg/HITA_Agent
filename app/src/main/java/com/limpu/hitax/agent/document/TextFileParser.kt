package com.limpu.hitax.agent.document

import android.content.Context
import android.net.Uri
import com.limpu.hitax.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/**
 * 纯文本文件解析器
 *
 * 支持的格式：
 * - TXT
 * - Markdown (.md, .markdown)
 * - CSV
 * - JSON
 * - XML
 * - HTML
 *
 * 功能：
 * - 自动检测编码（UTF-8, GBK, GB2312）
 * - 移除 BOM 标记
 * - 限制文件大小
 */
class TextFileParser : FileParser {

    companion object {
        private const val MAX_TEXT_LENGTH = 5000
        private const val BOM_UTF8 = "\uFEFF"
    }

    override val supportedExtensions = listOf("txt", "md", "markdown", "csv", "json", "xml", "html", "htm")
    override val supportedMimeTypes = listOf(
        "text/plain",
        "text/markdown",
        "text/csv",
        "application/json",
        "application/xml",
        "text/html",
        "text/xml"
    )

    override suspend fun parse(context: Context, uri: Uri, fileName: String): ParseResult {
        return withContext(Dispatchers.IO) {
            try {
                LogUtils.d("开始解析文本文件: $fileName")

                // 1. 检查文件大小
                val fileSize = getFileSize(context, uri)
                if (fileSize > FileSizeLimits.MAX_TEXT_FILE_SIZE) {
                    return@withContext ParseResult.Error(
                        message = "文本文件过大 (${formatFileSize(fileSize)})，最大支持 ${formatFileSize(FileSizeLimits.MAX_TEXT_FILE_SIZE)}",
                        isRetryable = false
                    )
                }

                // 2. 读取文件
                val text = readTextFile(context, uri, fileSize)
                LogUtils.d("读取文本长度: ${text.length}")

                // 3. 判断文件类型
                val extension = fileName.substringAfterLast('.', "").lowercase()
                val fileLabel = when (extension) {
                    "md", "markdown" -> "【Markdown文档】"
                    "csv" -> "【CSV表格】"
                    "json" -> "【JSON数据】"
                    "xml", "html", "htm" -> "【${extension.uppercase()}文档】"
                    else -> "【文本文件】"
                }

                // 4. 限制长度
                val finalText = if (text.length > MAX_TEXT_LENGTH) {
                    text.take(MAX_TEXT_LENGTH) + "\n\n...(内容过长，仅显示前${MAX_TEXT_LENGTH}字)"
                } else {
                    text
                }

                // 5. 统计信息
                val wordCount = finalText.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                val lineCount = finalText.split('\n').size

                ParseResult.Success(
                    text = "$fileLabel\n行数：${lineCount}\n字数：${wordCount}\n\n内容：\n$finalText",
                    metadata = ParseResult.Metadata(
                        wordCount = wordCount
                    )
                )
            } catch (e: Exception) {
                LogUtils.e("文本文件解析失败", e)
                ParseResult.Error(
                    message = "文本文件解析失败: ${e.message}",
                    cause = e,
                    isRetryable = false
                )
            }
        }
    }

    /**
     * 读取文本文件，自动检测编码
     */
    @Suppress("UNUSED_PARAMETER")
    private fun readTextFile(context: Context, uri: Uri, fileSize: Long): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取文件")

        // 尝试不同编码
        val encodings = listOf(
            Charsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB2312"),
            Charsets.UTF_16LE,
            Charsets.UTF_16BE
        )

        for (encoding in encodings) {
            try {
                var text = String(bytes, encoding)

                // 移除 UTF-8 BOM
                if (encoding == Charsets.UTF_8 && text.startsWith(BOM_UTF8)) {
                    text = text.substring(1)
                }

                // 简单验证：检查是否包含大量非法字符
                if (isValidText(text)) {
                    LogUtils.d("使用编码: ${encoding.name()}")
                    return text
                }
            } catch (e: Exception) {
                // 尝试下一个编码
                continue
            }
        }

        // 如果所有编码都失败，使用 UTF-8 并替换非法字符
        return String(bytes, Charsets.UTF_8).map { c ->
            when {
                c.code in 0x20..0x7E || c.code in 0x4E00..0x9FFF -> c  // ASCII 或中文
                c == '\n' || c == '\r' || c == '\t' -> c  // 换行、制表符
                else -> ' '  // 其他字符替换为空格
            }
        }.joinToString("")
    }

    /**
     * 简单验证文本是否有效
     */
    private fun isValidText(text: String): Boolean {
        // 统计可打印字符的比例
        val printableChars = text.count { c ->
            c.code in 0x20..0x7E || c.code in 0x4E00..0x9FFF || c == '\n' || c == '\r' || c == '\t'
        }
        val ratio = if (text.isNotEmpty()) printableChars.toFloat() / text.length else 0f
        return ratio > 0.7f  // 至少70%是可打印字符
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
