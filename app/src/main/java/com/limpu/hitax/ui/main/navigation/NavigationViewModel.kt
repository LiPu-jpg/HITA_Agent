package com.limpu.hitax.ui.main.navigation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.Trigger
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.TimetableRepository

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val timetableRepository = TimetableRepository(application)

    private val recentTimetableController = MutableLiveData<Trigger>()
    val recentTimetableLiveData: LiveData<Timetable?> = recentTimetableController.switchMap {
            return@switchMap timetableRepository.getRecentTimetable()
        }
    val timetableCountLiveData: LiveData<Int> = recentTimetableController.switchMap {
            return@switchMap timetableRepository.getTimetableCount()
        }

    fun startRefresh() {
        recentTimetableController.value = Trigger.actioning
    }
}
