package com.limpu.hitax.ui.resource

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.resource.CourseReadmeData
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.repository.HoaRepository

class CourseReadmeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HoaRepository.getInstance()
    private val easRepository = EASRepository.getInstance(application)
    private val hoaCampus = easRepository.getHoaCampus()
    private val repoNameLiveData = MutableLiveData<String>()

    val readmeLiveData: LiveData<DataState<CourseReadmeData>> = repoNameLiveData.switchMap {
        repository.getCourseReadme(it, hoaCampus)
    }

    fun load(repoName: String) {
        repoNameLiveData.value = repoName
    }
}