package com.limpu.hitauser.util

object HttpUtils {
    fun getHeaderAuth(token: String): String {
        return "Bearer $token"
    }
}