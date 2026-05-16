# External Resource Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independent search page for HIT course materials from HITCS (GitHub) and Fireworks (薪火笔记社), with unified search results, directory browsing, and direct download.

**Architecture:** Two new WebSource objects (HITCSWebSource, FireworksWebSource) following the existing Jsoup + Thread + LiveData + DataState pattern. A Repository merges results via MediatorLiveData. A new Activity/ViewModel pair provides the UI, reusing the same layout structure as the existing HOA search page.

**Tech Stack:** Kotlin, Jsoup, LiveData, DataState, Hilt, ViewBinding, BaseListAdapter

**Spec:** `docs/superpowers/specs/2026-05-16-external-resource-search-design.md`

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `app/src/main/java/com/limpu/hitax/data/model/resource/ExternalResourceItem.kt` | Data models: `ResourceSource`, `ExternalCourseItem`, `ExternalResourceEntry` |
| Create | `app/src/main/java/com/limpu/hitax/data/source/web/HITCSWebSource.kt` | GitHub Tree API search + Contents API directory listing |
| Create | `app/src/main/java/com/limpu/hitax/data/source/web/FireworksWebSource.kt` | AList API search + directory listing |
| Create | `app/src/main/java/com/limpu/hitax/data/repository/ExternalResourceRepository.kt` | Merge results from both sources via MediatorLiveData |
| Create | `app/src/main/java/com/limpu/hitax/ui/resource/ExternalResourceSearchViewModel.kt` | ViewModel with queryLiveData.switchMap |
| Create | `app/src/main/java/com/limpu/hitax/ui/resource/ExternalResourceSearchActivity.kt` | Search UI + directory browsing mode |
| Create | `app/src/main/res/layout/activity_external_resource_search.xml` | Main search layout |
| Create | `app/src/main/res/layout/item_external_course.xml` | Search result item layout |
| Create | `app/src/main/res/layout/item_external_resource_entry.xml` | Directory entry item layout |
| Modify | `app/src/main/res/values/strings.xml` | Add string resources |
| Modify | `app/src/main/AndroidManifest.xml` | Register new Activity |
| Modify | `app/src/main/java/com/limpu/hitax/ui/resource/CourseResourceSearchActivity.kt` | Add "外部资料" toolbar button as entry point |

---

### Task 1: Data Models

**Files:**
- Create: `app/src/main/java/com/limpu/hitax/data/model/resource/ExternalResourceItem.kt`

- [ ] **Step 1: Create data model file**

```kotlin
package com.limpu.hitax.data.model.resource

enum class ResourceSource { HITCS, FIREWORKS }

data class ExternalCourseItem(
    var courseName: String = "",
    var category: String = "",
    var source: ResourceSource = ResourceSource.HITCS,
    var path: String = "",
    var description: String = "",
)

data class ExternalResourceEntry(
    var name: String = "",
    var isDir: Boolean = false,
    var path: String = "",
    var size: Long = 0,
    var downloadUrl: String = "",
    var source: ResourceSource = ResourceSource.HITCS,
)
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd c:/Users/wfy/hita_lib/HITA_Agent && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 2: HITCSWebSource

**Files:**
- Create: `app/src/main/java/com/limpu/hitax/data/source/web/HITCSWebSource.kt`

- [ ] **Step 1: Create HITCSWebSource with search and directory listing**

```kotlin
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

object HITCSWebSource {
    private const val REPO = "HITLittleZheng/HITCS"
    private const val API_BASE = "https://api.github.com/repos/$REPO"
    private const val TIMEOUT = 15000

    // Cache: list of (category, courseName, path) tuples from Tree API
    @Volatile
    private var courseCache: List<Triple<String, String, String>>? = null

    @Volatile
    private var cacheTimestamp: Long = 0L

    private const val CACHE_TTL_MS = 3600_000L // 1 hour

    private fun withHeaders(req: Connection): Connection {
        req.ignoreContentType(true)
            .ignoreHttpErrors(true)
            .timeout(TIMEOUT)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HITA_L/${BuildConfig.VERSION_NAME}")
        return req
    }

