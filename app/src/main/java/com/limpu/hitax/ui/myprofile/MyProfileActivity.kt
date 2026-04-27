package com.limpu.hitax.ui.myprofile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import com.limpu.hitax.R
import com.limpu.hitax.databinding.ActivityMyProfileBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import com.limpu.component.data.DataState
import com.limpu.stupiduser.data.model.UserLocal
import com.limpu.stupiduser.data.model.UserProfile
import com.limpu.style.widgets.PopUpEditText
import com.limpu.style.widgets.PopUpSelectableList
import dagger.hilt.android.AndroidEntryPoint

/**
 * 我的个人资料 Activity
 */
@AndroidEntryPoint
class MyProfileActivity : HiltBaseActivity<ActivityMyProfileBinding>() {

    protected val viewModel: MyProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    override fun initViews() {
        viewModel.userProfileLiveData.observe(this, { userProfileDataState ->
            if (userProfileDataState.state === DataState.STATE.SUCCESS) {
                userProfileDataState.data?.let { setUserProfile(it) }
            } else {
                Toast.makeText(getThis(), "加载失败", Toast.LENGTH_SHORT).show()
            }
        })
        viewModel.changeNicknameResult?.observe(this, { stringDataState: DataState<String?> ->
            if (stringDataState.state === DataState.STATE.SUCCESS) {
                Toast.makeText(getThis(), R.string.notif_nick_updated, Toast.LENGTH_SHORT).show()
                viewModel.startRefresh()
            } else {
                Toast.makeText(applicationContext, R.string.fail, Toast.LENGTH_SHORT).show()
            }
        })
        viewModel.changeGenderResult.observe(this, { stringDataState ->
            if (stringDataState.state === DataState.STATE.SUCCESS) {
                Toast.makeText(getThis(), R.string.notif_nick_updated, Toast.LENGTH_SHORT).show()
                viewModel.startRefresh()
            } else {
                Toast.makeText(applicationContext, R.string.fail, Toast.LENGTH_SHORT).show()
            }
        })

        viewModel.changeSignatureResult?.observe(this) { stringDataState ->
            if (stringDataState.state === DataState.STATE.SUCCESS) {
                Toast.makeText(getThis(), R.string.notif_signature_updated, Toast.LENGTH_SHORT).show()
                viewModel.startRefresh()
            } else {
                Toast.makeText(applicationContext, R.string.fail, Toast.LENGTH_SHORT).show()
            }
        }
        binding.nicknameLayout.setOnClickListener {
            val up = viewModel.userProfileLiveData.value
            if (up != null && up.state === DataState.STATE.SUCCESS) {
                PopUpEditText()
                    .setTitle(R.string.set_nickname)
                    .setText(up.data!!.nickname)
                    .setOnConfirmListener(object : PopUpEditText.OnConfirmListener {
                        override fun OnConfirm(text: String) {
                            viewModel.startChangeNickname(text)
                        }
                    })
                    .show(supportFragmentManager, "edit")
            }
        }

        binding.genderLayout.setOnClickListener {
            val up = viewModel.userProfileLiveData.value
            if (up != null && up.state === DataState.STATE.SUCCESS) {
                PopUpSelectableList<UserLocal.GENDER>()
                    .setTitle(R.string.choose_gender)
                    .setInitValue(up.data!!.gender)
                    .setListData(
                       listOf(getString(R.string.male), getString(R.string.female),getString(R.string.other_gender)),
                        listOf(UserLocal.GENDER.MALE, UserLocal.GENDER.FEMALE, UserLocal.GENDER.OTHER)
                    ).setOnConfirmListener(object :
                        PopUpSelectableList.OnConfirmListener<UserLocal.GENDER> {

                        override fun onConfirm(title: String?, key: UserLocal.GENDER) {
                            viewModel.startChangeGender(key)
                        }
                    })
                    .show(supportFragmentManager, "select")
            }
        }

        binding.signatureLayout.setOnClickListener {
            val up = viewModel.userProfileLiveData.value
            if (up != null && up.state === DataState.STATE.SUCCESS) {
                PopUpEditText()
                    .setTitle(R.string.choose_signature)
                    .setText(up.data!!.signature)
                    .setOnConfirmListener(object : PopUpEditText.OnConfirmListener {
                        override fun OnConfirm(text: String) {
                            viewModel.startChangeSignature(text)
                        }
                    })
                    .show(supportFragmentManager, "edit")
            }
        }
    }

    private fun setUserProfile(profile: UserProfile) {
        com.limpu.stupiduser.util.ImageUtils.loadAvatarInto(getThis(), profile.avatar, binding.avatar)
        binding.nickname.text = profile.nickname
        if (!profile.signature.isNullOrEmpty()) {
            binding.signature.text = profile.signature
        } else {
            binding.signature.setText(R.string.drawer_signature_none)
        }
        binding.username.text = profile.username
        binding.gender.setText(when (profile.gender){
            UserLocal.GENDER.MALE ->R.string.male
            UserLocal.GENDER.FEMALE->R.string.female
            UserLocal.GENDER.OTHER->R.string.other_gender
            else -> R.string.other_gender
        } )
    }

    override fun onStart() {
        super.onStart()
        viewModel.startRefresh()
    }

    override fun initViewBinding(): ActivityMyProfileBinding {
        return ActivityMyProfileBinding.inflate(layoutInflater)
    }
}
