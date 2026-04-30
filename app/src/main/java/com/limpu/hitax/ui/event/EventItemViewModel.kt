package com.limpu.hitax.ui.event

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.repository.SubjectRepository
import com.limpu.hitax.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EventItemViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository,
    private val timetableRepository: TimetableRepository
) : ViewModel() {

    /**
     * 数据区
     */
    val eventItemLiveData: MutableLiveData<EventItem> = MutableLiveData()

    val progressLiveData: LiveData<Pair<Int, Int>> = eventItemLiveData.switchMap {
        return@switchMap subjectRepository.getProgressOfSubject(it.subjectId, it.from.time)
    }

    val subjectLiveData: LiveData<TermSubject> = eventItemLiveData.switchMap {
        subjectRepository.getSubjectById(it.subjectId)
    }

    fun changeSubjectColor(color: Int) {
        subjectLiveData.value?.let { subject ->
            subjectRepository.actionChangeSubjectColor(subject.id, color)
        }
    }

    fun delete(){
        eventItemLiveData.value?.let {
            timetableRepository.actionDeleteEvents(listOf(it))
        }

    }
}
