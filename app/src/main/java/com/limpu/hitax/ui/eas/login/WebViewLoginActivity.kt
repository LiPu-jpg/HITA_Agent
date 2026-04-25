package com.limpu.hitax.ui.eas.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.databinding.ActivityWebviewLoginBinding
import com.limpu.hitax.utils.LogUtils
import com.limpu.style.base.BaseActivity
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class WebViewLoginActivity : BaseActivity<WebViewLoginViewModel, ActivityWebviewLoginBinding>() {

    companion object {
        const val EXTRA_SILENT_MODE = "silent_mode"
        const val EXTRA_CAMPUS = "campus"

        // 校园网络URL常量
        private object CampusUrls {
            // 本部校区URL
            private const val BENBU_BASE = "http://i-hit-edu-cn.ivpn.hit.edu.cn:1080"
            private const val JWTS_BASE = "http://jwts-hit-edu-cn.ivpn.hit.edu.cn:1080"

            // 威海校区URL
            private const val WEIHAI_BASE = "https://webvpn.hitwh.edu.cn"
            private const val WEIHAI_EAS_PREFIX = "$WEIHAI_BASE/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667"

            val BENBU_LOGIN = "$BENBU_BASE/portal/home/"
            val BENBU_JWTS = "$JWTS_BASE/loginCAS"
            val BENBU_PROBE_URLS = listOf(
                "$JWTS_BASE/loginCAS",
                "$BENBU_BASE/",
                "$BENBU_BASE/portal/home/"
            )

            val WEIHAI_LOGIN = "$WEIHAI_BASE/"
            val WEIHAI_JWTS = "$WEIHAI_EAS_PREFIX/loginCAS"
            val WEIHAI_PROBE_URLS = listOf(
                WEIHAI_JWTS,
                "$WEIHAI_EAS_PREFIX/kjscx/queryJxlListBySjid",
                "$WEIHAI_EAS_PREFIX/cjcx/queryQmcj",
                "$WEIHAI_BASE/"
            )
        }

        private const val COOKIE_RETRY_COUNT = 30
        private const val COOKIE_RETRY_DELAY_MS = 500L
        private const val SILENT_TIMEOUT_MS = 18000L
        private val BENBU_REQUIRED_COOKIES = setOf("JSESSIONID", "HIT", "TWFID")
        private const val WEIHAI_TICKET_COOKIE_PREFIX = "wengine_vpn_ticket"
        private val WEIHAI_EAS_SESSION_COOKIE_HINTS = listOf("JSESSIONID", "HIT", "TWFID")
    }

    private data class CampusWebConfig(
        val campus: EASToken.Campus,
        val loginUrl: String,
        val jwtsUrl: String,
        val cookieProbeUrls: List<String>
    )

    private var finished = false
    private var cookieRetryCount = 0
    private var autoOpeningJwts = false
    private var silentMode = false
    private lateinit var config: CampusWebConfig

    override fun getViewModelClass(): Class<WebViewLoginViewModel> = WebViewLoginViewModel::class.java

    override fun initViewBinding(): ActivityWebviewLoginBinding =
        ActivityWebviewLoginBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        val campus = runCatching {
            EASToken.Campus.valueOf(
                intent?.getStringExtra(EXTRA_CAMPUS) ?: EASToken.Campus.BENBU.name
            )
        }.getOrDefault(EASToken.Campus.BENBU)
        config = configFor(campus)
        silentMode = intent?.getBooleanExtra(EXTRA_SILENT_MODE, false) == true

        super.onCreate(savedInstanceState)

        if (silentMode) {
            window?.setDimAmount(0f)
            binding.toolbar.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
            binding.webview.alpha = 0.02f
        } else {
            setToolbarActionBack(binding.toolbar)
        }
        LogUtils.d( "onCreate silentMode=$silentMode campus=${config.campus}")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun initViews() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webview, true)
        setupWebView()
        if (silentMode) {
            binding.webview.postDelayed({
                if (!finished) {
                    LogUtils.w( "silent web login timeout campus=${config.campus}")
                    finishWithCancelledResult()
                }
            }, SILENT_TIMEOUT_MS)
        }
        LogUtils.d( "load login url=${config.loginUrl} campus=${config.campus}")
        binding.webview.loadUrl(config.loginUrl)
    }

    private fun configFor(campus: EASToken.Campus): CampusWebConfig {
        return when (campus) {
            EASToken.Campus.BENBU -> CampusWebConfig(
                campus = campus,
                loginUrl = CampusUrls.BENBU_LOGIN,
                jwtsUrl = CampusUrls.BENBU_JWTS,
                cookieProbeUrls = CampusUrls.BENBU_PROBE_URLS
            )
            EASToken.Campus.WEIHAI -> CampusWebConfig(
                campus = campus,
                loginUrl = CampusUrls.WEIHAI_LOGIN,
                jwtsUrl = CampusUrls.WEIHAI_JWTS,
                cookieProbeUrls = CampusUrls.WEIHAI_PROBE_URLS
            )
            EASToken.Campus.SHENZHEN -> configFor(EASToken.Campus.BENBU)
        }
    }

    private fun setupWebView() {
        binding.webview.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress == 100) {
                        binding.progressBar.visibility = View.GONE
                    } else {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.progressBar.progress = newProgress
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    val hasJsessionid = url.contains("jsessionid", ignoreCase = true)
                    LogUtils.i( "📄 Page: ${url.take(80)}... jsessionid=$hasJsessionid campus=${config.campus}")

                    // 打印当前 cookie 状态
                    val currentCookies = collectCookies()
                    val vpnTicket = hasWeihaiVpnTicket(currentCookies)
                    val easJsessionid = currentCookies.containsKey("JSESSIONID")
                    LogUtils.i( "🍪 Current cookies: VPN ticket=$vpnTicket, JSESSIONID=$easJsessionid, total=${currentCookies.size}")

                    if (finished) return

                    when {
                        isPortalHomePage(url) -> {
                            LogUtils.i( "🏠 Portal home detected, auto open jwts campus=${config.campus}")
                            autoOpenJwts(view)
                        }
                        isSuccessPage(url) -> {
                            // 已进入成功页面，威海校区需要调用 VPN API 获取真实 cookies
                            autoOpeningJwts = false
                            LogUtils.i( "✅ Success page detected, finishing login campus=${config.campus}")
                            handleSuccessPage()
                        }
                        isJwtsPage(url) -> {
                            autoOpeningJwts = false
                            LogUtils.i( "🔐 Login page detected, waiting for user action campus=${config.campus}")
                            // 登录页面，启动定时检测
                            startCookiePolling()
                        }
                        else -> {
                            LogUtils.d( "⏳ Other page, waiting... url=$url campus=${config.campus}")
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    LogUtils.w( "onReceivedError url=${request?.url} code=${error?.errorCode} desc=${error?.description} campus=${config.campus}")
                }
            }
        }
    }

    private fun isPortalHomePage(url: String): Boolean {
        val uri = Uri.parse(url)
        val normalizedPath = uri.path?.trimEnd('/') ?: ""
        return when (config.campus) {
            EASToken.Campus.BENBU -> uri.host == "i-hit-edu-cn.ivpn.hit.edu.cn" && normalizedPath == "/portal/home"
            EASToken.Campus.WEIHAI -> uri.host == "webvpn.hitwh.edu.cn" && (normalizedPath.isBlank() || normalizedPath == "/portal/home")
            EASToken.Campus.SHENZHEN -> false
        }
    }

    private fun isJwtsPage(url: String): Boolean {
        val urlLower = url.lowercase()
        // 威海校区：loginCAS 页面（不管有没有jsessionid，都让 isSuccessPage 判断）
        if (config.campus == EASToken.Campus.WEIHAI) {
            return false // 威海不使用这个判断，全部交给 isSuccessPage
        }
        // 其他校区：按原逻辑
        return urlLower.contains("logincas") || urlLower.contains("login")
    }

    private fun isSuccessPage(url: String): Boolean {
        val uri = Uri.parse(url) ?: return false
        val path = uri.path?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val urlLower = url.lowercase()

        return when (config.campus) {
            EASToken.Campus.BENBU -> {
                // 本部：已进入教务系统页面（非登录页）
                val cookies = collectCookies()
                val hasRequiredCookies = cookies.containsKey("JSESSIONID") &&
                                         cookies.containsKey("HIT") &&
                                         cookies.containsKey("TWFID")

                // 如果cookies齐全，不管在什么页面都认为登录成功
                if (hasRequiredCookies) {
                    LogUtils.i( "✅ Benbu: all required cookies present, treating as success")
                    return true
                }

                // 否则需要到达功能页面才算成功
                (host.contains("jwts") || host.contains("hit.edu.cn")) &&
                (path.contains("kbcx") || path.contains("cjcx") || path.contains("kjscx") ||
                 path.contains("xswh") || path.contains("query") || path.contains("index"))
            }
            EASToken.Campus.WEIHAI -> {
                // 威海：只要在 loginCAS 页面就算成功（让用户输入账号密码）
                // 或者在其他功能页面
                val isLoginCasPage = urlLower.contains("logincas")
                val isFunctionPage = path.contains("kbcx") || path.contains("cjcx") ||
                                   path.contains("kjscx") || path.contains("query") ||
                                   path.contains("index")
                isLoginCasPage || isFunctionPage
            }
            EASToken.Campus.SHENZHEN -> false
        }
    }

    private fun autoOpenJwts(webView: WebView) {
        if (autoOpeningJwts) return
        autoOpeningJwts = true
        LogUtils.i( "🔄 Auto opening JWTS campus=${config.campus}")
        webView.loadUrl(config.jwtsUrl)
    }

    private fun startCookiePolling() {
        // 威海校区：启动定时检测，等待用户输入账号密码
        if (config.campus == EASToken.Campus.WEIHAI) {
            cookieRetryCount = 0
            binding.webview.postDelayed({
                checkCookiesAndFinish()
            }, COOKIE_RETRY_DELAY_MS)
        }
    }

    private fun checkCookiesAndFinish() {
        if (finished) return

        val cookies = collectCookies()
        // 当前URL，用于调试（暂时未使用）
        // val currentUrl = binding.webview.url.orEmpty()
        val hasVpnTicket = hasWeihaiVpnTicket(cookies)
        val hasJsessionid = cookies.containsKey("JSESSIONID")

        LogUtils.i( "🔍 Cookie check #$cookieRetryCount: VPN ticket=$hasVpnTicket, JSESSIONID=$hasJsessionid, total=${cookies.size}")

        // 威海校区：只要有 VPN ticket 和 JSESSIONID 就完成
        if (config.campus == EASToken.Campus.WEIHAI && hasVpnTicket && hasJsessionid) {
            LogUtils.i( "✅ Cookies ready, finishing campus=${config.campus}")

            // 调用 VPN API 获取完整 cookies
            fetchVpnEasCookies { vpnCookies ->
                val mergedCookies = LinkedHashMap(cookies)
                vpnCookies.forEach { (key, value) -> mergedCookies.putIfAbsent(key, value) }
                LogUtils.i( "🍪 Final cookies: ${mergedCookies.keys} ${fingerprintSummary(mergedCookies)}")
                finishWithCookies(mergedCookies)
            }
            return
        }

        // 超时检查
        if (cookieRetryCount >= COOKIE_RETRY_COUNT) {
            LogUtils.w( "⏱️ Cookie polling timeout, staying on page campus=${config.campus}")
            return
        }

        // 继续轮询
        cookieRetryCount++
        binding.webview.postDelayed({
            checkCookiesAndFinish()
        }, COOKIE_RETRY_DELAY_MS)
    }

    private fun handleSuccessPage() {
        if (finished) return
        LogUtils.i( "🎯 Handling success page, collecting cookies campus=${config.campus}")
        val cookies = collectCookies()
        // 当前页面URL，用于调试和日志记录
        val currentUrl = binding.webview.url.orEmpty()
        LogUtils.d( "当前URL: $currentUrl")

        LogUtils.i( "🔍 Cookie validation before finish:")
        LogUtils.i( "  - VPN ticket: ${hasWeihaiVpnTicket(cookies)}")
        LogUtils.i( "  - JSESSIONID: ${cookies.containsKey("JSESSIONID")} (${cookies["JSESSIONID"]?.take(8) ?: "missing"})")
        LogUtils.i( "  - HIT: ${cookies.containsKey("HIT")} (${cookies["HIT"]?.take(8) ?: "missing"})")
        LogUtils.i( "  - TWFID: ${cookies.containsKey("TWFID")} (${cookies["TWFID"]?.take(8) ?: "missing"})")

        // 对于威海校区，需要调用 VPN API 获取真实的 EAS session cookies
        if (config.campus == EASToken.Campus.WEIHAI && hasWeihaiVpnTicket(cookies)) {
            LogUtils.i( "🌐 Weihai: fetching VPN EAS cookies campus=${config.campus}")
            fetchVpnEasCookies { vpnCookies ->
                val mergedCookies = LinkedHashMap(cookies)
                vpnCookies.forEach { (key, value) -> mergedCookies.putIfAbsent(key, value) }

                LogUtils.i( "🔍 Cookie validation after VPN merge:")
                LogUtils.i( "  - VPN ticket: ${hasWeihaiVpnTicket(mergedCookies)}")
                LogUtils.i( "  - JSESSIONID: ${mergedCookies.containsKey("JSESSIONID")} (${mergedCookies["JSESSIONID"]?.take(8) ?: "missing"})")
                LogUtils.i( "  - HIT: ${mergedCookies.containsKey("HIT")} (${mergedCookies["HIT"]?.take(8) ?: "missing"})")
                LogUtils.i( "  - TWFID: ${mergedCookies.containsKey("TWFID")} (${mergedCookies["TWFID"]?.take(8) ?: "missing"})")

                LogUtils.i( "🍪 Weihai merged cookies: ${mergedCookies.keys} ${fingerprintSummary(mergedCookies)} campus=${config.campus}")
                finishWithCookies(mergedCookies)
            }
        } else {
            // 其他校区直接完成
            LogUtils.i( "🍪 Non-Weihai: finishing with ${cookies.size} cookies ${fingerprintSummary(cookies)} campus=${config.campus}")
            finishWithCookies(cookies)
        }
    }

    private fun hasRequiredCookies(cookies: Map<String, String>, currentUrl: String): Boolean {
        return when (config.campus) {
            EASToken.Campus.BENBU, EASToken.Campus.SHENZHEN -> {
                // 本部校区：需要所有三个 cookies（JSESSIONID, HIT, TWFID）
                // 如果 URL 中有 jsessionid，将其视为 JSESSIONID cookie
                val hasJsession = cookies.containsKey("JSESSIONID") || hasUrlJsession(currentUrl)
                val hasHit = cookies.containsKey("HIT")
                val hasTmfid = cookies.containsKey("TWFID")
                hasJsession && hasHit && hasTmfid
            }
            EASToken.Campus.WEIHAI -> {
                // 威海校区：需要 VPN ticket + JSESSIONID
                val hasVpnTicket = hasWeihaiVpnTicket(cookies)
                val hasJsession = cookies.containsKey("JSESSIONID")
                LogUtils.d( "Weihai cookie check: hasVpnTicket=$hasVpnTicket hasJsession=$hasJsession campus=${config.campus}")
                hasVpnTicket && hasJsession
            }
        }
    }

    private fun hasWeihaiVpnTicket(cookies: Map<String, String>): Boolean {
        return cookies.keys.any { key ->
            key.startsWith(WEIHAI_TICKET_COOKIE_PREFIX, ignoreCase = true) ||
                key.contains(WEIHAI_TICKET_COOKIE_PREFIX, ignoreCase = true)
        }
    }

    private fun hasUrlJsession(url: String): Boolean {
        return url.contains(";jsessionid=", ignoreCase = true) ||
            url.contains("jsessionid=", ignoreCase = true)
    }

    private fun collectCookies(): LinkedHashMap<String, String> {
        val cookieManager = CookieManager.getInstance()
        val cookies = LinkedHashMap<String, String>()

        // 先从配置的 probe URLs 收集
        config.cookieProbeUrls.forEach { url ->
            val parsed = parseCookies(cookieManager.getCookie(url))
            LogUtils.d( "cookies from $url -> ${parsed.keys.sorted()} ${fingerprintSummary(parsed)} campus=${config.campus}")
            parsed.forEach { (key, value) ->
                cookies.putIfAbsent(key, value)
            }
        }

        // 额外从当前页面 URL 收集 cookie（适用于用户被重定向到其他页面的情况）
        val currentUrl = binding.webview.url
        if (!currentUrl.isNullOrBlank() && currentUrl.startsWith("http")) {
            val parsed = parseCookies(cookieManager.getCookie(currentUrl))
            LogUtils.d( "cookies from current page $currentUrl -> ${parsed.keys.sorted()} ${fingerprintSummary(parsed)} campus=${config.campus}")
            parsed.forEach { (key, value) ->
                cookies.putIfAbsent(key, value)
            }

            // 如果 CookieManager 中没有 JSESSIONID，尝试从 URL 中提取
            // 某些 EAS 系统将 session ID 放在 URL 而不是 cookie 中
            if (!cookies.containsKey("JSESSIONID")) {
                val jsessionid = extractJsessionidFromUrl(currentUrl)
                if (jsessionid != null) {
                    LogUtils.d( "extracted JSESSIONID from URL: $jsessionid campus=${config.campus}")
                    cookies["JSESSIONID"] = jsessionid
                }
            }
        }

        return cookies
    }

    private fun fetchVpnEasCookies(callback: (Map<String, String>) -> Unit) {
        // 在主线程上捕获 WebView 数据
        val currentUrl = binding.webview.url ?: ""
        val cookieHeader = buildCookieHeader(currentUrl)

        Thread {
            val result = mutableMapOf<String, String>()
            try {
                // 威海校区的 EAS 系统域名和登录页面路径
                val easHost = "jwts.hitwh.edu.cn"
                val easPath = "/loginCAS"  // 使用登录页面路径

                // 调用 VPN cookie API
                val cookieApiUrl = "https://webvpn.hitwh.edu.cn/wengine-vpn/cookie?method=get&host=$easHost&scheme=http&path=$easPath&vpn_timestamp=${System.currentTimeMillis()}"

                LogUtils.d( "fetching VPN cookies from: $cookieApiUrl campus=${config.campus}")

                // 使用同步 HTTP 请求
                val connection = URL(cookieApiUrl).openConnection() as javax.net.ssl.HttpsURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Cookie", cookieHeader)
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.setRequestProperty("Accept", "*/*")

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    LogUtils.d( "VPN cookie API response: $response campus=${config.campus}")

                    // 解析返回的 cookies（格式：name=value; JSESSIONID=xxx; HIT=yyy）
                    val parts = response.split(";")
                    for (part in parts) {
                        val trimmed = part.trim()
                        if (trimmed.contains("=")) {
                            val idx = trimmed.indexOf('=')
                            val key = trimmed.substring(0, idx).trim()
                            val value = trimmed.substring(idx + 1).trim()
                            if (key.isNotEmpty() && value.isNotEmpty()) {
                                // 只关心 EAS 相关的 cookies
                                if (key == "JSESSIONID" || key == "HIT" || key == "TWFID") {
                                    result[key] = value
                                    LogUtils.d( "parsed VPN cookie: $key=$value campus=${config.campus}")
                                }
                            }
                        }
                    }
                } else {
                    LogUtils.w( "VPN cookie API failed with code: $responseCode campus=${config.campus}")
                }
            } catch (e: Exception) {
                LogUtils.e( "failed to fetch VPN cookies: ${e.javaClass.simpleName} ${e.message} campus=${config.campus}")
            }

            binding.webview.post {
                callback(result)
            }
        }.start()
    }

    private fun buildCookieHeader(currentUrl: String): String {
        val cookieManager = CookieManager.getInstance()

        // 从当前 URL 和 VPN 主域获取 cookies
        val cookies = mutableSetOf<String>()

        // 添加当前页面的 cookies
        if (currentUrl.isNotEmpty()) {
            val currentCookies = cookieManager.getCookie(currentUrl)
            if (!currentCookies.isNullOrBlank()) {
                cookies.add(currentCookies)
            }
        }

        // 添加 VPN 主域的 cookies
        val vpnCookies = cookieManager.getCookie("https://webvpn.hitwh.edu.cn/")
        if (!vpnCookies.isNullOrBlank()) {
            cookies.add(vpnCookies)
        }

        return cookies.joinToString("; ")
    }

    private fun extractJsessionidFromUrl(url: String): String? {
        // 匹配 URL 中的 ;jsessionid=XXX 或 jsessionid=XXX 参数
        val patterns = listOf(
            ";jsessionid=([^;&?]*)",
            "[?&]jsessionid=([^;&]*)"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

    private fun fingerprintSummary(cookies: Map<String, String>): String {
        return when (config.campus) {
            EASToken.Campus.BENBU, EASToken.Campus.SHENZHEN -> {
                BENBU_REQUIRED_COOKIES.sorted().joinToString(prefix = "[", postfix = "]") { key ->
                    val value = cookies[key]
                    "$key=${value?.take(8) ?: "-"}"
                }
            }
            EASToken.Campus.WEIHAI -> {
                val ticketKey = cookies.keys.firstOrNull { it.startsWith(WEIHAI_TICKET_COOKIE_PREFIX) }
                val ticketValue = ticketKey?.let { cookies[it] }
                val easSummary = WEIHAI_EAS_SESSION_COOKIE_HINTS.joinToString(prefix = "[", postfix = "]") { key ->
                    "$key=${cookies[key]?.take(8) ?: "-"}"
                }
                "[$WEIHAI_TICKET_COOKIE_PREFIX=${ticketValue?.take(8) ?: "-"}]$easSummary"
            }
        }
    }

    private fun finishWithCookies(cookies: Map<String, String>) {
        if (finished) return
        finished = true
        LogUtils.d( "finishWithCookies keys=${cookies.keys.sorted()} ${fingerprintSummary(cookies)} campus=${config.campus}")

        // 详细打印所有 cookies
        LogUtils.i( "=== 📦 FINAL COOKIES (${cookies.size} total) ===")
        cookies.forEach { (key, value) ->
            val displayValue = if (value.length > 20) "${value.take(20)}..." else value
            LogUtils.i( "  ✅ $key = $displayValue")
        }
        LogUtils.i( "==========================================")

        val cookiesJson = JSONObject(cookies as Map<*, *>).toString()
        LogUtils.d( "cookies json length=${cookiesJson.length} campus=${config.campus}")

        val intent = Intent().apply {
            putExtra("cookies", cookiesJson)
        }
        LogUtils.i( "✅ Login SUCCESS! Returning RESULT_OK with cookies campus=${config.campus}")
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun finishWithCancelledResult() {
        if (finished) return
        finished = true
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun parseCookies(cookieString: String?): Map<String, String> {
        if (cookieString.isNullOrBlank()) return emptyMap()
        return cookieString.split(";")
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isBlank() || !trimmed.contains("=")) return@mapNotNull null
                val idx = trimmed.indexOf('=')
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                if (key.isBlank()) null else key to value
            }
            .toMap()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.webview.canGoBack()) {
            binding.webview.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        LogUtils.d( "onDestroy finished=$finished campus=${config.campus}")
        (binding.webview.parent as? ViewGroup)?.removeView(binding.webview)
        binding.webview.stopLoading()
        binding.webview.webChromeClient = WebChromeClient()
        binding.webview.webViewClient = WebViewClient()
        binding.webview.destroy()
        super.onDestroy()
    }
}

class WebViewLoginViewModel : androidx.lifecycle.ViewModel()
