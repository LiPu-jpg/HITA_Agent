package com.limpu.hitax.agent.document

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.limpu.hitax.utils.LogUtils

/**
 * AgentChat 专用的文件处理辅助类
 *
 * 功能：
 * - 统一的文件处理接口
 * - 本地解析和云端处理的自动路由
 * - Base64 编码（用于图片/视频）
 * - 详细的日志记录
 */
object AgentChatFileParser {

    private const val TAG = "[FILE-PARSER]"

    /**
     * 处理附件文件
     *
     * @return 文件处理结果
     */
    suspend fun processAttachment(
        context: Context,
        uri: Uri,
        fileName: String,
        userText: String
    ): FileProcessResult {
        return withContext(Dispatchers.IO) {
            LogUtils.d( "========== 开始处理附件 ==========")
            LogUtils.d( "📝 用户输入: ${userText.take(50)}...")
            LogUtils.d( "📁 文件名: $fileName")
            LogUtils.d( "🔗 URI: $uri")

            try {
                // 1. 使用新的文件解析调度器
                val dispatcher = FileParserDispatcher()
                val parseResult = dispatcher.parse(context, uri, fileName)

                when (parseResult) {
                    is ParseResult.Success -> {
                        LogUtils.d( "✅ 本地解析成功")
                        LogUtils.d( "📝 文本长度: ${parseResult.text.length}")

                        FileProcessResult.LocalParsed(
                            text = userText,
                            fileName = fileName,
                            content = parseResult.text,
                            metadata = parseResult.metadata
                        )
                    }

                    is ParseResult.NeedCloud -> {
                        LogUtils.d( "📤 需要云端处理: ${parseResult.reason}")

                        // 读取文件并编码
                        val tempFile = File(context.cacheDir, fileName)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }

                        val fileBytes = tempFile.readBytes()
                        LogUtils.d( "📊 文件大小: ${fileBytes.size} bytes")

                        val base64Content = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                        LogUtils.d( "✅ Base64编码完成: ${base64Content.length} 字符")

                        val mimeType = getMimeType(fileName)

                        FileProcessResult.NeedCloud(
                            text = userText,
                            fileName = fileName,
                            base64Content = base64Content,
                            mimeType = mimeType ?: "application/octet-stream"
                        )
                    }

                    is ParseResult.Error -> {
                        LogUtils.e( "❌ 解析失败: ${parseResult.message}")

                        FileProcessResult.Error(
                            message = parseResult.message,
                            cause = parseResult.cause,
                            isRetryable = parseResult.isRetryable
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtils.e( "❌ 附件处理异常", e)
                FileProcessResult.Error(
                    message = "附件处理失败: ${e.message}",
            cause = e,
            isRetryable = false
                )
            }
        }
    }

    /**
     * 获取 MIME 类型
     */
    private fun getMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
            fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            fileName.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            else -> null
        }
    }
}

/**
 * 文件处理结果
 */
sealed class FileProcessResult {
    /** 本地解析成功 */
    data class LocalParsed(
        val text: String,
        val fileName: String,
        val content: String,
        val metadata: ParseResult.Metadata
    ) : FileProcessResult()

    /** 需要云端处理 */
    data class NeedCloud(
        val text: String,
        val fileName: String,
        val base64Content: String,
        val mimeType: String
    ) : FileProcessResult()

    /** 处理失败 */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRetryable: Boolean = false
    ) : FileProcessResult()
}
