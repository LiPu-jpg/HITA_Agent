package com.limpu.hitax.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.limpu.component.data.DataState
import com.limpu.component.data.ErrorCode
import com.limpu.component.data.Result
import kotlinx.coroutines.CoroutineExceptionHandler

object ErrorUtils {

    fun <T> safeDataState(
        operation: String,
        errorState: DataState.STATE = DataState.STATE.FETCH_FAILED,
        block: () -> T
    ): DataState<T> {
        return try {
            val result = block()
            DataState(result)
        } catch (e: Exception) {
            LogUtils.e("$operation failed", e)
            DataState(errorState, e.message)
        }
    }

    fun <T> safeLiveData(
        operation: String,
        errorState: DataState.STATE = DataState.STATE.FETCH_FAILED,
        block: () -> T
    ): LiveData<DataState<T>> {
        val result = MutableLiveData<DataState<T>>()
        try {
            result.postValue(DataState(block()))
        } catch (e: Exception) {
            LogUtils.e("$operation failed", e)
            result.postValue(DataState(errorState, e.message))
        }
        return result
    }

    fun <T> safeBackgroundLiveData(
        operation: String,
        errorState: DataState.STATE = DataState.STATE.FETCH_FAILED,
        block: () -> T
    ): LiveData<DataState<T>> {
        val result = MutableLiveData<DataState<T>>()
        Thread {
            try {
                result.postValue(DataState(block()))
            } catch (e: Exception) {
                LogUtils.e("$operation failed", e)
                result.postValue(DataState(errorState, e.message))
            }
        }.start()
        return result
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> safeResult(
        operation: String,
        errorCode: ErrorCode = ErrorCode.UNKNOWN_ERROR,
        block: () -> T
    ): Result<T> {
        return try {
            Result.Success(block())
        } catch (e: Exception) {
            LogUtils.e("$operation failed", e)
            Result.Error(errorCode, e.message, e) as Result<T>
        }
    }

    fun Throwable.toDataState(state: DataState.STATE = DataState.STATE.FETCH_FAILED): DataState<Nothing> {
        LogUtils.e("Exception converted to DataState", this)
        return DataState(state, this.message)
    }

    fun Throwable.toErrorCode(): ErrorCode {
        return when (this) {
            is java.net.SocketTimeoutException -> ErrorCode.TIMEOUT
            is java.net.UnknownHostException,
            is java.net.ConnectException -> ErrorCode.NO_INTERNET
            is java.io.IOException -> ErrorCode.NETWORK_ERROR
            is org.json.JSONException -> ErrorCode.PARSE_ERROR
            is java.lang.IllegalArgumentException -> ErrorCode.PARAM_ERROR
            else -> ErrorCode.UNKNOWN_ERROR
        }
    }
}

val GlobalCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    LogUtils.e("Unhandled coroutine exception", throwable)
}
