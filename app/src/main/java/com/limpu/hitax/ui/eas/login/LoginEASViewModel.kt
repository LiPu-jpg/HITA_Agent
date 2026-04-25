package com.limpu.hitax.ui.eas.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.data.repository.EASRepository

class LoginEASViewModel(application: Application) : AndroidViewModel(application){

    /**
     * 仓库区
     */
    private val easRepository = EASRepository.getInstance(application)

    /**
     * LiveData区
     */
    private val loginController:MutableLiveData<LoginTrigger> = MutableLiveData()
    val loginResultLiveData:LiveData<DataState<Boolean>>
        get() {
            return  loginController.switchMap{
                return@switchMap easRepository.login(it.username, it.password, it.campus)
            }
        }


    /**
     * 方法区
     */

    fun startLogin(username:String,password:String, campus: EASToken.Campus){
        loginController.value = LoginTrigger.getActioning(username, password, campus, null)
    }

}