package com.limpu.hitax.data.repository

import android.app.Application
import androidx.lifecycle.LiveData
import com.limpu.component.data.DataState
import com.limpu.hitax.data.source.web.StaticWebSource

class StaticRepository constructor(
    application: Application,
    private val staticWebSource: StaticWebSource
) {

    fun getAboutPage(): LiveData<DataState<String?>> {
        return staticWebSource.getAboutPage()
    }

    fun getUAPage(): LiveData<DataState<String?>> {
        return staticWebSource.getUserAgreementPage()
    }

    fun getPPPage(): LiveData<DataState<String?>> {
        return staticWebSource.getPrivacyPolicyPage()
    }

}