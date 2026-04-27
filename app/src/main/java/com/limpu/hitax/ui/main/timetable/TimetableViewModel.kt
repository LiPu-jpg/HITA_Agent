package com.limpu.hitax.ui.main.timetable

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.MTransformations
import com.limpu.component.data.Trigger
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.data.repository.TimetableStyleRepository
import com.limpu.hitax.ui.main.timetable.TimetableFragment.Companion.WEEK_MILLS
import com.limpu.hitax.ui.main.timetable.TimetableFragment.Companion.WINDOW_SIZE
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val timetableStyleRepository: TimetableStyleRepository
) : ViewModel() {

    private val timetableController = MutableLiveData<Trigger>()
    val timetableLiveData: LiveData<List<Timetable>> = timetableController.switchMap{
            return@switchMap timetableRepository.getTimetables()
        }
    val startTimeLiveData: LiveData<Int>
        get() = timetableStyleRepository.startTimeLiveData
    val periodLabelLiveData: LiveData<Boolean>
        get() = timetableStyleRepository.periodLabelLiveData

    var currentPageStartDate: MutableLiveData<Long>
    var currentIndex = 0
    var startIndex = 0

    private val timetableStyleLiveData: LiveData<TimetableStyleSheet> =
        timetableStyleRepository.getStyleSheetLiveData()
    val windowEventsData: MutableList<MediatorLiveData<Pair<List<EventItem>, TimetableStyleSheet>>> =
        mutableListOf()
    val windowStartData: MutableList<MutableLiveData<Long>> = mutableListOf()
    val windowHashesData = mutableListOf<Int>()

    init {
        val ws = Calendar.getInstance()
        ws.firstDayOfWeek = Calendar.MONDAY
        ws[Calendar.DAY_OF_WEEK] = Calendar.MONDAY
        ws[Calendar.HOUR_OF_DAY] = 0
        ws[Calendar.MINUTE] = 0
        ws[Calendar.SECOND] = 0
        ws[Calendar.MILLISECOND] = 0
        currentPageStartDate = MutableLiveData(ws.timeInMillis)
        for (i in 0 until WINDOW_SIZE) {
            windowHashesData.add(0)
            val startLD = MutableLiveData<Long>()
            windowStartData.add(startLD)
            val eventsRawData =  startLD.switchMap{
                return@switchMap timetableRepository.getEventsDuringWithColor(
                    it,
                    it + WEEK_MILLS
                )
            }
            val eventsData = MTransformations.switchMap(eventsRawData,timetableStyleLiveData){
                return@switchMap MutableLiveData(it)
            }
            windowEventsData.add(eventsData)
        }
    }

    fun startRefresh() {
        timetableRepository.ensureDefaultCustomTimetableAsync()
        timetableController.value = Trigger.actioning
    }

    fun addStartDate(offset: Long) {
        currentPageStartDate.value?.let {
            currentPageStartDate.value = it + offset
        }
    }

}
