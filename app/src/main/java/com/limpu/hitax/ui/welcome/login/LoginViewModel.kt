package com.limpu.hitax.ui.welcome.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.hitax.R
import com.limpu.hitax.ui.welcome.login.LoginTrigger.Companion.getRequestState
import com.limpu.hitax.utils.TextTools
import com.limpu.stupiduser.data.repository.UserRepository

/**
 * 层次：ViewModel
 * 登录界面的ViewModel
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {
    /**
     * 数据区
     */
    //数据本体：登录表单
    val loginFormState = MutableLiveData<LoginFormState>()

    //状态数据：登录章台
    private val loginState = MutableLiveData<LoginTrigger>()

    // 用户协议勾选状态
    var isAgreementChecked = false

    /**
     * 仓库区
     */
    //用户仓库
    private val userRepository: UserRepository = UserRepository.getInstance(application)

    val loginResult: LiveData<com.limpu.stupiduser.data.model.LoginResult>
        get() =  loginState.switchMap{ input: LoginTrigger ->
            if (input.isActioning) {
                return@switchMap userRepository.login(input.username!!, input.password!!)
            }
            MutableLiveData()
        }

    /**
     * 登录操作
     *
     * @param username 用户名
     * @param password 密码
     */
    fun login(username: String?, password: String?) {
        loginState.value = getRequestState(username, password)
    }

    /**
     * 当文本框信息改变时，调用本函数
     *
     * @param username 用户名
     * @param password 密码
     */
    fun loginDataChanged(username: String?, password: String?) {
        //检查输入合法性，若合法则更新登录表单
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
