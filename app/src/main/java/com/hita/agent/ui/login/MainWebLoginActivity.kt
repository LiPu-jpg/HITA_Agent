package com.hita.agent.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.hita.agent.core.data.DebugLog
import org.json.JSONObject

class MainWebLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startUrl = intent.getStringExtra(EXTRA_START_URL) ?: DEFAULT_START_URL
        DebugLog.i("MainWebLogin", "start webview login")
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        setContent {
            val loading = remember { mutableStateOf(true) }
            val finished = remember { mutableStateOf(false) }

            MaterialTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (loading.value) {
                        LinearProgressIndicator()
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.userAgentString = WebSettings.getDefaultUserAgent(context)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                        DebugLog.d("MainWebLogin", "page started host=${Uri.parse(url).host}")
                                        loading.value = true
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        DebugLog.d("MainWebLogin", "page finished host=${Uri.parse(url).host}")
                                        loading.value = false
                                        if (finished.value) return
                                        if (isLoginCallback(url)) {
                                            DebugLog.i("MainWebLogin", "login callback detected")
                                            val cookiesByHost = collectCookies(cookieManager, MAIN_COOKIE_HOSTS)
                                            val filtered = filterCookies(cookiesByHost)
                                            if (filtered.isNotEmpty()) {
                                                DebugLog.i("MainWebLogin", "session cookies captured hosts=${filtered.keys.size}")
                                                finished.value = true
                                                val payload = JSONObject(filtered).toString()
                                                setResult(RESULT_OK, Intent().putExtra(EXTRA_COOKIES_JSON, payload))
                                                finish()
                                            }
                                        }
                                    }
                                }
                                loadUrl(startUrl)
                            }
                        }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_START_URL = "extra_start_url"
        private const val EXTRA_COOKIES_JSON = "extra_cookies_json"
        private const val DEFAULT_START_URL =
            "https://ids-hit-edu-cn-s.ivpn.hit.edu.cn/authserver/login?service=https%3A%2F%2Fjwts-hit-edu-cn.ivpn.hit.edu.cn%2FloginCAS"
        private val MAIN_COOKIE_HOSTS = listOf(
            "ids-hit-edu-cn-s.ivpn.hit.edu.cn",
            "ivpn.hit.edu.cn",
            "i-hit-edu-cn.ivpn.hit.edu.cn",
            "jwts-hit-edu-cn.ivpn.hit.edu.cn"
        )

        fun createIntent(context: Context, startUrl: String? = null): Intent {
            val intent = Intent(context, MainWebLoginActivity::class.java)
            if (!startUrl.isNullOrBlank()) {
                intent.putExtra(EXTRA_START_URL, startUrl)
            }
            return intent
        }

        fun extractCookies(intent: Intent?): Map<String, String> {
            val json = intent?.getStringExtra(EXTRA_COOKIES_JSON) ?: return emptyMap()
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.optString(key, "")
            }
            return filterCookies(map)
        }

        private fun isLoginCallback(url: String): Boolean {
            return url.contains("jwts-hit-edu-cn.ivpn.hit.edu.cn") && url.contains("loginCAS")
        }

        private fun collectCookies(cookieManager: CookieManager, hosts: List<String>): Map<String, String> {
            val map = mutableMapOf<String, String>()
            hosts.forEach { host ->
                val https = cookieManager.getCookie("https://$host")
                val http = cookieManager.getCookie("http://$host")
                val value = when {
                    !https.isNullOrBlank() -> https
                    !http.isNullOrBlank() -> http
                    else -> ""
                }
                map[host] = value
            }
            return map
        }
    }
}

fun filterCookies(raw: Map<String, String>): Map<String, String> {
    return raw.filterValues { it.isNotBlank() }
}
