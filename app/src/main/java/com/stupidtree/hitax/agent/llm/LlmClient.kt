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

// 智谱多模态API接口
interface ZhipuApiService {
    @POST("api/paas/v4/chat/completions")
    fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ZhipuChatRequest,
    ): Call<ZhipuChatResponse>
}

// 智谱请求数据类
data class ZhipuChatRequest(
    val model: String = "glm-4.6v-flash",
    val messages: List<ZhipuMessage>,
    val stream: Boolean = false
)

data class ZhipuMessage(
    val role: String,
    val content: List<ZhipuContent>
)

data class ZhipuContent(
    val type: String,
    val text: String? = null,
    val image_url: ZhipuImageUrl? = null,
    val video_url: ZhipuVideoUrl? = null,
    val file_url: ZhipuFileUrl? = null
)

data class ZhipuImageUrl(val url: String)
data class ZhipuVideoUrl(val url: String)
data class ZhipuFileUrl(val url: String)

// 智谱响应数据类
data class ZhipuChatResponse(
    val id: String,
    val created: Long,
    val model: String,
    val choices: List<ZhipuChoice>,
    val usage: ZhipuUsage
)

data class ZhipuChoice(
    val index: Int,
    val message: ZhipuResponseMessage,
    val finish_reason: String
)

data class ZhipuResponseMessage(
    val role: String,
    val content: String
)

data class ZhipuUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

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

object ZhipuClient {
    private const val BASE_URL = "https://open.bigmodel.cn/"
    private const val API_KEY = "f95bd58458c946f4962b74a6ac3d403f.tPY9sl1T2xtqdVpP"

    val MODEL = "glm-4.6v-flash"

    val service: ZhipuApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ZhipuApiService::class.java)
    }

    fun authHeader(): String = "Bearer $API_KEY"
}
