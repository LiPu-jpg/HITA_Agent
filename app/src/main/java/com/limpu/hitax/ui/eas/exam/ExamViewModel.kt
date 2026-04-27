package com.limpu.hitax.ui.eas.exam

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.MTransformations
import com.limpu.component.data.Trigger
import com.limpu.hitax.data.model.eas.ExamItem
import com.limpu.hitax.data.model.eas.TermItem
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.source.preference.EasPreferenceSource
import com.limpu.hitax.data.source.preference.TimetablePreferenceSource
import com.limpu.hitax.data.source.local.ExamMemoStore
import com.limpu.hitax.ui.eas.EASViewModel

class ExamViewModel(application: Application) : EASViewModel(application){
    /**
     * 仓库区
     */
    private val easRepository = EASRepository(application, EasPreferenceSource(application.applicationContext), TimetablePreferenceSource(application.applicationContext))
    private val memoStore = ExamMemoStore(application)

    /**
     * LiveData区
     */
    private val pageController = MutableLiveData<Trigger>()

    val termsLiveData: LiveData<DataState<List<TermItem>>> = pageController.switchMap {
        return@switchMap easRepository.getAllTerms()
    }

    val selectedTermLiveData: MutableLiveData<TermItem> = MutableLiveData()
    val selectedExamTypeLiveData: MutableLiveData<ExamType> = MutableLiveData()

    private val rawExamLiveData = MutableLiveData<DataState<List<ExamItem>>>()

    private val filterLiveData =
        MTransformations.map(selectedTermLiveData, selectedExamTypeLiveData) { it }

    val examInfoLiveData: LiveData<DataState<List<ExamItem>>> =
        MTransformations.map(rawExamLiveData, filterLiveData) { pair ->
            val state = pair.first
            val term = pair.second.first
            val type = pair.second.second
            val data = state.data
            if (state.state != DataState.STATE.SUCCESS || data.isNullOrEmpty()) {
                return@map state
            }
            val filtered = data.filter { item ->
                matchTerm(item, term) && matchType(item.examType, type)
            }
            DataState(filtered, state.state)
        }

    /**
     * 方法区
     */
    fun startRefresh() {
        pageController.value = Trigger.actioning
        rawExamLiveData.value = DataState(memoStore.load(), DataState.STATE.SUCCESS)
    }

    fun addMemo(item: ExamItem) {
        rawExamLiveData.value = DataState(memoStore.add(item), DataState.STATE.SUCCESS)
    }

    fun deleteMemo(memoId: String) {
        rawExamLiveData.value = DataState(memoStore.delete(memoId), DataState.STATE.SUCCESS)
    }

    private fun matchTerm(item: ExamItem, term: TermItem?): Boolean {
        if (term == null) return true
        val raw = (item.termName ?: "").trim()
        if (raw.isBlank()) {
            val examDate = (item.examDate ?: "").trim()
            val year = Regex("(\\d{4})").find(examDate)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (year != null) {
                val years = Regex("(\\d{4})").findAll(term.yearCode).mapNotNull { it.value.toIntOrNull() }.toList()
                if (years.isNotEmpty()) {
                    val min = years.minOrNull() ?: year
                    val max = years.maxOrNull() ?: year
                    return year in min..max
                }
            }
            return true
        }
        val compact = raw.replace("\\s".toRegex(), "")
        val targetName = term.name.replace("\\s".toRegex(), "")
        if (compact == targetName) return true
        val code = term.getCode().replace("\\s".toRegex(), "")
        if (compact == code) return true
        val yearName = term.yearName.replace("\\s".toRegex(), "")
        val termName = term.termName.replace("\\s".toRegex(), "")
        return (yearName.isNotBlank() && compact.contains(yearName) && compact.contains(termName))
    }

    private fun matchType(examType: String?, type: ExamType?): Boolean {
        if (type == null || type == ExamType.ALL) return true
        val raw = (examType ?: "").trim()
        if (raw.isBlank()) return true
        return when (type) {
            ExamType.MIDTERM -> raw.contains("期中")
            ExamType.FINAL -> raw.contains("期末")
            ExamType.ALL -> true
        }
    }

    enum class ExamType {
        ALL,
        MIDTERM,
        FINAL
    }

}
