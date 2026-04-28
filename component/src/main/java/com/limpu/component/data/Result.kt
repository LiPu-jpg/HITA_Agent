package com.limpu.component.data

/**
 * 统一的结果封装类，用于数据层错误处理
 * 替代直接使用 DataState 或抛出异常的方式
 *
 * 使用示例：
 * ```kotlin
 * when (val result = repository.fetchData()) {
 *     is Result.Success -> handleData(result.data)
 *     is Result.Error -> handleError(result.code, result.message)
 * }
 * ```
 */
sealed class Result<T> {

    /**
     * 成功结果
     * @param data 返回的数据
     */
    data class Success<T>(val data: T) : Result<T>()

    /**
     * 错误结果
     * @param code 错误代码
     * @param message 错误信息
     * @param throwable 原始异常（可选）
     */
    data class Error(
        val code: ErrorCode,
        val message: String? = null,
        val throwable: Throwable? = null
    ) : Result<Any>()

    /**
     * 加载中状态
     */
    object Loading : Result<Any>()

    /**
     * 判断是否成功
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * 判断是否失败
     */
    val isError: Boolean get() = this is Error

    /**
     * 判断是否加载中
     */
    val isLoading: Boolean get() = this is Loading

    /**
     * 获取成功数据，失败时返回 null
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * 获取成功数据，失败时返回默认值
     */
    fun getOrDefault(default: T): T = (this as? Success)?.data ?: default

    /**
     * 获取错误信息，成功时返回 null
     */
    fun errorOrNull(): Error? = this as? Error

    /**
     * 成功时执行操作
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * 失败时执行操作
     */
    inline fun onError(action: (Error) -> Unit): Result<T> {
        if (this is Error) action(this)
        return this
    }

    /**
     * 映射成功数据
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <R> map(transform: (T) -> R): Result<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> this as Result<R>
            is Loading -> this as Result<R>
        }
    }

    /**
     * 转换为 DataState（用于 UI 层）
     */
    fun toDataState(): DataState<T> {
        return when (this) {
            is Success -> DataState(data)
            is Error -> DataState(code.toDataState(), message ?: code.defaultMessage)
            is Loading -> DataState(DataState.STATE.LOADING)
        }
    }

    companion object {
        /**
         * 快速创建成功结果
         */
        fun <T> success(data: T): Result<T> = Success(data)

        /**
         * 快速创建错误结果
         */
        fun error(code: ErrorCode, message: String? = null, throwable: Throwable? = null): Result<Any> =
            Error(code, message, throwable)

        /**
         * 快速创建加载中状态
         */
        fun loading(): Result<Any> = Loading

        /**
         * 安全执行可能抛出异常的操作
         */
        @Suppress("UNCHECKED_CAST")
        inline fun <T> safeCall(
            errorCode: ErrorCode = ErrorCode.UNKNOWN_ERROR,
            block: () -> T
        ): Result<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Error(errorCode, e.message, e) as Result<T>
            }
        }

        /**
         * 安全执行异步操作（用于协程）
         */
        @Suppress("UNCHECKED_CAST")
        suspend inline fun <T> safeApiCall(
            errorCode: ErrorCode = ErrorCode.NETWORK_ERROR,
            crossinline block: suspend () -> T
        ): Result<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Error(errorCode, e.message, e) as Result<T>
            }
        }
    }
}

/**
 * 错误代码枚举
 */
enum class ErrorCode(val defaultMessage: String) {
    // 网络相关
    NETWORK_ERROR("网络连接失败"),
    TIMEOUT("请求超时"),
    NO_INTERNET("无网络连接"),
    SERVER_ERROR("服务器错误"),

    // 认证相关
    UNAUTHORIZED("未授权"),
    TOKEN_INVALID("登录已过期，请重新登录"),
    FORBIDDEN("拒绝访问"),
    NOT_LOGGED_IN("用户未登录"),

    // 数据相关
    NOT_FOUND("数据不存在"),
    PARSE_ERROR("数据解析失败"),
    EMPTY_DATA("数据为空"),

    // 本地相关
    DATABASE_ERROR("数据库操作失败"),
    CACHE_ERROR("缓存操作失败"),
    FILE_ERROR("文件操作失败"),

    // 业务相关
    PARAM_ERROR("参数错误"),
    BUSINESS_ERROR("业务处理失败"),

    // 未知
    UNKNOWN_ERROR("未知错误"),
    CANCELLED("操作已取消")
}

/**
 * 将 ErrorCode 映射为 DataState.STATE
 */
fun ErrorCode.toDataState(): DataState.STATE {
    return when (this) {
        ErrorCode.NOT_LOGGED_IN -> DataState.STATE.NOT_LOGGED_IN
        ErrorCode.TOKEN_INVALID -> DataState.STATE.TOKEN_INVALID
        ErrorCode.NOT_FOUND -> DataState.STATE.NOT_EXIST
        ErrorCode.EMPTY_DATA -> DataState.STATE.NOT_EXIST
        ErrorCode.NETWORK_ERROR,
        ErrorCode.TIMEOUT,
        ErrorCode.NO_INTERNET,
        ErrorCode.SERVER_ERROR -> DataState.STATE.FETCH_FAILED
        else -> DataState.STATE.FETCH_FAILED
    }
}

/**
 * DataState 扩展函数
 */
inline fun <T> DataState<T>.onSuccess(action: (T) -> Unit): DataState<T> {
    if (state == DataState.STATE.SUCCESS && data != null) {
        action(data!!)
    }
    return this
}

inline fun <T> DataState<T>.onError(action: (DataState.STATE, String?) -> Unit): DataState<T> {
    if (state != DataState.STATE.SUCCESS) {
        action(state, message)
    }
    return this
}

inline fun <T> DataState<T>.onFetchFailed(action: (String?) -> Unit): DataState<T> {
    if (state == DataState.STATE.FETCH_FAILED) {
        action(message)
    }
    return this
}
