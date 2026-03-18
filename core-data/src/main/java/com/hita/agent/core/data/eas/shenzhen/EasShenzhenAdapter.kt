package com.hita.agent.core.data.eas.shenzhen

import com.hita.agent.core.data.eas.CampusEasAdapter
import com.hita.agent.core.data.eas.shenzhen.dto.TermListResponse
import com.hita.agent.core.data.eas.shenzhen.dto.WeekListResponse
import com.hita.agent.core.data.eas.shenzhen.mapper.ShenzhenEmptyRoomMapper
import com.hita.agent.core.data.eas.shenzhen.mapper.ShenzhenScoreMapper
import com.hita.agent.core.data.eas.shenzhen.mapper.ShenzhenTimetableMapper
import com.hita.agent.core.data.net.InMemoryCookieJar
import com.hita.agent.core.domain.model.CampusId
import com.hita.agent.core.domain.model.CampusSession
import com.hita.agent.core.domain.model.DataSource
import com.hita.agent.core.domain.model.EmptyRoomResult
import com.hita.agent.core.domain.model.UnifiedCourseItem
import com.hita.agent.core.domain.model.UnifiedScoreItem
import com.hita.agent.core.domain.model.UnifiedTerm
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class EasShenzhenAdapter(
    private val baseUrl: String = "https://mjw.hitsz.edu.cn/incoSpringBoot"
) : CampusEasAdapter {
    private val json = Json { ignoreUnknownKeys = true }
    private val userAgent = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/144.0 Mobile Safari/537.36 uni-app"

    override suspend fun login(username: String, password: String): CampusSession {
        val cookieJar = InMemoryCookieJar()
        val client = OkHttpClient.Builder().cookieJar(cookieJar).build()
        val loginClient = ShenzhenLoginClient(baseUrl, cookieJar, client)
        val result = loginClient.login(username, password)
        return CampusSession(
            campusId = CampusId.SHENZHEN,
            bearerToken = result.accessToken,
            cookiesByHost = result.cookiesByHost,
            createdAt = Instant.now(),
            expiresAt = null
        )
    }

    override suspend fun validateSession(session: CampusSession): Boolean {
        val response = postAuthedForm(session, "/app/commapp/queryxnxqlist")
        val body = response.body?.string().orEmpty()
        val code = json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.content?.toIntOrNull()
        return code == 200
    }

    override suspend fun fetchTerms(session: CampusSession): List<UnifiedTerm> {
        val response = postAuthedForm(session, "/app/commapp/queryxnxqlist")
        val body = response.body?.string().orEmpty()
        val payload = json.decodeFromString<TermListResponse>(body)
        return payload.content.mapNotNull { item ->
            val year = item.year ?: return@mapNotNull null
            val term = item.term ?: return@mapNotNull null
            val termId = item.yearTermCode ?: (year + term)
            UnifiedTerm(
                termId = termId,
                year = year,
                term = term,
                name = item.nameCn ?: termId,
                isCurrent = item.isCurrent == "1"
            )
        }
    }

    override suspend fun fetchTimetable(term: UnifiedTerm, session: CampusSession): List<UnifiedCourseItem> {
        val weekBody = json.encodeToString(mapOf("xn" to term.year, "xq" to term.term))
        val weekResp = postAuthedJson(session, "/app/commapp/queryzclistbyxnxq", weekBody)
        val weekPayload = json.decodeFromString<WeekListResponse>(weekResp.body?.string().orEmpty())
        val weeks = weekPayload.content.mapNotNull { it.week }.ifEmpty { listOf(1) }

        val results = mutableListOf<UnifiedCourseItem>()
        weeks.forEach { zc ->
            val body = json.encodeToString(
                mapOf("xn" to term.year, "xq" to term.term, "zc" to zc.toString(), "type" to "json")
            )
            val resp = postAuthedJson(session, "/app/Kbcx/query", body)
            val courses = ShenzhenTimetableMapper.map(resp.body?.string().orEmpty(), zc)
            results.addAll(courses)
        }
        return results
    }

    override suspend fun fetchScores(
        term: UnifiedTerm,
        session: CampusSession,
        qzqmFlag: String
    ): List<UnifiedScoreItem> {
        val body = json.encodeToString(
            mapOf("xn" to term.year, "xq" to term.term, "qzqmFlag" to qzqmFlag, "type" to "json")
        )
        val resp = postAuthedJson(session, "/app/cjgl/xscjList?_lang=zh_CN", body)
        return ShenzhenScoreMapper.mapScores(resp.body?.string().orEmpty())
    }

    override suspend fun fetchEmptyRooms(
        session: CampusSession,
        date: String,
        buildingId: String,
        period: String
    ): EmptyRoomResult {
        val form = FormBody.Builder()
            .add("nyr", date)
            .add("jxl", buildingId)
            .build()
        val resp = postAuthedForm(session, "/app/kbrcbyapp/querycdzyxx", form)
        val rooms = ShenzhenEmptyRoomMapper.filter(resp.body?.string().orEmpty(), period)
        val now = Instant.now()
        return EmptyRoomResult(
            rooms = rooms,
            buildingName = buildingId,
            cachedAt = now,
            expiresAt = now,
            stale = false,
            source = DataSource.NETWORK,
            error = null
        )
    }

    private fun postAuthedForm(session: CampusSession, path: String, body: FormBody? = null): okhttp3.Response {
        val cookieJar = InMemoryCookieJar()
        seedCookies(cookieJar, session)
        val client = OkHttpClient.Builder().cookieJar(cookieJar).build()
        val requestBody = body ?: FormBody.Builder().build()
        val request = Request.Builder()
            .url(baseUrl.toHttpUrl().newBuilder().addEncodedPathSegments(path.trimStart('/')).build())
            .post(requestBody)
            .addHeader("Authorization", "bearer ${session.bearerToken}")
            .addHeader("rolecode", "06")
            .addHeader("_lang", "cn")
            .addHeader("User-Agent", userAgent)
            .addHeader("Accept", "*/*")
            .build()
        return client.newCall(request).execute()
    }

    private fun postAuthedJson(session: CampusSession, path: String, jsonBody: String): okhttp3.Response {
        val cookieJar = InMemoryCookieJar()
        seedCookies(cookieJar, session)
        val client = OkHttpClient.Builder().cookieJar(cookieJar).build()
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(baseUrl.toHttpUrl().newBuilder().addEncodedPathSegments(path.trimStart('/')).build())
            .post(requestBody)
            .addHeader("Authorization", "bearer ${session.bearerToken}")
            .addHeader("rolecode", "06")
            .addHeader("_lang", "cn")
            .addHeader("User-Agent", userAgent)
            .addHeader("Accept", "*/*")
            .build()
        return client.newCall(request).execute()
    }

    private fun seedCookies(cookieJar: InMemoryCookieJar, session: CampusSession) {
        session.cookiesByHost.forEach { (host, header) ->
            cookieJar.seedFromHeader(host, header)
        }
    }
}
