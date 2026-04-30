package com.limpu.hitauser.data.repository

import android.content.Context
import com.limpu.hitauser.data.UserDatabase
import com.limpu.hitauser.data.model.UserLocal
import com.limpu.hitauser.data.source.preference.UserPreferenceSource
import javax.inject.Inject

/**
 * 层次：Repository
 * ”我的“页面的Repository，同时也是全局的本地用户仓库
 */
class LocalUserRepository @Inject constructor(
    private val mePreferenceSource: UserPreferenceSource,
    private val appDatabase: UserDatabase
) {

    //将已登录用户缓存在内存里
    private var loggedInUser: UserLocal? = null

    /**
     * 登出
     */
    fun logout(@Suppress("UNUSED_PARAMETER") context: Context) {
        loggedInUser = null
        mePreferenceSource.clearLocalUser()
        //本地缓存清空
        Thread {
            appDatabase.userProfileDao().clearTable()
        }.start()
    }

    /**
     * 更改该本地缓存的头像地址
     * @param newAvatar 头像地址
     */
    fun changeLocalAvatar(newAvatar: String?) {
        mePreferenceSource.saveAvatar(newAvatar)
        loggedInUser = mePreferenceSource.localUser
        // getThis().getSharedPreferences("Glide", Context.MODE_PRIVATE).edit().
    }

    /**
     * 更改本地缓存的昵称
     * @param nickname 新昵称
     */
    fun changeLocalNickname(nickname: String?) {
        mePreferenceSource.saveNickname(nickname)
        loggedInUser = mePreferenceSource.localUser
    }

    /**
     * 更改本地缓存的用户性别
     * @param gender 性别/MALE/FEMALE
     */
    fun changeLocalGender(gender: String?) {
        mePreferenceSource.saveGender(gender)
        loggedInUser = mePreferenceSource.localUser
    }



    /**
     * 更改本地缓存的签名
     * @param signature 新签名
     */
    fun changeLocalSignature(signature: String?) {
        mePreferenceSource.saveSignature(signature)
    }

    /**
     * 直接获取本地已登陆的用户对象
     * 同步获取
     * @return 本地用户对象
     */
    fun getLoggedInUser(): UserLocal {
        //Log.e("get_local_user", String.valueOf(loggedInUser));
        //if(loggedInUser==null){
        loggedInUser = mePreferenceSource.localUser
        // }
        return loggedInUser!!
    }




}