package com.limpu.stupiduser.util

object HttpUtils {
    fun getHeaderAuth(token: String): String {
        return "Bearer $token"
    }
}