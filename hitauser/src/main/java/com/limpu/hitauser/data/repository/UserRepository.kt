package com.limpu.hitauser.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.limpu.hitauser.data.source.preference.UserPreferenceSource
import com.limpu.hitauser.data.source.web.UserWebSource
import com.limpu.hitauser.data.model.LoginResult
import com.limpu.hitauser.data.model.SignUpResult
import javax.inject.Inject

/**
 * 层次：Repository层
 * 用户操作的Repository
 */
class UserRepository @Inject constructor(
    private val userPreferenceSource: UserPreferenceSource,
    private val userWebSource: UserWebSource
) {
    /**
     * 登录
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    fun login(username: String, password: String): LiveData<LoginResult> {
        return userWebSource.login(username, password).map{ input: LoginResult ->

            if (input.state === LoginResult.STATES.SUCCESS) {
                userPreferenceSource.saveLocalUser(input.userLocal!!)
            }
            input
        }
    }

    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @param gender 性别
     * @param nickname 昵称
     * @return 注册结果
     */
    fun signUp(username: String?, password: String?,
               gender: String?, nickname: String?): LiveData<SignUpResult> {
        return userWebSource.signUp(username, password, gender, nickname).map { input: SignUpResult?->
            if (input != null) {
                if (input.state === SignUpResult.STATES.SUCCESS) {
                    userPreferenceSource.saveLocalUser(input.userLocal!!)
                }
            }
            input!!
        }
    }


}