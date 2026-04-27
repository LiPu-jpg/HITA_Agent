package com.limpu.hitax.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.android.material.appbar.AppBarLayout
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.databinding.ActivityProfileBinding
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.stupiduser.data.model.UserLocal
import com.limpu.stupiduser.data.model.UserProfile
import com.limpu.stupiduser.data.repository.LocalUserRepository
import com.limpu.stupiduser.util.ImageUtils
import com.limpu.style.base.BaseActivity
import com.limpu.style.widgets.PopUpText
import com.limpu.sync.StupidSync

/**
 * 其他用户资料页面Activity
 */
class ProfileActivity : BaseActivity<ProfileViewModel, ActivityProfileBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }


    override fun getViewModelClass(): Class<ProfileViewModel> {
        return ProfileViewModel::class.java
    }

    override fun initViews() {
        setUpLiveData()
        val initElevation = binding.avatarCard.cardElevation
        binding.appbar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            val percentage = -verticalOffset.toFloat() / binding.appbar.totalScrollRange.toFloat()
            binding.avatarCard.pivotY = binding.avatarCard.height.toFloat() * 0.5f
            binding.avatarCard.pivotX = binding.avatarCard.width.toFloat() * 0.3f
            binding.avatarCard.scaleX = 1 - percentage
            binding.avatarCard.scaleY = 1 - percentage
            binding.textNickname.alpha = 1 - 2.5f * percentage
            binding.textSignature.alpha = 1 - 2.5f * percentage
            binding.iconGender.alpha = 1 - 2.5f * percentage
            binding.avatarCard.cardElevation = initElevation * (1 - percentage)
        })
        binding.refresh.setColorSchemeColors(getColorPrimary())
        binding.refresh.setOnRefreshListener {
            startRefresh()
        }
        binding.logout.setOnClickListener {
            PopUpText().setTitle(R.string.logout_hint).setOnConfirmListener(
                object : PopUpText.OnConfirmListener {
                    override fun OnConfirm() {
                        viewModel.logout(getThis())
                        TimetableRepository(application).actionClearData()
                        StupidSync.clearData()
                        finish()
                    }
                }
            ).show(supportFragmentManager, "logout")
        }

    }

    private fun setUpLiveData() {
        viewModel.userProfileLiveData.observe(this) { userProfileDataState ->
            binding.refresh.isRefreshing = false
            if (userProfileDataState?.state === DataState.STATE.SUCCESS) {
                setProfileView(userProfileDataState.data)
                if (userProfileDataState.data?.id == LocalUserRepository.getInstance(application)
                        .getLoggedInUser().id
                ) {
                    binding.logout.visibility = View.VISIBLE
                } else {
                    binding.logout.visibility = View.GONE
                }
            } else {
                Toast.makeText(getThis(), "获取出错", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startRefresh()
    }

    private fun startRefresh() {
        val id = intent.getStringExtra("id")
        if (id != null) {
            viewModel.startRefresh(id)
            binding.refresh.isRefreshing = true
        }
    }

    private fun setProfileView(userInfo: UserProfile?) {
        if (userInfo != null) {
            ImageUtils.loadAvatarInto(getThis(), userInfo.avatar, binding.avatar)
            binding.textUsername.text = userInfo.username
            binding.textNickname.text = userInfo.nickname
            binding.iconGender.visibility = View.VISIBLE
            if (userInfo.signature.isNullOrEmpty()) {
                binding.textSignature.setText(R.string.drawer_signature_none)
            } else {
                binding.textSignature.text = userInfo.signature
            }
            if (userInfo.gender == UserLocal.GENDER.MALE) {
                binding.iconGender.setImageResource(R.drawable.ic_male_blue_24)
                binding.iconGender.contentDescription = getString(R.string.male)
            } else {
                binding.iconGender.setImageResource(R.drawable.ic_female_pink_24)
                binding.iconGender.contentDescription = getString(R.string.female)
            }
        }
    }

    override fun initViewBinding(): ActivityProfileBinding {
        return ActivityProfileBinding.inflate(layoutInflater)
    }
}
