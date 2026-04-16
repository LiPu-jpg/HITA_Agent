package com.stupidtree.hitax.data.repository

import android.app.Application
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.R
import com.stupidtree.hitax.data.AppDatabase
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.data.model.timetable.EventItem
import com.stupidtree.hitax.data.model.timetable.TimePeriodInDay
import com.stupidtree.hitax.data.model.timetable.Timetable
import com.stupidtree.hitax.ui.main.timetable.TimetableFragment.Companion.WEEK_MILLS
import com.stupidtree.hitax.utils.TimeTools
import com.stupidtree.sync.StupidSync
import com.stupidtree.sync.data.model.History
import java.lang.NumberFormatException
import java.util.*
import java.util.concurrent.Executors

import net.fortuna.ical4j.model.component.VAlarm

import net.fortuna.ical4j.model.component.VEvent

import net.fortuna.ical4j.data.CalendarOutputter

import net.fortuna.ical4j.model.Dur

import net.fortuna.ical4j.util.UidGenerator

import net.fortuna.ical4j.model.DateTime

import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.data.CalendarBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream


class TimetableRepository(val application: Application) {
    private val historyTag = "timetable"
    private val manualEventFallbackColor by lazy {
        ContextCompat.getColor(application, R.color.subject8)
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val eventItemDao = AppDatabase.getDatabase(application).eventItemDao()
    private val timetableDao = AppDatabase.getDatabase(application).timetableDao()
    private val subjectDao = AppDatabase.getDatabase(application).subjectDao()

    /**
     * 获取[from,to)内的事件
     */
    fun getEventsDuring(from: Long, to: Long): LiveData<List<EventItem>> {
        return eventItemDao.getEventsDuring(from, to)
    }

    /**
     * 获取[from,...)内的至多limit个事件
     */
    fun getEventsAfter(from: Long,limit:Int): LiveData<List<EventItem>> {
        return eventItemDao.getEventsAfter(from,limit)
    }


    /**
     * 获取今日事件
     */
    @WorkerThread
    fun getTodayEventsSync(): List<EventItem> {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY,0)
        now.set(Calendar.MINUTE,0)
        val from = now.timeInMillis
        now.add(Calendar.DATE,1)
        val to = now.timeInMillis
        return eventItemDao.getEventsDuringSync(from, to)
            .sortedBy { it.from.time }
    }
    /**
     * 获取[from,to)内的事件，包含颜色
     */
    fun getEventsDuringWithColor(from: Long, to: Long): LiveData<List<EventItem>> {
        val res = MediatorLiveData<List<EventItem>>()
        res.addSource(eventItemDao.getEventsDuring(from, to)) { events ->
            val subjects = mutableSetOf<String>()
            for (e in events) {
                if (e.subjectId.isNotBlank()) {
                    subjects.add(e.subjectId)
                }
            }
            res.addSource(subjectDao.getSubjectColorsWithId(subjects)) { colors ->
                val map = mutableMapOf<String, Int>()
                for (color in colors) {
                    map[color.id] = color.color
                }
                for (e in events) {
                    map[e.subjectId]?.let {
                        e.color = it
                    } ?: run {
                        if (e.subjectId.isBlank()) {
                            e.color = manualEventFallbackColor
                        }
                    }
                }
                res.value = events
            }
        }
        return res
    }

    fun getClassesOfSubject(subjectId: String): LiveData<List<EventItem>> {
        return eventItemDao.getClassesOfSubject(subjectId)
    }

    /**
     * 获取所有课表
     */
    fun getTimetables(): LiveData<List<Timetable>> {
        return timetableDao.getTimetables()
    }

    fun getTimetablesById(id: String): LiveData<Timetable> {
        return timetableDao.getTimetableById(id)
    }

    @WorkerThread
    fun getRecentTimetableSync(): Timetable? {
        return timetableDao.getTimetableClosestToTimestampSync(System.currentTimeMillis())
    }

    @WorkerThread
    fun getTimetableByIdSync(id: String): Timetable? {
        return timetableDao.getTimetableByIdSync(id)
    }

    @WorkerThread
    fun getEventsOfTimetableSync(
        timetableId: String,
        fromMs: Long? = null,
        toMs: Long? = null
    ): List<EventItem> {
        if (fromMs != null && toMs != null) {
            return eventItemDao.getEventsOfTimetableDuringSync(timetableId, fromMs, toMs)
        }
        return eventItemDao.getEventsOfTimetableSync(timetableId)
    }

