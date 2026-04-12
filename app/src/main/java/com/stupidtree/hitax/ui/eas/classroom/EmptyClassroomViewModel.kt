package com.stupidtree.hitax.ui.eas.classroom

import android.app.Application
import androidx.lifecycle.*
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.data.model.timetable.TimePeriodInDay
import com.stupidtree.hitax.data.repository.EASRepository
import com.stupidtree.hitax.data.repository.TimetableRepository
import com.stupidtree.component.data.DataState
import com.stupidtree.component.data.Trigger
import com.stupidtree.hitax.ui.eas.EASViewModel
import com.stupidtree.component.data.MTransformations

class EmptyClassroomViewModel(application: Application) : EASViewModel(application) {
    /**
     * 仓库区
     */
    private val easRepository = EASRepository.getInstance(application)
    private val timetableRepository = TimetableRepository.getInstance(application)


    private val pageController = MutableLiveData<Trigger>()
    val termsLiveData: LiveData<DataState<List<TermItem>>> = pageController.switchMap {
        return@switchMap easRepository.getAllTerms().map { state ->
            val data = state.data
            if (state.state != DataState.STATE.SUCCESS || data.isNullOrEmpty()) {
                return@map state
            }
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
            val filtered = data.filter { term ->
                term.yearCode.contains(currentYear) || term.yearName.contains(currentYear) || term.name.contains(currentYear)
            }
            val finalList = if (filtered.isNotEmpty()) filtered else data
            DataState(finalList, state.state)
        }
    }
    val buildingsLiveData: LiveData<DataState<List<BuildingItem>>> = pageController.switchMap {
            return@switchMap easRepository.getTeachingBuildings()
        }
    val selectedTermLiveData = MutableLiveData<TermItem>()
    val selectedBuildingLiveData = MutableLiveData<BuildingItem>()
    val selectedWeekLiveData: MediatorLiveData<Int> =
        MTransformations.switchMap(selectedTermLiveData) { term ->
            return@switchMap timetableRepository.getCurrentWeekOfTimetable(term)
        }
    val timetableStructureLiveData: LiveData<DataState<MutableList<TimePeriodInDay>>> = selectedTermLiveData.switchMap {
        return@switchMap easRepository.getScheduleStructure(it)
    }
    val classroomLiveData: MediatorLiveData<DataState<List<ClassroomItem>>> =
        MTransformations.switchMap(timetableStructureLiveData) {
            val res = MediatorLiveData<DataState<List<ClassroomItem>>>()
            var currentQuerySource: LiveData<DataState<List<ClassroomItem>>>? = null

            fun launchClassroomQuery(term: TermItem, building: BuildingItem, week: Int) {
                currentQuerySource?.let { res.removeSource(it) }
                val source = easRepository.queryEmptyClassroom(term, building, week)
                currentQuerySource = source
                res.addSource(source) { state ->
                    res.value = state
                }
            }

            res.addSource(selectedWeekLiveData) { week ->
                selectedTermLiveData.value?.let { term ->
                    selectedBuildingLiveData.value?.let { building ->
                        launchClassroomQuery(term, building, week)
                    }
                }
            }
            res.addSource(selectedBuildingLiveData) { building ->
                selectedTermLiveData.value?.let { term ->
                    val week = selectedWeekLiveData.value ?: 1
                    launchClassroomQuery(term, building, week)
                }
            }
            return@switchMap res
        }


    fun startRefresh() {
        pageController.value = Trigger.actioning
    }

    fun retryCurrentQuery(): Boolean {
        val term = selectedTermLiveData.value ?: return false
        val building = selectedBuildingLiveData.value ?: return false
        val week = selectedWeekLiveData.value ?: return false
        selectedTermLiveData.value = term
        selectedBuildingLiveData.value = building
        selectedWeekLiveData.value = week
        return true
    }
}
