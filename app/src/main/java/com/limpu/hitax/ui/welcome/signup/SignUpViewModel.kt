package com.limpu.hitax.ui.welcome.signup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.hitax.R
import com.limpu.hitax.ui.welcome.signup.SignUpTrigger.Companion.getRequestState
import com.limpu.hitax.utils.TextTools
import com.limpu.hitauser.data.model.UserLocal
import com.limpu.hitauser.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val signUpFormState = MutableLiveData<SignUpFormState>()
    private val signUpController = MutableLiveData<SignUpTrigger>()
    var isAgreementChecked = false

    val loginFormState: LiveData<SignUpFormState>
        get() = signUpFormState

    val signUpResult: LiveData<com.limpu.hitauser.data.model.SignUpResult>
        get() = signUpController.switchMap { input ->
            if (input.isActioning) {
                return@switchMap userRepository.signUp(
                    input.username,
                    input.password,
                    input.getGender(),
                    input.nickname
                )
            }
            MutableLiveData()
        }

    fun signUp(
        username: String?, password: String?,
        gender: UserLocal.GENDER?, nickname: String?
    ) {
        signUpController.value = getRequestState(
            username, password, gender, nickname
        )
    }

    fun signUpDataChanged(
        username: String?,
        password: String?,
        passwordConfirm: String?,
        nickname: String?
    ) {
        if (!TextTools.isUsernameValid(username)) {
            signUpFormState.setValue(
                SignUpFormState(R.string.invalid_username, null, null, null)
            )
        } else if (!TextTools.isPasswordValid(password)) {
            signUpFormState.setValue(
                SignUpFormState(null, R.string.invalid_password, null, null)
            )
        } else if (password != passwordConfirm) {
            signUpFormState.setValue(
                SignUpFormState(null, null, R.string.inconsistent_password, null)
            )
        } else if (nickname.isNullOrEmpty()) {
            signUpFormState.setValue(
                SignUpFormState(null, null, null, R.string.empty_nickname)
            )
        } else if (!isAgreementChecked) {
            signUpFormState.setValue(
                SignUpFormState(null, null, null, null, R.string.user_agreement_required)
            )
        } else {
            signUpFormState.setValue(SignUpFormState(true))
        }
    }
}
