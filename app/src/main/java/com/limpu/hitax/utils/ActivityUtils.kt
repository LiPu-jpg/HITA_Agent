package com.limpu.hitax.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import com.limpu.hitax.R
import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.source.preference.EasPreferenceSource
import com.limpu.hitax.data.source.preference.TimetablePreferenceSource
import com.limpu.hitax.ui.eas.login.PopUpLoginEAS
import com.limpu.hitax.ui.myprofile.MyProfileActivity
import com.limpu.hitax.ui.eas.imp.ImportTimetableActivity
import com.limpu.hitax.ui.resource.CourseContributionActivity
import com.limpu.hitax.ui.resource.CourseReadmeActivity
import com.limpu.hitax.ui.resource.CourseResourceSearchActivity
import com.limpu.hitax.ui.resource.InternalWebActivity
import com.limpu.hitax.ui.news.NewsDetailActivity
import com.limpu.hitax.ui.profile.ProfileActivity
import com.limpu.hitax.ui.search.SearchActivity
import com.limpu.hitax.ui.subject.SubjectActivity
import com.limpu.hitax.ui.teacher.ActivityTeacherOfficial
import com.limpu.hitax.ui.timetable.detail.TimetableDetailActivity
import com.limpu.hitax.ui.timetable.manager.TimetableManagerActivity
import com.limpu.hitauser.data.model.CheckUpdateResult
import com.limpu.hitauser.data.repository.LocalUserRepository
import com.limpu.style.widgets.PopUpText
import com.limpu.style.widgets.PopUpUpdate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


object ActivityUtils {

    fun startOfficialTeacherActivity(from: Context, id: String, url: String, name: String) {
        val i = Intent(from, ActivityTeacherOfficial::class.java)
        i.putExtra("id", id)
        i.putExtra("url", url)
        i.putExtra("name", name)
        from.startActivity(i)
    }


    enum class SearchType { TEACHER }

    enum class CourseResourceMode { VIEW, SUBMIT }

    fun searchFor(from: Context, text: String?, type: SearchType) {
        if (text.isNullOrBlank()) return
        val i = Intent(from, SearchActivity::class.java)
        i.putExtra("keyword", text)
        i.putExtra("type", type.name)
        from.startActivity(i)

    }

    fun startSearchActivity(from: Context) {
        val i = Intent(from, SearchActivity::class.java)
        from.startActivity(i)
    }

    fun startSearchActivity(from: Activity,transition: View) {
        val i = Intent(from, SearchActivity::class.java)
        transition.transitionName = "search"
        val ao = ActivityOptionsCompat.makeSceneTransitionAnimation(from,transition,"search")
        from.startActivity(i,ao.toBundle())
    }
    fun startMyProfileActivity(from: Context) {
        val i = Intent(from, MyProfileActivity::class.java)
        from.startActivity(i)
    }

    fun startImportTimetableActivity(from: Context, autoImport: Boolean = false) {
        val i = Intent(from, ImportTimetableActivity::class.java)
        i.putExtra("autoImport", autoImport)
        from.startActivity(i)
    }

