package com.limpu.hitax.ui.welcome.signup

import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.limpu.hitax.R
import com.limpu.stupiduser.data.model.UserLocal
import com.limpu.hitax.databinding.FragmentSignUpBinding
import com.limpu.hitax.ui.about.UserAgreementDialog
import com.limpu.hitax.ui.base.HiltBaseFragment
import com.limpu.hitax.utils.AnimationUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignUpFragment : HiltBaseFragment<FragmentSignUpBinding>() {

    protected val viewModel: SignUpViewModel by viewModels()

    override fun initViews(view: View) {
        viewModel.loginFormState.observe(this, { signUpFormState: SignUpFormState? ->
            //将表单合法性同步到注册按钮可用性
            if (signUpFormState != null) {
                binding?.signUp?.let { AnimationUtils.enableLoadingButton(it,signUpFormState.isFormValid) }
                //若有表单上的错误，则通知View显示错误
                if (signUpFormState.usernameError != null) {
                    binding?.username?.error = getString(signUpFormState.usernameError!!)
                }
                if (signUpFormState.passwordError != null) {
                    binding?.password?.error = getString(signUpFormState.passwordError!!)
                }
                if (signUpFormState.passwordConfirmError != null) {
                    binding?.passwordConfirm?.error =
                        getString(signUpFormState.passwordConfirmError!!)
                }
                if (signUpFormState.nicknameError != null) {
                    binding?.nickname?.error = getString(signUpFormState.nicknameError!!)
                }
                if (signUpFormState.agreementError != null && viewModel.isAgreementChecked.not()) {
                    binding?.agreementText?.let {
                        Toast.makeText(context, getString(signUpFormState.agreementError!!), Toast.LENGTH_SHORT).show()
                    }
                }
            }

        })
        viewModel.signUpResult.observe(
            this,
            { signUpResult: com.limpu.stupiduser.data.model.SignUpResult? ->
                AnimationUtils.loadingButtonDone(
                    binding?.signUp,
                    signUpResult?.state === com.limpu.stupiduser.data.model.SignUpResult.STATES.SUCCESS,
                    toast = false
                )
                if (signUpResult != null) {
                    Toast.makeText(context, signUpResult.message, Toast.LENGTH_SHORT).show()
                }
                if (signUpResult != null) {
                    if (signUpResult.state === com.limpu.stupiduser.data.model.SignUpResult.STATES.SUCCESS) {
                        requireActivity().finish()
                    } else if (signUpResult.state === com.limpu.stupiduser.data.model.SignUpResult.STATES.USER_EXISTS) {
                        binding?.username?.error = getString(signUpResult.message)
                    }
                }
            })
        val afterTextChangedListener: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // ignore
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // ignore
            }

            override fun afterTextChanged(s: Editable) {
                //将文本信息改变通知给ViewModel
                viewModel.signUpDataChanged(
                    binding?.username?.text.toString(),
                    binding?.password?.text.toString(),
                    binding?.passwordConfirm?.text.toString(),
                    binding?.nickname?.text.toString()
                )
            }
        }
        binding?.username?.addTextChangedListener(afterTextChangedListener)
        binding?.password?.addTextChangedListener(afterTextChangedListener)
        binding?.passwordConfirm?.addTextChangedListener(afterTextChangedListener)
        binding?.nickname?.addTextChangedListener(afterTextChangedListener)

        // 用户协议勾选监听
        binding?.agreementCheckbox?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isAgreementChecked = isChecked
            viewModel.signUpDataChanged(
                binding?.username?.text.toString(),
                binding?.password?.text.toString(),
                binding?.passwordConfirm?.text.toString(),
                binding?.nickname?.text.toString()
            )
        }

        // 用户协议文本点击打开协议弹窗
        binding?.agreementText?.setOnClickListener {
            UserAgreementDialog().show(childFragmentManager, "user_agreement")
        }

        binding?.signUp?.let { AnimationUtils.enableLoadingButton(it,false) }
        binding?.signUp?.setOnClickListener {
            if (!viewModel.isAgreementChecked) {
                Toast.makeText(context, getString(R.string.user_agreement_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            binding?.signUp?.startAnimation()
            viewModel.signUp(
                binding?.username?.text.toString(),
                binding?.password?.text.toString(),
                if (binding?.genderGroup?.checkedRadioButtonId == R.id.radioButtonMale) UserLocal.GENDER.MALE else UserLocal.GENDER.FEMALE,
                binding?.nickname?.text.toString()
            )
        }
    }

    companion object {
        fun newInstance(): SignUpFragment {
            return SignUpFragment()
        }
    }

    override fun initViewBinding(): FragmentSignUpBinding {
        return FragmentSignUpBinding.inflate(layoutInflater)
    }
}
