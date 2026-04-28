package com.limpu.hitauser.data.source.web

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.limpu.component.web.BaseWebSource
import com.limpu.hitauser.data.source.web.service.UserService
import com.limpu.hitauser.R
import com.limpu.hitauser.data.model.ApiResponse
import com.limpu.hitauser.data.model.LoginResult
import com.limpu.hitauser.data.model.SignUpResult
import com.limpu.hitauser.data.model.UserLocal
import com.limpu.hitauser.data.source.web.service.codes.SUCCESS
import com.limpu.hitauser.data.source.web.service.codes.USER_ALREADY_EXISTS
import com.limpu.hitauser.data.source.web.service.codes.WRONG_PASSWORD
import com.limpu.hitauser.data.source.web.service.codes.WRONG_USERNAME

/**
 * 层次：DataSource
 * 用户的数据源
 * 类型：网络数据
 * 数据：异步读，异步写
 */
class UserWebSource(context: Context) : BaseWebSource<UserService>(
    context
) {
    override fun getServiceClass(): Class<UserService> {
        return UserService::class.java
    }

    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    fun login(username: String, password: String): LiveData<LoginResult> {
        return service.login(
                username,
                password
            ).map{ input: ApiResponse<UserLocal>? ->
            Log.e("login", input.toString())
            val loginResult = LoginResult()
            if (null == input) {
                loginResult[LoginResult.STATES.ERROR] = R.string.login_failed
            } else {
                when (input.code) {
                    SUCCESS -> {
                        Log.e("RESPONSE", "登录成功")
                        if (null == input.data) {
                            Log.e("RESPONSE", "没有找到token")
                            loginResult[LoginResult.STATES.ERROR] = R.string.login_failed
                        } else {
                            loginResult[LoginResult.STATES.SUCCESS] = R.string.login_success
                            loginResult.userLocal = input.data
                        }
                    }
                    WRONG_USERNAME -> {
                        Log.e("RESPONSE", "用户名错误")
                        loginResult[LoginResult.STATES.WRONG_USERNAME] = R.string.wrong_username
                    }
                    WRONG_PASSWORD -> {
                        Log.e("RESPONSE", "密码错误")
                        loginResult[LoginResult.STATES.WRONG_PASSWORD] = R.string.wrong_password
                    }
                    else -> loginResult[LoginResult.STATES.ERROR] = R.string.login_failed
                }
            }
            loginResult
        }
    }

    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @param gender 性别 MALE/FEMALE
     * @param nickname 昵称
     * @return 注册结果
     */
    fun signUp(
        username: String?,
        password: String?,
        gender: String?,
        nickname: String?
    ): LiveData<SignUpResult?> {
        return service.signUp(username, password, gender, nickname).map{ input ->
            val signUpResult = SignUpResult()
            if (input != null) {
                when (input.code) {
                    SUCCESS -> {
                        if (null == input.data) {
                            Log.e("RESPONSE", "没有找到token")
                            signUpResult[SignUpResult.STATES.ERROR] =
                                R.string.signup_confirm_password
                        } else {
                            signUpResult[SignUpResult.STATES.SUCCESS] = R.string.sign_up_success
                        }
                        signUpResult.userLocal = input.data
                    }
                    USER_ALREADY_EXISTS -> signUpResult[SignUpResult.STATES.USER_EXISTS] =
                        R.string.user_already_exists
                    else -> signUpResult[SignUpResult.STATES.ERROR] = R.string.sign_up_failed
                }
            } else {
                signUpResult[SignUpResult.STATES.ERROR] = R.string.sign_up_failed
            }
            signUpResult
        }
    }

    companion object {
        var instance: UserWebSource? = null
        fun getInstance(context: Context): UserWebSource {
            synchronized(UserWebSource::class.java) {
                if (instance == null) {
                    instance = UserWebSource(context.applicationContext)
                }
                return instance!!
            }
        }
    }
}