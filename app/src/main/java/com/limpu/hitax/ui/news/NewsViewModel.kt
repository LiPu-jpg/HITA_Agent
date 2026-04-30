package com.limpu.hitax.ui.news

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.StringTrigger
import com.limpu.hitax.data.repository.AdditionalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val addRepo: AdditionalRepository
) : ViewModel() {

    val refreshController = MutableLiveData<StringTrigger>()
    val metaData =  refreshController.switchMap {
        return@switchMap addRepo.getNewsMeta(it.data)
    }

    fun refresh(link: String) {
        refreshController.value = StringTrigger.getActioning(link)
    }
}