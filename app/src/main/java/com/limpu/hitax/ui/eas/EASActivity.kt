package com.limpu.hitax.ui.eas

import android.app.Activity
import android.content.Intent
import androidx.viewbinding.ViewBinding
import com.limpu.component.data.DataState
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.ui.eas.login.PopUpLoginEAS
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.LogUtils
import com.limpu.hitax.ui.base.HiltBaseActivity
import javax.inject.Inject

import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint

abstract class EASActivity<T : EASViewModel, V : ViewBinding> : HiltBaseActivity<V>() {

    companion object {
        private const val REQUEST_CODE_SILENT_RELOGIN = 10001
        private const val MAX_SESSION_RETRIES = 3
    }

    @Inject
    lateinit var easRepository: EASRepository

    protected abstract val viewModel: T
    private var reloginInProgress = false
    private var sessionRetryCount = 0
    private var pendingSessionRetryAction: (() -> Boolean)? = null

    protected open fun shouldRefreshOnStart(): Boolean = true

    protected open fun shouldCheckLoginOnStart(): Boolean = false

    override fun onStart() {
        super.onStart()
        if (shouldRefreshOnStart()) {
            refresh()
        }
        if (shouldCheckLoginOnStart()) {
            viewModel.startLoginCheck()
        }
    }

    abstract fun refresh()

    open fun onLoginCheckSuccess(retry: Boolean) {
        refresh()
    }

    open fun onLoginCheckFailed() {}

    protected fun handleSessionExpired(retryAction: () -> Boolean): Boolean {
        if (reloginInProgress || sessionRetryCount >= MAX_SESSION_RETRIES) {
            return false
        }
        sessionRetryCount++
        reloginInProgress = true
        pendingSessionRetryAction = retryAction
        val tokenCampus = easRepository.getEasToken().campus

        ActivityUtils.showEasVerifyWindow<Activity>(
            this,
            easRepository,
            directTo = null,
            autoLaunchWebLogin = true,
            preferredCampus = tokenCampus,
            onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                override fun onSuccess(window: PopUpLoginEAS) {
                    window.dismiss()
                    reloginInProgress = false
                    val started = pendingSessionRetryAction?.invoke() == true
                    pendingSessionRetryAction = null
                    if (!started) {
                        sessionRetryCount = 0
                    }
                }

                override fun onFailed(window: PopUpLoginEAS) {
                    reloginInProgress = false
                    pendingSessionRetryAction = null
                }
            }
        )
        return true
    }

    protected fun resetSessionRetryState() {
        reloginInProgress = false
        sessionRetryCount = 0
        pendingSessionRetryAction = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SILENT_RELOGIN) {
            LogUtils.d("silentReloginResult: resultCode=$resultCode")
            if (resultCode == Activity.RESULT_OK) {
                reloginInProgress = false
                val started = pendingSessionRetryAction?.invoke() == true
                pendingSessionRetryAction = null
                if (!started) {
                    sessionRetryCount = 0
                }
            } else {
                reloginInProgress = false
                pendingSessionRetryAction = null
            }
        }
    }

    override fun initViews() {
        viewModel.loginCheckResult.observe(this) {
            if (it.state == DataState.STATE.SUCCESS) {
                if (it.data == true) {
                    onLoginCheckSuccess(false)
                } else {
                    ActivityUtils.showEasVerifyWindow<Activity>(
                        getThis(),
                        easRepository,
                        lock = true,
                        onResponseListener = object :
                            PopUpLoginEAS.OnResponseListener {
                            override fun onSuccess(window: PopUpLoginEAS) {
                                window.dismiss()
                                onLoginCheckSuccess(true)
                            }

                            override fun onFailed(window: PopUpLoginEAS) {
                                onLoginCheckFailed()
                            }

                        })
                }

            }
        }
    }

}
