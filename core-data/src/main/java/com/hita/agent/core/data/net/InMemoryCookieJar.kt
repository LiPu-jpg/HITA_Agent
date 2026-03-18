package com.hita.agent.core.data.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class InMemoryCookieJar : CookieJar {
    private val store: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val list = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { cookie ->
            list.removeAll { it.name == cookie.name }
            list.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return store[url.host].orEmpty()
    }

    fun cookieHeader(host: String): String? {
        val cookies = store[host].orEmpty()
        if (cookies.isEmpty()) return null
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun seedFromHeader(host: String, cookieHeader: String) {
        val parts = cookieHeader.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
        if (parts.isEmpty()) return
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .build()
        val list = store.getOrPut(host) { mutableListOf() }
        parts.forEach { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@forEach
            val name = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            val cookie = Cookie.Builder()
                .name(name)
                .value(value)
                .domain(host)
                .path("/")
                .build()
            list.removeAll { it.name == name }
            list.add(cookie)
        }
        saveFromResponse(url, list)
    }
}
