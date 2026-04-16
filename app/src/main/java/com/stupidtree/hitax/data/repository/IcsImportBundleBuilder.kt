package com.stupidtree.hitax.data.repository

import com.stupidtree.hitax.data.model.timetable.EventItem
import com.stupidtree.hitax.data.model.timetable.TermSubject
import com.stupidtree.hitax.data.model.timetable.Timetable
import com.stupidtree.hitax.utils.ColorTools
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.TimeZone

data class IcsImportBundle(
    val timetable: Timetable,
    val subjects: List<TermSubject>,
    val events: List<EventItem>
)

data class IcsImportResult(
    val timetableId: String,
    val timetableName: String,
    val importedCount: Int
)

data class ExpandedIcsEvent(
    val rawEvent: VEvent,
    val startTime: Long,
    val endTime: Long
)

object IcsImportBundleBuilder {
    private const val MAX_WEEKLY_OCCURRENCES = 256
    private const val MAX_WEEKLY_SPAN_DAYS = 370L
    private const val MINUTE_MILLIS = 60_000L
    private val PERIOD_LINE_REGEX = Regex("""^第\s*\d+(?:\s*[-~到]\s*\d+)?\s*节$""")

    fun build(
        events: List<VEvent>,
        sourceName: String?,
        now: Long = System.currentTimeMillis(),
        nextColor: () -> Int = ColorTools::randomColorMaterial
    ): IcsImportBundle {
        val validEvents = expandEvents(events).mapNotNull { expanded ->
            val courseName = resolveCourseName(expanded.rawEvent)
            ParsedIcsEvent(
                rawEvent = expanded.rawEvent,
                courseName = courseName,
                startTime = expanded.startTime,
                endTime = expanded.endTime
            )
        }.sortedWith(compareBy<ParsedIcsEvent> { it.startTime }.thenBy { it.endTime })

        require(validEvents.isNotEmpty()) { "empty events" }

        val timetable = Timetable().apply {
            name = resolveTimetableName(sourceName, now)
            startTime = Timestamp(resolveWeekStart(validEvents.first().startTime))
            endTime = Timestamp(validEvents.maxOf { it.endTime })
        }

        val subjectsByName = LinkedHashMap<String, TermSubject>()
        val importedEvents = mutableListOf<EventItem>()
        for (event in validEvents) {
            val subject = subjectsByName.getOrPut(event.courseName) {
                TermSubject().apply {
                    name = event.courseName
                    timetableId = timetable.id
                    color = nextColor()
                }
            }
            val item = IcsImportEventMapper.map(
                event = event.rawEvent,
                timetableId = timetable.id,
                subjectId = subject.id,
                type = EventItem.TYPE.CLASS,
                startTimeOverride = event.startTime,
                endTimeOverride = event.endTime
            ) ?: continue
            importedEvents.add(item)
        }

        return IcsImportBundle(
            timetable = timetable,
            subjects = subjectsByName.values.toList(),
            events = importedEvents
        )
    }

    fun expandEvents(events: List<VEvent>): List<ExpandedIcsEvent> {
        val expanded = mutableListOf<ExpandedIcsEvent>()
        events.forEach { expanded.addAll(expandSingleEvent(it)) }
        return expanded.sortedWith(compareBy<ExpandedIcsEvent> { it.startTime }.thenBy { it.endTime })
    }

    private fun expandSingleEvent(event: VEvent): List<ExpandedIcsEvent> {
        val start = event.startDate?.date?.time ?: return emptyList()
        val end = event.endDate?.date?.time ?: return emptyList()
        if (end <= start) return emptyList()

        val duration = end - start
        val starts = LinkedHashSet<Long>()
        starts.add(start)

        val rrules = extractPropertyValues(event, "RRULE")
        for (rrule in rrules) {
            starts.addAll(expandStartsByRule(start, rrule))
        }

        val excluded = extractPropertyDateTimes(event, "EXDATE")
            .map(::normalizeToMinute)
            .toSet()
        if (excluded.isNotEmpty()) {
            starts.removeAll { normalizeToMinute(it) in excluded }
        }

        val extras = extractPropertyDateTimes(event, "RDATE")
        if (extras.isNotEmpty()) {
            starts.addAll(extras)
        }

        return starts
            .toList()
            .distinctBy(::normalizeToMinute)
            .sorted()
            .map { ExpandedIcsEvent(event, it, it + duration) }
    }

