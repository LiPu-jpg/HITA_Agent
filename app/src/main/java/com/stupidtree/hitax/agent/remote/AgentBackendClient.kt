package com.stupidtree.hitax.agent.remote

import com.stupidtree.hitax.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.MediaType
import java.io.File
import java.util.concurrent.TimeUnit

data class SkillError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)

data class CourseSearchRequest(val keyword: String)

data class BraveSearchRequest(val query: String, val count: Int = 5)

data class BraveAnswerRequest(
    val query: String,
    val model: String = "",
    val country: String = "",
    val language: String = "",
    val enable_citations: Boolean = true,
    val enable_research: Boolean = false,
)

data class BraveAnswerResult(
    val ok: Boolean,
    val answer: String = "",
    val model: String = "",
    val usage: Map<String, Any> = emptyMap(),
    val error: SkillError? = null,
)

data class TeacherSearchRequest(val name: String)

data class CourseSearchResponse(val ok: Boolean, val results: Any? = null, val error: SkillError? = null)

data class BraveSearchResult(val ok: Boolean, val results: List<Map<String, Any>> = emptyList(), val error: SkillError? = null)

data class UploadResponse(val ok: Boolean, val id: String = "", val name: String = "", val size: Long = 0, val mime_type: String = "")

data class ParseRequest(val id: String, val filename: String = "", val max_chars: Int = 10000)

data class ParseResponse(val ok: Boolean, val markdown: String = "", val filename: String = "", val truncated: Boolean = false, val char_count: Int = 0)

data class CourseReadRequest(val course_code: String, val campus: String = "shenzhen")

data class CourseReadResponse(val ok: Boolean, val course: Any? = null, val result: Any? = null, val error: SkillError? = null)

data class CourseDetailRequest(val course_code: String, val campus: String = "shenzhen")

data class CrawlRequest(val url: String, val content_filter: Boolean = true, val output_format: String = "markdown")

data class CrawlSiteRequest(val url: String, val max_pages: Int = 10, val content_filter: Boolean = true)

data class CrawlStatusRequest(val task_id: String)

data class CrawlResponse(val ok: Boolean, val result: Any? = null, val error: SkillError? = null)

data class TempListRequest(val user_id: String? = null, val session_id: String? = null)

data class FileDownloadRequest(val key: String, val bucket: String? = null)

data class FileListRequest(val prefix: String = "", val bucket: String? = null, val limit: Int = 100)

data class RagQueryRequest(val query: String, val repo: String? = null, val top_k: Int = 5)

data class VisitRequest(val device_id: String)

