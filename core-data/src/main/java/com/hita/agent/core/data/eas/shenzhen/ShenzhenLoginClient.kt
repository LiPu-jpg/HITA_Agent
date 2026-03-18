package com.hita.agent.core.data.eas.shenzhen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import com.hita.agent.core.data.net.InMemoryCookieJar

class ShenzhenLoginClient(
    baseUrl: String,
    private val cookieJar: InMemoryCookieJar = InMemoryCookieJar(),
    private val client: OkHttpClient = OkHttpClient.Builder().cookieJar(cookieJar).build()
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }

    private val authBasic = "Basic aW5jb246MTIzNDU="
    private val userAgent = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/144.0 Mobile Safari/537.36 uni-app"

    data class LoginResult(
        val accessToken: String,
        val refreshToken: String?,
        val cookiesByHost: Map<String, String>
    )

    suspend fun login(username: String, password: String): LoginResult {
        postForm("/component/queryApplicationSetting/rsa", rolecode = "01")
        postForm("/c_raskey", rolecode = "06")
        val body = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()
        val response = postForm("/authentication/ldap", rolecode = "06", body = body)
        val payload = response.body?.string().orEmpty()
        val obj = json.parseToJsonElement(payload).jsonObject
        val access = obj["access_token"]?.jsonPrimitive?.content.orEmpty()
        val refresh = obj["refresh_token"]?.jsonPrimitive?.content
        val cookies = cookieJar.cookieHeader(base.host)?.let { mapOf(base.host to it) }.orEmpty()
        return LoginResult(accessToken = access, refreshToken = refresh, cookiesByHost = cookies)
    }

    private fun postForm(path: String, rolecode: String, body: FormBody? = null): okhttp3.Response {
        val requestBody = body ?: FormBody.Builder().build()
        val request = Request.Builder()
            .url(base.newBuilder().addEncodedPathSegments(path.trimStart('/')).build())
            .post(requestBody)
            .addHeader("Authorization", authBasic)
            .addHeader("rolecode", rolecode)
            .addHeader("_lang", "cn")
            .addHeader("User-Agent", userAgent)
            .addHeader("Accept", "*/*")
            .build()
        return client.newCall(request).execute()
    }
}
