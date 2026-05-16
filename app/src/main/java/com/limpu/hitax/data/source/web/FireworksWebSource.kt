package com.limpu.hitax.data.source.web

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.limpu.component.data.DataState
import com.limpu.hitax.BuildConfig
import com.limpu.hitax.data.model.resource.ExternalCourseItem
import com.limpu.hitax.data.model.resource.ExternalResourceEntry
import com.limpu.hitax.data.model.resource.ResourceSource
import com.limpu.hitax.utils.LogUtils
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup

object FireworksWebSource {
    private const val ALIST_API = "https://olist-eo.jwyihao.top/api/fs/list"
    private const val DOWNLOAD_BASE = "https://alist-d.jwyihao.top/d/Fireworks"
    private const val ROOT_PATH = "/Fireworks"
    private const val TIMEOUT = 15000

    @Volatile
    private var courseCache: List<Triple<String, String, String>>? = null

    @Volatile
    private var cacheTimestamp: Long = 0L

    private const val CACHE_TTL_MS = 3600_000L

    private fun listAlistDir(path: String): JSONArray {
        val body = JSONObject()
        body.put("path", path)
        body.put("password", "")
        body.put("page", 1)
        body.put("per_page", 0)
        body.put("refresh", false)

        val response = Jsoup.connect(ALIST_API)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "HITA_L/${BuildConfig.VERSION_NAME}")
            .requestBody(body.toString())
            .method(Connection.Method.POST)
            .execute()

        if (response.statusCode() >= 400) {
            throw Exception("AList API returned HTTP ${response.statusCode()}")
        }

        val resObj = JSONObject(response.body())
        val code = resObj.optInt("code", -1)
        if (code != 200) {
            val msg = resObj.optString("message", "Unknown error")
            throw Exception("AList API error: $msg (code=$code)")
        }

        val data = resObj.optJSONObject("data")
        return data?.optJSONArray("content") ?: JSONArray()
    }

    fun searchCourses(query: String): LiveData<DataState<List<ExternalCourseItem>>> {
        val result = MutableLiveData<DataState<List<ExternalCourseItem>>>()
        Thread {
            try {
                val courses = ensureCourseCache()
                val keyword = query.trim().lowercase()
                val matched = courses.filter { (_, courseName, _) ->
                    courseName.lowercase().contains(keyword)
                }.map { (category, courseName, fullPath) ->
                    ExternalCourseItem(
                        courseName = courseName,
                        category = category,
                        source = ResourceSource.FIREWORKS,
                        path = fullPath,
                    )
                }
                result.postValue(DataState(matched, DataState.STATE.SUCCESS))
            } catch (e: Exception) {
                LogUtils.e("Fireworks search failed: ${e.message}")
                result.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return result
    }

    fun listDirectory(path: String): LiveData<DataState<List<ExternalResourceEntry>>> {
        val result = MutableLiveData<DataState<List<ExternalResourceEntry>>>()
        Thread {
            try {
                val content = listAlistDir(path)
                val entries = mutableListOf<ExternalResourceEntry>()
                for (i in 0 until content.length()) {
                    val obj = content.optJSONObject(i) ?: continue
                    val isDir = obj.optBoolean("is_dir", false)
                    val name = obj.optString("name", "")
                    val entryPath = obj.optString("path", "")
                    val size = obj.optLong("size", 0)

                    val downloadUrl = if (!isDir) {
                        val encodedPath = entryPath.split("/").joinToString("/") { segment ->
                            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                        }
                        "$DOWNLOAD_BASE$encodedPath"
                    } else ""

                    entries.add(
                        ExternalResourceEntry(
                            name = name,
                            isDir = isDir,
                            path = entryPath,
                            size = size,
                            downloadUrl = downloadUrl,
                            source = ResourceSource.FIREWORKS,
                        )
                    )
                }
                entries.sortWith(compareByDescending<ExternalResourceEntry> { it.isDir }.thenBy { it.name })
                result.postValue(DataState(entries, DataState.STATE.SUCCESS))
            } catch (e: Exception) {
                LogUtils.e("Fireworks listDirectory failed: ${e.message}")
                result.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return result
    }

    @Synchronized
    private fun ensureCourseCache(): List<Triple<String, String, String>> {
        val cached = courseCache
        if (cached != null && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            return cached
        }
        LogUtils.d("Fireworks: loading course cache from AList")

        val rootContent = listAlistDir(ROOT_PATH)
        val categories = mutableListOf<Pair<String, String>>()
        for (i in 0 until rootContent.length()) {
            val obj = rootContent.optJSONObject(i) ?: continue
            if (obj.optBoolean("is_dir", false)) {
                categories.add(Pair(obj.optString("name", ""), obj.optString("path", "")))
            }
        }

        val courses = mutableListOf<Triple<String, String, String>>()
        for ((categoryName, categoryPath) in categories) {
            try {
                val courseContent = listAlistDir(categoryPath)
                for (j in 0 until courseContent.length()) {
                    val obj = courseContent.optJSONObject(j) ?: continue
                    if (obj.optBoolean("is_dir", false)) {
                        courses.add(
                            Triple(
                                categoryName,
                                obj.optString("name", ""),
                                obj.optString("path", ""),
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtils.w("Fireworks: failed to list category $categoryName: ${e.message}")
            }
        }

        LogUtils.d("Fireworks: cached ${courses.size} courses from ${categories.size} categories")
        courseCache = courses
        cacheTimestamp = System.currentTimeMillis()
        return courses
    }
}
