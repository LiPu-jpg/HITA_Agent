package com.limpu.hitax.ui.event.add

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.model.timetable.TimeInDay
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.SubjectRepository
import com.limpu.hitax.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.sql.Timestamp
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AddEventViewModel @Inject constructor(
    private val eventRepo: TimetableRepository,
    private val subjectRepo: SubjectRepository
) : ViewModel() {
    enum class AddMode {
        BATCH_PERIOD,
        FREE_RANGE
    }

    val addModeLiveData = MutableLiveData(AddMode.BATCH_PERIOD)
    val timetableLiveData = MutableLiveData<DataState<Timetable>>()
    val subjectLiveData = MediatorLiveData<DataState<TermSubject>>()
    val timeRangeLiveDate = MediatorLiveData<DataState<CourseTime>>()
    val customDateLiveData = MutableLiveData<DataState<Long>>()
    val customTimePeriodLiveData = MutableLiveData<DataState<TimePeriodInDay>>()
    val customFromToLiveData = MediatorLiveData<DataState<Pair<Long, Long>>>()
    val nameLiveData = MediatorLiveData<String?>()

    val locationLiveData = MediatorLiveData<DataState<String>>()
    val teacherLiveData = MediatorLiveData<DataState<String>>()

    val doneLiveData = MediatorLiveData<Boolean>()

    var addSubject: Boolean = false

    init {
        doneLiveData.addSource(addModeLiveData) {
            checkDone()
        }
        doneLiveData.addSource(subjectLiveData) {
            checkDone()
        }
        doneLiveData.addSource(nameLiveData) {
            checkDone()
        }
        doneLiveData.addSource(timetableLiveData) {
            checkDone()
        }
        doneLiveData.addSource(timeRangeLiveDate) {
            checkDone()
        }
        doneLiveData.addSource(customFromToLiveData) {
            checkDone()
        }

        timeRangeLiveDate.addSource(timetableLiveData) {
            if (it.state == DataState.STATE.SUCCESS) {
                if (initCourseT != null) {
                    timeRangeLiveDate.value = DataState(initCourseT!!)
                    initCourseT = null
                } else {
                    timeRangeLiveDate.value = DataState(DataState.STATE.NOTHING)
                }
            } else {
                timeRangeLiveDate.value = DataState(DataState.STATE.FETCH_FAILED)
            }
        }

        customDateLiveData.value = DataState(DataState.STATE.NOTHING)
        customTimePeriodLiveData.value = DataState(DataState.STATE.NOTHING)

        customFromToLiveData.addSource(customDateLiveData) {
            refreshCustomFromTo()
        }
        customFromToLiveData.addSource(customTimePeriodLiveData) {
            refreshCustomFromTo()
        }

        subjectLiveData.addSource(timeRangeLiveDate) {
            refreshSubjectState()
        }
        subjectLiveData.addSource(addModeLiveData) {
            refreshSubjectState()
        }

        teacherLiveData.addSource(subjectLiveData) {
            if (it.state == DataState.STATE.SUCCESS
                || it.state == DataState.STATE.SPECIAL
                || it.state == DataState.STATE.NOTHING
            ) {
                if (teacherLiveData.value?.state != DataState.STATE.SUCCESS) {
                    teacherLiveData.value = DataState(DataState.STATE.NOTHING)
                }
            } else {
                teacherLiveData.value = DataState(DataState.STATE.FETCH_FAILED)
            }
        }
        locationLiveData.addSource(subjectLiveData) {
            if (it.state == DataState.STATE.SUCCESS
                || it.state == DataState.STATE.SPECIAL
                || it.state == DataState.STATE.NOTHING
            ) {
                if (locationLiveData.value?.state != DataState.STATE.SUCCESS) {
                    locationLiveData.value = DataState(DataState.STATE.NOTHING)
                }
            } else {
                locationLiveData.value = DataState(DataState.STATE.FETCH_FAILED)
            }
        }
    }

    fun setAddMode(mode: AddMode) {
        if (addModeLiveData.value == mode) return
        addModeLiveData.value = mode
        checkDone()
    }

    private fun refreshCustomFromTo() {
        val date = customDateLiveData.value
        val period = customTimePeriodLiveData.value
        if (date?.state == DataState.STATE.SUCCESS && period?.state == DataState.STATE.SUCCESS) {
            val cFrom = Calendar.getInstance()
            cFrom.timeInMillis = date.data ?: 0L
            cFrom.set(Calendar.HOUR_OF_DAY, period.data?.from?.hour ?: 0)
            cFrom.set(Calendar.MINUTE, period.data?.from?.minute ?: 0)
            cFrom.set(Calendar.SECOND, 0)
            cFrom.set(Calendar.MILLISECOND, 0)

            val cTo = Calendar.getInstance()
            cTo.timeInMillis = date.data ?: 0L
            cTo.set(Calendar.HOUR_OF_DAY, period.data?.to?.hour ?: 0)
            cTo.set(Calendar.MINUTE, period.data?.to?.minute ?: 0)
            cTo.set(Calendar.SECOND, 0)
            cTo.set(Calendar.MILLISECOND, 0)

            if (cTo.timeInMillis > cFrom.timeInMillis) {
                customFromToLiveData.value = DataState(Pair(cFrom.timeInMillis, cTo.timeInMillis))
            } else {
                customFromToLiveData.value = DataState(DataState.STATE.NOTHING)
            }
        } else if (date?.state == DataState.STATE.FETCH_FAILED || period?.state == DataState.STATE.FETCH_FAILED) {
            customFromToLiveData.value = DataState(DataState.STATE.FETCH_FAILED)
        } else {
            customFromToLiveData.value = DataState(DataState.STATE.NOTHING)
        }
    }

    private fun refreshSubjectState() {
        val mode = addModeLiveData.value ?: AddMode.BATCH_PERIOD
        val timeState = timeRangeLiveDate.value?.state
        if (mode == AddMode.BATCH_PERIOD && timeState != DataState.STATE.SUCCESS) {
            subjectLiveData.value = DataState(DataState.STATE.FETCH_FAILED)
            return
        }
        if (addSubject) {
            subjectLiveData.value = DataState(DataState.STATE.SPECIAL)
            return
        }
        if (initSubject != null) {
            subjectLiveData.value = DataState(initSubject!!)
            initSubject = null
            return
        }
        if (subjectLiveData.value?.state != DataState.STATE.SUCCESS
            || subjectLiveData.value?.data?.timetableId != timetableLiveData.value?.data?.id
        ) {
            subjectLiveData.value = DataState(DataState.STATE.NOTHING)
        }
    }

    private fun checkDone() {
        val mode = addModeLiveData.value ?: AddMode.BATCH_PERIOD
        val baseReady = timetableLiveData.value?.state == DataState.STATE.SUCCESS
            && !nameLiveData.value.isNullOrBlank()
        val done = when (mode) {
            AddMode.BATCH_PERIOD -> {
                baseReady
                    && timeRangeLiveDate.value?.state == DataState.STATE.SUCCESS
            }

            AddMode.FREE_RANGE -> {
                baseReady && customFromToLiveData.value?.state == DataState.STATE.SUCCESS
            }
        }
        doneLiveData.value = done
    }

    var initSubject: TermSubject? = null
    var initCourseT: CourseTime? = null
    fun init(
        addSubject: Boolean,
        timetable: Timetable?,
        subject: TermSubject?,
        courseTime: CourseTime?
    ) {
        initCourseT = courseTime
        initSubject = subject
        this.addSubject = addSubject

        if (timetable == null) {
            timetableLiveData.value = DataState(DataState.STATE.NOTHING)
        } else {
            timetableLiveData.value = DataState(timetable)
        }

        if (courseTime == null) {
            customTimePeriodLiveData.value = DataState(DataState.STATE.NOTHING)
        } else {
            customTimePeriodLiveData.value = DataState(courseTime.period.clone())
        }
    }

    fun setCustomDate(dateMs: Long) {
        customDateLiveData.value = DataState(dateMs)
    }

    fun setCustomTimePeriod(from: TimeInDay, to: TimeInDay) {
        customTimePeriodLiveData.value = DataState(TimePeriodInDay(from, to))
    }

    fun createEvent() {
        var maxEndTime: Long = 0
        val data = mutableListOf<EventItem>()

        timetableLiveData.value?.data?.let { timetable ->
            when (addModeLiveData.value ?: AddMode.BATCH_PERIOD) {
                AddMode.BATCH_PERIOD -> {
                    var subject: TermSubject?
                    if (addSubject) {
                        subject = TermSubject()
                        subject.name = nameLiveData.value ?: ""
                        subject.timetableId = timetable.id
                        if (addSubject) subjectRepo.actionSaveSubjectInfo(subject)
                    } else {
                        subject = subjectLiveData.value?.data
                    }
                    timeRangeLiveDate.value?.data?.let { range ->
                        subject?.let {
                            for (w in range.weeks) {
                                val ei = EventItem()
                                ei.type = EventItem.TYPE.CLASS
                                ei.source = EventItem.SOURCE_MANUAL
                                ei.name = nameLiveData.value?.trim() ?: ""
                                ei.timetableId = timetable.id
                                ei.subjectId = subject.id
                                ei.place = locationLiveData.value?.data ?: ""
                                ei.teacher = teacherLiveData.value?.data ?: ""
                                val se = timetable.getTimestamps(w, range.dow, range.period)
                                ei.from = Timestamp(se[0])
                                ei.to = Timestamp(se[1])
                                val nums = timetable.transformCourseNumber(range.period)
                                ei.fromNumber = nums.first
                                ei.lastNumber = nums.second - nums.first + 1
                                maxEndTime = maxEndTime.coerceAtLeast(ei.to.time)
                                data.add(ei)
                            }
                        }
                    }
                }

                AddMode.FREE_RANGE -> {
                    customFromToLiveData.value?.data?.let { fromTo ->
                        val ei = EventItem()
                        ei.type = EventItem.TYPE.OTHER
                        ei.source = EventItem.SOURCE_MANUAL
                        ei.name = nameLiveData.value?.trim() ?: ""
                        ei.timetableId = timetable.id
                        ei.subjectId = ""
                        ei.place = locationLiveData.value?.data ?: ""
                        ei.teacher = ""
                        ei.from = Timestamp(fromTo.first)
                        ei.to = Timestamp(fromTo.second)
                        ei.fromNumber = 0
                        ei.lastNumber = 0
                        maxEndTime = maxEndTime.coerceAtLeast(ei.to.time)
                        data.add(ei)
                    }
                }
            }
        }

        if (data.isEmpty()) return

        eventRepo.actionAddEvents(data)
        timetableLiveData.value?.data?.let { timetable ->
            if (maxEndTime > timetable.endTime.time) {
                val c = Calendar.getInstance()
                c.timeInMillis = maxEndTime
                c.firstDayOfWeek = Calendar.MONDAY
                c.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                c.set(Calendar.HOUR_OF_DAY, 23)
                c.set(Calendar.MINUTE, 59)
                timetable.endTime.time = c.timeInMillis
                eventRepo.actionSaveTimetable(timetable)
            }
        }
    }
}
