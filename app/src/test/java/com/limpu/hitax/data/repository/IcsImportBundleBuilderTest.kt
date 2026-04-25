package com.limpu.hitax.data.repository

import com.limpu.hitax.data.model.timetable.EventItem
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class IcsImportBundleBuilderTest {
    @Test
    fun build_createsStandaloneTimetableAndGroupsEventsIntoColoredSubjects() {
        val colors = ArrayDeque(listOf(1001, 1002))
        val bundle = IcsImportBundleBuilder.build(
            events = listOf(
                createEvent("高等数学", "20260407T100000", "20260407T120000", "A101 张老师"),
                createEvent("高等数学", "20260409T100000", "20260409T120000", "A101 张老师"),
                createEvent("大学英语", "20260408T130000", "20260408T143000", "B202 李老师")
            ),
            sourceName = "2026春季课程.ics",
            now = 1_744_000_000_000L,
            nextColor = { colors.removeFirst() }
        )

        assertEquals("2026春季课程", bundle.timetable.name)
        assertEquals(startOfWeek("20260407T100000"), bundle.timetable.startTime.time)
        assertEquals(DateTime("20260409T120000").time, bundle.timetable.endTime.time)
        assertEquals(2, bundle.subjects.size)
        assertEquals(3, bundle.events.size)

        val subjectsByName = bundle.subjects.associateBy { it.name }
        val math = subjectsByName["高等数学"]
        val english = subjectsByName["大学英语"]
        assertNotNull(math)
        assertNotNull(english)
        math ?: return
        english ?: return

        assertEquals(bundle.timetable.id, math.timetableId)
        assertEquals(bundle.timetable.id, english.timetableId)
        assertEquals(1001, math.color)
        assertEquals(1002, english.color)
        assertNotEquals(math.id, english.id)

        val mathEvents = bundle.events.filter { it.name == "高等数学" }
        val englishEvent = bundle.events.single { it.name == "大学英语" }

        assertEquals(2, mathEvents.size)
        assertTrue(mathEvents.all { it.subjectId == math.id })
        assertEquals(english.id, englishEvent.subjectId)
        assertTrue(bundle.events.all { it.timetableId == bundle.timetable.id })
        assertTrue(bundle.events.all { it.type == EventItem.TYPE.CLASS })
        assertTrue(bundle.events.all { it.fromNumber == 0 })
        assertTrue(bundle.events.all { it.lastNumber == 0 })
        assertFalse(bundle.events.any { it.subjectId.isBlank() })
    }

    @Test
    fun build_fallsBackToImportNameWhenSourceNameMissing() {
        val bundle = IcsImportBundleBuilder.build(
            events = listOf(
                createEvent("高等数学", "20260407T100000", "20260407T120000", "A101 张老师")
            ),
            sourceName = "   ",
            now = 1_744_444_400_000L,
            nextColor = { 2026 }
        )

        assertTrue(bundle.timetable.name?.startsWith("ICS导入") == true)
        assertEquals(1, bundle.subjects.size)
        assertEquals(1, bundle.events.size)
        assertEquals(2026, bundle.subjects.single().color)
    }

    private fun createEvent(
        summary: String,
        start: String,
        end: String,
        location: String
    ): VEvent {
        val event = VEvent(DateTime(start), DateTime(end), summary)
        event.properties.add(Location(location))
        return event
    }

    private fun startOfWeek(start: String): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = DateTime(start).time
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
