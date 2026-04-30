package com.limpu.hitax.ui.welcome

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import com.limpu.hitax.R
import com.limpu.hitax.databinding.ActivityWelcomeBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import com.limpu.style.base.BaseTabAdapter
import com.limpu.hitax.ui.welcome.login.LoginFragment
import com.limpu.hitax.ui.welcome.signup.SignUpFragment
import com.limpu.hitax.utils.AnimationUtils
import dagger.hilt.android.AndroidEntryPoint

/**
 * 用户注册/登录页面
 */
@SuppressLint("NonConstantResourceId")
@AndroidEntryPoint
class WelcomeActivity : HiltBaseActivity<ActivityWelcomeBinding>() {

    protected val viewModel: WelcomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setWindowParams(statusBar = true, darkColor = true, navi = true)
        binding.toolbar.title = ""
        setToolbarActionBack(binding.toolbar)
    }

    override fun initViews() {

        supportActionBar?.title = ""
        //设置两个fragment，一个登录一个注册
        binding.pager.adapter = object : BaseTabAdapter(supportFragmentManager, 2) {
            override fun initItem(position: Int): Fragment {
                return if(position==0){
                    LoginFragment.newInstance()
                }else{
                    SignUpFragment.newInstance()
                }
            }

            override fun getPageTitle(position: Int): CharSequence {
                return if (position == 0) {
                    getString(R.string.login)
                } else getString(R.string.sign_up)
            }
        }
        binding.tabs.setupWithViewPager(binding.pager)
    }

    override fun onStart() {
        super.onStart()
    }


    override fun initViewBinding(): ActivityWelcomeBinding {
        return ActivityWelcomeBinding.inflate(layoutInflater)
    }
}