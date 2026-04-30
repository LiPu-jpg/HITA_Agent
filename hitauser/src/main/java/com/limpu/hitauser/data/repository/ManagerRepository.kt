package com.limpu.hitauser.data.repository

import androidx.lifecycle.LiveData
import com.limpu.component.data.DataState
import com.limpu.hitauser.data.model.CheckUpdateResult
import com.limpu.hitauser.data.source.web.ManagerWebSource
import javax.inject.Inject

/**
 * Repository层：用户资料页面的Repository
 */
class ManagerRepository @Inject constructor(
    private val managerWebSource: ManagerWebSource
) {

    fun checkUpdate(token: String, versionCode:Long, id: String?): LiveData<DataState<CheckUpdateResult>> {
        return managerWebSource.checkUpdate(token,versionCode,id)
    }
}