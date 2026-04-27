package com.limpu.hitax.ui.profile

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.limpu.component.data.DataState
import com.limpu.component.data.StringTrigger
import com.limpu.stupiduser.data.repository.LocalUserRepository
import com.limpu.stupiduser.data.repository.ProfileRepository

/**
 * 层次：ViewModel
 * 其他用户资料页面绑定的ViewModel
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProfileRepository = ProfileRepository.getInstance(application)
    private val localUserRepository: LocalUserRepository =
        LocalUserRepository.getInstance(application)

    private var profileController = MutableLiveData<StringTrigger>()

    var userProfileLiveData =  profileController.switchMap{ input ->
        val user = localUserRepository.getLoggedInUser()
        if (user.isValid()) {
            return@switchMap repository.getUserProfile(input.data, user.token!!)
        } else {
            return@switchMap MutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
        }
    }

    fun startRefresh(id: String) {
        profileController.value = StringTrigger.getActioning(id)
    }

    fun logout(context: Context) {
        localUserRepository.logout(context)
    }
}
