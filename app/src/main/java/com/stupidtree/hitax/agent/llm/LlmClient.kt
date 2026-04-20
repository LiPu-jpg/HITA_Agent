package com.stupidtree.hitax.agent.llm

import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface MiniMaxApiService {
    @POST("v1/chat/completions")
    fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): Call<ChatCompletionResponse>
}

object LlmClient {
    private const val BASE_URL = "https://api.minimaxi.com/"
    private const val API_KEY = "Bearer sk-cp-xqBXPT7PTX8CG_IMl3xnbrrVi50i1wEjBQ8AACpgDhR3wpD6BJeTsYrBt2J9CJSMy9weFfPUQHJ6DWYMXqvD6Whvszor2IZhc_jACOJXGx3QbcygaiIFgLo"

    val MODEL = "MiniMax-M2.7"

    val service: MiniMaxApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MiniMaxApiService::class.java)
    }

    fun authHeader(): String = API_KEY
}
