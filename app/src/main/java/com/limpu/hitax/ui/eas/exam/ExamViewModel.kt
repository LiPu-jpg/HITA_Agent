package com.limpu.hitax.ui.eas.exam

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.MTransformations
import com.limpu.component.data.Trigger
import com.limpu.hitax.data.AppDatabase
import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.data.model.eas.ExamItem
import com.limpu.hitax.data.model.eas.TermItem
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.ui.eas.EASViewModel
import com.limpu.hitax.utils.LogUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ExamViewModel @Inject constructor(
    easRepo: EASRepository,
    @ApplicationContext private val context: Context
) : EASViewModel(easRepo) {
    /**
     * LiveData区
     */
    private val pageController = MutableLiveData<Trigger>()

    val termsLiveData: LiveData<DataState<List<TermItem>>> = pageController.switchMap {
        return@switchMap easRepo.getAllTerms()
    }

    val selectedTermLiveData: MutableLiveData<TermItem> = MutableLiveData()
    val selectedExamTypeLiveData: MutableLiveData<ExamType> = MutableLiveData()

    init {
        // 初始化默认值，避免筛选时把所有数据都过滤掉
        selectedExamTypeLiveData.value = ExamType.ALL
    }

    private val rawExamLiveData = MutableLiveData<DataState<List<ExamItem>>>()

    private val filterLiveData =
        MTransformations.map(selectedTermLiveData, selectedExamTypeLiveData) { it }

    val examInfoLiveData: LiveData<DataState<List<ExamItem>>> =
        MTransformations.map(rawExamLiveData, filterLiveData) { pair ->
            val state = pair.first
            val term = pair.second.first
            val type = pair.second.second
            val data = state.data

            LogUtils.d("🔍 filter: data size=${data?.size}, term=${term?.name}, type=$type", "ExamViewModel")

            if (state.state != DataState.STATE.SUCCESS || data.isNullOrEmpty()) {
                return@map state
            }

            val filtered = data.filter { item ->
                val termMatch = matchTerm(item, term)
                val typeMatch = matchType(item.examType, type)
                val result = termMatch && typeMatch
                LogUtils.d("🔍 item: ${item.courseName}, termMatch=$termMatch, typeMatch=$typeMatch, result=$result", "ExamViewModel")
                result
            }

            LogUtils.d("🔍 filtered result: ${filtered.size} items", "ExamViewModel")
            DataState(filtered, state.state)
        }

    /**
     * 方法区
     */

    /**
     * 判断是否需要显示考试类型筛选
     *
     * 不同校区的差异：
     * - 深圳校区：API返回的所有考试都是"期末"类型，没有实际分类意义，不显示筛选器
     * - 本部：有明确的期中期末分类，显示筛选器
     * - 威海：暂不支持考试查询
     */
    fun shouldShowExamTypeFilter(): Boolean {
        return easRepo.getCurrentCampus() == EASToken.Campus.BENBU
    }
    fun startRefresh() {
        LogUtils.d("=== 🔄 startRefresh called ===", "ExamViewModel")

        val currentTime = System.currentTimeMillis()
        val selectedTerm = selectedTermLiveData.value
        val termJustChanged = (selectedTerm != null && !selectedTerm.equals(previousTerm))

        LogUtils.d("🌐 current selected term: ${selectedTerm?.name}, previous term: ${previousTerm?.name}, termJustChanged: $termJustChanged", "ExamViewModel")

        // 防止重复调用，但如果学期刚变化了则允许刷新
        if (!termJustChanged && currentTime - lastRefreshTime < 1000) {
            LogUtils.d("⚠️ Refresh called too frequently, skipping", "ExamViewModel")
            return
        }
        lastRefreshTime = currentTime
        previousTerm = selectedTerm

        pageController.value = Trigger.actioning

        // 如果学期为null，等待学期列表加载，不提前设置本地数据
        if (selectedTerm == null) {
            LogUtils.d("⏳ term is null, waiting for term list to load first", "ExamViewModel")
            termsLiveData.observeForever(object : androidx.lifecycle.Observer<DataState<List<TermItem>>> {
                override fun onChanged(result: DataState<List<TermItem>>) {
                    if (result.state == DataState.STATE.SUCCESS) {
                        val termList = result.data
                        if (termList != null && termList.isNotEmpty()) {
                            // 找到当前学期并自动选择
                            val currentTerm = termList.find { it.isCurrent } ?: termList.firstOrNull()
                            currentTerm?.let {
                                LogUtils.d("✅ auto-selected current term: ${it.name}", "ExamViewModel")
                                selectedTermLiveData.value = it
                                // 移除观察者
                                termsLiveData.removeObserver(this)
                                // 触发考试查询
                                startRefresh()
                            }
                        }
                    }
                }
            })
            return
        }

        // 调用API获取服务器考试数据，传递学期参数
        LogUtils.d("🌐 calling getExamInfo API with term: ${selectedTerm.name}", "ExamViewModel")

        // 移除旧的观察者（如果有的话）
        examInfoObserver?.let {
            easRepo.getExamInfo(selectedTerm).removeObserver(it)
        }

        // 创建新的观察者并保存引用
        val newObserver = androidx.lifecycle.Observer<DataState<List<ExamItem>>> { result ->
            LogUtils.d("📥 API response received: state=${result.state}, data size=${result.data?.size}", "ExamViewModel")
            when (result.state) {
                DataState.STATE.SUCCESS -> {
                    // API获取成功，直接使用服务器数据
                    val serverExams = result.data ?: emptyList()
                    LogUtils.d("✅ got ${serverExams.size} exams from server", "ExamViewModel")
                    rawExamLiveData.postValue(DataState(serverExams, DataState.STATE.SUCCESS))
                }
                DataState.STATE.FETCH_FAILED -> {
                    // API获取失败
                    LogUtils.e("❌ API failed: ${result.message}", null, "ExamViewModel")
                    rawExamLiveData.postValue(result)
                }
                else -> {
                    // 其他状态
                    LogUtils.d("⚠️ other state: ${result.state}", "ExamViewModel")
                    rawExamLiveData.postValue(result)
                }
            }
        }

        easRepo.getExamInfo(selectedTerm).observeForever(newObserver)
        examInfoObserver = newObserver
    }

    private var examInfoObserver: androidx.lifecycle.Observer<DataState<List<ExamItem>>>? = null
    private var lastRefreshTime: Long = 0
    private var previousTerm: TermItem? = null

    /**
     * 学期匹配逻辑 - 使用统一的ID进行精确匹配
     *
     * 设计原则：
     * 1. 使用 termId 和 id 进行精确匹配，不使用 name 或 termName
     * 2. 避免字符串匹配带来的问题（格式不一致、边界情况等）
     * 3. 三个校区的数据统一使用相同的匹配逻辑
     *
     * 为什么使用ID匹配：
     * - API返回的termName可能是"2026春季"、"2026Spring"等不同格式
     * - TermItem.name可能是"2025-2026 2026春季"，与API数据不一致
     * - 使用ID匹配可以避免所有这些格式问题
     *
     * 注意事项：
     * - 确保考试数据解析时正确设置了termId（格式：yearCode-termCode）
     * - 确保学期数据初始化时正确设置了id（格式：yearCode-termCode）
     *
     * @param item 考试项，必须包含有效的termId
     * @param term 选中的学期，必须包含有效的id
     * @return true表示该考试属于选中的学期
     */
    private fun matchTerm(item: ExamItem, term: TermItem?): Boolean {
        if (term == null) return true

        // 使用统一的termId进行匹配
        val itemTermId = item.termId
        val selectedTermId = term.id

        LogUtils.d("🔍 matchTerm: item.termId='$itemTermId', selected term.id='$selectedTermId'", "ExamViewModel")

        val result = itemTermId != null && itemTermId == selectedTermId
        LogUtils.d("🔍 matchTerm: ID匹配结果=$result", "ExamViewModel")

        return result
    }

    private fun matchType(examType: String?, type: ExamType?): Boolean {
        if (type == null || type == ExamType.ALL) return true
        val raw = (examType ?: "").trim()
        if (raw.isBlank()) return true
        return when (type) {
            ExamType.MIDTERM -> raw.contains("期中")
            ExamType.FINAL -> raw.contains("期末")
            ExamType.ALL -> true
        }
    }

    enum class ExamType {
        ALL,
        MIDTERM,
        FINAL
    }

    /**
     * 批量导入考试到默认课表的结果
     */
    data class ImportResult(
        val totalCount: Int,
        val successCount: Int,
        val skippedCount: Int
    )

    /**
     * 批量导入考试到默认课表
     *
     * 功能：
     * 1. 获取或创建默认课表
     * 2. 检查每个考试是否已导入（通过课程名称和地点匹配）
     * 3. 只导入未导入的考试
     * 4. 返回导入统计结果
     *
     * 线程安全：此方法应在后台线程调用
     *
     * @param exams 要导入的考试列表
     * @return ImportResult 包含总数、成功数、跳过数
     */
    fun importAllExams(exams: List<ExamItem>): ImportResult {
        val appDatabase = AppDatabase.getDatabase(context.applicationContext as Application)
        val eventItemDao = appDatabase.eventItemDao()
        val timetableDao = appDatabase.timetableDao()

        // 获取或创建默认课表
        val defaultTimetable = timetableDao.getFirstCustomTimetableSync()
        val timetable = if (defaultTimetable == null) {
            createDefaultTimetable(timetableDao)
        } else {
            defaultTimetable
        }

        // 获取已导入的考试事件（用于去重）
        val existingEvents = eventItemDao.getEventsOfTimetableSync(timetable.id)
            .filter { it.type == EventItem.TYPE.EXAM }

        var successCount = 0
        var skippedCount = 0

        for (exam in exams) {
            // 检查是否已导入（名称可能已添加"[考试]"前缀）
            val isDuplicate = existingEvents.any { event ->
                (event.name == exam.courseName || event.name == "[考试] ${exam.courseName}") &&
                event.place == exam.examLocation
            }

            if (isDuplicate) {
                skippedCount++
                LogUtils.d("⏭️ 跳过已导入: ${exam.courseName}", "ExamViewModel")
                continue
            }

            // 解析并导入考试
            val examEvent = parseExamToEvent(exam, timetable.id)
            if (examEvent != null) {
                eventItemDao.insertEventSync(examEvent)
                successCount++
                LogUtils.d("✅ 导入成功: ${exam.courseName}", "ExamViewModel")
            } else {
                skippedCount++
                LogUtils.e("❌ 解析失败: ${exam.courseName}", null, "ExamViewModel")
            }
        }

        LogUtils.d("📊 批量导入完成: 总数=${exams.size}, 成功=$successCount, 跳过=$skippedCount", "ExamViewModel")
        return ImportResult(
            totalCount = exams.size,
            successCount = successCount,
            skippedCount = skippedCount
        )
    }

    /**
     * 创建默认课表
     * 用于存放EAS导入的课程和AI创建的内容
     */
    private fun createDefaultTimetable(timetableDao: com.limpu.hitax.data.source.dao.TimetableDao): Timetable {
        val defaultPrefix = context.getString(com.limpu.hitax.R.string.default_timetable_name)

        // 查找现有的默认课表，确定编号
        val existingTables = timetableDao.getTimetableNamesWithDefaultSync("$defaultPrefix%")
        var maxNumber = 0
        for (tableName in existingTables) {
            val number = try {
                tableName.replace(defaultPrefix, "").trim().toInt()
            } catch (e: NumberFormatException) {
                0
            }
            if (number > maxNumber) {
                maxNumber = number
            }
        }

        // 创建新的默认课表
        val newTable = Timetable().apply {
            id = UUID.randomUUID().toString()
            name = if (maxNumber > 0) "$defaultPrefix ${maxNumber + 1}" else defaultPrefix
            code = "" // 空code表示自定义课表
            startTime = Timestamp(System.currentTimeMillis())
            createdAt = Timestamp(System.currentTimeMillis())
        }

        timetableDao.saveTimetableSync(newTable)
        LogUtils.d("✅ 创建默认课表: ${newTable.name}", "ExamViewModel")

        return newTable
    }

    /**
     * 将考试信息解析为EventItem
     *
     * 解析逻辑：
     * 1. 解析日期：examDate格式 "YYYY-MM-DD"
     * 2. 解析时间：examTime格式 "HH:MM-HH:MM"
     * 3. 创建EventItem，type设置为EXAM
     *
     * @param exam 考试信息
     * @param timetableId 课表ID
     * @return EventItem对象，解析失败返回null
     */
    private fun parseExamToEvent(exam: ExamItem, timetableId: String): EventItem? {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            // 解析日期
            val date = exam.examDate ?: return null
            val parsedDate = dateFormat.parse(date) ?: return null

            // 解析时间段 "HH:MM-HH:MM"
            val timeRange = exam.examTime ?: return null
            val times = timeRange.split("-")
            if (times.size != 2) return null

            val startTime = timeFormat.parse(times[0]) ?: return null
            val endTime = timeFormat.parse(times[1]) ?: return null

            // 组合日期和时间
            val calendarStart = Calendar.getInstance().apply {
                time = parsedDate
                set(Calendar.HOUR_OF_DAY, startTime.hours)
                set(Calendar.MINUTE, startTime.minutes)
            }

            val calendarEnd = Calendar.getInstance().apply {
                time = parsedDate
                set(Calendar.HOUR_OF_DAY, endTime.hours)
                set(Calendar.MINUTE, endTime.minutes)
            }

            // 创建EventItem
            return EventItem().apply {
                type = EventItem.TYPE.EXAM
                source = EventItem.SOURCE_EAS_IMPORT
                name = "[考试] " + (exam.courseName ?: "考试")
                place = exam.examLocation ?: ""
                teacher = "" // 考试没有教师信息
                subjectId = ""
                this.timetableId = timetableId
                from = Timestamp(calendarStart.timeInMillis)
                to = Timestamp(calendarEnd.timeInMillis)
                fromNumber = 0 // 考试不使用节次
                lastNumber = 0
            }

        } catch (e: Exception) {
            LogUtils.e("❌ 解析考试时间失败: ${e.message}", e, "ExamViewModel")
            return null
        }
    }

}