internal fun buildAgentBackendHttpError(code: Int, rawBody: String?): SkillError {
    val body = rawBody?.trim().orEmpty()
    val parsedMessage = listOf(
        Regex("\\\"error\\\"\\s*:\\s*\\{[\\s\\S]*?\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""),
        Regex("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""),
        Regex("\\\"detail\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""),
    ).firstNotNullOfOrNull { regex ->
        regex.find(body)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }
    val message = parsedMessage ?: body.takeIf { it.isNotBlank() }?.take(300) ?: "HTTP $code"
    return SkillError(
        code = "HTTP_$code",
        message = message,
        retryable = code in listOf(408, 429, 500, 502, 503),
    )
}

interface AgentBackendApi {
    @GET("health")
    fun health(): Call<Map<String, String>>

    @Multipart
    @POST("api/temp/upload")
    fun uploadFile(@Part file: MultipartBody.Part): Call<UploadResponse>

    @POST("api/temp/parse")
    fun parseFile(@Body request: ParseRequest): Call<ParseResponse>

    @POST("api/temp/list")
    fun listTempFiles(@Body request: TempListRequest): Call<Map<String, Any>>

    @POST("api/files/download")
    fun downloadFileFromCos(@Body request: FileDownloadRequest): Call<Map<String, Any>>

    @POST("api/files/list")
    fun listCosFiles(@Body request: FileListRequest): Call<Map<String, Any>>

    @POST("api/courses/search")
    fun searchCourses(@Body request: CourseSearchRequest): Call<CourseSearchResponse>

    @POST("api/courses/read")
    fun readCourse(@Body request: CourseReadRequest): Call<CourseReadResponse>

    @POST("api/teachers/search")
    fun searchTeachers(@Body request: TeacherSearchRequest): Call<Map<String, Any>>

    @POST("api/crawl/page")
    fun crawlPage(@Body request: CrawlRequest): Call<CrawlResponse>

    @POST("api/crawl/site")
    fun crawlSite(@Body request: CrawlSiteRequest): Call<CrawlResponse>

    @POST("api/crawl/status")
    fun crawlStatus(@Body request: CrawlStatusRequest): Call<Map<String, Any>>

    @POST("api/search/brave")
    fun braveSearch(@Body request: BraveSearchRequest): Call<Map<String, Any>>

    @POST("api/search/brave/answer")
    fun braveAnswer(@Body request: BraveAnswerRequest): Call<Map<String, Any>>

    @POST("api/rag/query")
    fun ragQuery(@Body request: RagQueryRequest): Call<Map<String, Any>>

    @POST("api/visit")
    fun visit(@Body request: VisitRequest): Call<Map<String, Any>>
}

object AgentBackendClient {

    private val BASE_URL = BuildConfig.AGENT_BACKEND_BASE_URL.removeSuffix("/") + "/"

    val api: AgentBackendApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AgentBackendApi::class.java)
    }

    fun searchCoursesSync(keyword: String): CourseSearchResponse {
        val response = api.searchCourses(CourseSearchRequest(keyword)).execute()
        if (!response.isSuccessful) {
            return CourseSearchResponse(
                ok = false,
                error = SkillError(
                    code = "HTTP_${response.code()}",
                    message = response.errorBody()?.string()?.take(300) ?: "HTTP ${response.code()}",
                    retryable = response.code() in listOf(408, 429, 500, 502, 503),
                )
            )
        }
        return response.body() ?: CourseSearchResponse(
            ok = false,
            error = SkillError("EMPTY", "Empty response body", false),
        )
    }

    fun searchTeacherSync(name: String): Map<String, Any>? {
        return try {
            val response = api.searchTeachers(TeacherSearchRequest(name)).execute()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    fun uploadFileSync(file: File, mimeType: String): UploadResponse? {
        return try {
            val mediaType = MediaType.parse(mimeType)
            val requestBody = RequestBody.create(mediaType, file)
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
            val response = api.uploadFile(part).execute()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    fun parseFileSync(fileId: String): ParseResponse? {
        return try {
            val response = api.parseFile(ParseRequest(id = fileId)).execute()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    fun crawlPageSync(url: String): String? {
        return try {
            val response = api.crawlPage(CrawlRequest(url = url)).execute()
            if (!response.isSuccessful) return null
            val body = response.body() ?: return null
            if (!body.ok) return null
            val result = body.result as? Map<*, *> ?: return null
            val content = result["content"] as? List<*> ?: return null
            if (content.isEmpty()) return null
            val first = content[0] as? Map<*, *> ?: return null
            val text = first["text"] as? String ?: return null
            val parsed = org.json.JSONObject(text)
            parsed.optString("content", "").takeIf { it.isNotEmpty() } ?: text
        } catch (e: Exception) {
            null
        }
    }

    fun crawlSiteSync(url: String, maxPages: Int = 10): String? {
        return try {
            val response = api.crawlSite(CrawlSiteRequest(url = url, max_pages = maxPages)).execute()
            if (!response.isSuccessful) return null
            val body = response.body() ?: return null
            if (!body.ok) return null
            val result = body.result as? Map<*, *> ?: return null
            val taskId = result["task_id"] as? String ?: return null
            "站点爬取已启动，任务ID: $taskId"
        } catch (e: Exception) {
            null
        }
    }

    fun crawlStatusSync(taskId: String): String? {
        return try {
            val response = api.crawlStatus(CrawlStatusRequest(task_id = taskId)).execute()
            if (!response.isSuccessful) return null
            val body = response.body() ?: return null
            val status = body["status"] as? String ?: "unknown"
            val progress = body["progress"] as? Map<*, *>
            val percent = progress?.get("percent") as? Double ?: 0.0
            "爬取状态: $status (${percent.toInt()}%)"
        } catch (e: Exception) {
            null
        }
    }

    fun braveSearchSync(query: String): BraveSearchResult {
        return try {
            val response = api.braveSearch(BraveSearchRequest(query = query, count = 5)).execute()
            if (!response.isSuccessful) {
                return BraveSearchResult(ok = false, error = buildAgentBackendHttpError(response.code(), response.errorBody()?.string()))
            }
            val body = response.body()
                ?: return BraveSearchResult(ok = false, error = SkillError("EMPTY", "Empty response body", false))
            val ok = body["ok"] as? Boolean ?: false
            if (!ok) {
                val error = body["error"] as? Map<*, *>
                return BraveSearchResult(
                    ok = false,
                    error = SkillError(
                        code = error?.get("code")?.toString() ?: "BACKEND_ERROR",
                        message = error?.get("message")?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown error",
                        retryable = false,
                    ),
                )
            }
            BraveSearchResult(
                ok = true,
                results = (body["results"] as? List<Map<String, Any>>) ?: emptyList(),
            )
        } catch (e: Exception) {
            BraveSearchResult(
                ok = false,
                error = SkillError("EXCEPTION", e.message ?: "Web search request failed", true),
            )
        }
    }

    fun readCourseSync(courseCode: String): CourseReadResponse {
        return try {
            val response = api.readCourse(CourseReadRequest(course_code = courseCode)).execute()
            if (response.isSuccessful) {
                response.body() ?: CourseReadResponse(ok = false, error = SkillError("EMPTY", "Empty response body", false))
            } else {
                CourseReadResponse(ok = false, error = buildAgentBackendHttpError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            CourseReadResponse(ok = false, error = SkillError("EXCEPTION", e.message ?: "Course read failed", true))
        }
    }

    fun braveAnswerSync(query: String): BraveAnswerResult {
        return try {
            val response = api.braveAnswer(BraveAnswerRequest(query = query)).execute()
            if (!response.isSuccessful) {
                return BraveAnswerResult(ok = false, error = buildAgentBackendHttpError(response.code(), response.errorBody()?.string()))
            }
            val body = response.body()
                ?: return BraveAnswerResult(ok = false, error = SkillError("EMPTY", "Empty response body", false))
            val ok = body["ok"] as? Boolean ?: false
            if (!ok) {
                val error = body["error"] as? Map<*, *>
                return BraveAnswerResult(
                    ok = false,
                    error = SkillError(
                        code = error?.get("code")?.toString() ?: "BACKEND_ERROR",
                        message = error?.get("message")?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown error",
                        retryable = false,
                    ),
                )
            }
            BraveAnswerResult(
                ok = true,
                answer = body["answer"] as? String ?: "",
                model = body["model"] as? String ?: "",
                usage = (body["usage"] as? Map<String, Any>) ?: emptyMap(),
            )
        } catch (e: Exception) {
            BraveAnswerResult(
                ok = false,
                error = SkillError("EXCEPTION", e.message ?: "Brave answer request failed", true),
            )
        }
    }

    fun ragQuerySync(query: String): String? {
        return try {
            val response = api.ragQuery(RagQueryRequest(query = query, top_k = 5)).execute()
            if (!response.isSuccessful) return null
            val body = response.body() ?: return null
            val ok = body["ok"] as? Boolean ?: false
            if (!ok) return null
            val result = body["result"] as? Map<*, *> ?: return null
            val hits = result["hits"] as? List<Map<String, Any>> ?: return null
            if (hits.isEmpty()) return "未找到相关内容。"
            buildString {
                append("找到 ${hits.size} 条相关内容:\n")
                hits.take(5).forEach { h ->
                    val title = h["title"]?.toString()?.takeIf { it.isNotBlank() } ?: "无标题"
                    val snippet = h["snippet"]?.toString()?.take(200) ?: ""
                    val score = h["score"]?.toString()?.take(5) ?: ""
                    append("\n• [$title] 相关度:$score\n  $snippet")
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun reportVisit(deviceId: String) {
        try {
            api.visit(VisitRequest(device_id = deviceId)).execute()
        } catch (e: Exception) {
        }
    }
}
