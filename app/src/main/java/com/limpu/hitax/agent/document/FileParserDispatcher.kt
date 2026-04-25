package com.limpu.hitax.agent.document

import android.content.Context
import com.limpu.hitax.utils.LogUtils
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文件解析调度器
 *
 * 功能：
 * - 根据文件扩展名和 MIME 类型自动选择合适的解析器
 * - 支持本地解析和云端处理的智能路由
 * - 统一的错误处理和日志记录
 *
 * 使用示例：
 * ```
 * val dispatcher = FileParserDispatcher.getInstance()
 * val result = dispatcher.parse(context, uri, fileName)
 * when (result) {
 *     is ParseResult.Success -> // 处理成功结果
 *     is ParseResult.NeedCloud -> // 走云端API
 *     is ParseResult.Error -> // 显示错误
 * }
 * ```
 */
class FileParserDispatcher private constructor() {

    companion object {
        private const val TAG = "FileParserDispatcher"

        @Volatile
        private var instance: FileParserDispatcher? = null

        fun getInstance(): FileParserDispatcher {
            return instance ?: synchronized(this) {
                instance ?: FileParserDispatcher().also { instance = it }
            }
        }
    }

    /**
     * 注册的解析器列表
     */
    private val parsers = listOf<FileParser>(
        PdfFileParser(),
        DocxFileParser(),
        ExcelFileParser(),
        PowerPointFileParser(),
        TextFileParser()
    )

    /**
     * 图片和视频的扩展名
     */
    private val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    private val videoExtensions = listOf("mp4", "mov", "avi", "mkv", "webm")

    /**
     * 解析文件
     *
     * @param context 上下文
     * @param uri 文件 URI
     * @param fileName 文件名
     * @return 解析结果
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun parse(context: Context, uri: Uri, fileName: String): ParseResult {
        return withContext(Dispatchers.IO) {
            try {
                LogUtils.d( "========== 开始文件解析 ==========")
                LogUtils.d( "文件名: $fileName")
                LogUtils.d( "URI: $uri")

                // 1. 提取文件扩展名
                val extension = fileName.substringAfterLast('.', "").lowercase()
                LogUtils.d( "扩展名: $extension")

                // 2. 获取 MIME 类型
                val mimeType = getMimeType(context, uri, fileName, extension)
                LogUtils.d( "MIME类型: $mimeType")

                // 3. 检查文件大小
                val fileSize = getFileSize(context, uri)
                LogUtils.d( "文件大小: ${formatFileSize(fileSize)}")

                if (fileSize == 0L) {
                    return@withContext ParseResult.Error("文件为空或无法读取")
                }

                // 4. 判断是否需要云端处理
                if (imageExtensions.contains(extension) || mimeType?.startsWith("image/") == true) {
                    LogUtils.d( "判定为图片文件，需要云端AI处理")
                    return@withContext ParseResult.NeedCloud(
                        reason = "图片文件需要云端AI识别",
                        suggestedCloudParser = "ai_vision"
                    )
                }

                if (videoExtensions.contains(extension) || mimeType?.startsWith("video/") == true) {
                    LogUtils.d( "判定为视频文件，需要云端AI处理")
                    return@withContext ParseResult.NeedCloud(
                        reason = "视频文件需要云端AI识别",
                        suggestedCloudParser = "ai_video"
                    )
                }

                // 5. 查找合适的本地解析器
                val parser = findParser(extension, mimeType)
                if (parser == null) {
                    LogUtils.d( "未找到合适的本地解析器")
                    return@withContext ParseResult.NeedCloud(
                        reason = "不支持的文件类型，建议使用云端解析",
                        suggestedCloudParser = "general_cloud"
                    )
                }

                LogUtils.d( "使用解析器: ${parser::class.simpleName}")

                // 6. 执行解析
                val result = parser.parse(context, uri, fileName)

                LogUtils.d( "解析结果: ${result::class.simpleName}")
                LogUtils.d( "========== 文件解析完成 ==========")

                result
            } catch (e: Exception) {
                LogUtils.e( "文件解析异常", e)
                ParseResult.Error(
                    message = "文件解析失败: ${e.message}",
                    cause = e,
                    isRetryable = false
                )
            }
        }
    }

    /**
     * 查找合适的解析器
     */
    private fun findParser(extension: String, mimeType: String?): FileParser? {
        return parsers.firstOrNull { it.supports(extension, mimeType) }
    }

    /**
     * 获取 MIME 类型
     */
    private fun getMimeType(context: Context, uri: Uri, fileName: String, extension: String): String? {
        // 1. 从 ContentResolver 获取
        val mimeTypeFromResolver = context.contentResolver.getType(uri)
        if (mimeTypeFromResolver != null && mimeTypeFromResolver != "application/octet-stream") {
            return mimeTypeFromResolver
        }

        // 2. 从文件扩展名获取
        val mimeTypeFromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (mimeTypeFromExtension != null) {
            return mimeTypeFromExtension
        }

        // 3. 默认值
        return null
    }

    /**
     * 获取文件大小
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
        } catch (e: Exception) {
            LogUtils.e("获取文件大小失败", e)
            0L
        }
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * 检查是否支持本地解析
     */
    fun supportsLocalParsing(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return findParser(extension, mimeType) != null
    }

    /**
     * 检查是否需要云端处理
     */
    fun needsCloudProcessing(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return imageExtensions.contains(extension) ||
               videoExtensions.contains(extension) ||
               mimeType?.startsWith("image/") == true ||
               mimeType?.startsWith("video/") == true
    }
}
