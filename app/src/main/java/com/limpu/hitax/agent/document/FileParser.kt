package com.limpu.hitax.agent.document

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文件解析结果
 */
sealed class ParseResult {
    /** 成功解析出的文本 */
    data class Success(
        val text: String,
        val metadata: Metadata = Metadata()
    ) : ParseResult()

    /** 需要云端处理 */
    data class NeedCloud(
        val reason: String,
        val suggestedCloudParser: String = "ai_vision"
    ) : ParseResult()

    /** 解析失败 */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRetryable: Boolean = false
    ) : ParseResult()

    /** 元数据 */
    data class Metadata(
        val title: String? = null,
        val author: String? = null,
        val pageCount: Int? = null,
        val wordCount: Int? = null,
        val createdAt: String? = null
    )
}

/**
 * 文件解析器接口
 */
interface FileParser {
    /**
     * 支持的文件扩展名（不含点号，小写）
     */
    val supportedExtensions: List<String>

    /**
     * 支持的 MIME 类型
     */
    val supportedMimeTypes: List<String>

    /**
     * 是否支持该文件
     */
    fun supports(extension: String, mimeType: String?): Boolean {
        return supportedExtensions.contains(extension.lowercase()) ||
               supportedMimeTypes.contains(mimeType)
    }

    /**
     * 解析文件
     */
    suspend fun parse(context: Context, uri: Uri, fileName: String): ParseResult
}

/**
 * 文本提取器接口（用于从解析器中提取纯文本）
 */
interface TextExtractor {
    /**
     * 从文件内容中提取纯文本
     */
    fun extractText(content: String): String
}

/**
 * 文件大小限制
 */
object FileSizeLimits {
    const val MAX_TEXT_FILE_SIZE = 100L * 1024L        // 100KB - 纯文本
    const val MAX_PDF_FILE_SIZE = 20L * 1024L * 1024L   // 20MB
    const val MAX_DOCX_FILE_SIZE = 10L * 1024L * 1024L  // 10MB
    const val MAX_IMAGE_FILE_SIZE = 10L * 1024L * 1024L // 10MB
    const val MAX_VIDEO_FILE_SIZE = 50L * 1024L * 1024L // 50MB

    fun getMaxSizeFor(extension: String): Long {
        return when (extension.lowercase()) {
            "pdf" -> MAX_PDF_FILE_SIZE
            "docx", "xlsx", "pptx" -> MAX_DOCX_FILE_SIZE
            "jpg", "jpeg", "png", "gif", "webp" -> MAX_IMAGE_FILE_SIZE
            "mp4", "mov", "avi", "mkv" -> MAX_VIDEO_FILE_SIZE
            "txt", "md", "csv", "json", "xml" -> MAX_TEXT_FILE_SIZE
            else -> MAX_DOCX_FILE_SIZE
        }
    }
}
