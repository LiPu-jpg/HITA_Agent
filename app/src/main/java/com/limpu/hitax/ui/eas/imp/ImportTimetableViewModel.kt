package com.limpu.hitax.ui.eas.imp

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import com.limpu.hitax.data.model.eas.TermItem
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.source.preference.BenbuStartDatePreferenceSource
import com.limpu.hitax.ui.eas.EASViewModel
import java.util.Calendar

class ImportTimetableViewModel(application: Application) : EASViewModel(application) {
    private val easRepository = EASRepository.getInstance(application)
    private val benbuStartDatePreference = BenbuStartDatePreferenceSource.getInstance(application)

    private val termsController = MutableLiveData<Trigger>()
    private var startDateSource: LiveData<DataState<Calendar>>? = null

    val termsLiveData: LiveData<DataState<List<TermItem>>> = termsController.switchMap {
        easRepository.getAllTerms()
    }

    val selectedTermLiveData: MutableLiveData<TermItem?> = MutableLiveData()
    val startDateLiveData = MediatorLiveData<DataState<Calendar>>()
    val benbuCalibrationConfirmedLiveData = MediatorLiveData<Boolean>()
    val importTimetableResultLiveData = MediatorLiveData<DataState<Boolean>>()
    val isUndergraduateLiveData = MutableLiveData<Boolean>()
    val scheduleStructureLiveData: MediatorLiveData<DataState<MutableList<TimePeriodInDay>>> =
        MediatorLiveData()

    init {
        startDateLiveData.value = DataState(Calendar.getInstance())
        benbuCalibrationConfirmedLiveData.value = true

        startDateLiveData.addSource(selectedTermLiveData) { term ->
            startDateSource?.let { startDateLiveData.removeSource(it) }
            if (term == null) {
                startDateLiveData.value = DataState(Calendar.getInstance())
                benbuCalibrationConfirmedLiveData.value = true
                return@addSource
            }
            val source = easRepository.getStartDateOfTerm(term)
            startDateSource = source
            startDateLiveData.addSource(source) { state ->
                startDateLiveData.value = resolveStartDateState(term, state)
                benbuCalibrationConfirmedLiveData.value = isBenbuCalibrationConfirmed(term)
            }
        }

        benbuCalibrationConfirmedLiveData.addSource(selectedTermLiveData) { term ->
            benbuCalibrationConfirmedLiveData.value = term?.let { isBenbuCalibrationConfirmed(it) } ?: true
        }

        scheduleStructureLiveData.addSource(selectedTermLiveData) {
            isUndergraduateLiveData.value?.let { isu ->
                scheduleStructureLiveData.addSource(
                    easRepository.getScheduleStructure(
                        it!!,
                        isu
                    )
                ) { itt ->
                    scheduleStructureLiveData.value = itt
                }
            }
        }
        scheduleStructureLiveData.addSource(isUndergraduateLiveData) {
            selectedTermLiveData.value?.let { st ->
                scheduleStructureLiveData.addSource(
                    easRepository.getScheduleStructure(
                        st, it
                    )
                ) { itt ->
                    scheduleStructureLiveData.value = itt
                }
            }
        }
    }

    fun startRefreshTerms() {
        termsController.value = Trigger.actioning
    }

    fun changeSelectedTerm(termItem: TermItem) {
        selectedTermLiveData.value = termItem
    }

    fun changeIsUndergraduate(isUnder: Boolean) {
        isUndergraduateLiveData.value = isUnder
    }

    fun startGetAllTerms(): List<TermItem> {
        return termsLiveData.value?.data ?: listOf()
    }

    fun startImportTimetable(): Boolean {
        selectedTermLiveData.value?.let { term ->
            startDateLiveData.value?.let { date ->
                scheduleStructureLiveData.value?.let { schedule ->
                    if (schedule.data != null && date.state == DataState.STATE.SUCCESS && date.data != null) {
                        easRepository.startImportTimetableOfTerm(
                            term,
                            date.data!!,
                            schedule.data!!,
                            importTimetableResultLiveData
                        )
                        return true
                    }
                }
            }
        }
        return false
    }

    fun retryImportTimetable(): Boolean {
        return startImportTimetable()
    }


    fun setStructureData(periodInDay: TimePeriodInDay, position: Int) {
        if (position < (scheduleStructureLiveData.value?.data?.size ?: 0)) {
            scheduleStructureLiveData.value?.data?.set(position, periodInDay)
            scheduleStructureLiveData.value = scheduleStructureLiveData.value
        }
    }

    fun changeStartDate(date: Calendar) {
        startDateLiveData.value = DataState(cloneCalendar(date))
    }

    fun shiftStartDateByWeek(offsetWeeks: Int) {
        val current = startDateLiveData.value?.data ?: return
        val shifted = cloneCalendar(current).apply {
            add(Calendar.DAY_OF_MONTH, offsetWeeks * 7)
        }
        startDateLiveData.value = DataState(shifted)
    }

    fun saveBenbuCalibration() {
        val term = selectedTermLiveData.value ?: return
        val date = startDateLiveData.value?.data ?: return
        if (!isBenbuTerm(term)) return
        benbuStartDatePreference.saveCalibration(term.getCode(), date.timeInMillis, true)
        benbuCalibrationConfirmedLiveData.value = true
    }

    fun isBenbuTerm(term: TermItem? = selectedTermLiveData.value): Boolean {
        return term != null && easRepository.getEasToken().isBenbuCampus()
    }

    private fun resolveStartDateState(term: TermItem, state: DataState<Calendar>): DataState<Calendar> {
        val sourceDate = state.data ?: return state
        val resolved = cloneCalendar(sourceDate)
        if (isBenbuTerm(term)) {
            benbuStartDatePreference.getStartDateMillis(term.getCode())?.let {
                resolved.timeInMillis = it
            }
        }
        return DataState(resolved, state.state).apply {
            message = state.message
        }
    }

    private fun isBenbuCalibrationConfirmed(term: TermItem): Boolean {
        return !isBenbuTerm(term) || benbuStartDatePreference.isConfirmed(term.getCode())
    }

    private fun cloneCalendar(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
