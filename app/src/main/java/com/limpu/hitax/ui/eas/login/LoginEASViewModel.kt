package com.limpu.hitax.ui.eas.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.ui.eas.EASViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginEASViewModel @Inject constructor(easRepo: EASRepository) : EASViewModel(easRepo) {

    /**
     * LiveData区
     */
    private val loginController:MutableLiveData<LoginTrigger> = MutableLiveData()
    val loginResultLiveData:LiveData<DataState<Boolean>>
        get() {
            return  loginController.switchMap{
                return@switchMap easRepo.login(it.username, it.password, it.campus)
            }
        }


    /**
     * 方法区
     */

    fun startLogin(username:String,password:String, campus: EASToken.Campus){
        loginController.value = LoginTrigger.getActioning(username, password, campus, null)
    }

}