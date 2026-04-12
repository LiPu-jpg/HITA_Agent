package com.stupidtree.hitax.ui.eas.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.stupidtree.hitax.data.model.eas.EASToken
import com.stupidtree.hitax.databinding.ActivityWebviewLoginBinding
import com.stupidtree.style.base.BaseActivity
import org.json.JSONObject

class WebViewLoginActivity : BaseActivity<WebViewLoginViewModel, ActivityWebviewLoginBinding>() {

    companion object {
        const val EXTRA_SILENT_MODE = "silent_mode"
        const val EXTRA_CAMPUS = "campus"

        private const val TAG = "EASWebLogin"
        private const val COOKIE_RETRY_COUNT = 10
        private const val COOKIE_RETRY_DELAY_MS = 500L
        private const val SILENT_TIMEOUT_MS = 4000L
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
        Log.d(TAG, "onCreate silentMode=$silentMode campus=${config.campus}")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun initViews() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webview, true)
        setupWebView()
        if (silentMode) {
            binding.webview.postDelayed({
                if (!finished) {
                    Log.w(TAG, "silent web login timeout campus=${config.campus}")
                    finishWithCancelledResult()
                }
            }, SILENT_TIMEOUT_MS)
        }
        Log.d(TAG, "load login url=${config.loginUrl} campus=${config.campus}")
        binding.webview.loadUrl(config.loginUrl)
    }

    private fun configFor(campus: EASToken.Campus): CampusWebConfig {
        return when (campus) {
            EASToken.Campus.BENBU -> CampusWebConfig(
                campus = campus,
                loginUrl = "http://i-hit-edu-cn.ivpn.hit.edu.cn:1080/portal/home/",
                jwtsUrl = "http://jwts-hit-edu-cn.ivpn.hit.edu.cn:1080/loginCAS",
                cookieProbeUrls = listOf(
                    "http://jwts-hit-edu-cn.ivpn.hit.edu.cn:1080/loginCAS",
                    "http://i-hit-edu-cn.ivpn.hit.edu.cn:1080/",
                    "http://i-hit-edu-cn.ivpn.hit.edu.cn:1080/portal/home/"
                )
            )
            EASToken.Campus.WEIHAI -> CampusWebConfig(
                campus = campus,
                loginUrl = "https://webvpn.hitwh.edu.cn/",
                jwtsUrl = "https://webvpn.hitwh.edu.cn/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667/loginCAS",
                cookieProbeUrls = listOf(
                    "https://webvpn.hitwh.edu.cn/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667/loginCAS",
                    "https://webvpn.hitwh.edu.cn/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667/kjscx/queryJxlListBySjid",
                    "https://webvpn.hitwh.edu.cn/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667/cjcx/queryQmcj",
                    "https://webvpn.hitwh.edu.cn/"
                )
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
                    Log.d(TAG, "onPageFinished url=$url campus=${config.campus}")
                    if (finished) return
                    if (isPortalHomePage(url)) {
                        autoOpenJwts(view)
                        return
                    }
                    if (isJwtsPage(url)) {
                        autoOpeningJwts = false
                        Log.d(TAG, "jwts page detected, start cookie polling campus=${config.campus}")
                        cookieRetryCount = 0
                        tryFinishAfterCookiesReady()
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Log.w(TAG, "onReceivedError url=${request?.url} code=${error?.errorCode} desc=${error?.description} campus=${config.campus}")
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
        return url.contains("loginCAS", ignoreCase = true)
    }

    private fun autoOpenJwts(webView: WebView) {
        if (autoOpeningJwts) return
        autoOpeningJwts = true
        Log.d(TAG, "portal home detected, auto open jwts campus=${config.campus}")
        webView.loadUrl(config.jwtsUrl)
    }

    private fun tryFinishAfterCookiesReady() {
        if (finished) return

        val cookies = collectCookies()
        val currentUrl = binding.webview.url.orEmpty()
        val keys = cookies.keys.sorted().joinToString(",")
        Log.d(TAG, "cookie poll #$cookieRetryCount keys=[$keys] urlHasJsession=${hasUrlJsession(currentUrl)} ${fingerprintSummary(cookies)} campus=${config.campus}")
        if (hasRequiredCookies(cookies, currentUrl)) {
            Log.d(TAG, "required cookies ready, finishing web login campus=${config.campus}")
            finishWithCookies(cookies)
            return
        }

        if (cookieRetryCount >= COOKIE_RETRY_COUNT) {
            val hasVpnTicket = hasWeihaiVpnTicket(cookies)
            if (config.campus == EASToken.Campus.WEIHAI && !silentMode && hasVpnTicket) {
                Log.w(TAG, "required cookies missing after retries, fallback finish for manual Weihai login")
                finishWithCookies(cookies)
                return
            }
            Log.w(TAG, "required cookies missing after retries, stay on page campus=${config.campus}")
            return
        }

        cookieRetryCount++
        binding.webview.postDelayed({
            tryFinishAfterCookiesReady()
        }, COOKIE_RETRY_DELAY_MS)
    }

    private fun hasRequiredCookies(cookies: Map<String, String>, currentUrl: String): Boolean {
        return when (config.campus) {
            EASToken.Campus.BENBU, EASToken.Campus.SHENZHEN -> BENBU_REQUIRED_COOKIES.all { cookies.containsKey(it) }
            EASToken.Campus.WEIHAI -> {
                val hasVpnTicket = hasWeihaiVpnTicket(cookies)
                val hasEasSessionCookie = WEIHAI_EAS_SESSION_COOKIE_HINTS.any { cookies.containsKey(it) }
                val hasEasSessionFromUrl = hasUrlJsession(currentUrl)
                hasVpnTicket && (hasEasSessionCookie || hasEasSessionFromUrl)
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
        config.cookieProbeUrls.forEach { url ->
            val parsed = parseCookies(cookieManager.getCookie(url))
            Log.d(TAG, "cookies from $url -> ${parsed.keys.sorted()} ${fingerprintSummary(parsed)} campus=${config.campus}")
            parsed.forEach { (key, value) ->
                cookies.putIfAbsent(key, value)
            }
        }
        return cookies
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
        Log.d(TAG, "finishWithCookies keys=${cookies.keys.sorted()} ${fingerprintSummary(cookies)} campus=${config.campus}")
        val cookiesJson = JSONObject(cookies as Map<*, *>).toString()
        val intent = Intent().apply {
            putExtra("cookies", cookiesJson)
        }
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
        Log.d(TAG, "onDestroy finished=$finished campus=${config.campus}")
        (binding.webview.parent as? ViewGroup)?.removeView(binding.webview)
        binding.webview.stopLoading()
        binding.webview.webChromeClient = WebChromeClient()
        binding.webview.webViewClient = WebViewClient()
        binding.webview.destroy()
        super.onDestroy()
    }
}

class WebViewLoginViewModel : androidx.lifecycle.ViewModel()