    fun startWelcomeActivity(from: Context, easRepository: EASRepository) {
        if (from is AppCompatActivity) {
            showEasVerifyWindow<Activity>(
                from = from,
                easRepository = easRepository,
                directTo = null,
                onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                    override fun onSuccess(window: PopUpLoginEAS) {
                        window.dismiss()
                    }

                    override fun onFailed(window: PopUpLoginEAS) {}
                }
            )
        } else {
            Toast.makeText(from, R.string.eas_login_prompt, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 进行教务认证，或直接跳转
     * @param directTo 若存在已登录token，则直接跳转到activity。传null表示忽略
     * @param lock 是否锁定窗口（=true时，若cancel则连带宿主一起销毁）
     * @param onResponseListener 认证监听
     */
    fun <T : Activity> showEasVerifyWindow(
        from: Context,
        easRepository: EASRepository,
        directTo: Class<T>? = null,
        lock: Boolean = false,
        autoLaunchWebLogin: Boolean = false,
        preferredCampus: EASToken.Campus? = null,
        onResponseListener: PopUpLoginEAS.OnResponseListener
    ) {
        LogUtils.d("=== 🔍 showEasVerifyWindow START ===")
        LogUtils.d("Original context type: ${from.javaClass.name}")
        LogUtils.d("Original context class hierarchy: ${getContextHierarchy(from)}")

        // 解包Hilt的Context包装器，获取底层的AppCompatActivity
        val activity = unwrapContextToActivity(from)

        LogUtils.d("Unwrapped activity: ${activity?.javaClass?.name}, is AppCompatActivity=${activity is AppCompatActivity}")

        if (activity is AppCompatActivity) {
            LogUtils.d("✅ Successfully unwrapped to AppCompatActivity: ${activity.javaClass.simpleName}")
            if (easRepository.getEasToken().isLogin()) {
                directTo?.let {
                    LogUtils.d("User already logged in, starting direct activity: ${directTo.simpleName}")
                    val i = Intent(activity, directTo)
                    activity.startActivity(i)
                    return
                }
            }
            LogUtils.d("Creating PopUpLoginEAS and showing")
            val window = PopUpLoginEAS()
            window.lock = lock
            window.autoLaunchWebLogin = autoLaunchWebLogin
            window.preferredCampus = preferredCampus
            window.onResponseListener = onResponseListener
            window.show(activity.supportFragmentManager, "verify")
        } else {
            LogUtils.e("❌ showEasVerifyWindow FAILED: unable to extract AppCompatActivity from context")
            LogUtils.e("Original context type: ${from.javaClass.name}")
            LogUtils.e("Context hierarchy: ${getContextHierarchy(from)}")
            LogUtils.e("This usually means the context is not properly initialized or is a wrong type")
        }
    }

    /**
     * 从可能被包装的Context中提取底层的Activity
     * 支持Hilt的FragmentContextWrapper和其他Context包装器
     */
    private fun unwrapContextToActivity(context: Context): AppCompatActivity? {
        var currentContext: Context? = context

        LogUtils.d("=== 🔧 Starting context unwrapping ===")
        LogUtils.d("Initial context: ${currentContext?.javaClass?.name}")

        // 最多解包5层，避免无限循环
        repeat(5) {
            val ctx = currentContext ?: run {
                LogUtils.d("❌ Context became null at iteration $it")
                return null
            }

            LogUtils.d("Iteration $it: context type = ${ctx.javaClass.name}")

            when (ctx) {
                is AppCompatActivity -> {
                    LogUtils.d("✅ Found AppCompatActivity: ${ctx.javaClass.simpleName}")
                    return ctx
                }
                is Activity -> {
                    LogUtils.d("⚠️ Found Activity (not AppCompatActivity): ${ctx.javaClass.simpleName}")
                    // 如果是Activity但不是AppCompatActivity，尝试转换
                    return ctx as? AppCompatActivity
                }
                is android.view.ContextThemeWrapper -> {
                    LogUtils.d("📦 Unwrapping ContextThemeWrapper")
                    currentContext = ctx.baseContext
                    LogUtils.d("   -> baseContext: ${currentContext?.javaClass?.name}")
                }
                else -> {
                    LogUtils.d("🔍 Attempting to unwrap custom wrapper: ${ctx.javaClass.simpleName}")
                    // 尝试多种方式获取baseContext
                    var unwrapped = false

                    // 方法1: 尝试反射获取baseContext字段
                    try {
                        val baseContextField = ctx.javaClass.getDeclaredField("baseContext")
                        baseContextField.isAccessible = true
                        val baseCtx = baseContextField.get(ctx) as? Context
                        if (baseCtx != null && baseCtx != ctx) {
                            LogUtils.d("   -> [reflection] baseContext: ${baseCtx.javaClass.name}")
                            currentContext = baseCtx
                            unwrapped = true
                        }
                    } catch (e: Exception) {
                        LogUtils.d("   -> [reflection] Failed: ${e.message}")
                    }

                    // 方法2: 尝试获取activity字段（针对FragmentContextWrapper）
                    if (!unwrapped) {
                        try {
                            val activityField = ctx.javaClass.getDeclaredField("activity")
                            activityField.isAccessible = true
                            val activity = activityField.get(ctx) as? Activity
                            if (activity != null) {
                                LogUtils.d("   -> [activity field] Found activity: ${activity.javaClass.name}")
                                return activity as? AppCompatActivity
                            }
                        } catch (e: Exception) {
                            LogUtils.d("   -> [activity field] Failed: ${e.message}")
                        }
                    }

                    // 方法3: 尝试通过getActivity方法（如果有）
                    if (!unwrapped) {
                        try {
                            val getActivityMethod = ctx.javaClass.getDeclaredMethod("getActivity")
                            getActivityMethod.isAccessible = true
                            val activity = getActivityMethod.invoke(ctx) as? Activity
                            if (activity != null) {
                                LogUtils.d("   -> [getActivity method] Found activity: ${activity.javaClass.name}")
                                return activity as? AppCompatActivity
                            }
                        } catch (e: Exception) {
                            LogUtils.d("   -> [getActivity method] Failed: ${e.message}")
                        }
                    }

                    if (!unwrapped) {
                        LogUtils.d("❌ Could not unwrap context, stopping")
                        currentContext = null
                    }
                }
            }
        }

        LogUtils.d("❌ Context unwrapping failed after 5 iterations")
        return null
    }

    /**
     * 获取Context的继承层次结构，用于debug
     */
    private fun getContextHierarchy(context: Context?): String {
        if (context == null) return "null"

        val hierarchy = mutableListOf<String>()
        var current: Any? = context
        var depth = 0
        val maxDepth = 10

        while (current != null && depth < maxDepth) {
            hierarchy.add("${current.javaClass.name}")
            when (current) {
                is android.view.ContextThemeWrapper -> {
                    current = current.baseContext
                }
                is Context -> {
                    // 尝试通过反射获取baseContext
                    try {
                        val baseContextField = current.javaClass.getDeclaredField("baseContext")
                        baseContextField.isAccessible = true
                        val baseCtx = baseContextField.get(current) as? Context
                        if (baseCtx != null && baseCtx != current) {
                            current = baseCtx
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
                else -> break
            }
            depth++
        }

        return hierarchy.joinToString(" -> ")
    }


    fun <T : Activity> startActivity(from: Context, activity: Class<T>) {
        val i = Intent(from, activity)
        from.startActivity(i)
    }

    fun startTimetableManager(from: Context) {
        val i = Intent(from, TimetableManagerActivity::class.java)
        from.startActivity(i)
    }


    fun startSubjectActivity(from: Context, id: String) {
        val i = Intent(from, SubjectActivity::class.java)
        i.putExtra("subjectId", id)
        from.startActivity(i)
    }

    fun startCourseResourceSearchActivity(
        from: Context,
        query: String? = null,
        mode: CourseResourceMode = CourseResourceMode.VIEW,
    ) {
        val i = Intent(from, CourseResourceSearchActivity::class.java)
        i.putExtra("query", query)
        i.putExtra("mode", mode.name)
        from.startActivity(i)
    }

    fun startCourseReadmeActivity(
        from: Context,
        repoName: String,
        courseName: String,
        courseCode: String,
        repoType: String = "normal",
    ) {
        val i = Intent(from, CourseReadmeActivity::class.java)
        i.putExtra("repoName", repoName)
        i.putExtra("courseName", courseName)
        i.putExtra("courseCode", courseCode)
        i.putExtra("repoType", repoType)
        from.startActivity(i)
    }

    fun startCourseContributionActivity(
        from: Context,
        repoName: String,
        courseName: String,
        courseCode: String,
        repoType: String = "normal",
    ) {
        val i = Intent(from, CourseContributionActivity::class.java)
        i.putExtra("repoName", repoName)
        i.putExtra("courseName", courseName)
        i.putExtra("courseCode", courseCode)
        i.putExtra("repoType", repoType)
        from.startActivity(i)
    }

    fun startInternalWebActivity(from: Context, title: String, url: String) {
        val i = Intent(from, InternalWebActivity::class.java)
        i.putExtra("title", title)
        i.putExtra("url", url)
        from.startActivity(i)
    }

    fun startTeacherHomepageSearch(from: Context, teacherName: String) {
        if (teacherName.isBlank()) return
        val encodedName = URLEncoder.encode(teacherName, StandardCharsets.UTF_8.toString())
        startInternalWebActivity(
            from = from,
            title = teacherName,
            url = "https://homepage.hit.edu.cn/search-teacher-by-phoneticize?condition=$encodedName",
        )
    }

    fun startTimetableDetailActivity(from: Context, id: String) {
        val i = Intent(from, TimetableDetailActivity::class.java)
        i.putExtra("id", id)
        from.startActivity(i)
    }

    fun startProfileActivity(from: Context, userId: String?, imageView: ImageView?=null) {
        val i = Intent(from, ProfileActivity::class.java)
        i.putExtra("id", userId)
        imageView?.let {
            val op = ActivityOptionsCompat.makeSceneTransitionAnimation(from as Activity,it,"useravatar")
            from.startActivity(i,op.toBundle())
        }?:run {
            from.startActivity(i)
        }
    }

    fun showUpdateNotificationForce(cr:CheckUpdateResult,activity: AppCompatActivity){
        PopUpText().setText("版本：${cr.latestVersionName}\n更新内容：${cr.updateLog}\n" + "是否前往下载？")
            .setTitle(R.string.new_version_available)
            .setOnConfirmListener(object : PopUpText.OnConfirmListener {

                override fun OnConfirm() {
                    val uri: Uri = Uri.parse(cr.latestUrl);
                    val intent = Intent(Intent.ACTION_VIEW, uri);
                    activity.startActivity(intent)
                }
            }).show(activity.supportFragmentManager, "update")
    }


    fun showUpdateNotification(cr:CheckUpdateResult,activity: AppCompatActivity){
       val preference: SharedPreferences =
            activity.application.getSharedPreferences("update_skip", Context.MODE_PRIVATE)
        if(preference.getBoolean(cr.latestVersionCode.toString(),false)) return
        PopUpUpdate().setText("版本：${cr.latestVersionName}\n更新内容：${cr.updateLog}\n" + "是否前往下载？")
            .setTitle(R.string.new_version_available)
            .setOnActionListener(object : PopUpUpdate.OnActionListener {
                override fun onConfirm() {
                    val uri: Uri = Uri.parse(cr.latestUrl);
                    val intent = Intent(Intent.ACTION_VIEW, uri);
                    activity.startActivity(intent)
                }

                override fun onCancel() {

                }

                override fun onSkip() {
                    preference.edit().putBoolean(cr.latestVersionCode.toString(),true).apply()
                }
            }).show(activity.supportFragmentManager, "update")
    }

    fun startNewsActivity(from: Context, url: String, title: String) {
        val i = Intent(from, NewsDetailActivity::class.java)
        i.putExtra("link", url)
        i.putExtra("title", title)
        i.putExtra("mode", "hitsz_news")
        from.startActivity(i)
    }
}
