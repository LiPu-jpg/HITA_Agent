package com.limpu.hitax.ui.welcome.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.hitax.R
import com.limpu.hitax.ui.welcome.login.LoginTrigger.Companion.getRequestState
import com.limpu.hitax.utils.TextTools
import com.limpu.hitauser.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    val loginFormState = MutableLiveData<LoginFormState>()
    private val loginState = MutableLiveData<LoginTrigger>()
    var isAgreementChecked = false

    val loginResult: LiveData<com.limpu.hitauser.data.model.LoginResult>
        get() = loginState.switchMap { input: LoginTrigger ->
            if (input.isActioning) {
                return@switchMap userRepository.login(input.username!!, input.password!!)
            }
            MutableLiveData()
        }

    fun login(username: String?, password: String?) {
        loginState.value = getRequestState(username, password)
    }

    fun loginDataChanged(username: String?, password: String?) {
        if (!TextTools.isUsernameValid(username)) {
            loginFormState.setValue(LoginFormState(R.string.invalid_username, null))
        } else if (!TextTools.isPasswordValid(password)) {
            loginFormState.setValue(LoginFormState(null, R.string.invalid_password))
        } else if (!isAgreementChecked) {
            loginFormState.setValue(LoginFormState(null, null, R.string.user_agreement_required))
        } else {
            loginFormState.setValue(LoginFormState(true))
        }
    }
}
