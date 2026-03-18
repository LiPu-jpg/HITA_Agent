package com.hita.agent.core.data

import android.util.Log

object DebugLog {
    private const val PREFIX = "HITA"

    @Volatile
    var enabled: Boolean = true

    fun d(tag: String, message: String) {
        if (enabled) Log.d("$PREFIX/$tag", message)
    }

    fun i(tag: String, message: String) {
        if (enabled) Log.i("$PREFIX/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) Log.w("$PREFIX/$tag", message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) Log.e("$PREFIX/$tag", message, throwable)
    }
}