    fun searchCourses(query: String): LiveData<DataState<List<ExternalCourseItem>>> {
        val result = MutableLiveData<DataState<List<ExternalCourseItem>>>()
        Thread {
            try {
                val courses = ensureCourseCache()
                val keyword = query.trim().lowercase()
                val matched = courses.filter { (_, courseName, _) ->
                    courseName.lowercase().contains(keyword)
                }.map { (category, courseName, path) ->
                    ExternalCourseItem(
                        courseName = courseName,
                        category = category,
                        source = ResourceSource.HITCS,
                        path = path,
                    )
                }
                result.postValue(DataState(matched, DataState.STATE.SUCCESS))
            } catch (e: Exception) {
                LogUtils.e("HITCS search failed: ${e.message}")
                result.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return result
    }

    fun listDirectory(path: String): LiveData<DataState<List<ExternalResourceEntry>>> {
        val result = MutableLiveData<DataState<List<ExternalResourceEntry>>>()
        Thread {
            try {
                val url = "$API_BASE/contents/${encodePath(path)}"
                val response = withHeaders(Jsoup.connect(url))
                    .method(Connection.Method.GET)
                    .execute()

                if (response.statusCode() >= 400) {
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, "HTTP ${response.statusCode()}"))
                    return@Thread
                }

                val arr = JSONArray(response.body())
                val entries = mutableListOf<ExternalResourceEntry>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    entries.add(
                        ExternalResourceEntry(
                            name = obj.optString("name", ""),
                            isDir = obj.optString("type") == "dir",
                            path = obj.optString("path", ""),
                            size = obj.optLong("size", 0),
                            downloadUrl = obj.optString("download_url", ""),
                            source = ResourceSource.HITCS,
                        )
                    )
                }
                // Sort: directories first, then files, both alphabetical
                entries.sortWith(compareByDescending<ExternalResourceEntry> { it.isDir }.thenBy { it.name })
                result.postValue(DataState(entries, DataState.STATE.SUCCESS))
            } catch (e: Exception) {
                LogUtils.e("HITCS listDirectory failed: ${e.message}")
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
        LogUtils.d("HITCS: loading course cache from Tree API")
        val url = "$API_BASE/git/trees/main?recursive=1"
        val response = withHeaders(Jsoup.connect(url))
            .method(Connection.Method.GET)
            .execute()

        if (response.statusCode() >= 400) {
            throw Exception("GitHub Tree API returned HTTP ${response.statusCode()}")
        }

        val tree = JSONObject(response.body())
        val treeArr = tree.optJSONArray("tree") ?: JSONArray()

        // Collect all directory paths at depth 2 (category/course)
        val dirPaths = mutableSetOf<String>()
        for (i in 0 until treeArr.length()) {
            val item = treeArr.optJSONObject(i) ?: continue
            if (item.optString("type") != "tree") continue
            val itemPath = item.optString("path", "")
            val depth = itemPath.count { it == '/' }
            if (depth == 1) {
                dirPaths.add(itemPath)
            }
        }

        val courses = mutableListOf<Triple<String, String, String>>()
        for (dirPath in dirPaths) {
            val parts = dirPath.split("/", limit = 2)
            if (parts.size == 2) {
                val category = parts[0]
                val courseName = parts[1]
                // Skip non-course directories (dotfiles, meta files)
                if (!courseName.startsWith(".")) {
                    courses.add(Triple(category, courseName, dirPath))
                }
            }
        }
        LogUtils.d("HITCS: cached ${courses.size} courses")
        courseCache = courses
        cacheTimestamp = System.currentTimeMillis()
        return courses
    }

    private fun encodePath(path: String): String {
        return path.split("/").joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd c:/Users/wfy/hita_lib/HITA_Agent && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 3: FireworksWebSource

**Files:**
- Create: `app/src/main/java/com/limpu/hitax/data/source/web/FireworksWebSource.kt`

- [ ] **Step 1: Create FireworksWebSource with search and directory listing**

```kotlin
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

    // Cache: list of (category, courseName, fullPath)
    @Volatile
    private var courseCache: List<Triple<String, String, String>>? = null

    @Volatile
    private var cacheTimestamp: Long = 0L

    private const val CACHE_TTL_MS = 3600_000L // 1 hour

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
        val categories = mutableListOf<Pair<String, String>>() // (name, path)
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
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd c:/Users/wfy/hita_lib/HITA_Agent && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 4: ExternalResourceRepository

**Files:**
- Create: `app/src/main/java/com/limpu/hitax/data/repository/ExternalResourceRepository.kt`

- [ ] **Step 1: Create repository with MediatorLiveData merge logic**

```kotlin
package com.limpu.hitax.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.resource.ExternalCourseItem
import com.limpu.hitax.data.model.resource.ExternalResourceEntry
import com.limpu.hitax.data.model.resource.ResourceSource
import com.limpu.hitax.data.source.web.FireworksWebSource
import com.limpu.hitax.data.source.web.HITCSWebSource
import javax.inject.Inject

class ExternalResourceRepository @Inject constructor() {

    fun searchCourses(query: String): LiveData<DataState<List<ExternalCourseItem>>> {
        val mediator = MediatorLiveData<DataState<List<ExternalCourseItem>>>()
        var hitcsResult: List<ExternalCourseItem>? = null
        var fireworksResult: List<ExternalCourseItem>? = null
        var hitcsFailed = false
        var fireworksFailed = false

        val hitcsLive = HITCSWebSource.searchCourses(query)
        val fireworksLive = FireworksWebSource.searchCourses(query)

        fun mergeAndPost() {
            val merged = mutableListOf<ExternalCourseItem>()
            hitcsResult?.let { merged.addAll(it) }
            fireworksResult?.let { merged.addAll(it) }
            merged.sortBy { it.courseName }

            if (merged.isNotEmpty()) {
                mediator.value = DataState(merged, DataState.STATE.SUCCESS)
            } else if (hitcsFailed && fireworksFailed) {
                mediator.value = DataState(DataState.STATE.FETCH_FAILED, "所有数据源均不可用")
            }
            // If one source returned empty and other is still loading, wait
        }

        mediator.addSource(hitcsLive) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                hitcsResult = state.data ?: emptyList()
            } else {
                hitcsFailed = true
            }
            mergeAndPost()
        }

        mediator.addSource(fireworksLive) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                fireworksResult = state.data ?: emptyList()
            } else {
                fireworksFailed = true
            }
            mergeAndPost()
        }