    private fun expandStartsByRule(start: Long, rrule: String): List<Long> {
        val ruleMap = parseRRule(rrule)
        if (ruleMap.isEmpty()) return emptyList()
        if (ruleMap["FREQ"] != "WEEKLY") return emptyList()

        val interval = parsePositiveInt(ruleMap["INTERVAL"]) ?: 1
        val count = parsePositiveInt(ruleMap["COUNT"])
        val until = parseUntil(ruleMap["UNTIL"])
        val maxOccurrences = minOf(count ?: MAX_WEEKLY_OCCURRENCES, MAX_WEEKLY_OCCURRENCES)
        if (maxOccurrences <= 1) return emptyList()

        val byDays = parseByDay(ruleMap["BYDAY"])
        return if (byDays.isEmpty()) {
            expandWeeklySimple(start, interval, maxOccurrences, until)
        } else {
            expandWeeklyWithByDay(start, interval, maxOccurrences, until, byDays)
        }
    }

    private fun expandWeeklySimple(
        start: Long,
        interval: Int,
        maxOccurrences: Int,
        until: Long?
    ): List<Long> {
        val result = mutableListOf<Long>()
        val horizon = start + MAX_WEEKLY_SPAN_DAYS * 24L * 60L * 60L * 1000L
        val calendar = Calendar.getInstance().apply { timeInMillis = start }

        while (result.size < maxOccurrences - 1) {
            calendar.add(Calendar.WEEK_OF_YEAR, interval)
            val nextStart = calendar.timeInMillis
            if (until != null && nextStart > until) break
            if (nextStart > horizon) break
            result.add(nextStart)
        }

        return result
    }

