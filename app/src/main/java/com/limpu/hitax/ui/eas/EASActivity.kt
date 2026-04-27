package com.limpu.hitax.ui.eas

import android.app.Activity
import androidx.viewbinding.ViewBinding
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.source.preference.EasPreferenceSource
import com.limpu.hitax.data.source.preference.TimetablePreferenceSource
import com.limpu.hitax.ui.eas.login.PopUpLoginEAS
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.style.base.BaseActivity

abstract class EASActivity<T : EASViewModel, V : ViewBinding> : BaseActivity<T, V>() {

    private var reloginInProgress = false
    private var sessionRetryConsumed = false
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
        if (reloginInProgress || sessionRetryConsumed) {
            return false
        }
        reloginInProgress = true
        pendingSessionRetryAction = retryAction
        val tokenCampus = EASRepository(application, EasPreferenceSource(application.applicationContext), TimetablePreferenceSource(application.applicationContext)).getEasToken().campus
        ActivityUtils.showEasVerifyWindow<Activity>(
            getThis(),
            directTo = null,
            autoLaunchWebLogin = true,
            preferredCampus = if (tokenCampus == EASToken.Campus.BENBU || tokenCampus == EASToken.Campus.WEIHAI) tokenCampus else null,
            onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                override fun onSuccess(window: PopUpLoginEAS) {
                    window.dismiss()
                    reloginInProgress = false
                    sessionRetryConsumed = true
                    val started = pendingSessionRetryAction?.invoke() == true
                    pendingSessionRetryAction = null
                    if (!started) {
                        sessionRetryConsumed = false
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
        sessionRetryConsumed = false
        pendingSessionRetryAction = null
    }

    override fun initViews() {
        viewModel.loginCheckResult.observe(this) {
            if (it.state == DataState.STATE.SUCCESS) {
                if (it.data == true) {
                    onLoginCheckSuccess(false)
                } else {
                    ActivityUtils.showEasVerifyWindow<Activity>(
                        getThis(),
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
