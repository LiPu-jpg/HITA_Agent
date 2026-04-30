package com.limpu.hitax.utils

import android.content.Context
import android.net.Uri
import java.io.Closeable
import java.io.File
import com.limpu.hitax.utils.LogUtils

/**
 * 资源清理工具类
 * 用于自动管理临时文件和IO资源
 */
object ResourceCleaner {

    /**
     * 清理应用缓存目录中的临时文件
     * @param context 应用上下文
     * @param maxAgeMs 文件最大年龄（毫秒），超过此年龄的文件将被删除
     * @return 清理的文件数量
     */
    fun cleanOldCacheFiles(context: Context, maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L): Int {
        val cacheDir = context.cacheDir ?: return 0
        val currentTime = System.currentTimeMillis()
        var cleanedCount = 0

        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < currentTime - maxAgeMs) {
                    if (file.delete()) {
                        cleanedCount++
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.e("Failed to clean old cache files", e)
        }

        return cleanedCount
    }

    /**
     * 清理指定目录
     * @param directory 要清理的目录
     * @return 是否清理成功
     */
    fun cleanDirectory(directory: File?): Boolean {
        if (directory == null || !directory.exists()) return false

        return try {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    cleanDirectory(file)
                } else {
                    file.delete()
                }
            }
            true
        } catch (e: Exception) {
            LogUtils.e("Failed to clean directory: ${directory?.absolutePath}", e)
            false
        }
    }

    /**
     * 安全地关闭Closeable资源
     * @param closeable 要关闭的资源
     */
    fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (e: Exception) {
            // 忽略关闭异常
        }
    }

    /**
     * 安全地删除文件
     * @param file 要删除的文件
     * @return 是否删除成功
     */
    fun safeDelete(file: File?): Boolean {
        if (file == null || !file.exists()) return false

        return try {
            file.delete()
        } catch (e: Exception) {
            LogUtils.e("Failed to delete file: ${file?.absolutePath}", e)
            false
        }
    }

    /**
     * 清理URI对应的临时文件
     * @param context 应用上下文
     * @param uri 文件URI
     * @return 是否清理成功
     */
    fun cleanUriTempFile(context: Context, uri: Uri): Boolean {
        // 这里可以根据URI的scheme来判断是否需要清理
        // 例如：content:// URI可能不需要清理，而file:// URI可能需要
        return try {
            val path = uri.path
            if (path != null && path.startsWith(context.cacheDir.path)) {
                val file = File(path)
                safeDelete(file)
            } else {
                false
            }
        } catch (e: Exception) {
            LogUtils.e("Failed to clean URI temp file: $uri", e)
            false
        }
    }

    /**
     * 获取缓存目录的大小
     * @param context 应用上下文
     * @return 缓存大小（字节）
     */
    fun getCacheSize(context: Context): Long {
        val cacheDir = context.cacheDir ?: return 0
        return getDirectorySize(cacheDir)
    }

    /**
     * 获取目录大小
     * @param directory 目录
     * @return 大小（字节）
     */
    private fun getDirectorySize(directory: File): Long {
        var size = 0L
        try {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirectorySize(file)
                } else {
                    file.length()
                }
            }
        } catch (e: Exception) {
            LogUtils.e("Failed to calculate directory size: ${directory.absolutePath}", e)
        }
        return size
    }

    /**
     * 格式化文件大小
     * @param size 字节大小
     * @return 格式化后的字符串（如 "1.5 MB"）
     */
    fun formatFileSize(size: Long): String {
        if (size < 1024) return "$size B"
        if (size < 1024 * 1024) return "%.1f KB".format(size / 1024.0)
        if (size < 1024 * 1024 * 1024) return "%.1f MB".format(size / (1024.0 * 1024.0))
        return "%.1f GB".format(size / (1024.0 * 1024.0 * 1024.0))
    }
}
