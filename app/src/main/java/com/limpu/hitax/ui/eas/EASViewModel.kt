package com.limpu.hitax.ui.eas

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.utils.LiveDataUtils

abstract class EASViewModel(protected val easRepo: EASRepository) : ViewModel() {


    private val loginCheckController = MutableLiveData<Trigger>()
    val loginCheckResult:LiveData<DataState<Boolean>> = loginCheckController.switchMap{
        if(it.isActioning){
            return@switchMap easRepo.loginCheck()
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