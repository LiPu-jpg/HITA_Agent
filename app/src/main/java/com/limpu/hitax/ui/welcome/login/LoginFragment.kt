package com.limpu.hitax.ui.welcome.login

import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import com.limpu.hitax.databinding.FragmentLoginBinding
import com.limpu.hitax.ui.about.UserAgreementDialog
import com.limpu.hitax.utils.AnimationUtils
import com.limpu.stupiduser.data.model.LoginResult
import com.limpu.style.base.BaseFragment

/**
 * 登录页面Fragment
 */
class LoginFragment : BaseFragment<LoginViewModel, FragmentLoginBinding>() {

    override fun initViews(view: View) {
        //登录表单的数据变更监听器
        viewModel.loginFormState.observe(this, { loginFormState: LoginFormState ->
            //将表单合法性同步到登录按钮可用性
            binding?.login?.let { AnimationUtils.enableLoadingButton(it,loginFormState.isDataValid) }
            //若有表单上的错误，则通知View显示错误
            if (loginFormState.usernameError != null) {
                binding?.username?.error = getString(loginFormState.usernameError!!)
            }
            if (loginFormState.passwordError != null) {
                binding?.password?.error = getString(loginFormState.passwordError!!)
            }
            if (loginFormState.agreementError != null && viewModel.isAgreementChecked.not()) {
                binding?.agreementText?.let {
                    Toast.makeText(context, getString(loginFormState.agreementError!!), Toast.LENGTH_SHORT).show()
                }
            }
        })

        //登录结果的数据变更监听
        viewModel.loginResult.observe(this, { loginResult ->
            AnimationUtils.loadingButtonDone(binding?.login,loginResult != null&&loginResult.state==LoginResult.STATES.SUCCESS,toast = false)
            if (loginResult != null) {
                Toast.makeText(context, loginResult.message, Toast.LENGTH_SHORT).show()
            }
            if (loginResult != null) {
                when (loginResult.state) {
                    com.limpu.stupiduser.data.model.LoginResult.STATES.SUCCESS -> {
                        requireActivity().finish()
                    }
                    com.limpu.stupiduser.data.model.LoginResult.STATES.WRONG_USERNAME -> {
                        binding?.username?.error = getString(loginResult.message)
                    }
                    com.limpu.stupiduser.data.model.LoginResult.STATES.WRONG_PASSWORD -> {
                        binding?.password?.error = getString(loginResult.message)
                    }
                    else -> {

                    }
                }
            }
        })

        // 登录表单的文本监视器
        val afterTextChangedListener: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // ignore
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // ignore
            }

            override fun afterTextChanged(s: Editable) {
                //将文本信息改变通知给ViewModel
                viewModel.loginDataChanged(binding?.username?.text.toString(),
                        binding?.password?.text.toString())
            }
        }
        binding?.username?.addTextChangedListener(afterTextChangedListener)
        binding?.password?.addTextChangedListener(afterTextChangedListener)

        // 用户协议勾选监听
        binding?.agreementCheckbox?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isAgreementChecked = isChecked
            viewModel.loginDataChanged(binding?.username?.text.toString(),
                    binding?.password?.text.toString())
        }

        // 用户协议文本点击打开协议弹窗
        binding?.agreementText?.setOnClickListener {
            UserAgreementDialog().show(childFragmentManager, "user_agreement")
        }

        binding?.login?.let { AnimationUtils.enableLoadingButton(it,false) }
        //使得手机输入法上"完成"按钮映射到登录动作
        binding?.password?.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
            }
            false
        }
        binding?.login?.setOnClickListener {
            attemptLogin()
        }
    }

    private fun attemptLogin() {
        if (!viewModel.isAgreementChecked) {
            Toast.makeText(context, getString(com.limpu.hitax.R.string.user_agreement_required), Toast.LENGTH_SHORT).show()
            return
        }
        binding?.login?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        binding?.login?.startAnimation()
        viewModel.login(binding?.username?.text.toString(),
                binding?.password?.text.toString())
    }

    companion object {
        fun newInstance(): LoginFragment {
            return LoginFragment()
        }
    }

    override fun getViewModelClass(): Class<LoginViewModel> {
        return LoginViewModel::class.java
    }

    override fun initViewBinding(): FragmentLoginBinding {
        return FragmentLoginBinding.inflate(layoutInflater)
    }
}
