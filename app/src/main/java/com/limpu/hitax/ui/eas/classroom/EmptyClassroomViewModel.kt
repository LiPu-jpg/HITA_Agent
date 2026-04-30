package com.limpu.hitax.ui.eas.classroom

import androidx.lifecycle.*
import com.limpu.hitax.data.model.eas.TermItem
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import com.limpu.hitax.ui.eas.EASViewModel
import com.limpu.component.data.MTransformations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EmptyClassroomViewModel @Inject constructor(
    easRepo: EASRepository,
    private val timetableRepository: TimetableRepository
) : EASViewModel(easRepo) {


    private val pageController = MutableLiveData<Trigger>()
    val termsLiveData: LiveData<DataState<List<TermItem>>> = pageController.switchMap {
        return@switchMap easRepo.getAllTerms().map { state ->
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
            return@switchMap easRepo.getTeachingBuildings()
        }
    val selectedTermLiveData = MutableLiveData<TermItem>()
    val selectedBuildingLiveData = MutableLiveData<BuildingItem>()
    val selectedWeekLiveData: MediatorLiveData<Int> =
        MTransformations.switchMap(selectedTermLiveData) { term ->
            return@switchMap timetableRepository.getCurrentWeekOfTimetable(term)
        }
    val timetableStructureLiveData: LiveData<DataState<MutableList<TimePeriodInDay>>> = selectedTermLiveData.switchMap {
        return@switchMap easRepo.getScheduleStructure(it)
    }
    val classroomLiveData: MediatorLiveData<DataState<List<ClassroomItem>>> =
        MTransformations.switchMap(timetableStructureLiveData) {
            val res = MediatorLiveData<DataState<List<ClassroomItem>>>()
            var currentQuerySource: LiveData<DataState<List<ClassroomItem>>>? = null

            fun launchClassroomQuery(term: TermItem, building: BuildingItem, week: Int) {
                currentQuerySource?.let { res.removeSource(it) }
                val source = easRepo.queryEmptyClassroom(term, building, week)
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