        return mediator
    }

    fun listDirectory(
        path: String,
        source: ResourceSource,
    ): LiveData<DataState<List<ExternalResourceEntry>>> {
        return when (source) {
            ResourceSource.HITCS -> HITCSWebSource.listDirectory(path)
            ResourceSource.FIREWORKS -> FireworksWebSource.listDirectory(path)
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd c:/Users/wfy/hita_lib/HITA_Agent && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 5: String Resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add new string entries**

Add the following entries after the existing `course_resource_*` entries:

```xml
<string name="external_resource_title">外部资料</string>
<string name="external_resource_hint">输入课程名搜索</string>
<string name="external_resource_empty">没有找到相关课程资料</string>
<string name="external_resource_failed">资料加载失败</string>
<string name="external_resource_source_hitcs">HITCS</string>
<string name="external_resource_source_fireworks">薪火笔记社</string>
<string name="external_resource_browse">浏览资料</string>
<string name="external_resource_toolbar_entry">外部资料</string>
```

- [ ] **Step 2: Build to verify resources compile**

Run: `cd c:/Users/wfy/hita_lib/HITA_Agent && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 6: Layout Files

**Files:**
- Create: `app/src/main/res/layout/activity_external_resource_search.xml`
- Create: `app/src/main/res/layout/item_external_course.xml`
- Create: `app/src/main/res/layout/item_external_resource_entry.xml`

- [ ] **Step 1: Create main search layout**

`app/src/main/res/layout/activity_external_resource_search.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/backgroundColorBottom">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize" />

        <!-- Search bar (hidden in browse mode) -->
        <LinearLayout
            android:id="@+id/search_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_margin="16dp"
            android:background="@drawable/element_rounded_button_bg_white"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingStart="12dp"
            android:paddingEnd="12dp">

            <ImageView
                android:id="@+id/search_button"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:padding="2dp"
                app:srcCompat="@drawable/ic_baseline_search_24"
                app:tint="?attr/colorPrimary" />

            <EditText
                android:id="@+id/search_input"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:background="@android:color/transparent"
                android:hint="@string/external_resource_hint"
                android:imeOptions="actionSearch"
                android:inputType="text"
                android:maxLines="1" />

        </LinearLayout>

        <!-- Breadcrumb (visible in browse mode) -->
        <TextView
            android:id="@+id/breadcrumb"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:layout_marginEnd="16dp"
            android:layout_marginBottom="8dp"
            android:textColor="?attr/colorPrimary"
            android:textSize="14sp"
            android:visibility="gone" />

        <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
            android:id="@+id/swipe_refresh"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1">

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/list"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:layoutAnimation="@anim/recycler_layout_animation_falls_down" />

        </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

        <TextView
            android:id="@+id/empty_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:padding="24dp"
            android:text="@string/external_resource_empty"
            android:textColor="?attr/textColorSecondary"
            android:visibility="gone" />

    </LinearLayout>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: Create course result item layout**

`app/src/main/res/layout/item_external_course.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/card"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginStart="16dp"
    android:layout_marginTop="8dp"
    android:layout_marginEnd="16dp"
    android:layout_marginBottom="8dp"
    android:clickable="true"
    android:foreground="?attr/selectableItemBackgroundBorderless"
    app:cardBackgroundColor="?attr/backgroundColorSecond"
    app:cardCornerRadius="16dp"
    app:cardElevation="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="16dp">

        <ImageView
            android:id="@+id/icon"
            android:layout_width="46dp"
            android:layout_height="46dp"
            android:scaleType="centerCrop"
            app:srcCompat="@drawable/ic_baseline_menu_24"
            app:tint="?attr/colorPrimary" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:singleLine="true"
                android:textColor="?attr/textColorPrimary"
                android:textSize="16sp" />

            <TextView
                android:id="@+id/subtitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:singleLine="true"
                android:textColor="?attr/textColorSecondary"
                android:textSize="14sp" />

        </LinearLayout>

        <TextView
            android:id="@+id/source_tag"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:background="@drawable/element_rounded_button_bg_grey"
            android:paddingStart="8dp"
            android:paddingTop="4dp"
            android:paddingEnd="8dp"
            android:paddingBottom="4dp"
            android:textColor="?attr/textColorSecondary"
            android:textSize="12sp" />

    </LinearLayout>

</androidx.cardview.widget.CardView>
```

- [ ] **Step 3: Create resource entry item layout**

`app/src/main/res/layout/item_external_resource_entry.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/card"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginStart="16dp"
    android:layout_marginTop="4dp"
    android:layout_marginEnd="16dp"
    android:layout_marginBottom="4dp"
    android:clickable="true"
    android:foreground="?attr/selectableItemBackgroundBorderless"
    app:cardBackgroundColor="?attr/backgroundColorSecond"
    app:cardCornerRadius="12dp"
    app:cardElevation="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="12dp">

        <ImageView
            android:id="@+id/icon"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:scaleType="centerInside"
            app:tint="?attr/colorPrimary" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/name"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:singleLine="true"
                android:textColor="?attr/textColorPrimary"
                android:textSize="15sp" />

            <TextView
                android:id="@+id/size"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:singleLine="true"
                android:textColor="?attr/textColorSecondary"
                android:textSize="12sp"
                android:visibility="gone" />

        </LinearLayout>

    </LinearLayout>

</androidx.cardview.widget.CardView>
```

- [ ] **Step 4: Build to verify layouts compile**

Run: `cd c:/Users/wfy/hita_lib/HITA_Agent && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 7: ViewModel

**Files:**
- Create: `app/src/main/java/com/limpu/hitax/ui/resource/ExternalResourceSearchViewModel.kt`

- [ ] **Step 1: Create ViewModel**

```kotlin
package com.limpu.hitax.ui.resource

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.resource.ExternalCourseItem
import com.limpu.hitax.data.model.resource.ExternalResourceEntry
import com.limpu.hitax.data.model.resource.ResourceSource
import com.limpu.hitax.data.repository.ExternalResourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ExternalResourceSearchViewModel @Inject constructor(
    private val repository: ExternalResourceRepository,
) : ViewModel() {

    private val queryLiveData = MutableLiveData<String>()
    private val browseLiveData = MutableLiveData<Pair<String, ResourceSource>>()

    val searchResults: LiveData<DataState<List<ExternalCourseItem>>> = queryLiveData.switchMap {
        repository.searchCourses(it)
    }

    val browseResults: LiveData<DataState<List<ExternalResourceEntry>>> = browseLiveData.switchMap { (path, source) ->
        repository.listDirectory(path, source)
    }

    fun search(query: String) {
        if (query.isBlank()) return
        queryLiveData.value = query.trim()
    }

    fun browse(path: String, source: ResourceSource) {
        browseLiveData.value = Pair(path, source)
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd c:/Users/wfy/hita_lib/HITA_Agent && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 8: Activity

**Files:**
- Create: `app/src/main/java/com/limpu/hitax/ui/resource/ExternalResourceSearchActivity.kt`

- [ ] **Step 1: Create Activity with search + browse modes**

```kotlin
package com.limpu.hitax.ui.resource

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.data.model.resource.ExternalCourseItem
import com.limpu.hitax.data.model.resource.ExternalResourceEntry
import com.limpu.hitax.data.model.resource.ResourceSource
import com.limpu.hitax.databinding.ActivityExternalResourceSearchBinding
import com.limpu.hitax.databinding.ItemExternalCourseBinding
import com.limpu.hitax.databinding.ItemExternalResourceEntryBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import com.limpu.hitax.utils.LogUtils
import com.limpu.style.base.BaseListAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExternalResourceSearchActivity :
    HiltBaseActivity<ActivityExternalResourceSearchBinding>() {

    private val viewModel: ExternalResourceSearchViewModel by viewModels()
    private lateinit var courseAdapter: CourseAdapter
    private lateinit var entryAdapter: EntryAdapter
    private var isBrowseMode = false
    private val browseStack = ArrayDeque<BrowseState>()

    private data class BrowseState(
        val path: String,
        val source: ResourceSource,
        val breadcrumb: String,
    )

    override fun initViewBinding(): ActivityExternalResourceSearchBinding =
        ActivityExternalResourceSearchBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
        applyStatusBarInsets()
    }

    override fun initViews() {
        binding.toolbar.title = getString(R.string.external_resource_title)

        courseAdapter = CourseAdapter(mutableListOf())
        entryAdapter = EntryAdapter(mutableListOf())
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = courseAdapter

        binding.searchInput.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                    startSearch()
                    return true
                }
                return false
            }
        })
        binding.searchButton.setOnClickListener { startSearch() }
        binding.swipeRefresh.setColorSchemeColors(getColorPrimary())
        binding.swipeRefresh.setOnRefreshListener {
            if (isBrowseMode) {
                val state = browseStack.lastOrNull() ?: return@setOnRefreshListener
                viewModel.browse(state.path, state.source)
            } else {
                startSearch()
            }
        }

        viewModel.searchResults.observe(this) { state ->
            if (isBrowseMode) return@observe
            binding.swipeRefresh.isRefreshing = false
            if (state.state == DataState.STATE.SUCCESS) {
                val items = state.data ?: emptyList()
                courseAdapter.notifyItemChangedSmooth(items)
                binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyText.setText(R.string.external_resource_empty)
            } else {
                binding.emptyText.visibility = View.VISIBLE
                binding.emptyText.setText(R.string.external_resource_failed)
                state.message?.takeIf { it.isNotBlank() }?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewModel.browseResults.observe(this) { state ->
            if (!isBrowseMode) return@observe
            binding.swipeRefresh.isRefreshing = false
            if (state.state == DataState.STATE.SUCCESS) {
                val items = state.data ?: emptyList()
                entryAdapter.notifyItemChangedSmooth(items)
                binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyText.setText(R.string.external_resource_empty)
            } else {
                binding.emptyText.visibility = View.VISIBLE
                binding.emptyText.setText(R.string.external_resource_failed)
                state.message?.takeIf { it.isNotBlank() }?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startSearch() {
        val input = binding.searchInput.text?.toString()?.trim().orEmpty()
        if (input.isBlank()) return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)

        // Exit browse mode if active
        if (isBrowseMode) {
            exitBrowseMode()
        }

        binding.swipeRefresh.isRefreshing = true
        viewModel.search(input)
    }

    private fun enterBrowseMode(item: ExternalCourseItem) {
        isBrowseMode = true
        browseStack.clear()
        val state = BrowseState(item.path, item.source, item.courseName)
        browseStack.addLast(state)

        binding.searchBar.visibility = View.GONE
        binding.breadcrumb.visibility = View.VISIBLE
        binding.breadcrumb.text = item.courseName
        binding.list.adapter = entryAdapter
        binding.toolbar.title = getString(R.string.external_resource_browse)

        binding.swipeRefresh.isRefreshing = true
        viewModel.browse(item.path, item.source)
    }

    private fun navigateInto(entry: ExternalResourceEntry) {
        if (!entry.isDir) {
            // Download file
            if (entry.downloadUrl.isNotBlank()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.downloadUrl))
                startActivity(intent)
            }
            return
        }

        val currentState = browseStack.lastOrNull() ?: return
        val newState = BrowseState(
            path = entry.path,
            source = currentState.source,
            breadcrumb = "${currentState.breadcrumb} / ${entry.name}",
        )
        browseStack.addLast(newState)

        binding.breadcrumb.text = newState.breadcrumb
        binding.swipeRefresh.isRefreshing = true
        viewModel.browse(entry.path, entry.source)
    }

    private fun exitBrowseMode() {
        isBrowseMode = false
        browseStack.clear()

        binding.searchBar.visibility = View.VISIBLE
        binding.breadcrumb.visibility = View.GONE
        binding.list.adapter = courseAdapter
        binding.toolbar.title = getString(R.string.external_resource_title)
    }

    @Deprecated("Use OnBackPressedCallback in production")
    override fun onBackPressed() {
        if (isBrowseMode && browseStack.size > 1) {
            browseStack.removeLast()
            val previous = browseStack.last()
            binding.breadcrumb.text = previous.breadcrumb
            binding.swipeRefresh.isRefreshing = true
            viewModel.browse(previous.path, previous.source)
        } else if (isBrowseMode) {
            exitBrowseMode()
            binding.emptyText.visibility = View.GONE
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun applyStatusBarInsets() {
        val target = binding.root
        val originalTop = target.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = originalTop + bars.top)
            insets
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    // --- Adapters ---

    inner class CourseAdapter(mBeans: MutableList<ExternalCourseItem>) :
        BaseListAdapter<ExternalCourseItem, CourseAdapter.Holder>(this, mBeans) {

        inner class Holder(val binding: ItemExternalCourseBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun getViewBinding(parent: ViewGroup, viewType: Int): ViewBinding {
            return ItemExternalCourseBinding.inflate(layoutInflater, parent, false)
        }

        override fun createViewHolder(viewBinding: ViewBinding, viewType: Int): Holder {
            return Holder(viewBinding as ItemExternalCourseBinding)
        }

        override fun bindHolder(holder: Holder, data: ExternalCourseItem?, position: Int) {
            data ?: return
            holder.binding.title.text = data.courseName
            holder.binding.subtitle.text = data.category
            holder.binding.sourceTag.text = when (data.source) {
                ResourceSource.HITCS -> getString(R.string.external_resource_source_hitcs)
                ResourceSource.FIREWORKS -> getString(R.string.external_resource_source_fireworks)
            }
            holder.binding.sourceTag.visibility = View.VISIBLE
            holder.binding.card.setOnClickListener {
                enterBrowseMode(data)
            }
        }
    }

    inner class EntryAdapter(mBeans: MutableList<ExternalResourceEntry>) :
        BaseListAdapter<ExternalResourceEntry, EntryAdapter.Holder>(this, mBeans) {

        inner class Holder(val binding: ItemExternalResourceEntryBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun getViewBinding(parent: ViewGroup, viewType: Int): ViewBinding {
            return ItemExternalResourceEntryBinding.inflate(layoutInflater, parent, false)
        }

        override fun createViewHolder(viewBinding: ViewBinding, viewType: Int): Holder {
            return Holder(viewBinding as ItemExternalResourceEntryBinding)
        }

        override fun bindHolder(holder: Holder, data: ExternalResourceEntry?, position: Int) {
            data ?: return
            holder.binding.name.text = data.name
            if (data.isDir) {
                holder.binding.icon.setImageResource(R.drawable.ic_baseline_menu_24)
                holder.binding.size.visibility = View.GONE
            } else {
                holder.binding.icon.setImageResource(R.drawable.ic_baseline_search_24)
                if (data.size > 0) {
                    holder.binding.size.text = formatFileSize(data.size)
                    holder.binding.size.visibility = View.VISIBLE
                } else {
                    holder.binding.size.visibility = View.GONE
                }
            }
            holder.binding.card.setOnClickListener {
                navigateInto(data)
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd c:/Users/wfy/hita_lib/HITA_Agent && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 9: AndroidManifest & Entry Point

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` — register new Activity
- Modify: `app/src/main/java/com/limpu/hitax/ui/resource/CourseResourceSearchActivity.kt` — add toolbar entry button

- [ ] **Step 1: Register Activity in AndroidManifest.xml**

Find the line with `CourseResourceSearchActivity` declaration and add after it:

```xml
<activity android:name=".ui.resource.ExternalResourceSearchActivity" />
```

- [ ] **Step 2: Add "外部资料" button to CourseResourceSearchActivity toolbar menu**

Create a new menu resource file `app/src/main/res/menu/menu_course_resource_search.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <item
        android:id="@+id/action_external_resource"
        android:title="@string/external_resource_toolbar_entry"
        app:showAsAction="ifRoom" />

</menu>
```

- [ ] **Step 3: Wire menu in CourseResourceSearchActivity**

Add menu inflation and click handling to `CourseResourceSearchActivity.kt`. Add the following import:

```kotlin
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
```

Add these methods to the class:

```kotlin
override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_course_resource_search, menu)
    return true
}

override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
        R.id.action_external_resource -> {
            startActivity(Intent(this, ExternalResourceSearchActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
```

- [ ] **Step 4: Full build to verify everything compiles together**

Run: `cd c:/Users/wfy/hita_lib/HITA_Agent && ./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

---

### Task 10: Final Verification

- [ ] **Step 1: Full clean build**

Run: `cd c:/Users/wfy/hita_lib/HITA_Agent && ./gradlew clean :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify all new files exist**

Run: `find c:/Users/wfy/hita_lib/HITA_Agent/app/src/main -name "ExternalResource*" -o -name "HITCS*" -o -name "Fireworks*" -o -name "external_resource*" -o -name "item_external*" | sort`

Expected output:
```
.../res/layout/activity_external_resource_search.xml
.../res/layout/item_external_course.xml
.../res/layout/item_external_resource_entry.xml
.../res/menu/menu_course_resource_search.xml
.../data/model/resource/ExternalResourceItem.kt
.../data/repository/ExternalResourceRepository.kt
.../data/source/web/FireworksWebSource.kt
.../data/source/web/HITCSWebSource.kt
.../ui/resource/ExternalResourceSearchActivity.kt
.../ui/resource/ExternalResourceSearchViewModel.kt
```
