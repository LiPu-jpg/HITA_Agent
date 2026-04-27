package com.limpu.hitax.ui.resource

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.resource.CourseResourceItem
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.repository.HoaRepository
import com.limpu.hitax.data.source.preference.EasPreferenceSource
import com.limpu.hitax.data.source.preference.TimetablePreferenceSource

class CourseResourceSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HoaRepository()
    private val easRepository = EASRepository(application, EasPreferenceSource(application.applicationContext), TimetablePreferenceSource(application.applicationContext))
    private val hoaCampus = easRepository.getHoaCampus()
    private val queryLiveData = MutableLiveData<String>()

    val resultsLiveData: LiveData<DataState<List<CourseResourceItem>>> = queryLiveData.switchMap {
        repository.searchCourses(it, hoaCampus)
    }

    fun search(query: String) {
        if (query.isBlank()) return
        queryLiveData.value = query.trim()
    }
}