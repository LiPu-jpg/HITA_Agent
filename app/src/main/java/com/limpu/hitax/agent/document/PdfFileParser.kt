package com.limpu.hitax.agent.document

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 文件解析器
 *
 * 功能：
 * - 提取纯文本内容
 * - 处理文字型 PDF（非扫描件）
 * - 支持限制页数避免内存溢出
 * - 支持单双周标识清理
 *
 * 限制：
 * - 无法提取扫描型 PDF 中的文字（需要 OCR）
 * - 复杂排版可能影响文本提取质量
 */
class PdfFileParser : FileParser {

    companion object {
        private const val TAG = "PdfFileParser"
        private const val MAX_PAGES_TO_PARSE = 10  // 最多解析10页，避免内存问题
        private const val MAX_TEXT_LENGTH = 5000    // 最大文本长度
    }

    override val supportedExtensions = listOf("pdf")
    override val supportedMimeTypes = listOf("application/pdf")

    override suspend fun parse(context: Context, uri: Uri, fileName: String): ParseResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始解析PDF: $fileName")

                // 1. 检查文件大小
                val fileSize = getFileSize(context, uri)
                if (fileSize > FileSizeLimits.MAX_PDF_FILE_SIZE) {
                    return@withContext ParseResult.Error(
                        message = "PDF文件过大 (${formatFileSize(fileSize)})，最大支持 ${formatFileSize(FileSizeLimits.MAX_PDF_FILE_SIZE)}",
                        isRetryable = false
                    )
                }

                // 2. 保存到临时文件
                val tempFile = copyToTempFile(context, uri, fileName)

                // 3. 使用 PDFBox 解析
                PDDocument.load(tempFile).use { document ->
                    val totalPages = document.numberOfPages
                    Log.d(TAG, "PDF总页数: $totalPages")

                    if (totalPages == 0) {
                        return@withContext ParseResult.Error("PDF文件为空")
                    }

                    // 限制解析页数
                    val pagesToParse = minOf(totalPages, MAX_PAGES_TO_PARSE)

                    // 提取文本
                    val stripper = PDFTextStripper().apply {
                        startPage = 1
                        endPage = pagesToParse
                    }

                    val extractedText = stripper.getText(document)
                    Log.d(TAG, "提取文本长度: ${extractedText.length}")

                    // 清理文本（移除多余空白）
                    val cleanedText = cleanExtractedText(extractedText)

                    // 限制长度
                    val finalText = if (cleanedText.length > MAX_TEXT_LENGTH) {
                        cleanedText.take(MAX_TEXT_LENGTH) + "\n\n...(内容过长，仅显示前${MAX_TEXT_LENGTH}字)"
                    } else {
                        cleanedText
                    }

                    // 构建元数据
                    val metadata = ParseResult.Metadata(
                        pageCount = totalPages,
                        wordCount = finalText.count { it.isLetter() }
                    )

                    ParseResult.Success(
                        text = "【PDF文档】\n页数：${totalPages}${if (totalPages > MAX_PAGES_TO_PARSE) "（仅解析前${MAX_PAGES_TO_PARSE}页）" else ""}\n\n内容：\n$finalText",
                        metadata = metadata
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "PDF解析失败", e)
                ParseResult.Error(
                    message = "PDF解析失败: ${e.message}",
                    cause = e,
                    isRetryable = false
                )
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "内存不足", e)
                ParseResult.Error(
                    message = "内存不足，请尝试更小的PDF文件",
                    cause = e,
                    isRetryable = true
                )
            } finally {
                // 清理临时文件
                cleanupTempFile(context, fileName)
            }
        }
    }

    /**
     * 清理提取的文本
     */
    private fun cleanExtractedText(text: String): String {
        return text
            .replace("\u00A0", " ")           // 替换不间断空格
            .replace(Regex("\\s+"), " ")      // 合并多个空白字符
            .replace(Regex("\\n\\s*\\n"), "\n") // 移除空行
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
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