    fun getTimetableByEasCode(code: String): LiveData<Timetable?> {
        return timetableDao.getTimetableByEASCode(code)
    }

    /**
     * 获得某学期（本地可能没有）的当前周数
     */
    fun getCurrentWeekOfTimetable(termItem: TermItem?): LiveData<Int> {
        val result = MediatorLiveData<Int>()
        result.addSource(timetableDao.getTimetableByEASCode(termItem?.getCode() ?: "")) {
            it?.let {
                result.value = it.getWeekNumber(System.currentTimeMillis())
            } ?: kotlin.run {
                result.value = 1
            }
        }
        return result
    }

    fun getRecentTimetable(): LiveData<Timetable?> {
        return timetableDao.getTimetableClosestToTimestamp(System.currentTimeMillis())
    }

    fun getTimetableCount(): LiveData<Int> {
        return timetableDao.geeTimetableCount()
    }

    fun searchLocation(str: String): LiveData<List<String>> {
        return timetableDao.searchLocation("%$str%")
    }

    fun actionDeleteTimetables(timetables: List<Timetable>) {
        val ids = mutableListOf<String>()
        for (tt in timetables) {
            ids.add(tt.id)
        }
        executor.execute {
            val eventIds = eventItemDao.getEventIdsFromTimetablesSync(ids)
            StupidSync.putHistorySync("event", History.ACTION.REMOVE, eventIds)
            val subjectIds = subjectDao.getSubjectIdsOfTimetablesSync(ids)
            StupidSync.putHistorySync("subject", History.ACTION.REMOVE, subjectIds)

            timetableDao.deleteTimetablesSync(timetables)
            eventItemDao.deleteEventsFromTimetablesSync(ids)
            subjectDao.deleteSubjectsFromTimetablesSync(ids)
            StupidSync.putHistorySync(historyTag, History.ACTION.REMOVE, ids)
        }

    }

    fun actionDeleteEvents(courses: Collection<EventItem>) {
        val ids = mutableListOf<String>()
        for (tt in courses) {
            ids.add(tt.id)
        }
        executor.execute {
            val eventIds = eventItemDao.getEventIdsFromTimetablesSync(ids)
            StupidSync.putHistorySync("event", History.ACTION.REMOVE, eventIds)
            eventItemDao.deleteEventsInIdsSync(ids)
        }
    }

    fun actionNewTimetable() {
        executor.execute {
            val newTable = buildNextDefaultTimetableSync()
            timetableDao.saveTimetableSync(newTable)
            StupidSync.putHistorySync("timetable", History.ACTION.REQUIRE, listOf(newTable.id))
        }
    }

    fun ensureDefaultCustomTimetableAsync() {
        executor.execute {
            ensureDefaultCustomTimetableSync()
        }
    }

    @WorkerThread
    fun ensureDefaultCustomTimetableSync(): Timetable {
        val existing = timetableDao.getFirstCustomTimetableSync()
        if (existing != null) return existing

        val newTable = buildNextDefaultTimetableSync()
        timetableDao.saveTimetableSync(newTable)
        StupidSync.putHistorySync("timetable", History.ACTION.REQUIRE, listOf(newTable.id))
        return newTable
    }

    @WorkerThread
    private fun buildNextDefaultTimetableSync(): Timetable {
        val defaultPrefix = application.getString(R.string.default_timetable_name)
        val defaultTables = timetableDao.getTimetableNamesWithDefaultSync("$defaultPrefix%")
        var max = 0
        for (tt in defaultTables) {
            val i = try {
                tt.replace(defaultPrefix, "").toInt()
            } catch (e: NumberFormatException) {
                null
            }
            if (i != null && i > max) {
                max = i
            }
        }
        val newTable = Timetable()
        val c = TimeTools.getMonday(System.currentTimeMillis())
        newTable.startTime.time = c.timeInMillis
        newTable.endTime.time = c.timeInMillis + WEEK_MILLS
        newTable.name = defaultPrefix + (max + 1).toString()
        return newTable
    }

    fun actionSaveTimetable(timetable: Timetable) {
        executor.execute {
            timetableDao.saveTimetableSync(timetable)
            StupidSync.putHistorySync("timetable", History.ACTION.REQUIRE, listOf(timetable.id))
        }
    }

