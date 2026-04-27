package com.limpu.hitax.ui.eas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.source.preference.EasPreferenceSource
import com.limpu.hitax.data.source.preference.TimetablePreferenceSource
import com.limpu.hitax.utils.LiveDataUtils

abstract class EASViewModel(application: Application) : AndroidViewModel(application){
    /**
     * 仓库区
     */
    private val easRepository = EASRepository(application, EasPreferenceSource(application.applicationContext), TimetablePreferenceSource(application.applicationContext))


    private val loginCheckController = MutableLiveData<Trigger>()
    val loginCheckResult:LiveData<DataState<Boolean>> = loginCheckController.switchMap{
        if(it.isActioning){
            return@switchMap easRepository.loginCheck()
        }
        return@switchMap LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOTHING))
    }

    /**
     * 方法区
     */
    fun startLoginCheck(){
        loginCheckController.value = Trigger.actioning
    }

}