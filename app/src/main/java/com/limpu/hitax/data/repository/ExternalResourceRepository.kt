package com.limpu.hitax.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.resource.ExternalCourseItem
import com.limpu.hitax.data.model.resource.ExternalResourceEntry
import com.limpu.hitax.data.model.resource.ResourceSource
import com.limpu.hitax.data.source.web.FireworksWebSource
import com.limpu.hitax.data.source.web.HITCSWebSource
import javax.inject.Inject

class ExternalResourceRepository @Inject constructor() {

    fun searchCourses(query: String): LiveData<DataState<List<ExternalCourseItem>>> {
        val mediator = MediatorLiveData<DataState<List<ExternalCourseItem>>>()
        var hitcsResult: List<ExternalCourseItem>? = null
        var fireworksResult: List<ExternalCourseItem>? = null
        var hitcsFailed = false
        var fireworksFailed = false

        val hitcsLive = HITCSWebSource.searchCourses(query)
        val fireworksLive = FireworksWebSource.searchCourses(query)

        fun mergeAndPost() {
            val hitcsDone = hitcsResult != null || hitcsFailed
            val fireworksDone = fireworksResult != null || fireworksFailed
            if (!hitcsDone || !fireworksDone) return

            val merged = mutableListOf<ExternalCourseItem>()
            hitcsResult?.let { merged.addAll(it) }
            fireworksResult?.let { merged.addAll(it) }
            merged.sortBy { it.courseName }

            if (merged.isNotEmpty()) {
                mediator.value = DataState(merged, DataState.STATE.SUCCESS)
            } else if (hitcsFailed && fireworksFailed) {
                mediator.value = DataState(DataState.STATE.FETCH_FAILED, "所有数据源均不可用")
            } else {
                mediator.value = DataState(merged, DataState.STATE.SUCCESS)
            }
        }

        mediator.addSource(hitcsLive) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                hitcsResult = state.data ?: emptyList()
            } else {
                hitcsFailed = true
            }
            mergeAndPost()
        }

        mediator.addSource(fireworksLive) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                fireworksResult = state.data ?: emptyList()
            } else {
                fireworksFailed = true
            }
            mergeAndPost()
        }

        return mediator
    }

    fun listDirectory(
        path: String,
        source: ResourceSource,
    ): LiveData<DataState<List<ExternalResourceEntry>>> {
        return when (source) {
            ResourceSource.HITCS -> HITCSWebSource.listDirectory(path)
            ResourceSource.FIREWORKS -> FireworksWebSource.listDirectory(path)
        }
    }
}
