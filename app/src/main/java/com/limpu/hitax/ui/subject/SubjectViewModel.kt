package com.limpu.hitax.ui.subject

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.SubjectRepository
import com.limpu.hitax.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SubjectViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository,
    private val timetableRepository: TimetableRepository
) : ViewModel() {

    /**
     * LiveData区
     */
    private val subjectController = MutableLiveData<String>()
    val subjectLiveData: LiveData<TermSubject> = subjectController.switchMap{
        return@switchMap subjectRepository.getSubjectById(it)
    }
    val timetableLiveData:LiveData<Timetable> = subjectLiveData.switchMap{
        return@switchMap timetableRepository.getTimetablesById(it.timetableId)
    }

    val classesLiveData: LiveData<List<EventItem>> = subjectController.switchMap {
        return@switchMap timetableRepository.getClassesOfSubject(it)
    }

    val teachersLiveData: LiveData<List<String>> = subjectLiveData.switchMap{
        return@switchMap subjectRepository.getTeachersOfSubject(it.timetableId, it.id)
    }


    fun startRefresh(id: String) {
        subjectController.value = id
    }

    fun startSaveSubject() {
        subjectLiveData.value?.let { it1 -> subjectRepository.actionSaveSubjectInfo(it1) }
    }

    fun deleteCourses(list:Collection<EventItem>) {
       timetableRepository.actionDeleteEvents(list)
    }
}