    @WorkerThread
    fun saveTimetableSync(timetable: Timetable) {
        timetableDao.saveTimetableSync(timetable)
        StupidSync.putHistorySync("timetable", History.ACTION.REQUIRE, listOf(timetable.id))
    }

    fun actionChangeTimetableStartDate(timetable: Timetable, startTime: Long) {
        val calendar = TimeTools.getMonday(startTime)
        val offset = calendar.timeInMillis - timetable.startTime.time
        timetable.endTime.time = timetable.endTime.time + offset
        timetable.startTime.time = calendar.timeInMillis
        executor.execute {
            timetableDao.saveTimetableSync(timetable)
            eventItemDao.updateClassesAddOffset(timetableId = timetable.id, offset)
            StupidSync.putHistorySync("timetable", History.ACTION.REQUIRE, listOf(timetable.id))
        }
    }

    fun actionChangeTimetableStructure(timetable: Timetable, tp: TimePeriodInDay, position: Int) {
        timetable.setScheduleStructure(tp, position)
        executor.execute {
            timetableDao.saveTimetableSync(timetable)
            StupidSync.putHistorySync("timetable", History.ACTION.REQUIRE, listOf(timetable.id))
            val fromToChange = eventItemDao.getClassAtFromNumberSync(timetable.id, position + 1)
            val tmp = Calendar.getInstance()
            val ids = mutableListOf<String>()
            for (e in fromToChange) {
                ids.add(e.id)
                tmp.timeInMillis = e.from.time
                tmp.set(Calendar.HOUR_OF_DAY, tp.from.hour)
                tmp.set(Calendar.MINUTE, tp.from.minute)
                e.from.time = tmp.timeInMillis
            }
            eventItemDao.saveEvents(fromToChange)
            val endToChange = eventItemDao.getClassAtToNumberSync(timetable.id, position + 1)
            for (e in endToChange) {
                ids.add(e.id)
                tmp.timeInMillis = e.to.time
                tmp.set(Calendar.HOUR_OF_DAY, tp.to.hour)
                tmp.set(Calendar.MINUTE, tp.to.minute)
                e.to.time = tmp.timeInMillis
            }
            eventItemDao.saveEvents(endToChange)
            StupidSync.putHistorySync("event", History.ACTION.REQUIRE, ids)
        }
    }

    fun actionAddEvents(data:List<EventItem>) {
        executor.execute {
            eventItemDao.addEvents(data)
            val ids = mutableListOf<String>()
            for (e in data) {
                ids.add(e.id)
            }
            StupidSync.putHistorySync("event", History.ACTION.REQUIRE, ids)
        }
    }

    @WorkerThread
    fun addEventsSync(data: List<EventItem>) {
        eventItemDao.addEvents(data)
        val ids = mutableListOf<String>()
        for (e in data) {
            ids.add(e.id)
        }
        StupidSync.putHistorySync("event", History.ACTION.REQUIRE, ids)
    }


