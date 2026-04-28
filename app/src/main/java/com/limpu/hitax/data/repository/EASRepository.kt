package com.limpu.hitax.data.repository

import android.app.Application
import android.os.Handler
import javax.inject.Inject
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.AppDatabase
import com.limpu.hitax.data.model.eas.*
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.source.preference.EasPreferenceSource
import com.limpu.hitax.data.source.preference.TimetablePreferenceSource
import com.limpu.hitax.data.source.web.eas.BenbuEASWebSource
import com.limpu.hitax.data.source.web.eas.EASWebSource
import com.limpu.hitax.data.source.web.eas.WeihaiEASWebSource
import com.limpu.hitax.data.source.web.service.EASService
import com.limpu.hitax.ui.eas.classroom.BuildingItem
import com.limpu.hitax.ui.eas.classroom.ClassroomItem
import com.limpu.hitax.utils.LiveDataUtils
import com.limpu.hitax.utils.TimeTools.getDateAtWOT
import com.limpu.hitax.utils.TermNameFormatter
import com.limpu.hitax.utils.CourseCodeUtils
import com.limpu.hitax.utils.CourseNameUtils
import com.limpu.sync.StupidSync
import com.limpu.sync.data.model.History
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.limpu.hitax.utils.LogUtils

class EASRepository @Inject constructor(
    application: Application,
    private val easPreferenceSource: EasPreferenceSource,
    private val timetablePreferenceSource: TimetablePreferenceSource
) {
    private val appContext = application.applicationContext
    private val shenzhenService: EASWebSource = EASWebSource()
    private val benbuService: EASService = BenbuEASWebSource()
    private val weihaiService: EASService = WeihaiEASWebSource()
    private var eventItemDao = AppDatabase.getDatabase(application).eventItemDao()
    private var timetableDao = AppDatabase.getDatabase(application).timetableDao()
    private var subjectDao = AppDatabase.getDatabase(application).subjectDao()
    private val easTokenLiveData = MutableLiveData(easPreferenceSource.getEasToken())

    private val DEBUG_WEEK = 7
    private val DEBUG_DOW = setOf(5, 6)

    private fun getService(campus: EASToken.Campus): EASService {
        return when (campus) {
            EASToken.Campus.SHENZHEN -> shenzhenService
            EASToken.Campus.BENBU -> benbuService
            EASToken.Campus.WEIHAI -> weihaiService
        }
    }

    /**
     * 进行登录
     */
    fun login(username: String, password: String): LiveData<DataState<Boolean>> {
        return login(username, password, EASToken.Campus.SHENZHEN)
    }

    fun login(
        username: String,
        password: String,
        campus: EASToken.Campus
    ): LiveData<DataState<Boolean>> {
        val result = MediatorLiveData<DataState<Boolean>>()
        val loginSource = getService(campus).login(username, password, null)
        result.addSource(loginSource) { state ->
            if (state.state == DataState.STATE.NOTHING) {
                return@addSource
            }
            if (state.state != DataState.STATE.SUCCESS) {
                result.value = DataState(false, state.state).apply { message = state.message }
                return@addSource
            }
            val token = state.data
            if (token == null) {
                result.value = DataState(false, DataState.STATE.FETCH_FAILED).apply { message = state.message }
                return@addSource
            }
            token.campus = campus
            val enrichSource = getService(campus).getSafePersonalInfo(token)
            result.addSource(enrichSource) enrichObserver@{ enrichedState ->
                if (enrichedState.state == DataState.STATE.NOTHING) {
                    return@enrichObserver
                }
                val finalToken = if (enrichedState.state == DataState.STATE.SUCCESS) {
                    enrichedState.data ?: token
                } else {
                    token
                }
                finalToken.campus = campus
                saveEasToken(finalToken)
                result.value = DataState(true, DataState.STATE.SUCCESS)
                result.removeSource(enrichSource)
            }
            result.removeSource(loginSource)
        }
        return result
    }

    /**
     * 验证登录
     */
    fun loginCheck(): LiveData<DataState<Boolean>> {
        val token = easPreferenceSource.getEasToken()
        if (!token.isLogin()) {
            return LiveDataUtils.getMutableLiveData(DataState(false))
        }
        return getService(token.campus).loginCheck(token).switchMap {
            if (it.state == DataState.STATE.SUCCESS && it.data != null) {
                if (!it.data!!.first) {
                    clearEasToken()
                } else {
                    saveEasToken(it.data!!.second)
                }
            }
            return@switchMap MutableLiveData(DataState(it.data?.first == true))
        }
    }

    /**
     * 获取学期开始日期
     */
    fun getStartDateOfTerm(term: TermItem): LiveData<DataState<Calendar>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return getService(easToken.campus).getStartDate(easToken, term)
        }
        return LiveDataUtils.getMutableLiveData<DataState<Calendar>>(DataState(DataState.STATE.NOT_LOGGED_IN))
    }


    /**
     * 进行获取学年学期
     */
    fun getAllTerms(): LiveData<DataState<List<TermItem>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return getService(easToken.campus).getAllTerms(easToken)
        }
        return LiveDataUtils.getMutableLiveData<DataState<List<TermItem>>>(DataState(DataState.STATE.NOT_LOGGED_IN))
    }

    /**
     * 获取课表结构
     */
    fun getScheduleStructure(
        term: TermItem,
        isUndergraduate: Boolean? = null
    ): LiveData<DataState<MutableList<TimePeriodInDay>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return getService(easToken.campus).getScheduleStructure(term, isUndergraduate, easToken)
        }
        return LiveDataUtils.getMutableLiveData<DataState<MutableList<TimePeriodInDay>>>(
            DataState(
                DataState.STATE.NOT_LOGGED_IN
            )
        )

    }

    /**
     * 获取教学楼列表
     */
    fun getTeachingBuildings(): LiveData<DataState<List<BuildingItem>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return getService(easToken.campus).getTeachingBuildings(easToken)
        }
        return LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))

    }

    /**
     * 查询空教室
     */
    fun queryEmptyClassroom(
        term: TermItem,
        buildingItem: BuildingItem,
        week: Int
    ): LiveData<DataState<List<ClassroomItem>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return getService(easToken.campus).queryEmptyClassroom(
                easToken,
                term,
                buildingItem,
                listOf(week.toString())
            )
        }
        return LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
    }

    /**
     * 获取最终成绩
     */
    fun getPersonalScores(
        term: TermItem,
        testType: EASService.TestType
    ): LiveData<DataState<List<CourseScoreItem>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return getService(easToken.campus).getPersonalScores(term, easToken, testType)
        }
        return LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
    }

    fun getPersonalScoresWithSummary(
        term: TermItem,
        testType: EASService.TestType
    ): LiveData<DataState<ScoreQueryResult>> {
        val easToken = easPreferenceSource.getEasToken()
        if (!easToken.isLogin()) {
            return LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
        }
        val service = getService(easToken.campus)
        return when (service) {
            is EASWebSource -> service.getPersonalScoresWithSummary(term, easToken, testType)
            is BenbuEASWebSource -> service.getPersonalScoresWithSummary(term, easToken, testType)
            is WeihaiEASWebSource -> service.getPersonalScoresWithSummary(term, easToken, testType)
            else -> service.getPersonalScores(term, easToken, testType).map { state ->
                if (state.state == DataState.STATE.SUCCESS) {
                    DataState(ScoreQueryResult(items = state.data ?: emptyList(), summary = null), state.state)
                } else {
                    DataState<ScoreQueryResult>(state.state, state.message)
                }
            }
        }
    }

    /**
     * 获取考试信息
     */
    fun getExamInfo(): LiveData<DataState<List<ExamItem>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return getService(easToken.campus).getExamItems(easToken)
        }
        return LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
    }

    /**
     * 动作：导入课表
     */
    private var timetableWebLiveData: LiveData<DataState<List<CourseItem>>>? = null
    fun startImportTimetableOfTerm(
        term: TermItem,
        startDate: Calendar,
        schedule: List<TimePeriodInDay>,//课表结构
        importTimetableLiveData: MediatorLiveData<DataState<Boolean>>
    ) {
        startDate.set(Calendar.HOUR_OF_DAY, 0)
        startDate.set(Calendar.MINUTE, 0)
        startDate.firstDayOfWeek = Calendar.MONDAY
        startDate.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val easToken = easPreferenceSource.getEasToken()
        LogUtils.d(
            "startImportTimetableOfTerm term=${term.getCode()} campus=${easToken.campus} start=${startDate.time} scheduleSize=${schedule.size} loggedIn=${easToken.isLogin()}"
        )
        if (easToken.isLogin()) {
            val finished = AtomicBoolean(false)
            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (finished.compareAndSet(false, true)) {
                    importTimetableLiveData.value =
                        DataState(DataState.STATE.FETCH_FAILED, "导入超时，请重试")
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, 90_000L)
            timetableWebLiveData?.let { importTimetableLiveData.removeSource(it) }
            timetableWebLiveData =
                getService(easToken.campus).getTimetableOfTerm(term, easToken)
            importTimetableLiveData.addSource(timetableWebLiveData!!) {
                when (it.state) {
                    DataState.STATE.SUCCESS -> {
                        val courseItems = it.data
                        LogUtils.d( "import timetable response state=${it.state} term=${term.getCode()} courseCount=${courseItems?.size ?: -1} message=${it.message}")
                        if (courseItems.isNullOrEmpty()) {
                            if (finished.compareAndSet(false, true)) {
                                timeoutHandler.removeCallbacks(timeoutRunnable)
                                importTimetableLiveData.value =
                                    DataState(DataState.STATE.FETCH_FAILED, "empty timetable")
                            }
                            return@addSource
                        }
                        Thread {
                            try {
                                val meta = fetchSelectedSubjectMeta(term, easToken)
                                val teacherMap = meta.teacherMap
                                val creditMap = meta.creditMap
                                val maxPeriod = courseItems.maxOfOrNull { item ->
                                    (item.begin + item.last - 1).coerceAtLeast(item.begin)
                                } ?: 0
                                val safeSchedule = buildSafeSchedule(schedule, maxPeriod)
                                LogUtils.d(
                                    "import processing term=${term.getCode()} courseCount=${courseItems.size} maxPeriod=$maxPeriod safeScheduleSize=${safeSchedule.size}"
                                )
                                //更新timetable信息
                                var timetable = timetableDao.getTimetableByEASCodeSync(term.getCode())
                                if (timetable == null) {
                                    timetable = Timetable()
                                } else {
                                    //若存在，则先清空原有课表课程
                                    val eventIds =
                                        eventItemDao.getEventIdsFromTimetablesSync(listOf(timetable.id))
                                    StupidSync.putHistorySync("event", History.ACTION.REMOVE, eventIds)
                                    eventItemDao.deleteCourseFromTimetable(timetable.id)
                                }
                                StupidSync.putHistorySync(
                                    "timetable",
                                    History.ACTION.REQUIRE,
                                    listOf(timetable.id)
                                )
                                //记录最后的时间戳，作为学期结束的标志
                                var maxTs: Long = 0
                                //添加时间表
                                val events = mutableListOf<EventItem>()
                                val requireSubjects = mutableMapOf<String, String>()
                                for (item in courseItems) {
                                    val startIndex = item.begin - 1
                                    val endIndex = item.begin + item.last - 2
                                    if (startIndex !in safeSchedule.indices || endIndex !in safeSchedule.indices) {
                                        continue
                                    }
                                    val spStart = safeSchedule[startIndex]
                                    val spEnd = safeSchedule[endIndex]
                                    val rawName = item.name?.toString().orEmpty().trim()
                                    val normalizedName = CourseNameUtils.normalize(rawName) ?: rawName

                                    //添加科目
                                    var subject = subjectDao.getSubjectByName(timetable.id, normalizedName)
                                    if (subject == null) {//不存在，新建
                                        subject = TermSubject()
                                        // 优先保存完整的原始名称
                                        subject.name = rawName
                                        subject.timetableId = timetable.id
                                        subject.id = UUID.randomUUID().toString()
                                    } else {
                                        // 科目已存在，总是尝试更新为更完整的名称
                                        // 优先选择包含更多信息（括号、方括号）的名称
                                        val oldHasBrackets = subject.name.contains("（") || subject.name.contains("(") ||
                                                               subject.name.contains("[") || subject.name.contains("【")
                                        val newHasBrackets = rawName.contains("（") || rawName.contains("(") ||
                                                               rawName.contains("[") || rawName.contains("【")

                                        // 如果新名称包含括号信息（通常更完整），或者新名称明显更长，则更新
                                        if (newHasBrackets && !oldHasBrackets) {
                                            subject.name = rawName
                                        } else if (rawName.length > subject.name.length + 2) {
                                            // 只有新名称明显更长时才更新（避免因细微差异反复更新）
                                            subject.name = rawName
                                        }
                                    }
                                    val code = CourseCodeUtils.normalize(item.code) ?: item.code?.trim().orEmpty()
                                    if (code.isNotBlank() && subject.code.isNullOrBlank()) {
                                        subject.code = code
                                    }
                                    if (subject.credit <= 0f) {
                                        val mappedCredit = creditMap[code]
                                            ?: creditMap[rawName]
                                            ?: creditMap[normalizedName]
                                        if (mappedCredit != null && mappedCredit > 0f) {
                                            subject.credit = mappedCredit
                                        }
                                    }
                                    if (subject.field.isNullOrBlank()) {
                                        val mappedField = meta.fieldMap[code]
                                            ?: meta.fieldMap[rawName]
                                            ?: meta.fieldMap[normalizedName]
                                        if (!mappedField.isNullOrBlank()) {
                                            subject.field = mappedField
                                        }
                                    }
                                    if (subject.selectCategory.isNullOrBlank()) {
                                        val mappedSelect = meta.selectCategoryMap[code]
                                            ?: meta.selectCategoryMap[rawName]
                                            ?: meta.selectCategoryMap[normalizedName]
                                        if (!mappedSelect.isNullOrBlank()) {
                                            subject.selectCategory = mappedSelect
                                        }
                                    }
                                    if (subject.nature.isNullOrBlank()) {
                                        val mappedNature = meta.natureMap[code]
                                            ?: meta.natureMap[rawName]
                                            ?: meta.natureMap[normalizedName]
                                        if (!mappedNature.isNullOrBlank()) {
                                            subject.nature = mappedNature
                                        }
                                    }
                                    subjectDao.saveSubjectSync(subject)
                                    if (requireSubjects[subject.id] == null) {
                                        requireSubjects[subject.id] = subject.id
                                        StupidSync.putHistorySync(
                                            "subject",
                                            History.ACTION.REQUIRE,
                                            listOf(subject.id)
                                        )
                                    }

                                    for (week in item.weeks) {
                                        val from = getDateAtWOT(startDate, week, item.dow)
                                        val to = getDateAtWOT(startDate, week, item.dow)
                                        from.set(Calendar.HOUR_OF_DAY, spStart.from.hour)
                                        from.set(Calendar.MINUTE, spStart.from.minute)
                                        to.set(Calendar.HOUR_OF_DAY, spEnd.to.hour)
                                        to.set(Calendar.MINUTE, spEnd.to.minute)
                                        val e = EventItem()
                                        e.source = EventItem.SOURCE_EAS_IMPORT
                                        // 使用原始完整名称而不是normalized
                                        e.name = rawName
                                        e.from.time = from.timeInMillis
                                        e.fromNumber = item.begin
                                        e.subjectId = subject.id
                                        e.lastNumber = item.last
                                        e.to.time = to.timeInMillis
                                        val itemTeacher = sanitizeImportedTeacher(rawName, item.teacher)
                                        val mappedTeacher = itemTeacher
                                            ?: code.takeIf { it.isNotBlank() }?.let { teacherMap[it] }
                                            ?: teacherMap[rawName]
                                            ?: teacherMap[normalizedName]
                                        val teacherSource = when {
                                            !itemTeacher.isNullOrBlank() -> "item"
                                            !code.isNullOrBlank() && !teacherMap[code].isNullOrBlank() -> "meta_by_code"
                                            !teacherMap[rawName].isNullOrBlank() -> "meta_by_name_raw"
                                            !teacherMap[normalizedName].isNullOrBlank() -> "meta_by_name_normalized"
                                            else -> "none"
                                        }
                                        if (item.dow in DEBUG_DOW && week == DEBUG_WEEK) {
                                            val periodEnd = item.begin + item.last - 1
                                            LogUtils.d(
                                                "[DBG_W7_D56][MAP] week=$week dow=${item.dow} " +
                                                    "nameRaw=$rawName nameNorm=$normalizedName code=${item.code} period=${item.begin}-$periodEnd " +
                                                    "itemTeacherRaw=${item.teacher} itemTeacher=$itemTeacher mappedTeacher=$mappedTeacher source=$teacherSource " +
                                                    "teacherMapByCode=${if (code.isBlank()) null else teacherMap[code]} teacherMapByNameRaw=${teacherMap[rawName]} teacherMapByNameNorm=${teacherMap[normalizedName]}"
                                            )
                                        }
                                        e.teacher = mappedTeacher
                                        e.place = item.classroom
                                        e.timetableId = timetable.id
                                        if (e.to.time > maxTs) maxTs = e.to.time
                                        events.add(e)
                                    }
                                }
                                if (events.isEmpty()) {
                                    LogUtils.w( "import generated empty events for term=${term.getCode()}")
                                    if (finished.compareAndSet(false, true)) {
                                        timeoutHandler.removeCallbacks(timeoutRunnable)
                                        importTimetableLiveData.postValue(
                                            DataState(DataState.STATE.FETCH_FAILED, "empty events")
                                        )
                                    }
                                    return@Thread
                                }
                                LogUtils.d( "import saving ${events.size} events for term=${term.getCode()}")
                                eventItemDao.saveEvents(events)
                                StupidSync.putHistorySync("event", History.ACTION.REQUIRE, events.getIds())

                                //更新timetable对象
                                timetable.name = buildTimetableName(term)
                                timetable.startTime = Timestamp(startDate.timeInMillis)
                                timetable.endTime = Timestamp(maxTs)
                                timetable.code = term.getCode()
                                timetable.scheduleStructure = safeSchedule
                                timetableDao.saveTimetableSync(timetable)

                                if (finished.compareAndSet(false, true)) {
                                    timeoutHandler.removeCallbacks(timeoutRunnable)
                                    LogUtils.d( "import success term=${term.getCode()} timetable=${timetable.name} events=${events.size}")
                                    importTimetableLiveData.postValue(DataState(true, DataState.STATE.SUCCESS))
                                }
                            } catch (e: Exception) {
                                LogUtils.e( "import failed term=${term.getCode()}: ${e.message}", e)
                                if (finished.compareAndSet(false, true)) {
                                    timeoutHandler.removeCallbacks(timeoutRunnable)
                                    importTimetableLiveData.postValue(
                                        DataState(DataState.STATE.FETCH_FAILED, e.message)
                                    )
                                }
                            }
                        }.start()
                    }
                    DataState.STATE.FETCH_FAILED, DataState.STATE.NOT_LOGGED_IN -> {
                        LogUtils.w( "import timetable fetch failed term=${term.getCode()} message=${it.message}")
                        if (finished.compareAndSet(false, true)) {
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            importTimetableLiveData.value =
                                DataState(DataState.STATE.FETCH_FAILED, it.message)
                        }
                    }
                    else -> Unit
                }
            }
        } else {
            importTimetableLiveData.value = DataState(DataState.STATE.NOT_LOGGED_IN)
        }
    }

    private fun sanitizeImportedTeacher(courseName: String?, teacherRaw: String?): String? {
        val source = teacherRaw?.trim().orEmpty()
        if (source.isBlank()) return null

        val name = courseName?.trim().orEmpty()
        val normalized = source.replace(" ", "")
        val looksLikeCoursePayload = normalized.startsWith("【") ||
            (name.isNotBlank() && (source.startsWith(name) || normalized.contains(name.replace(" ", ""))))
        if (looksLikeCoursePayload) return null

        val cleaned = source
            .replace(Regex("^第[一二三四五六七八九十0-9]+批"), "")
            .trimStart('/', '／', ' ', '\t')
            .trim()
        return cleaned.ifBlank { null }
    }

    private fun buildSafeSchedule(
        schedule: List<TimePeriodInDay>,
        requiredMaxPeriod: Int
    ): List<TimePeriodInDay> {
        if (requiredMaxPeriod <= 0) return schedule
        if (schedule.size >= requiredMaxPeriod) return schedule
        val defaults = Timetable().getDefaultTimeStructure()
        val size = maxOf(requiredMaxPeriod, defaults.size, schedule.size)
        return List(size) { idx ->
            schedule.getOrNull(idx) ?: defaults.getOrNull(idx) ?: defaults.last()
        }
    }

    private fun buildTimetableName(term: TermItem): String {
        return TermNameFormatter.shortTermName(term.termName, term.name)
    }

    private data class SelectedSubjectMeta(
        val teacherMap: Map<String, String>,
        val creditMap: Map<String, Float>,
        val fieldMap: Map<String, String>,
        val selectCategoryMap: Map<String, String>,
        val natureMap: Map<String, String>
    )

    private fun fetchSelectedSubjectMeta(term: TermItem, token: EASToken): SelectedSubjectMeta {
        val teacherMap = mutableMapOf<String, String>()
        val creditMap = mutableMapOf<String, Float>()
        val fieldMap = mutableMapOf<String, String>()
        val selectCategoryMap = mutableMapOf<String, String>()
        val natureMap = mutableMapOf<String, String>()
        val latch = CountDownLatch(1)
        val live = getService(token.campus).getSubjectsOfTerm(token, term)
        val observer = Observer<DataState<MutableList<TermSubject>>> { state ->
            if (state.state == DataState.STATE.SUCCESS || state.state == DataState.STATE.FETCH_FAILED) {
                state.data?.forEach { subject ->
                    val teacher = subject.teacher?.trim()
                    if (!teacher.isNullOrEmpty()) {
                        subject.code?.let { code -> teacherMap[code] = teacher }
                        if (subject.name.isNotBlank()) teacherMap[subject.name] = teacher
                    }
                    val credit = subject.credit
                    if (credit > 0f) {
                        subject.code?.let { code -> creditMap[code] = credit }
                        if (subject.name.isNotBlank()) creditMap[subject.name] = credit
                    }
                    subject.field?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                        subject.code?.let { code -> fieldMap[code] = value }
                        if (subject.name.isNotBlank()) fieldMap[subject.name] = value
                    }
                    subject.selectCategory?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                        subject.code?.let { code -> selectCategoryMap[code] = value }
                        if (subject.name.isNotBlank()) selectCategoryMap[subject.name] = value
                    }
                    subject.nature?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                        subject.code?.let { code -> natureMap[code] = value }
                        if (subject.name.isNotBlank()) natureMap[subject.name] = value
                    }
                }
                latch.countDown()
            }
        }
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post { live.observeForever(observer) }
        latch.await(4, TimeUnit.SECONDS)
        mainHandler.post { live.removeObserver(observer) }
        return SelectedSubjectMeta(teacherMap, creditMap, fieldMap, selectCategoryMap, natureMap)
    }

    fun startAutoImportCurrentTimetable(
        isUndergraduate: Boolean,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val token = easPreferenceSource.getEasToken()
        if (!token.isLogin()) {
            onResult?.invoke(false)
            return
        }
        Thread {
            val service = getService(token.campus)
            val termsState = awaitLiveData(service.getAllTerms(token), 6)
            LogUtils.d( "autoImport terms state=${termsState.state} count=${termsState.data?.size ?: -1} message=${termsState.message}")
            val term = termsState.data?.firstOrNull { it.isCurrent } ?: termsState.data?.firstOrNull()
            if (term == null) {
                onResult?.invoke(false)
                return@Thread
            }
            val startState = awaitLiveData(service.getStartDate(token, term), 6)
            val startDate = startState.data
            LogUtils.d( "autoImport startDate state=${startState.state} value=${startDate?.time} message=${startState.message}")
            val scheduleState = awaitLiveData(
                service.getScheduleStructure(term, isUndergraduate, token),
                6
            )
            val schedule = scheduleState.data ?: timetablePreferenceSource.getSchedule()
            LogUtils.d( "autoImport schedule state=${scheduleState.state} size=${schedule.size} message=${scheduleState.message}")
            if (startDate == null) {
                onResult?.invoke(false)
                return@Thread
            }
            val importLive = MediatorLiveData<DataState<Boolean>>()
            val latch = CountDownLatch(1)
            var success = false
            val observer = Observer<DataState<Boolean>> { state ->
                if (state.state == DataState.STATE.SUCCESS || state.state == DataState.STATE.FETCH_FAILED) {
                    success = state.state == DataState.STATE.SUCCESS
                    latch.countDown()
                }
            }
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                importLive.observeForever(observer)
                startImportTimetableOfTerm(term, startDate, schedule, importLive)
            }
            latch.await(25, TimeUnit.SECONDS)
            mainHandler.post { importLive.removeObserver(observer) }
            onResult?.invoke(success)
        }.start()
    }

    private fun <T> awaitLiveData(
        live: LiveData<DataState<T>>,
        timeoutSeconds: Long
    ): DataState<T> {
        val latch = CountDownLatch(1)
        var result = DataState<T>(DataState.STATE.FETCH_FAILED)
        val observer = Observer<DataState<T>> { state ->
            result = state
            latch.countDown()
        }
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post { live.observeForever(observer) }
        latch.await(timeoutSeconds, TimeUnit.SECONDS)
        mainHandler.post { live.removeObserver(observer) }
        return result
    }

    fun isSubjectMetaSupported(campus: EASToken.Campus = easPreferenceSource.getEasToken().campus): Boolean {
        return campus == EASToken.Campus.SHENZHEN
    }

    fun getHoaCampus(@Suppress("UNUSED_PARAMETER") campus: EASToken.Campus = easPreferenceSource.getEasToken().campus): String {
        return "shenzhen"
    }

    fun getEasToken(): EASToken {
        return easPreferenceSource.getEasToken()
    }

    fun observeEasToken(): LiveData<EASToken> {
        return easTokenLiveData
    }

    private fun saveEasToken(token: EASToken) {
        easPreferenceSource.saveEasToken(token)
        easTokenLiveData.postValue(token)
    }

    private fun clearEasToken() {
        easPreferenceSource.clearEasToken()
        easTokenLiveData.postValue(easPreferenceSource.getEasToken())
    }

    fun logout() {
        clearEasToken()
    }


}

private fun List<EventItem>.getIds(): List<String> {
    val res = mutableListOf<String>()
    for (e in this) {
        res.add(e.id)
    }
    return res
}
