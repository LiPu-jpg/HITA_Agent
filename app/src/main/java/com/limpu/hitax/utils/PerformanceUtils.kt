package com.limpu.hitax.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * 性能优化工具类
 * 提供常用的性能优化模式和工具方法
 */
object PerformanceUtils {

    /**
     * 批量操作优化
     * 将大量操作分批执行，避免内存溢出和ANR
     * @param items 要处理的项目列表
     * @param batchSize 每批次处理的项目数量
     * @param process 处理函数
     * @return 处理结果列表
     */
    suspend fun <T, R> processBatched(
        items: List<T>,
        batchSize: Int = 100,
        process: suspend (List<T>) -> List<R>
    ): List<R> = withContext(Dispatchers.Default) {
        val results = mutableListOf<R>()
        items.chunked(batchSize).forEach { batch ->
            val batchResults = process(batch)
            results.addAll(batchResults)
        }
        results
    }

    /**
     * 并行处理优化
     * 对列表中的项目进行并行处理
     * @param items 要处理的项目列表
     * @param process 处理函数
     * @return 处理结果列表
     */
    suspend fun <T, R> processParallel(
        items: List<T>,
        process: suspend (T) -> R
    ): List<R> = withContext(Dispatchers.Default) {
        items.map { item ->
            async { process(item) }
        }.awaitAll()
    }

    /**
     * 条件处理优化
     * 只在满足条件时执行操作
     * @param condition 条件
     * @param action 要执行的操作
     * @return 是否执行了操作
     */
    inline fun <T> processIf(condition: Boolean, action: () -> T): T? {
        return if (condition) action() else null
    }

    /**
     * 缓存操作结果
     * 用于避免重复计算
     * @param cacheKey 缓存键
     * @param cache 缓存Map
     * @param compute 计算函数
     * @return 计算结果
     */
    fun <K, V> cacheCompute(
        cacheKey: K,
        cache: MutableMap<K, V>,
        compute: () -> V
    ): V {
        return cache.getOrPut(cacheKey) {
            compute()
        }
    }

    /**
     * 性能监控包装器
     * 用于测量操作的执行时间
     * @param operationName 操作名称
     * @param operation 要执行的操作
     * @return 操作结果
     */
    suspend fun <T> measurePerformance(
        operationName: String,
        operation: suspend () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        return try {
            val result = operation()
            val duration = System.currentTimeMillis() - startTime
            if (duration > 1000) {
                LogUtils.w("$operationName took ${duration}ms (SLOW)")
            } else {
                LogUtils.d("$operationName took ${duration}ms")
            }
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            LogUtils.e("$operationName failed after ${duration}ms", e)
            throw e
        }
    }

    /**
     * 内存使用监控
     * 打印当前内存使用情况
     * @param label 标签
     */
    fun logMemoryUsage(label: String = "Memory") {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usedPercent = (usedMemory * 100) / maxMemory

        LogUtils.d("$label - Memory: ${formatBytes(usedMemory)} / ${formatBytes(maxMemory)} ($usedPercent%)")
    }

    /**
     * 格式化字节数
     * @param bytes 字节数
     * @return 格式化后的字符串
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * 延迟执行（用于性能优化）
     * @param delayMs 延迟时间（毫秒）
     * @param action 要执行的操作
     */
    suspend fun delayedExecution(delayMs: Long, action: suspend () -> Unit) {
        kotlinx.coroutines.delay(delayMs)
        action()
    }

    /**
     * 重试机制
     * @param maxRetries 最大重试次数
     * @param delayMs 重试延迟（毫秒）
     * @param operation 要执行的操作
     * @return 操作结果
     */
    suspend fun <T> retryOperation(
        maxRetries: Int = 3,
        delayMs: Long = 1000,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                LogUtils.w("Operation failed on attempt ${attempt + 1}: ${e.message}")
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(delayMs * (attempt + 1))
                }
            }
        }

        throw lastException ?: IllegalStateException("Operation failed after $maxRetries attempts")
    }
}
