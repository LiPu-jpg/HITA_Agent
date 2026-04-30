package com.limpu.hitauser.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.map
import com.limpu.component.data.DataState
import com.limpu.hitauser.data.UserDatabase
import com.limpu.hitauser.data.model.UserProfile
import com.limpu.hitauser.data.source.web.ProfileWebSource
import javax.inject.Inject

/**
 * Repository层：用户资料页面的Repository
 */
class ProfileRepository @Inject constructor(
    private val userDatabase: UserDatabase,
    private val localUserRepository: LocalUserRepository,
    private val profileWebSource: ProfileWebSource
) {
    //数据源1：网络类型数据源，用户网络操作
    private val userProfileDao = userDatabase.userProfileDao()

    /**
     * 获取用户资料
     *@param userId 用户id
     * @param token 令牌
     * @return 用户资料
     * 这里的用户资料本体是UserProfile类
     * 其中DataState用于包装这个本体，附带状态信息
     * MutableLiveData则是UI层面的，用于和ViewModel层沟通
     */
    fun getUserProfile(userId: String, token: String): LiveData<DataState<UserProfile>> {
        val result = MediatorLiveData<DataState<UserProfile>>()
        result.addSource(userProfileDao.queryProfile(userId)) { it ->
            it?.let {
                result.value = DataState(it)
            }
        }
        result.addSource(profileWebSource.getUserProfile(token, userId)) {
            if (it.state == DataState.STATE.SUCCESS) {
                Thread {
                    it.data?.let { it1 -> userProfileDao.saveProfile(it1) }
                }.start()
            }
        }
        return result
    }

    /**
     * 更改用户昵称
     *
     * @param token    令牌
     * @param nickname 新昵称
     * @return 操作结果
     */
    fun changeNickname(token: String, nickname: String): LiveData<DataState<String?>> {
        return profileWebSource.changeNickname(
            token,
            nickname
        ).map { input: DataState<String?> ->
            if (input.state === DataState.STATE.SUCCESS) {
                localUserRepository.changeLocalNickname(nickname)
            }
            input
        }
    }

    /**
     * 更改用户性别
     *
     * @param token  令牌
     * @param gender 新性别 MALE/FEMALE
     * @return 操作结果
     */
    fun changeGender(token: String, gender: String): LiveData<DataState<String>> {
        return profileWebSource.changeGender(
            token,
            gender
        ).map { input ->
            if (input.state === DataState.STATE.SUCCESS) {
                localUserRepository.changeLocalGender(gender)
            }
            input
        }
    }


    /**
     * 更改用户签名
     *
     * @param token     令牌
     * @param signature 新签名
     * @return 操作结果
     */
    fun changeSignature(token: String, signature: String): LiveData<DataState<String>> {
        return profileWebSource.changeSignature(token, signature).map { input ->
            if (input.state === DataState.STATE.SUCCESS) {
                localUserRepository.changeLocalSignature(signature)
            }
            input
        }
    }


    fun getUsers(
        token: String,
        mode: String,
        pageSize: Int,
        pageNum: Int,
        extra: String
    ): LiveData<DataState<List<UserProfile>>> {
        return profileWebSource.getUsers(token, mode, pageSize, pageNum, extra)
    }



}