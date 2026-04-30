package com.limpu.hitax.ui.resource

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.resource.CourseReadmeData
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.repository.HoaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CourseReadmeViewModel @Inject constructor(
    private val repository: HoaRepository,
    private val easRepository: EASRepository
) : ViewModel() {
    private val hoaCampus = easRepository.getHoaCampus()
    private val repoNameLiveData = MutableLiveData<String>()

    val readmeLiveData: LiveData<DataState<CourseReadmeData>> = repoNameLiveData.switchMap {
        repository.getCourseReadme(it, hoaCampus)
    }

    fun load(repoName: String) {
        repoNameLiveData.value = repoName
    }
}