package com.limpu.hitax.agent.tools

import android.app.Application
import com.limpu.hitax.BuildConfig
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentTraceEvent
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import com.limpu.hitax.utils.AppConstants
import com.limpu.hitax.utils.LogUtils
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Shared infrastructure for ReAct tool implementations. */
internal object ToolHelper {

    private val prServerBaseUrl = BuildConfig.HOA_BASE_URL.removeSuffix("/")
    private val prServerApiKey = BuildConfig.HOA_API_KEY

    fun prServerRequest(url: String): Connection {
        val req = Jsoup.connect(url)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .timeout(AppConstants.Network.READ_TIMEOUT.toInt())
            .header("Accept", "application/json")
            .header("User-Agent", "HITA_Agent/${BuildConfig.VERSION_NAME}")
        if (prServerApiKey.isNotBlank()) req.header("X-Api-Key", prServerApiKey)
        return req
    }

    fun parseDayOffset(text: String): Int {
        val cal = Calendar.getInstance()
        val todayDow = cal.get(Calendar.DAY_OF_WEEK)
        val dowMap = mapOf(
            "周一" to Calendar.MONDAY, "星期一" to Calendar.MONDAY,
            "周二" to Calendar.TUESDAY, "星期二" to Calendar.TUESDAY,
            "周三" to Calendar.WEDNESDAY, "星期三" to Calendar.WEDNESDAY,
            "周四" to Calendar.THURSDAY, "星期四" to Calendar.THURSDAY,
            "周五" to Calendar.FRIDAY, "星期五" to Calendar.FRIDAY,
            "周六" to Calendar.SATURDAY, "星期六" to Calendar.SATURDAY,
            "周日" to Calendar.SUNDAY, "星期日" to Calendar.SUNDAY, "周天" to Calendar.SUNDAY,
        )
        if (text.contains("今天")) return 0
        if (text.contains("明天")) return 1
        if (text.contains("大后天")) return 3
        if (text.contains("后天")) return 2
        for ((label, dow) in dowMap) {
            if (text.contains(label)) {
                var diff = dow - todayDow
                if (diff <= 0) diff += 7
                if (text.contains("下个") || text.contains("下周")) diff += 7
                return diff
            }
        }
        val dateRegex = Regex("(\\d{1,2})月(\\d{1,2})")
        val match = dateRegex.find(text) ?: return 0
        val month = match.groupValues[1].toIntOrNull() ?: return 0
        val day = match.groupValues[2].toIntOrNull() ?: return 0
        val target = Calendar.getInstance().apply { set(Calendar.MONTH, month - 1); set(Calendar.DAY_OF_MONTH, day) }
        val diffMs = target.timeInMillis - cal.timeInMillis
        val diffDays = (diffMs / (24 * 3600 * 1000)).toInt()
        return if (diffDays in 0..365) diffDays else 0
    }

    fun runTimetableToolSync(
        input: TimetableAgentInput,
        agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
        onTrace: (AgentTraceEvent) -> Unit,
    ): String {
        val session = agentProvider.createSession()
        val latch = CountDownLatch(1)
        var observation = ""
        session.run(input, onTrace) { result ->
            observation = when {
                result.ok && result.data != null -> {
                    val output = result.data
                    when (output.action) {
                        TimetableAgentInput.Action.GET_LOCAL_TIMETABLE -> {
                            if (output.events.isEmpty()) ""
                            else {
                                val sdf = SimpleDateFormat("HH:mm", Locale.CHINA)
                                buildString {
                                    output.events.take(15).forEach { ev ->
                                        val from = sdf.format(Date(ev.fromMs))
                                        val to = sdf.format(Date(ev.toMs))
                                        append("- ${ev.name}  $from~$to")
                                        if (ev.place.isNotBlank()) append("  @ ${ev.place}")
                                        append("\n")
                                    }
                                }
                            }
                        }
                        TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT -> {
                            if (output.addedEventIds.isNotEmpty()) "已成功添加 ${output.addedEventIds.size} 个活动。"
                            else "活动添加成功。"
                        }
                    }
                }
                else -> "工具执行失败: ${result.error ?: "未知错误"}"
            }
            latch.countDown()
        }
        if (!latch.await(30, TimeUnit.SECONDS)) {
            LogUtils.e("工具执行超时")
            return "工具执行超时"
        }
        return observation.ifBlank { "" }
    }
}
