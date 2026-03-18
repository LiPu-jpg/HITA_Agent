package com.hita.agent.core.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class HttpClient(private val client: OkHttpClient) {
    fun execute(request: Request): Response = client.newCall(request).execute()

    companion object {
        fun create(): HttpClient = HttpClient(OkHttpClient.Builder().build())
    }
}
