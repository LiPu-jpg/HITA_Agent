package com.limpu.hitax.agent.remote

import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

internal fun buildPrHttpError(code: Int, rawBody: String?): PrError {
    val body = rawBody?.trim().orEmpty()
    val parsedMessage = listOf(
        Regex("\"error\"\\s*:\\s*\\{[\\s\\S]*?\"message\"\\s*:\\s*\"([^\"]+)\""),
        Regex("\"detail\"\\s*:\\s*\"([^\"]+)\""),
        Regex("\"message\"\\s*:\\s*\"([^\"]+)\""),
    ).firstNotNullOfOrNull { regex ->
        regex.find(body)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }
    val suffix = (parsedMessage ?: body.takeIf { it.isNotBlank() }?.take(300)).orEmpty()
    val message = if (suffix.isBlank()) "HTTP $code" else "HTTP $code: $suffix"
    return PrError("HTTP_$code", message)
}

internal fun buildSubmitIdempotencyKey(courseCode: String, uniqueSuffix: Long = System.nanoTime()): String {
    val normalizedCourseCode = courseCode
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "course" }
    return "android-$normalizedCourseCode-$uniqueSuffix"
}

data class PreviewRequest(
    val target: Target,
    val ops: List<Map<String, @JvmSuppressWildcards Any>>,
) {
    data class Target(val campus: String, val course_code: String)
}

data class SubmitRequest(
    val target: PreviewRequest.Target,
    val ops: List<Map<String, @JvmSuppressWildcards Any>>,
    val idempotency_key: String,
)

data class PrResponse(val ok: Boolean, val data: PrData? = null, val error: PrError? = null)

data class PrData(
    val result: Map<String, Any>? = null,
    val base: Map<String, Any>? = null,
    val pr: PrInfo? = null,
)

data class PrInfo(val number: Int = 0, val url: String = "", val head_branch: String = "")

data class PrError(val code: String, val message: String)

interface PrServerApi {
    @POST("v1/course:preview")
    fun preview(@Body request: PreviewRequest): Call<PrResponse>

    @POST("v1/course:submit")
    fun submit(@Body request: SubmitRequest): Call<PrResponse>
}

object PrServerClient {

    private const val BASE_URL = "http://47.115.160.70:8081/"

    val api: PrServerApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PrServerApi::class.java)
    }

    fun previewSync(courseCode: String, ops: List<Map<String, Any>>): PrResponse {
        val request = PreviewRequest(
            target = PreviewRequest.Target(campus = "shenzhen", course_code = courseCode),
            ops = ops,
        )
        val response = api.preview(request).execute()
        if (!response.isSuccessful) {
            return PrResponse(ok = false, error = buildPrHttpError(response.code(), response.errorBody()?.string()))
        }
        return response.body() ?: PrResponse(ok = false, error = PrError("EMPTY", "Empty response"))
    }

    fun submitSync(courseCode: String, ops: List<Map<String, Any>>): PrResponse {
        val key = buildSubmitIdempotencyKey(courseCode)
        val request = SubmitRequest(
            target = PreviewRequest.Target(campus = "shenzhen", course_code = courseCode),
            ops = ops,
            idempotency_key = key,
        )
        val response = api.submit(request).execute()
        if (!response.isSuccessful) {
            return PrResponse(ok = false, error = buildPrHttpError(response.code(), response.errorBody()?.string()))
        }
        return response.body() ?: PrResponse(ok = false, error = PrError("EMPTY", "Empty response"))
    }
}
