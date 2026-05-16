package com.limpu.hitax.ui.resource

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.resource.ExternalCourseItem
import com.limpu.hitax.data.model.resource.ExternalResourceEntry
import com.limpu.hitax.data.model.resource.ResourceSource
import com.limpu.hitax.data.repository.ExternalResourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ExternalResourceSearchViewModel @Inject constructor(
    private val repository: ExternalResourceRepository,
) : ViewModel() {

    private val queryLiveData = MutableLiveData<String>()
    private val browseLiveData = MutableLiveData<Pair<String, ResourceSource>>()

    val searchResults: LiveData<DataState<List<ExternalCourseItem>>> = queryLiveData.switchMap {
        repository.searchCourses(it)
    }

    val browseResults: LiveData<DataState<List<ExternalResourceEntry>>> = browseLiveData.switchMap { (path, source) ->
        repository.listDirectory(path, source)
    }

    fun search(query: String) {
        if (query.isBlank()) return
        queryLiveData.value = query.trim()
    }

    fun browse(path: String, source: ResourceSource) {
        browseLiveData.value = Pair(path, source)
    }
}
