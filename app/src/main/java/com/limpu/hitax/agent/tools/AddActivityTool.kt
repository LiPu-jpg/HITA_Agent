package com.limpu.hitax.agent.tools

import android.app.Application
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentTraceEvent
import com.limpu.hitax.agent.timetable.ArrangementInput
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import com.limpu.hitax.utils.LogUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddActivityTool : ReActTool {
    override fun execute(input: ReActToolInput): String? {
        val activity = parseAddActivityInput(input.actionInput, input.userMessage)
        var name = activity.name
        var fromMs = activity.fromMs
        var toMs = activity.toMs
        var place = activity.place

        if (name == null || fromMs == null || toMs == null) {
            LogUtils.d("[DEBUG] LLM timestamps invalid, attempting to parse from user message")
            val parsedFromUser = parseTimeFromUserMessage(input.userMessage)
            if (parsedFromUser != null) {
                name = name ?: parsedFromUser.name
                fromMs = fromMs ?: parsedFromUser.fromMs
                toMs = toMs ?: parsedFromUser.toMs
                place = place.ifBlank { parsedFromUser.place }
                LogUtils.d("[DEBUG] Parsed from user message: name=$name fromMs=$fromMs toMs=$toMs place=$place")
            }
        }

        val now = System.currentTimeMillis()
        if (fromMs != null && toMs != null) {
            val oneYear = 365L * 24 * 60 * 60 * 1000
            if (fromMs < now - oneYear || fromMs > now + oneYear ||
                toMs < now - oneYear || toMs > now + oneYear) {
                LogUtils.w("[DEBUG] Timestamps out of reasonable range, rejecting: fromMs=$fromMs toMs=$toMs")
                return "时间戳不合理，请重新描述时间（如'明天下午3点'）"
            }
        }

        if (name != null && fromMs != null && toMs != null) {
            val timetableInput = TimetableAgentInput(
                application = input.application,
                action = TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT,
                timetableId = input.timetableId,
                arrangement = ArrangementInput(name = name, fromMs = fromMs, toMs = toMs, place = place),
            )
            return ToolHelper.runTimetableToolSync(timetableInput, input.agentProvider, input.onTrace)
        }
        return "未找到活动信息"
    }

    private data class ParsedActivity(val name: String?, val fromMs: Long?, val toMs: Long?, val place: String)

    private fun parseAddActivityInput(actionInput: String, userMessage: String): ParsedActivity {
        LogUtils.d("[DEBUG] parseAddActivityInput actionInput=$actionInput")
        LogUtils.d("[DEBUG] actionInput bytes=${actionInput.toByteArray().joinToString(",")}")

        val cleanedInput = actionInput
            .replace(Regex("""```(?:json)?\s*"""), "")
            .replace("```", "")
            .replace("`", "")
            .trim()
        LogUtils.d("[DEBUG] cleanedInput=$cleanedInput")

        try {
            val json = org.json.JSONObject(cleanedInput)
            val jsonName = json.optString("name", "").takeIf { it.isNotBlank() }
            val jsonPlace = json.optString("place", "")

            val dayOffset = json.optInt("day_offset", -999)
            val startTime = json.optString("start_time", "")
            val endTime = json.optString("end_time", "")

            if (jsonName != null && dayOffset != -999 && startTime.isNotBlank() && endTime.isNotBlank()) {
                val fromMs = parseRelativeTime(dayOffset, startTime)
                val toMs = parseRelativeTime(dayOffset, endTime)
                if (fromMs != null && toMs != null) {
                    LogUtils.d("[DEBUG] Parsed relative time: name=$jsonName fromMs=$fromMs toMs=$toMs")
                    return ParsedActivity(jsonName, fromMs, toMs, jsonPlace)
                }
            }

            val fromVal = json.opt("from")
            val toVal = json.opt("to")
            var jsonFrom: Long? = null
            var jsonTo: Long? = null

            when (fromVal) {
                is Number -> jsonFrom = fromVal.toLong()
                is String -> {
                    val ts = parseTimestampOrIso(fromVal as String)
                    if (ts != null) jsonFrom = ts
                }
            }

            when (toVal) {
                is Number -> jsonTo = toVal.toLong()
                is String -> {
                    val ts = parseTimestampOrIso(toVal as String)
                    if (ts != null) jsonTo = ts
                }
            }

            LogUtils.d("[DEBUG] JSON parsed: name=$jsonName from=$jsonFrom to=$jsonTo place=$jsonPlace")

            if (jsonName != null && jsonFrom != null && jsonTo != null) {
                LogUtils.d("[DEBUG] Parsed from JSON: name=$jsonName fromMs=$jsonFrom toMs=$jsonTo")
                return ParsedActivity(jsonName, jsonFrom, jsonTo, jsonPlace)
            }
        } catch (e: Exception) {
            LogUtils.d("[DEBUG] JSON parse failed: ${e.message}")
        }

        LogUtils.d("[DEBUG] Failed to parse add_activity input from JSON, trying user message fallback")
        val fallback = parseTimeFromUserMessage(userMessage)
        if (fallback != null && fallback.name != null) {
            return fallback
        }

        return ParsedActivity(null, null, null, "")
    }

    private fun parseTimestampOrIso(raw: String): Long? {
        val trimmed = raw.trim().removeSurrounding("\"")
        trimmed.toLongOrNull()?.let { return it }
        val isoFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.CHINA),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA),
        )
        for (fmt in isoFormats) {
            try {
                return fmt.parse(trimmed)?.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseRelativeTime(dayOffset: Int, timeStr: String): Long? {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)

        val parts = timeStr.split(":")
        if (parts.size != 2) return null

        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null

        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val result = cal.timeInMillis
        LogUtils.d("[DEBUG] parseRelativeTime: dayOffset=$dayOffset time=$timeStr -> $result (${java.util.Date(result)})")
        return result
    }

    private fun parseTimeFromUserMessage(userMessage: String): ParsedActivity? {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1
        val currentDay = now.get(Calendar.DAY_OF_MONTH)

        val nameMatch = Regex("""["']([^"']+)["']""").find(userMessage)
            ?: Regex("""添加.*?["']([^"']+)["']""").find(userMessage)
            ?: Regex("""名为["']([^"']+)["']""").find(userMessage)
        val name = nameMatch?.groupValues?.get(1)

        val placeMatch = Regex("""地点[是为]?\s*["']?([^"'\s]+)["']?""").find(userMessage)
            ?: Regex("""@\s*([A-Z0-9]+)""").find(userMessage)
            ?: Regex("""在\s*([A-Z0-9]+)""").find(userMessage)
        val place = placeMatch?.groupValues?.get(1) ?: ""

        var targetYear = currentYear
        var targetMonth = currentMonth
        var targetDay = currentDay

        when {
            userMessage.contains("明天") || userMessage.contains("明日") -> {
                val t = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                targetYear = t.get(Calendar.YEAR)
                targetMonth = t.get(Calendar.MONTH) + 1
                targetDay = t.get(Calendar.DAY_OF_MONTH)
            }
            userMessage.contains("后天") -> {
                val t = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }
                targetYear = t.get(Calendar.YEAR)
                targetMonth = t.get(Calendar.MONTH) + 1
                targetDay = t.get(Calendar.DAY_OF_MONTH)
            }
            userMessage.contains("大后天") -> {
                val t = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }
                targetYear = t.get(Calendar.YEAR)
                targetMonth = t.get(Calendar.MONTH) + 1
                targetDay = t.get(Calendar.DAY_OF_MONTH)
            }
            userMessage.contains("今天") || userMessage.contains("今日") -> {
            }
            Regex("""(\d{1,2})月(\d{1,2})[日号]""").find(userMessage) != null -> {
                val m = Regex("""(\d{1,2})月(\d{1,2})[日号]""").find(userMessage)!!
                targetMonth = m.groupValues[1].toInt()
                targetDay = m.groupValues[2].toInt()
            }
            else -> {
                val dayLabelRegex = Regex("""(明天|后天|大后天|下周[一二三四五六日天])/?(\\d{1,2}):(\\d{2})(?:[-~－—至到](\\d{1,2}):(\\d{2}))?""")
                val match = dayLabelRegex.find(userMessage)
                if (match != null) {
                    val dayLabel = match.groupValues[1]
                    val startHour = match.groupValues[2].toInt()
                    val startMinute = match.groupValues[3].toInt()
                    val endHour = match.groupValues[4].toIntOrNull()
                    val endMinute = match.groupValues[5].toIntOrNull()

                    val dayOffsetMap = mapOf(
                        "明天" to 1, "后天" to 2, "大后天" to 3,
                        "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4, "周五" to 5, "周六" to 6, "周日" to 7, "周天" to 7
                    )

                    val cal = Calendar.getInstance()
                    val todayDow = cal.get(Calendar.DAY_OF_WEEK)
                    var diff = dayOffsetMap[dayLabel] ?: 0

                    if (dayLabel.startsWith("下周")) {
                        diff += 7
                    }

                    if (diff == 0) {
                        diff = dowToDiff(dayLabel, todayDow)
                    }

                    cal.add(Calendar.DAY_OF_YEAR, diff)
                    targetYear = cal.get(Calendar.YEAR)
                    targetMonth = cal.get(Calendar.MONTH) + 1
                    targetDay = cal.get(Calendar.DAY_OF_MONTH)

                    val hour = startHour
                    val minute = startMinute
                    val endH = endHour ?: hour + 1
                    val endM = endMinute ?: minute

                    val fromCal = Calendar.getInstance().apply {
                        set(targetYear, targetMonth - 1, targetDay, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val toCal = Calendar.getInstance().apply {
                        set(targetYear, targetMonth - 1, targetDay, endH, endM, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    return ParsedActivity(name, fromCal.timeInMillis, toCal.timeInMillis, place)
                }
            }
        }

        var hour = 0
        var minute = 0
        var endHour = 0
        var endMinute = 0

        val timeRangeMatch = Regex("""(上午|下午|晚上)?\s*(\d{1,2})[:\u70b9](\d{1,2})?\s*[~-]\s*(上午|下午|晚上)?\s*(\d{1,2})[:\u70b9](\d{1,2})?""").find(userMessage)
        if (timeRangeMatch != null) {
            val startPeriod = timeRangeMatch.groupValues[1]
            val startHour = timeRangeMatch.groupValues[2].toInt()
            val startMinute = timeRangeMatch.groupValues[3].toIntOrNull() ?: 0
            val endPeriod = timeRangeMatch.groupValues[4]
            val endHourVal = timeRangeMatch.groupValues[5].toInt()
            val endMinuteVal = timeRangeMatch.groupValues[6].toIntOrNull() ?: 0

            hour = when {
                startPeriod.contains("下午") && startHour < 12 -> startHour + 12
                startPeriod.contains("晚上") && startHour < 12 -> startHour + 12
                else -> startHour
            }
            minute = startMinute

            endHour = when {
                endPeriod.contains("下午") && endHourVal < 12 -> endHourVal + 12
                endPeriod.contains("晚上") && endHourVal < 12 -> endHourVal + 12
                else -> endHourVal
            }
            endMinute = endMinuteVal
        } else {
            val singleTimeMatch = Regex("""(上午|下午|晚上)?\s*(\d{1,2})[:\u70b9](\d{1,2})?""").find(userMessage)
            if (singleTimeMatch != null) {
                val period = singleTimeMatch.groupValues[1]
                val h = singleTimeMatch.groupValues[2].toInt()
                val m = singleTimeMatch.groupValues[3].toIntOrNull() ?: 0

                hour = when {
                    period.contains("下午") && h < 12 -> h + 12
                    period.contains("晚上") && h < 12 -> h + 12
                    else -> h
                }
                minute = m
                endHour = hour + 1
                endMinute = minute
            }
        }

        if (hour == 0 && minute == 0) return null

        val fromCal = Calendar.getInstance().apply {
            set(targetYear, targetMonth - 1, targetDay, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val toCal = Calendar.getInstance().apply {
            set(targetYear, targetMonth - 1, targetDay, endHour, endMinute, 0)
            set(Calendar.MILLISECOND, 0)
        }

        LogUtils.d("[DEBUG] parseTimeFromUserMessage: name=$name place=$place date=$targetYear-$targetMonth-$targetDay from=$hour:$minute to=$endHour:$endMinute fromMs=${fromCal.timeInMillis} toMs=${toCal.timeInMillis}")

        return ParsedActivity(name, fromCal.timeInMillis, toCal.timeInMillis, place)
    }

    private fun dowToDiff(dayLabel: String, todayDow: Int): Int {
        val dowMap = mapOf(
            "周一" to Calendar.MONDAY, "星期二" to Calendar.TUESDAY,
            "周三" to Calendar.WEDNESDAY, "星期三" to Calendar.WEDNESDAY,
            "周四" to Calendar.THURSDAY, "星期四" to Calendar.THURSDAY,
            "周五" to Calendar.FRIDAY, "星期五" to Calendar.FRIDAY,
            "周六" to Calendar.SATURDAY, "星期六" to Calendar.SATURDAY,
            "周日" to Calendar.SUNDAY, "星期日" to Calendar.SUNDAY, "周天" to Calendar.SUNDAY,
        )
        val dow = dowMap[dayLabel] ?: return 0
        var diff = dow - todayDow
        if (diff <= 0) diff += 7
        return diff
    }
}