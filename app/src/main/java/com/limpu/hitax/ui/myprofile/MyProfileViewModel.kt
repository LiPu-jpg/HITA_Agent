package com.limpu.hitax.ui.myprofile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.StringTrigger
import com.limpu.hitauser.data.model.UserLocal
import com.limpu.hitauser.data.model.UserProfile
import com.limpu.hitauser.data.repository.LocalUserRepository
import com.limpu.hitauser.data.repository.ProfileRepository
import com.limpu.hitax.utils.LiveDataUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MyProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val localUserRepository: LocalUserRepository
) : ViewModel() {

    private var profileController = MutableLiveData<StringTrigger>()

    var userProfileLiveData: LiveData<DataState<UserProfile>> = profileController.switchMap {
        val userLocal = localUserRepository.getLoggedInUser()
        if (userLocal.isValid()) {
            return@switchMap profileRepository.getUserProfile(userLocal.id!!, userLocal.token!!)
        } else {
            return@switchMap LiveDataUtils.getMutableLiveData(DataState<UserProfile>(DataState.STATE.NOT_LOGGED_IN))
        }
    }

    var changeNicknameResult: LiveData<DataState<String?>>? = null
        get() {
            if (field == null) {
                changeNicknameResult = changeNicknameController.switchMap { input: StringTrigger ->
                    if (input.isActioning) {
                        val userLocal = localUserRepository.getLoggedInUser()
                        if (userLocal.isValid()) {
                            return@switchMap profileRepository.changeNickname(
                                userLocal.token!!,
                                input.data
                            )
                        } else {
                            return@switchMap LiveDataUtils.getMutableLiveData(
                                DataState(DataState.STATE.NOT_LOGGED_IN)
                            )
                        }
                    }
                    return@switchMap MutableLiveData<DataState<String?>>()
                }
            }
            return field!!
        }

    private var changeNicknameController = MutableLiveData<StringTrigger>()
    private var changeGenderController = MutableLiveData<StringTrigger>()

    var changeGenderResult: LiveData<DataState<String>> = changeGenderController.switchMap { input: StringTrigger ->
        if (input.isActioning) {
            val userLocal = localUserRepository.getLoggedInUser()
            if (userLocal.isValid()) {
                return@switchMap profileRepository.changeGender(
                    userLocal.token!!,
                    input.data
                )
            } else {
                return@switchMap LiveDataUtils.getMutableLiveData(
                    DataState<String>(DataState.STATE.NOT_LOGGED_IN)
                )
            }
        }
        MutableLiveData()
    }

    var changeSignatureResult: LiveData<DataState<String>>? = null
        get() {
            if (field == null) {
                changeSignatureResult = changeSignatureController.switchMap { input: StringTrigger ->
                    if (input.isActioning) {
                        val userLocal = localUserRepository.getLoggedInUser()
                        if (userLocal.isValid()) {
                            return@switchMap profileRepository.changeSignature(
                                userLocal.token!!,
                                input.data
                            )
                        } else {
                            return@switchMap LiveDataUtils.getMutableLiveData(
                                DataState(DataState.STATE.NOT_LOGGED_IN)
                            )
                        }
                    }
                    MutableLiveData()
                }
            }
            return field!!
        }

    private var changeSignatureController = MutableLiveData<StringTrigger>()

    fun startChangeNickname(nickname: String) {
        changeNicknameController.value = StringTrigger.getActioning(nickname)
    }

    fun startChangeGender(gender: UserLocal.GENDER) {
        val genderStr = gender.name
        changeGenderController.value = StringTrigger.getActioning(genderStr)
    }

    fun startChangeSignature(signature: String) {
        changeSignatureController.value = StringTrigger.getActioning(signature)
    }

    fun startRefresh() {
        val userLocal = localUserRepository.getLoggedInUser()
        userLocal.id?.let {
            profileController.value = StringTrigger.getActioning(it)
        }
    }
}
