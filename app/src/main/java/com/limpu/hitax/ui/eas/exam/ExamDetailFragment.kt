package com.limpu.hitax.ui.eas.exam

import android.app.Application
import android.view.View
import android.widget.Toast
import com.limpu.hitax.R
import com.limpu.hitax.data.AppDatabase
import com.limpu.hitax.data.model.eas.ExamItem
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.databinding.DialogBottomExamPickerBinding
import com.limpu.hitax.utils.LogUtils
import com.limpu.style.widgets.TransparentBottomSheetDialog
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class ExamDetailFragment (
    private val Exam: ExamItem
): TransparentBottomSheetDialog<DialogBottomExamPickerBinding>(){
    override fun getLayoutId(): Int {
        return R.layout.dialog_bottom_exam_picker
    }

    override fun initViewBinding(v: View): DialogBottomExamPickerBinding {
        return DialogBottomExamPickerBinding.bind(v)
    }

    override fun initViews(v: View) {
        binding.title.text = Exam.courseName
        binding.examCampus.text = Exam.campusName
        binding.examLocation.text = Exam.examLocation
        binding.examTerm.text = Exam.termName
        binding.examTime.text = Exam.examTime
        binding.examType.text = Exam.examType

        // 设置导入按钮点击事件
        binding.btnImportToTimetable.setOnClickListener {
            importExamToTimetable()
        }
    }

    /**
     * 导入考试到默认课表
     *
     * 功能：
     * 1. 获取或创建默认课表
     * 2. 解析考试日期和时间，创建EventItem
     * 3. 检查是否已经存在相同的考试事件
     * 4. 保存到数据库，避免重复导入
     *
     * 设计说明：
     * - 默认课表用于存放EAS导入的课程和AI创建的内容
     * - 避免与用户的自定义课表互相污染
     * - 保持自定义课表的干净和独立
     */
    private fun importExamToTimetable() {
        val context = requireContext()
        Toast.makeText(context, "正在导入...", Toast.LENGTH_SHORT).show()

        // 在后台线程执行数据库操作
        Thread {
            try {
                val appDatabase = AppDatabase.getDatabase(context.applicationContext as Application)
                val eventItemDao = appDatabase.eventItemDao()
                val timetableDao = appDatabase.timetableDao()

                // 获取或创建默认课表
                val defaultTimetable = timetableDao.getFirstCustomTimetableSync()
                val timetable = if (defaultTimetable == null) {
                    // 创建默认课表
                    createDefaultTimetable(timetableDao)
                } else {
                    defaultTimetable
                }

                // 导入考试事件
                val result = importExamEvent(timetable, eventItemDao)

                // 切换到主线程显示结果
                requireActivity().runOnUiThread {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    if (result.success) {
                        LogUtils.d("✅ ${result.message}", "ExamDetailFragment")
                        dismiss()
                    } else {
                        LogUtils.e("❌ ${result.message}", null, "ExamDetailFragment")
                    }
                }

            } catch (e: Exception) {
                LogUtils.e("❌ 导入考试失败: ${e.message}", e, "ExamDetailFragment")
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * 导入结果
     */
    private data class ImportResult(
        val success: Boolean,
        val message: String
    )

    /**
     * 创建默认课表
     * 用于存放EAS导入的课程和AI创建的内容
     */
    private fun createDefaultTimetable(timetableDao: com.limpu.hitax.data.source.dao.TimetableDao): Timetable {
        val defaultPrefix = requireContext().getString(R.string.default_timetable_name)

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
        LogUtils.d("✅ 创建默认课表: ${newTable.name}", "ExamDetailFragment")

        return newTable
    }

    /**
     * 导入考试事件到指定课表
     */
    private fun importExamEvent(timetable: Timetable, eventItemDao: com.limpu.hitax.data.source.dao.EventItemDao): ImportResult {
        // 检查是否已经存在相同的考试事件（名称可能已添加"[考试]"前缀）
        val existingEvents = eventItemDao.getEventsOfTimetableSync(timetable.id)
        val isDuplicate = existingEvents.any { event ->
            event.type == EventItem.TYPE.EXAM &&
            (event.name == Exam.courseName || event.name == "[考试] ${Exam.courseName}") &&
            event.place == Exam.examLocation
        }

        if (isDuplicate) {
            return ImportResult(false, "该考试已导入默认课表")
        }

        // 解析考试日期和时间
        val examEvent = parseExamToEvent(Exam, timetable.id)

        if (examEvent == null) {
            return ImportResult(false, "考试时间格式解析失败")
        }

        // 保存到数据库
        eventItemDao.insertEventSync(examEvent)
        return ImportResult(true, "已导入到默认课表: ${timetable.name}")
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
            LogUtils.e("❌ 解析考试时间失败: ${e.message}", e, "ExamDetailFragment")
            return null
        }
    }
}