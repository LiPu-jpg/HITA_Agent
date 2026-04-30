package com.limpu.hitax.ui.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.Trigger
import com.limpu.hitax.data.repository.StaticRepository
import com.limpu.hitax.data.source.web.StaticWebSource

class UserAgreementViewModel(application: Application) : AndroidViewModel(application) {
    private val staticRepo = StaticRepository(application, StaticWebSource(application.applicationContext))

    private val refreshController = MutableLiveData<Trigger>()

    val userAgreementPageLiveData =  refreshController.switchMap {
        return@switchMap staticRepo.getUAPage()
    }

    val privacyPolicyPageLiveData = refreshController.switchMap {
        return@switchMap staticRepo.getPPPage()
    }

    fun refresh() {
        refreshController.value = Trigger.actioning
    }

}