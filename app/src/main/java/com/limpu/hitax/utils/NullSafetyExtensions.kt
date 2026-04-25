package com.limpu.hitax.utils

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * 空安全相关的扩展函数和工具类
 * 用于减少代码中的 !! 操作符使用
 */

/**
 * 安全版本的Toast显示
 * 自动处理null context
 */
fun Context?.showSafeToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    this?.let {
        Toast.makeText(it, message, duration).show()
    }
}

/**
 * 安全版本的Toast显示（资源ID）
 */
fun Context?.showSafeToast(messageResId: Int, duration: Int = Toast.LENGTH_SHORT) {
    this?.let {
        Toast.makeText(it, messageResId, duration).show()
    }
}

/**
 * 安全的LiveData观察
 * 自动处理lifecycle owner为null的情况
 */
fun <T> LiveData<T>?.safeObserve(
    owner: androidx.lifecycle.LifecycleOwner?,
    observer: (T) -> Unit
) {
    if (this != null && owner != null) {
        this.observe(owner, Observer { observer(it) })
    }
}

/**
 * 安全的字符串转Int
 */
fun String?.toIntOrDefault(default: Int = 0): Int {
    return this?.toIntOrNull() ?: default
}

/**
 * 安全的字符串转Long
 */
fun String?.toLongOrDefault(default: Long = 0L): Long {
    return this?.toLongOrNull() ?: default
}

/**
 * 安全的字符串转Double
 */
fun String?.toDoubleOrDefault(default: Double = 0.0): Double {
    return this?.toDoubleOrNull() ?: default
}

/**
 * 安全的字符串toFloat
 */
fun String?.toFloatOrDefault(default: Float = 0f): Float {
    return this?.toFloatOrNull() ?: default
}

/**
 * 安全的字符串转Boolean
 */
fun String?.toBooleanOrDefault(default: Boolean = false): Boolean {
    return this?.toBooleanStrictOrNull() ?: default
}

/**
 * 非空检查，如果为null则执行fallback
 */
inline fun <T : Any> T?.orNull(fallback: () -> T?): T? {
    return this ?: fallback()
}

/**
 * 非空检查，如果为null则抛出特定异常
 */
inline fun <T : Any> T?.orThrow(exceptionProvider: () -> Throwable): T {
    return this ?: throw exceptionProvider()
}
