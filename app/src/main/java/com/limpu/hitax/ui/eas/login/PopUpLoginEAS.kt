package com.limpu.hitax.ui.eas.login

import com.limpu.hitax.utils.LogUtils
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.databinding.DialogBottomEasVerifyBinding
import com.limpu.hitax.ui.about.UserAgreementDialog
import com.limpu.hitax.utils.ImageUtils
import com.limpu.style.widgets.TransparentModeledBottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject

@AndroidEntryPoint
class PopUpLoginEAS :
    TransparentModeledBottomSheetDialog<LoginEASViewModel, DialogBottomEasVerifyBinding>() {

    var lock = false
    var autoLaunchWebLogin = false
    var preferredCampus: EASToken.Campus? = null
    var onResponseListener: OnResponseListener? = null
    private var pendingWebViewCampus: EASToken.Campus? = null
    private var autoLaunchTriggered = false
    private var silentWebLoginTried = false
    private var agreementChecked = false

    private val hiltViewModel: LoginEASViewModel by viewModels()

    private fun isUserAgreementAccepted(): Boolean {
        return requireContext().getSharedPreferences("user_agreement", android.content.Context.MODE_PRIVATE)
            .getBoolean("accepted", false)
    }

    private fun markUserAgreementAccepted() {
        requireContext().getSharedPreferences("user_agreement", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("accepted", true).apply()
    }

    override fun getViewModelClass(): Class<LoginEASViewModel> {
        return LoginEASViewModel::class.java
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = hiltViewModel
    }

    companion object {
        private const val REQUEST_CODE_WEBVIEW_LOGIN = 1001
        private const val TAG = "BenbuWebLogin"
    }

    override fun initViews(view: View) {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                binding?.buttonLogin?.background = ContextCompat.getDrawable(
                    requireContext(), if (isFormValid()) {
                        R.drawable.element_rounded_button_bg_primary
                    } else {
                        R.drawable.element_rounded_button_bg_grey
                    }
                )
            }
        }

        binding?.campusGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.campus_shenzhen -> {
                    binding?.username?.visibility = View.VISIBLE
                    binding?.password?.visibility = View.VISIBLE
                }

                R.id.campus_benbu, R.id.campus_weihai -> {
                    binding?.username?.visibility = View.GONE
                    binding?.password?.visibility = View.GONE
                }
            }
            textWatcher.afterTextChanged(null)
        }

        viewModel.loginResultLiveData.observe(this) {
            LogUtils.d( "popup observe loginResult state=${it.state} message=${it.message}")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                binding?.buttonLogin?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                binding?.buttonLogin?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            binding?.buttonLogin?.isEnabled = true
            val iconId: Int = if (it.state == DataState.STATE.SUCCESS) {
                R.drawable.ic_baseline_done_24
            } else {
                R.drawable.ic_baseline_error_24
            }
            val bitmap = ImageUtils.getResourceBitmap(requireContext(), iconId)
            binding?.buttonLogin?.doneLoadingAnimation(getColorPrimary(), bitmap)
            if (it.state == DataState.STATE.SUCCESS) {
                val token = viewModel.easRepo.getEasToken()
                LogUtils.d( "popup login success savedToken campus=${token.campus} isLogin=${token.isLogin()} cookieKeys=${token.cookies.keys.sorted()}")
                onResponseListener?.onSuccess(this)

            } else {
                binding?.buttonLogin?.postDelayed({
                    binding?.buttonLogin?.revertAnimation()
                }, 600)
                Toast.makeText(
                    requireContext(),
                    it.message ?: getString(R.string.hint_eas_login),
                    Toast.LENGTH_SHORT
                ).show()
                onResponseListener?.onFailed(this)
            }
        }

        // 监听loginCheck结果（用于WebView登录后的验证）
        viewModel.loginCheckResult.observe(this) {
            LogUtils.d( "popup observe loginCheckResult state=${it.state} message=${it.message}")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                binding?.buttonLogin?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                binding?.buttonLogin?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            binding?.buttonLogin?.isEnabled = true
            val iconId: Int = if (it.state == DataState.STATE.SUCCESS) {
                R.drawable.ic_baseline_done_24
            } else {
                R.drawable.ic_baseline_error_24
            }
            val bitmap = ImageUtils.getResourceBitmap(requireContext(), iconId)
            binding?.buttonLogin?.doneLoadingAnimation(getColorPrimary(), bitmap)
            if (it.state == DataState.STATE.SUCCESS) {
                val token = viewModel.easRepo.getEasToken()
                LogUtils.d( "popup loginCheck success savedToken campus=${token.campus} isLogin=${token.isLogin()} cookieKeys=${token.cookies.keys.sorted()}")
                onResponseListener?.onSuccess(this)

            } else {
                // Log token state after failed loginCheck
                val token = viewModel.easRepo.getEasToken()
                LogUtils.e( "popup loginCheck FAILED state=${it.state} message=${it.message} token.isLogin=${token.isLogin()} cookieKeys=${token.cookies.keys.sorted()}")
                binding?.buttonLogin?.postDelayed({
                    binding?.buttonLogin?.revertAnimation()
                }, 600)
                Toast.makeText(
                    requireContext(),
                    it.message ?: "登录验证失败",
                    Toast.LENGTH_SHORT
                ).show()
                onResponseListener?.onFailed(this)
            }
        }
        binding?.buttonLogin?.setOnClickListener {
            val campus = getSelectedCampus() ?: return@setOnClickListener
            LogUtils.d( "click login campus=$campus")
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (!agreementChecked && !isUserAgreementAccepted()) {
                Toast.makeText(requireContext(), R.string.user_agreement_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            markUserAgreementAccepted()
            performLogin(campus)
        }

        val alreadyAccepted = isUserAgreementAccepted()
        agreementChecked = alreadyAccepted
        binding?.agreementContainer?.visibility = View.VISIBLE
        binding?.agreementCheckbox?.isChecked = alreadyAccepted
        binding?.agreementCheckbox?.setOnCheckedChangeListener { _, isChecked ->
            agreementChecked = isChecked
            if (isChecked) markUserAgreementAccepted()
        }
        setupAgreementText()

        binding?.username?.addTextChangedListener(textWatcher)
        binding?.password?.addTextChangedListener(textWatcher)
        textWatcher.afterTextChanged(null)
    }

    private fun getSelectedCampus(): EASToken.Campus? {
        return when (binding?.campusGroup?.checkedRadioButtonId) {
            R.id.campus_shenzhen -> EASToken.Campus.SHENZHEN
            R.id.campus_benbu -> EASToken.Campus.BENBU
            R.id.campus_weihai -> EASToken.Campus.WEIHAI
            else -> null
        }
    }

    private fun setupAgreementText() {
        val tv = binding?.agreementText ?: return
        val hint = getString(R.string.user_agreement_hint)
        val span = android.text.SpannableString(hint)
        val uaStart = hint.indexOf("《")
        val uaEnd = hint.indexOf("》") + 1
        val ppStart = hint.indexOf("《", uaEnd)
        val ppEnd = hint.indexOf("》", ppStart) + 1

        if (uaStart >= 0 && uaEnd > uaStart) {
            span.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: android.view.View) {
                    UserAgreementDialog().apply {
                        setShowActionButtons(false)
                    }.show(childFragmentManager, "user_agreement_view")
                }
            }, uaStart, uaEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (ppStart >= 0 && ppEnd > ppStart) {
            span.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: android.view.View) {
                    UserAgreementDialog().apply {
                        setShowActionButtons(false)
                    }.show(childFragmentManager, "privacy_view")
                }
            }, ppStart, ppEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tv.text = span
        tv.movementMethod = android.text.method.LinkMovementMethod.getInstance()
    }

    fun isFormValid(): Boolean {
        val campus = getSelectedCampus()
        return when (campus) {
            EASToken.Campus.SHENZHEN -> {
                !binding?.username?.text.isNullOrEmpty() &&
                    !binding?.password?.text.isNullOrEmpty()
            }

            EASToken.Campus.BENBU -> true
            EASToken.Campus.WEIHAI -> true
            else -> false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_WEBVIEW_LOGIN) {
            LogUtils.i( "=== 📨 onActivityResult START ===")
            LogUtils.i( "resultCode=$resultCode (OK=${resultCode == Activity.RESULT_OK})")
            LogUtils.i( "silentTried=$silentWebLoginTried pendingCampus=$pendingWebViewCampus")

            if (resultCode != Activity.RESULT_OK) {
                val campus = pendingWebViewCampus
                pendingWebViewCampus = null
                if (autoLaunchWebLogin && silentWebLoginTried && campus == EASToken.Campus.BENBU) {
                    LogUtils.i( "retry non-silent login")
                    silentWebLoginTried = false
                    launchCampusWebLogin(campus, silentMode = false)
                } else {
                    LogUtils.e( "❌ WebView login FAILED")
                    binding?.buttonLogin?.isEnabled = true
                    onResponseListener?.onFailed(this)
                }
                return
            }

            val cookiesJson = data?.getStringExtra("cookies")
            val campus = pendingWebViewCampus
            LogUtils.i( "✅ WebView returned RESULT_OK")
            LogUtils.i( "cookiesJson length=${cookiesJson?.length ?: 0}")
            LogUtils.i( "pendingCampus=$campus selectedCampus=${getSelectedCampus()}")

            pendingWebViewCampus = null
            silentWebLoginTried = false

            if (cookiesJson != null && (campus == EASToken.Campus.BENBU || campus == EASToken.Campus.WEIHAI)) {
                LogUtils.i( "🚀 Processing cookies from WebView...")

                try {
                    val cookiesJsonObj = JSONObject(cookiesJson)
                    val cookiesMap = HashMap<String, String>()
                    val keys = cookiesJsonObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        cookiesMap[key] = cookiesJsonObj.getString(key)
                    }

                    val currentToken = viewModel.easRepo.getEasToken()
                    currentToken.cookies = cookiesMap
                    currentToken.campus = campus

                    val electronicExpToken = data?.getStringExtra("electronic_exp_token")
                    if (!electronicExpToken.isNullOrBlank()) {
                        currentToken.electronicExpToken = electronicExpToken
                        LogUtils.i( "✅ Saved eelabinfo JWT token, length=${electronicExpToken.length}")
                    }

                    viewModel.easRepo.saveEasTokenSync(currentToken)

                    LogUtils.i( "✅ Cookies saved, verifying login...")

                    binding?.buttonLogin?.startAnimation()
                    viewModel.startLoginCheck()
                } catch (e: Exception) {
                    LogUtils.e( "❌ Failed to parse cookies: ${e.message}")
                    binding?.buttonLogin?.isEnabled = true
                    onResponseListener?.onFailed(this)
                }
            } else {
                LogUtils.e( "❌ Invalid state: cookiesJson=${cookiesJson != null} campus valid=${campus == EASToken.Campus.BENBU || campus == EASToken.Campus.WEIHAI}")
                binding?.buttonLogin?.isEnabled = true
                onResponseListener?.onFailed(this)
            }
        }
    }

    interface OnResponseListener {
        fun onSuccess(window: PopUpLoginEAS)
        fun onFailed(window: PopUpLoginEAS)
    }

    override fun getLayoutId(): Int {
        return R.layout.dialog_bottom_eas_verify
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        if (lock) {
            if (context is Activity) {
                (context as Activity).finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = viewModel.easRepo.getEasToken()
        binding?.username?.setText(token.username)
        binding?.password?.setText(token.password)
        val preferred = preferredCampus
        val targetCampus = preferred ?: token.campus
        binding?.campusGroup?.check(
            when (targetCampus) {
                EASToken.Campus.SHENZHEN -> R.id.campus_shenzhen
                EASToken.Campus.BENBU -> R.id.campus_benbu
                EASToken.Campus.WEIHAI -> R.id.campus_weihai
            }
        )
        maybeAutoLaunchWebLogin(targetCampus)
    }

    private fun maybeAutoLaunchWebLogin(targetCampus: EASToken.Campus) {
        if (autoLaunchTriggered) return
        if (!autoLaunchWebLogin) return
        if (targetCampus != EASToken.Campus.BENBU) return
        if (!isUserAgreementAccepted()) return
        autoLaunchTriggered = true
        silentWebLoginTried = true
        LogUtils.d( "auto launch silent web login for session recovery campus=$targetCampus")
        launchCampusWebLogin(targetCampus, silentMode = true)
    }

    private fun performLogin(campus: EASToken.Campus) {
        when (campus) {
            EASToken.Campus.SHENZHEN -> {
                if (!isFormValid()) return
                binding?.buttonLogin?.startAnimation()
                viewModel.startLogin(
                    binding?.username?.text.toString(),
                    binding?.password?.text.toString(),
                    campus
                )
            }

            EASToken.Campus.BENBU, EASToken.Campus.WEIHAI -> {
                launchCampusWebLogin(campus)
            }
        }
    }

    private fun launchCampusWebLogin(campus: EASToken.Campus, silentMode: Boolean = false) {
        pendingWebViewCampus = campus
        binding?.buttonLogin?.isEnabled = false
        startActivityForResult(
            Intent(requireContext(), WebViewLoginActivity::class.java).apply {
                putExtra(WebViewLoginActivity.EXTRA_SILENT_MODE, silentMode)
                putExtra(WebViewLoginActivity.EXTRA_CAMPUS, campus.name)
            },
            REQUEST_CODE_WEBVIEW_LOGIN
        )
    }

    override fun initViewBinding(v: View): DialogBottomEasVerifyBinding {
        return DialogBottomEasVerifyBinding.bind(v)
    }
}
