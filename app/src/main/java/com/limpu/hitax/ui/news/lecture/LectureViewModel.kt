package com.limpu.hitax.ui.news.lecture

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.repository.AdditionalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LectureViewModel @Inject constructor(
    private val additionalRepo: AdditionalRepository
) : ViewModel() {

    val pageSize = 10

    val pageOffset = MutableLiveData<Pair<Int, Boolean>>()
    val listData = pageOffset.switchMap{ trigger ->
        return@switchMap additionalRepo.getLectures(pageSize, trigger.first).map{
            if (trigger.second) { //loadMore
                it.listAction = DataState.LIST_ACTION.APPEND
            } else {
                it.listAction = DataState.LIST_ACTION.REPLACE_ALL
            }
            return@map it
        }
    }


    fun refresh() {
        pageOffset.value = Pair(0, false)
    }

    fun loadMore() {
        pageOffset.value = Pair((pageOffset.value?.first ?: 0) + pageSize, true)
    }
}