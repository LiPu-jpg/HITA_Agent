package com.stupidtree.hitax.agent.document

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * DOCX 文件解析器（轻量级实现）
 *
 * 原理：
 * - DOCX 本质是 ZIP 压缩包
 * - 文本内容存储在 word/document.xml 中
 * - 直接解压并解析 XML 提取 <w:t> 标签内的文本
 *
 * 优势：
 * - 无需 Apache POI 库，APK 体积小
 * - 解析速度快
 * - 兼容性好
 *
 * 限制：
 * - 只提取纯文本，不保留格式
 * - 无法处理老式 .doc 格式
 */
class DocxFileParser : FileParser {

    companion object {
        private const val TAG = "DocxFileParser"
        private const val MAX_TEXT_LENGTH = 5000
        private const val DOCUMENT_XML_PATH = "word/document.xml"
    }

    override val supportedExtensions = listOf("docx")
    override val supportedMimeTypes = listOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )

    override suspend fun parse(context: Context, uri: Uri, fileName: String): ParseResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始解析DOCX: $fileName")

                // 1. 检查文件大小
                val fileSize = getFileSize(context, uri)
                if (fileSize > FileSizeLimits.MAX_DOCX_FILE_SIZE) {
                    return@withContext ParseResult.Error(
                        message = "DOCX文件过大 (${formatFileSize(fileSize)})",
                        isRetryable = false
                    )
                }

                // 2. 保存到临时文件
                val tempFile = copyToTempFile(context, uri, fileName)

                // 3. 解压并提取文本
                val extractedText = extractTextFromDocx(tempFile)
                Log.d(TAG, "提取文本长度: ${extractedText.length}")

                // 4. 限制长度
                val finalText = if (extractedText.length > MAX_TEXT_LENGTH) {
                    extractedText.take(MAX_TEXT_LENGTH) + "\n\n...(内容过长，仅显示前${MAX_TEXT_LENGTH}字)"
                } else {
                    extractedText
                }

                // 5. 统计信息
                val wordCount = finalText.split(Regex("\\s+")).filter { it.isNotEmpty() }.size

                ParseResult.Success(
                    text = "【Word文档】\n字数：${wordCount}\n\n内容：\n$finalText",
                    metadata = ParseResult.Metadata(wordCount = wordCount)
                )
            } catch (e: Exception) {
                Log.e(TAG, "DOCX解析失败", e)
                ParseResult.Error(
                    message = "DOCX解析失败: ${e.message}",
                    cause = e,
                    isRetryable = false
                )
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "内存不足", e)
                ParseResult.Error(
                    message = "内存不足，请尝试更小的文件",
                    cause = e,
                    isRetryable = true
                )
            } finally {
                cleanupTempFile(context, fileName)
            }
        }
    }

    /**
     * 从 DOCX 文件中提取文本
     */
    private fun extractTextFromDocx(file: File): String {
        val result = StringBuilder()

        Log.d(TAG, "开始解压DOCX文件: ${file.absolutePath}, 文件大小: ${file.length()}")

        ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            var entryCount = 0

            while (entry != null) {
                entryCount++
                Log.d(TAG, "ZIP条目 $entryCount: ${entry.name}, 大小: ${entry.size}")

                if (entry.name == DOCUMENT_XML_PATH) {
                    Log.d(TAG, "找到 word/document.xml，开始提取文本")

                    // 读取 XML 内容
                    val xmlContent = zip.reader().readText()
                    Log.d(TAG, "XML内容长度: ${xmlContent.length}, 预览: ${xmlContent.take(200)}")

                    // 提取 <w:t> 标签内的文本
                    val textPattern = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
                    val matches = textPattern.findAll(xmlContent).toList()

                    Log.d(TAG, "找到 ${matches.size} 个 <w:t> 标签")

                    var inParagraph = false
                    for (match in matches) {
                        val text = match.groupValues[1]
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&")
                            .replace("&quot;", "\"")
                            .replace("&apos;", "'")
                            .trim()

                        if (text.isNotEmpty()) {
                            if (!inParagraph) {
                                if (result.isNotEmpty()) result.append("\n")
                                inParagraph = true
                            }
                            result.append(text).append(" ")
                        }
                    }

                    Log.d(TAG, "提取的文本长度: ${result.length}, 内容: ${result.take(100)}")

                    // 处理段落换行
                    val paragraphPattern = Regex("<w:p[^>]*>.*?</w:p>", RegexOption.DOT_MATCHES_ALL)
                    val paragraphs = paragraphPattern.findAll(xmlContent).count()
                    Log.d(TAG, "段落数量: $paragraphs")

                    if (paragraphs > 1) {
                        // 多段落文档，清理多余空格
                        return postProcessText(result.toString())
                    }

                    break
                }
                entry = zip.nextEntry
            }

            if (entryCount == 0) {
                Log.e(TAG, "ZIP文件中没有找到任何条目！")
            } else {
                Log.e(TAG, "ZIP文件中没有找到 $DOCUMENT_XML_PATH")
            }
        }

        return postProcessText(result.toString())
    }

    /**
     * 后处理提取的文本
     */
    private fun postProcessText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")           // 合并多个空白字符
            .replace(Regex("\\n\\s*\\n"), "\n")  // 移除空行
            .replace(Regex(" (\\p{P})"), "$1")   // 移除标点符号前的空格
            .trim()
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
    }

    private fun copyToTempFile(context: Context, uri: Uri, fileName: String): File {
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private fun cleanupTempFile(context: Context, fileName: String) {
        try {
            val tempFile = File(context.cacheDir, fileName)
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理临时文件失败", e)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
