package com.stupidtree.hitax.data.source.web.eas

import android.util.Log
import com.stupidtree.hitax.data.model.eas.CourseItem
import org.jsoup.Jsoup

object BenbuScheduleParser {
    private const val TAG = "BenbuScheduleParser"

    fun parseScheduleHtml(html: String): List<CourseItem> {
        val result = mutableListOf<CourseItem>()
        try {
            val doc = Jsoup.parse(html)
            val table = doc.select("table.addlist_01").firstOrNull() ?: return result
            val rows = table.select("tr")

            for (rowIndex in 1 until rows.size) {
                val row = rows[rowIndex]
                val cells = row.select("td,th")
                if (cells.size < 3) continue

                val periodInfo = cells.getOrNull(1)?.text()?.trim().orEmpty()

                for (cellIndex in 2 until cells.size) {
                    val cell = cells[cellIndex]
                    val cellHtml = cell.html()
                    val cellText = cell.text().replace("\u00A0", " ").trim()
                    if (cellText.isBlank() || cellText.length < 2) continue

                    val dow = cellIndex - 1
                    val courses = parseCellCourses(cellHtml, dow, rowIndex, periodInfo)
                    result.addAll(courses)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseScheduleHtml failed", e)
            throw e
        }
        return result
    }

    private fun parseCellCourses(cellHtml: String, dow: Int, periodHint: Int, periodInfo: String): List<CourseItem> {
        val courses = mutableListOf<CourseItem>()

        var cleanedHtml = cellHtml.replace(Regex("<a[^>]*>.*?</a>", RegexOption.IGNORE_CASE), "")
        cleanedHtml = cleanedHtml.replace(Regex("<(?!/?br)[^>]+>", RegexOption.IGNORE_CASE), "")

        val parts = cleanedHtml.split(Regex("""<\s*/?\s*br\s*/?\s*>""", RegexOption.IGNORE_CASE))
            .map { it.replace(Regex("<[^>]+>"), "").replace("\u00A0", " ").trim() }
            .filter { it.isNotBlank() && it != "&nbsp;" }

        if (parts.isEmpty()) return courses

        var i = 0
        while (i < parts.size) {
            val line = parts[i]

            if (Regex("""\[([^\]]+)\](?:单|双)?周(?:\((?:单|双)\))?""").containsMatchIn(line)) {
                i++
                continue
            }

            val courseName = line
            i++

            val teacherLocationLines = mutableListOf<String>()
            while (i < parts.size) {
                val nextLine = parts[i]
                if (Regex("""\[([^\]]+)\](?:单|双)?周(?:\((?:单|双)\))?""").containsMatchIn(nextLine)) {
                    teacherLocationLines.add(nextLine)
                    i++
                } else {
                    break
                }
            }

            if (teacherLocationLines.isNotEmpty()) {
                val combined = teacherLocationLines.joinToString("")
                val elements = splitTeacherLocationPairs(combined)

                for (element in elements) {
                    val course = parseCourseElement(courseName, element, dow, periodHint, periodInfo)
                    if (course != null) {
                        courses.add(course)
                    }
                }
            }
        }

        return courses
    }

    private fun splitTeacherLocationPairs(text: String): List<String> {
        val elements = mutableListOf<String>()
        val current = StringBuilder()
        var bracketDepth = 0

        for (char in text) {
            when (char) {
                '[' -> bracketDepth++
                ']' -> bracketDepth--
                ',', '，' -> {
                    if (bracketDepth == 0) {
                        if (current.isNotEmpty()) {
                            elements.add(current.toString().trim())
                            current.clear()
                        }
                        continue
                    }
                }
            }
            current.append(char)
        }

        if (current.isNotEmpty()) {
            elements.add(current.toString().trim())
        }

        return elements
    }

    private fun parseCourseElement(courseName: String, element: String, dow: Int, periodHint: Int, periodInfo: String): CourseItem? {
        val weekPattern = Regex("""([^,，\[]*)\[([^\]]+)\](?:单|双)?周(?:\((?:单|双)\))?([^,，]*)""")
        val matches = weekPattern.findAll(element).toList()

        if (matches.isEmpty()) return null

        val match = matches[0]
        val teacherPart = match.groupValues[1].trim()
        val weeksStr = match.groupValues[2]
        val locationPart = match.groupValues[3].trim()

        val weeks = parseWeeksString(weeksStr)
        val periodResolved = resolvePeriod(periodInfo, periodHint)

        return CourseItem().apply {
            code = null
            name = courseName
            this.weeks = weeks
            teacher = teacherPart.ifBlank { null }
            classroom = locationPart.ifBlank { null }
            this.dow = dow
            this.begin = periodResolved.first
            last = periodResolved.second
        }
    }

    private fun resolvePeriod(periodInfo: String, periodHint: Int): Pair<Int, Int> {
        val normalized = periodInfo
            .replace("\u00A0", "")
            .replace("第", "")
            .replace("节", "")
            .replace("，", ",")
            .trim()

        val range = Regex("""(\d+)\s*[-,]\s*(\d+)""").find(normalized)
        if (range != null) {
            val begin = range.groupValues[1].toIntOrNull()
            val end = range.groupValues[2].toIntOrNull()
            if (begin != null && end != null && end >= begin) {
                return begin to (end - begin + 1)
            }
        }

        val single = Regex("""(\d+)""").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (single != null && single > 0) {
            return single to 1
        }

        return periodHint to 2
    }

    private fun parseWeeksString(weeksStr: String): MutableList<Int> {
        val source = weeksStr.replace("\u00A0", " ").replace(" ", "")
        if (source.isBlank()) return mutableListOf()

        val oddEvenHint = when {
            source.contains("单") -> "odd"
            source.contains("双") -> "even"
            else -> null
        }

        val normalized = source
            .replace("单周", "")
            .replace("双周", "")
            .replace("单", "")
            .replace("双", "")

        val weeks = linkedSetOf<Int>()
        val segments = normalized.split(Regex("[,，]"))

        segments.forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isBlank()) return@forEach

            if (trimmed.contains("-")) {
                val range = trimmed.split("-")
                val start = range.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val end = range.getOrNull(1)?.toIntOrNull() ?: return@forEach
                if (start > end) return@forEach

                val step = if (oddEvenHint == null) 1 else 2
                var first = start
                if (oddEvenHint == "odd" && first % 2 == 0) first += 1
                if (oddEvenHint == "even" && first % 2 != 0) first += 1
                for (week in first..end step step) {
                    weeks.add(week)
                }
            } else {
                val week = trimmed.toIntOrNull() ?: return@forEach
                if (oddEvenHint == null || (oddEvenHint == "odd" && week % 2 == 1) || (oddEvenHint == "even" && week % 2 == 0)) {
                    weeks.add(week)
                }
            }
        }

        return weeks.toMutableList().sorted().toMutableList()
    }
}
