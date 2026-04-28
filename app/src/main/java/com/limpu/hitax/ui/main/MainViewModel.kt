package com.limpu.hitax.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import com.limpu.hitax.BuildConfig
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.repository.UpdateRepository
import com.limpu.hitax.utils.LiveDataUtils
import com.limpu.hitauser.data.model.CheckUpdateResult
import com.limpu.hitauser.data.model.UserLocal
import com.limpu.hitauser.data.repository.LocalUserRepository
import com.limpu.hitauser.data.repository.ManagerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val localUserRepository: LocalUserRepository,
    private val managerRepository: ManagerRepository,
    private val updateRepository: UpdateRepository,
    private val easRepository: EASRepository
) : ViewModel() {

    /**
     * LiveData
     */
    private val localUserController = MutableLiveData<Trigger>()
    val loggedInUserLiveData: LiveData<UserLocal> = localUserController.switchMap{
        val res = MutableLiveData<UserLocal>()
        res.value = localUserRepository.getLoggedInUser()
        return@switchMap res
    }

    private val checkUpdateTrigger = MutableLiveData<Long>()
    val checkUpdateResult = checkUpdateTrigger.switchMap{
        updateRepository.checkUpdateFromGitHub(
            currentVersionName = BuildConfig.VERSION_NAME,
            updateUrl = BuildConfig.UPDATE_URL,
            allowPrerelease = BuildConfig.UPDATE_ALLOW_PRERELEASE
        )?.let { github ->
            return@switchMap github
        }
        buildLocalUpdateResult(it)?.let { local ->
            return@switchMap LiveDataUtils.getMutableLiveData(local)
        }
        if (localUserRepository.getLoggedInUser().isValid()) {
            return@switchMap managerRepository.checkUpdate(
                localUserRepository.getLoggedInUser().token!!,
                it,
                easRepository.getEasToken().stuId
            )
        } else {
            return@switchMap LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
        }
    }


    fun startRefreshUser() {
        localUserController.value = Trigger.actioning
    }


    fun checkForUpdate(versionCode: Long) {
        checkUpdateTrigger.value = versionCode
    }

    private fun buildLocalUpdateResult(currentCode: Long): DataState<CheckUpdateResult>? {
        val url = BuildConfig.UPDATE_URL.trim()
        if (url.isBlank()) return null
        val result = CheckUpdateResult().apply {
            latestVersionCode = BuildConfig.UPDATE_VERSION_CODE
            latestVersionName = BuildConfig.UPDATE_VERSION_NAME
            latestUrl = url
            updateLog = BuildConfig.UPDATE_LOG
            shouldUpdate = latestVersionCode > currentCode
        }
        return DataState(result)
    }
}