    private fun expandWeeklyWithByDay(
        start: Long,
        interval: Int,
        maxOccurrences: Int,
        until: Long?,
        byDays: List<Int>
    ): List<Long> {
        val result = mutableListOf<Long>()
        val horizon = start + MAX_WEEKLY_SPAN_DAYS * 24L * 60L * 60L * 1000L
        val startCalendar = Calendar.getInstance().apply { timeInMillis = start }
        val hour = startCalendar.get(Calendar.HOUR_OF_DAY)
        val minute = startCalendar.get(Calendar.MINUTE)
        val second = startCalendar.get(Calendar.SECOND)
        val millis = startCalendar.get(Calendar.MILLISECOND)

        val weekStart = Calendar.getInstance().apply {
            timeInMillis = start
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var cycle = 0
        while (result.size < maxOccurrences - 1) {
            val weekBase = weekStart.clone() as Calendar
            weekBase.add(Calendar.WEEK_OF_YEAR, cycle * interval)

            for (weekday in byDays) {
                val candidate = weekBase.clone() as Calendar
                candidate.set(Calendar.DAY_OF_WEEK, weekday)
                candidate.set(Calendar.HOUR_OF_DAY, hour)
                candidate.set(Calendar.MINUTE, minute)
                candidate.set(Calendar.SECOND, second)
                candidate.set(Calendar.MILLISECOND, millis)
                val nextStart = candidate.timeInMillis
                if (nextStart <= start) continue
                if (until != null && nextStart > until) continue
                if (nextStart > horizon) continue
                result.add(nextStart)
                if (result.size >= maxOccurrences - 1) break
            }

            if (result.isNotEmpty() && result.last() > horizon) break
            cycle++
            if (cycle > MAX_WEEKLY_OCCURRENCES) break
        }

        return result
    }

    private fun parseByDay(raw: String?): List<Int> {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return emptyList()
        val mapped = value.split(',').mapNotNull { token ->
            val day = token.trim().uppercase(Locale.US).takeLast(2)
            when (day) {
                "MO" -> Calendar.MONDAY
                "TU" -> Calendar.TUESDAY
                "WE" -> Calendar.WEDNESDAY
                "TH" -> Calendar.THURSDAY
                "FR" -> Calendar.FRIDAY
                "SA" -> Calendar.SATURDAY
                "SU" -> Calendar.SUNDAY
                else -> null
            }
        }
        return mapped.distinct()
    }

    private fun parseRRule(rrule: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        rrule.split(';').forEach { token ->
            val pair = token.split('=', limit = 2)
            if (pair.size != 2) return@forEach
            val key = pair[0].trim().uppercase(Locale.US)
            val value = pair[1].trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }

    private fun parsePositiveInt(raw: String?): Int? {
        val value = raw?.trim()?.toIntOrNull() ?: return null
        return if (value > 0) value else null
    }

    private fun parseUntil(raw: String?): Long? {
        return parseDateTime(raw, endOfDayWhenDateOnly = true)
    }

    private fun parseDateTime(raw: String?, endOfDayWhenDateOnly: Boolean = false): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null

        val formats = listOf(
            Triple("yyyyMMdd'T'HHmmss'Z'", TimeZone.getTimeZone("UTC"), false),
            Triple("yyyyMMdd'T'HHmm'Z'", TimeZone.getTimeZone("UTC"), false),
            Triple("yyyyMMdd'T'HHmmssX", null, false),
            Triple("yyyyMMdd'T'HHmmX", null, false),
            Triple("yyyyMMdd'T'HHmmssZ", null, false),
            Triple("yyyyMMdd'T'HHmmZ", null, false),
            Triple("yyyyMMdd'T'HHmmss", null, false),
            Triple("yyyyMMdd'T'HHmm", null, false),
            Triple("yyyyMMdd", null, true)
        )

        for ((pattern, tz, isDateOnly) in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = false
                if (tz != null) sdf.timeZone = tz
                val date = sdf.parse(value) ?: continue
                if (isDateOnly && endOfDayWhenDateOnly) {
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    return calendar.timeInMillis
                }
                return date.time
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun extractPropertyValues(event: VEvent, propertyName: String): List<String> {
        val properties = event.properties ?: return emptyList()
        val result = mutableListOf<String>()
        for (entry in properties) {
            val property = entry as? Property ?: continue
            if (!property.name.equals(propertyName, ignoreCase = true)) continue
            val value = property.value?.trim().orEmpty()
            if (value.isNotEmpty()) {
                result.add(value)
            }
        }
        return result
    }

    private fun extractPropertyDateTimes(event: VEvent, propertyName: String): List<Long> {
        val values = extractPropertyValues(event, propertyName)
        if (values.isEmpty()) return emptyList()

        val result = mutableListOf<Long>()
        for (raw in values) {
            val tokens = raw.split(',')
            for (token in tokens) {
                val value = token.trim().substringBefore('/').trim()
                if (value.isBlank()) continue
                parseDateTime(value)?.let { result.add(it) }
            }
        }
        return result
    }

    private fun normalizeToMinute(value: Long): Long {
        return value / MINUTE_MILLIS * MINUTE_MILLIS
    }

    private fun resolveCourseName(event: VEvent): String {
        val summary = event.summary?.value?.trim().orEmpty()
        if (summary.isNotEmpty()) return summary
        val fromDescription = event.description?.value
            ?.replace("\\n", "\n")
            ?.split("\n")
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() && !PERIOD_LINE_REGEX.matches(it) }
            .orEmpty()
        if (fromDescription.isNotBlank()) return fromDescription
        return event.uid?.value?.trim().orEmpty().ifBlank { "未知课程" }
    }

    private fun resolveTimetableName(sourceName: String?, now: Long): String {
        val trimmed = sourceName?.trim().orEmpty()
        val withoutExtension = trimmed.replace(Regex("\\.ics$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (withoutExtension.isNotEmpty()) {
            return withoutExtension
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        return "ICS导入 ${formatter.format(now)}"
    }

    private fun resolveWeekStart(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private data class ParsedIcsEvent(
        val rawEvent: VEvent,
        val courseName: String,
        val startTime: Long,
        val endTime: Long
    )
}