    fun exportToICS(timetableName: String, timetableId: String): LiveData<DataState<String>> {
        val res = MutableLiveData<DataState<String>>();
        executor.execute {
            val filename = "HITSZ助手：$timetableName.ics"
            val path = application.getExternalFilesDir("ics").toString() + "/" + filename
            try {
                val calendar = net.fortuna.ical4j.model.Calendar()
                calendar.properties.add(ProdId("-//StupidTree//HITA//EN"))
                calendar.properties.add(Version.VERSION_2_0)
                calendar.properties.add(CalScale.GREGORIAN)
                for (ei in eventItemDao.getEventsOfTimetableSync(timetableId)) {
                    val start = DateTime(ei.from)
                    start.isUtc = true
                    val end = DateTime(ei.to)
                    end.isUtc = true
                    val event = VEvent(start, end, ei.name)
                    val locationText = when (ei.type) {
                        EventItem.TYPE.CLASS, EventItem.TYPE.EXAM -> listOf(
                            ei.place?.trim().orEmpty(),
                            ei.teacher?.trim().orEmpty()
                        ).filter { it.isNotEmpty() }.joinToString(" ")

                        else -> ei.place?.trim().orEmpty()
                    }
                    if (locationText.isNotEmpty()) {
                        event.properties.add(Location(locationText))
                    }
                    event.properties.add(Uid(UidGenerator("hita").generateUid().value))
                    // 添加邀请者
//                val dev1 = Attendee(URI.create("https://hita.store/search?type=teacher"))
//                dev1.parameters.add(Role.REQ_PARTICIPANT)
//                dev1.parameters.add(Cn(ei.teacher))
//                event.properties.add(dev1)
//                val recur = Recur(Recur.WEEKLY, Int.MAX_VALUE)
//                recur.dayList.add(WeekDay.MO)
//                recur.dayList.add(WeekDay.TU)
//                recur.dayList.add(WeekDay.WE)
//                recur.dayList.add(WeekDay.TH)
//                recur.dayList.add(WeekDay.FR)
//                val rule = RRule(recur)
//                event.properties.add(rule)
                    val valarm = VAlarm(Dur(0, 0, -10, 0))
                    val isCourseLike = ei.type == EventItem.TYPE.CLASS || ei.type == EventItem.TYPE.EXAM
                    valarm.properties.add(Summary(if (isCourseLike) "课程提醒" else "事件提醒"))
                    valarm.properties.add(Action.DISPLAY)
                    valarm.properties.add(Description(ei.name + if (isCourseLike) "马上就要开始啦！" else "马上开始啦！"))
                    event.alarms.add(valarm)
                    calendar.components.add(event)

                }
                calendar.validate()

                val fos = FileOutputStream(path)
                val outputter = CalendarOutputter()
                outputter.output(calendar, fos)
                res.postValue(DataState(path))
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED))
            }
        }
        return res;
    }

    /**
     * 从 ICS 文件导入课表
     * @param inputStream ICS 文件输入流
     * @param timetableId 目标课表 ID
     * @return 导入结果，包含导入的课程数量
     */
    fun importFromICS(inputStream: InputStream, timetableId: String): LiveData<DataState<Int>> {
        val res = MutableLiveData<DataState<Int>>()
        executor.execute {
            try {
                val builder = CalendarBuilder()
                val calendar = builder.build(inputStream)
                val events = calendar.components.filterIsInstance<VEvent>()
                var importedCount = 0
                
                for (event in events) {
                    val ei = IcsImportEventMapper.map(event, timetableId) ?: continue
                    eventItemDao.insertEventSync(ei)
                    importedCount++
                }
                
                // 同步到云端
                if (importedCount > 0) {
                    val eventIds = eventItemDao.getEventIdsOfTimetableSync(timetableId)
                    StupidSync.putHistorySync("event", History.ACTION.REQUIRE, eventIds)
                }
                
                res.postValue(DataState(importedCount))
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }
        return res
    }

    fun importFromICSAsNewTimetable(
        inputStream: InputStream,
        sourceName: String?
    ): LiveData<DataState<IcsImportResult>> {
        val res = MutableLiveData<DataState<IcsImportResult>>()
        executor.execute {
            try {
                val calendar = inputStream.use { CalendarBuilder().build(it) }
                val bundle = IcsImportBundleBuilder.build(
                    events = calendar.components.filterIsInstance<VEvent>(),
                    sourceName = sourceName
                )
                timetableDao.saveTimetableSync(bundle.timetable)
                subjectDao.saveSubjectsSync(bundle.subjects)
                eventItemDao.saveEvents(bundle.events)

                StupidSync.putHistorySync(
                    historyTag,
                    History.ACTION.REQUIRE,
                    listOf(bundle.timetable.id)
                )
                StupidSync.putHistorySync(
                    "subject",
                    History.ACTION.REQUIRE,
                    bundle.subjects.map { it.id }
                )
                StupidSync.putHistorySync(
                    "event",
                    History.ACTION.REQUIRE,
                    bundle.events.map { it.id }
                )

                res.postValue(
                    DataState(
                        IcsImportResult(
                            timetableId = bundle.timetable.id,
                            timetableName = bundle.timetable.name ?: application.getString(R.string.default_timetable_name),
                            importedCount = bundle.events.size
                        )
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                val message = if (e is IllegalArgumentException) e.message else e.message
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, message))
            }
        }
        return res
    }

    fun actionClearData() {
        executor.execute {
            eventItemDao.clear()
            subjectDao.clear()
            timetableDao.clear()
        }
    }

    companion object {
        private var instance: TimetableRepository? = null
        fun getInstance(application: Application): TimetableRepository {
            if (instance == null) instance = TimetableRepository(application)
            return instance!!
        }
    }
}
