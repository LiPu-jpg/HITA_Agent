package com.limpu.hitax.ui.resource

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.resource.CourseStructureSummary
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.repository.HoaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class CourseContributionViewModel @Inject constructor(
    private val repository: HoaRepository,
    private val easRepository: EASRepository
) : ViewModel() {
    private val hoaCampus = easRepository.getHoaCampus()

    private val repoNameLiveData = MutableLiveData<String>()
    val structureLiveData: LiveData<DataState<CourseStructureSummary>> = repoNameLiveData.switchMap {
        repository.getCourseStructure(it, hoaCampus)
    }

    private val submitRequestLiveData = MutableLiveData<SubmitRequest>()
    val submitLiveData: LiveData<DataState<String>> = submitRequestLiveData.switchMap {
        repository.submitOps(it.repoName, it.courseCode, it.courseName, it.repoType, it.ops, hoaCampus)
    }

    fun load(repoName: String) {
        repoNameLiveData.value = repoName
    }

    fun submit(repoName: String, courseCode: String, courseName: String, repoType: String, ops: JSONArray) {
        submitRequestLiveData.value = SubmitRequest(repoName, courseCode, courseName, repoType, ops)
    }

    data class SubmitRequest(
        val repoName: String,
        val courseCode: String,
        val courseName: String,
        val repoType: String,
        val ops: JSONArray,
    )
}