package com.limpu.hitax.ui.main.navigation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import com.limpu.hita.theta.data.repository.MessageRepository
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.stupiduser.data.repository.LocalUserRepository

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val timetableRepository = TimetableRepository.getInstance(application)
    private val localUserRepository = LocalUserRepository.getInstance(application)
    private val messageRepo = MessageRepository.getInstance(application)

    private val recentTimetableController = MutableLiveData<Trigger>()
    val recentTimetableLiveData: LiveData<Timetable?> = recentTimetableController.switchMap {
            return@switchMap timetableRepository.getRecentTimetable()
        }
    val timetableCountLiveData: LiveData<Int> = recentTimetableController.switchMap {
            return@switchMap timetableRepository.getTimetableCount()
        }


    val unreadMessageLiveData: LiveData<DataState<Int>> = recentTimetableController.switchMap {
            val lu = localUserRepository.getLoggedInUser()
            if (lu.isValid()) {
                return@switchMap messageRepo.countUnread(lu.token!!, "all")
            } else {
                return@switchMap MutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
            }
        }

    fun startRefresh() {
        recentTimetableController.value = Trigger.actioning
    }
}