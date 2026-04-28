package com.limpu.hitax

import android.app.Application
import com.limpu.hitax.utils.LogUtils
import dagger.hilt.android.HiltAndroidApp
import androidx.annotation.WorkerThread
import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.JsonObject
import com.limpu.hitax.data.AppDatabase
import com.limpu.hitax.data.model.GsonBuilderUtil
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitauser.data.repository.LocalUserRepository
import javax.inject.Inject
import com.limpu.hitax.agent.remote.AgentBackendClient
import com.limpu.hitax.data.work.CourseReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@HiltAndroidApp
class HApplication : Application() {

    @Inject
    lateinit var localUserRepository: LocalUserRepository

    // 应用级别的协程作用域，生命周期与应用一致
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch(Dispatchers.IO) {
            initPdfCMapResources()
        }

        val database = AppDatabase.getDatabase(this@HApplication)
        val timetableDao = database.timetableDao()
        val subjectDao = database.subjectDao()
        val eventDao = database.eventItemDao()
        // 注意：在生产环境中应该移除或修改SSL设置
        // handleSSLHandshake()

        // 初始化课程提醒（根据用户设置自动调度或取消）
        CourseReminderScheduler.autoSchedule(this)

        reportAppVisit()
    }

    private fun reportAppVisit() {
        val prefs = getSharedPreferences("stats", android.content.Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
        applicationScope.launch {
            try {
                AgentBackendClient.reportVisit(deviceId)
            } catch (e: Exception) {
                LogUtils.e( "报告访问失败", e)
            }
        }
    }

    /**
     * 设置SSL握手处理
     *
     * ⚠️ 警告：此方法信任所有SSL证书，仅用于开发/调试环境。
     * 在生产环境中使用此配置会使应用容易受到中间人攻击。
     * 建议：在生产环境中移除此方法，使用正确的SSL证书验证。
     */
    @Suppress("DEPRECATION", "UNUSED")
    private fun handleSSLHandshake() {
        try {
            // 注意：信任所有证书是不安全的，仅用于特定的校园网络环境
            // 在正式环境中应该使用正确的证书验证
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate?> {
                    return arrayOfNulls(0)
                }

                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            LogUtils.e( "SSL握手设置失败", e)
        }
    }

    private fun initPdfCMapResources() {
        try {
            LogUtils.d( "📚 开始初始化 PDFBox CMap 资源...")
            val cmapDir = "com/tom_roush/pdfbox/resources/cmap/"
            val assets = assets

            val cmapFiles = assets.list(cmapDir)
            if (cmapFiles == null) {
                LogUtils.e( "❌ 无法列出 CMap 目录")
                return
            }

            LogUtils.d( "📂 找到 ${cmapFiles.size} 个 CMap 文件")

            val cMapManagerClass = Class.forName("com.tom_roush.pdfbox.pdmodel.font.CMapManager")
            val cMapParserClass = Class.forName("com.tom_roush.fontbox.cmap.CMapParser")

            // 探查 CMapManager 的实际结构
            LogUtils.d( "🔍 探查 CMapManager 类结构...")
            val declaredFields = cMapManagerClass.declaredFields
            LogUtils.d( "📋 CMapManager 字段列表 (${declaredFields.size} 个):")
            declaredFields.forEach { field ->
                LogUtils.d( "   - ${field.name}: ${field.type.name} (static=${java.lang.reflect.Modifier.isStatic(field.modifiers)})")
            }

            // 探查 CMapParser 的方法
            LogUtils.d( "🔍 探查 CMapParser 类方法...")
            val declaredMethods = cMapParserClass.declaredMethods
            LogUtils.d( "📋 CMapParser 方法列表 (${declaredMethods.size} 个):")
            declaredMethods.filter { it.name == "parse" }.forEach { method ->
                val paramTypes = method.parameterTypes.joinToString(", ") { it.simpleName }
                LogUtils.d( "   - parse($paramTypes): ${method.returnType.simpleName} (static=${java.lang.reflect.Modifier.isStatic(method.modifiers)})")
            }

            // 尝试找到所有可能的缓存字段
            val cacheField = declaredFields.firstOrNull {
                it.type == Map::class.java || it.type == ConcurrentHashMap::class.java
            }

            if (cacheField != null) {
                LogUtils.d( "✅ 找到缓存字段: ${cacheField.name}")

                // 检查是否需要实例
                if (java.lang.reflect.Modifier.isStatic(cacheField.modifiers)) {
                    cacheField.isAccessible = true
                    val cache = cacheField.get(null)
                    LogUtils.d( "✅ 缓存类型: ${cache?.javaClass?.name}")

                    val cmapCache = cache as? MutableMap<String, Any>
                    if (cmapCache != null) {
                        // 创建 CMapParser 实例
                        val parserConstructor = cMapParserClass.getDeclaredConstructor()
                        parserConstructor.isAccessible = true
                        val parserInstance = parserConstructor.newInstance()
                        LogUtils.d( "✅ 创建 CMapParser 实例: $parserInstance")

                        val parseMethod = cMapParserClass.getDeclaredMethod("parse", java.io.InputStream::class.java)
                        parseMethod.isAccessible = true

                        var registeredCount = 0
                        // 扩展常用CMap列表：覆盖中文、日文、韩文
                        val keyCMaps = listOf(
                            "Identity-H",           // 最常用：水平书写
                            "Identity-V",           // 垂直书写
                            "Adobe-GB1-UCS2",       // 简体中文
                            "Adobe-CNS1-UCS2",      // 繁体中文
                            "Adobe-Japan1-UCS2",    // 日文
                            "Adobe-Korea1-UCS2",    // 韩文
                            "GBK-EUC-H",            // 中文编码
                            "UniGB-UTF16-H"         // Unicode中文
                        )

                        for (cmapFile in keyCMaps) {
                            if (cmapFile in cmapFiles) {
                                try {
                                    LogUtils.d( "📖 注册: $cmapFile")
                                    val inputStream = assets.open("$cmapDir$cmapFile")
                                    val cmap = parseMethod.invoke(parserInstance, inputStream)
                                    inputStream.close()

                                    cmapCache[cmapFile] = cmap!!
                                    registeredCount++
                                    LogUtils.d( "✅ 注册成功: $cmapFile")
                                } catch (e: Exception) {
                                    LogUtils.w( "⚠️ 注册失败 $cmapFile: ${e::class.simpleName} - ${e.message}")
                                }
                            } else {
                                LogUtils.w( "⚠️ 文件不存在: $cmapFile")
                            }
                        }

                        LogUtils.d( "📊 注册完成: $registeredCount, 缓存大小: ${cmapCache.size}")
                    }
                }
            } else {
                LogUtils.w( "⚠️ 未找到 Map 类型的缓存字段")
            }

            LogUtils.d( "✅ PDFBox CMap 资源初始化完成")
        } catch (e: Exception) {
            LogUtils.e( "❌ 初始化 CMap 资源失败: ${e.message}", e)
        }
    }


}
