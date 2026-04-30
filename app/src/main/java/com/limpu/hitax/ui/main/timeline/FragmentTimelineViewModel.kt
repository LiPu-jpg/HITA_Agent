package com.limpu.hitax.ui.main.timeline

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.Trigger
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class FragmentTimelineViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository
) : ViewModel() {

    /**
     * 数据区
     */
    private val todayEventsController:MutableLiveData<Trigger> = MutableLiveData()
    val todayEventsLiveData:LiveData<List<EventItem>> =  todayEventsController.switchMap{
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY,0)
        now.set(Calendar.MINUTE,0)
        val from = now.timeInMillis
        now.add(Calendar.DATE,1)
        val to = now.timeInMillis
        return@switchMap timetableRepository.getEventsDuring(from,to)
    }

    val weekEventsLiveData:LiveData<List<EventItem>> = todayEventsController.switchMap{
        return@switchMap timetableRepository.getEventsAfter(System.currentTimeMillis(),4)
    }


    fun startRefresh(){
        todayEventsController.value = Trigger.actioning
    }
}