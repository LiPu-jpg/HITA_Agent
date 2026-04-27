package com.limpu.hitax.data.repository

import android.app.Application
import androidx.lifecycle.LiveData
import com.limpu.component.data.DataState
import com.limpu.hitax.data.source.web.StaticWebSource
import com.limpu.hitax.data.source.web.additional.AdditionalWebSource
import com.limpu.hitax.data.source.web.service.AdditionalService

class AdditionalRepository @Suppress("UNUSED_PARAMETER") constructor(application: Application) {
    private val additionalWebSource:AdditionalService = AdditionalWebSource()

    fun getLectures(pageSize:Int,pageOffset:Int): LiveData<DataState<List<Map<String,String>>>> {
        return additionalWebSource.getLectures(pageSize,pageOffset)
    }

    fun getNewsMeta(link:String): LiveData<DataState<Map<String,String>>> {
        return additionalWebSource.getNewsMeta(link)
    }


}
