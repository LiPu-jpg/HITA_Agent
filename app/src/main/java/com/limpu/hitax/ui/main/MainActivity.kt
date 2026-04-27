package com.limpu.hitax.ui.main

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.limpu.hitax.utils.LogUtils
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.drawerlayout.widget.DrawerLayout.GONE
import androidx.lifecycle.Observer
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.data.repository.EasSettingsRepository
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.databinding.ActivityMainBinding
import com.limpu.hitax.ui.about.ActivityAbout
import com.limpu.hitax.ui.about.UserAgreementDialog
import com.limpu.hitax.ui.eas.login.PopUpLoginEAS
import com.limpu.hitax.ui.event.add.PopupAddEvent
import com.limpu.hitax.ui.main.agent.AgentChatDialog
import com.limpu.hitax.ui.main.agent.AgentChatFragment
import com.limpu.hitax.ui.main.navigation.NavigationFragment
import com.limpu.hitax.ui.main.timeline.FragmentTimeLine
import com.limpu.hitax.ui.main.timetable.TimetableFragment
import com.limpu.hitax.ui.main.timetable.panel.FragmentTimetablePanel
import com.limpu.hitax.ui.widgets.WidgetUtils
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.ImageUtils
import com.limpu.stupiduser.data.repository.LocalUserRepository
import com.limpu.style.ThemeTools
import com.limpu.style.base.BaseActivity
import com.limpu.style.base.BaseTabAdapter
import com.limpu.style.widgets.PopUpText
import me.ibrahimsn.lib.OnItemSelectedListener

/**
 * 很显然，这是主界面
 */
class MainActivity : BaseActivity<MainViewModel, ActivityMainBinding>(),
    TimetableFragment.MainPageController, FragmentTimeLine.MainPageController {

    private val easTokenObserver = Observer<com.limpu.hitax.data.model.eas.EASToken> {
        refreshDrawerEasInfo()
    }

    /**
     * 抽屉里的View
     */
    private val autoReimportIntervalMs = 12 * 60 * 60 * 1000L
    private var autoReimportAttempted = false
    private var drawerAvatar: ImageView? = null
    private var drawerNickname: TextView? = null
    private var drawerUsername: TextView? = null
    private var drawerHeader: ViewGroup? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.toolbar)

        // AndroidBug5497Workaround: 修复沉浸式模式下 adjustResize 失效的问题
        AndroidBug5497Workaround.assistActivity(this)
    }

    // 内部类：AndroidBug5497Workaround 解决方案
    class AndroidBug5497Workaround private constructor(private val activity: Activity) {

        private var childContent: View? = null
        private var usableHeightPrevious = 0

        fun assistActivity() {
            val content = activity.findViewById<View>(android.R.id.content) as ViewGroup
            childContent = content.getChildAt(0)
            childContent?.viewTreeObserver?.addOnGlobalLayoutListener { possiblyResizeChildOfContent() }
        }

        private fun possiblyResizeChildOfContent() {
            val content = activity.findViewById<View>(android.R.id.content)
            val usableHeightNow = content.computeUsableHeight()
            if (usableHeightNow != usableHeightPrevious) {
                val usableHeightSansKeyboard = childContent?.rootView?.height ?: 0
                val heightDifference = usableHeightSansKeyboard - usableHeightNow
                if (heightDifference > (usableHeightSansKeyboard / 4)) {
                    // keyboard probably just became visible
                    // 只给 ViewPager 添加底部 padding，不影响导航栏
                    val pager = activity.findViewById<View>(R.id.pager)
                    pager?.setPadding(
                        pager.paddingLeft,
                        pager.paddingTop,
                        pager.paddingRight,
                        heightDifference // 底部padding等于键盘高度
                    )
                } else {
                    // keyboard probably just became hidden
                    val pager = activity.findViewById<View>(R.id.pager)
                    pager?.setPadding(
                        pager.paddingLeft,
                        pager.paddingTop,
                        pager.paddingRight,
                        0 // 移除底部padding
                    )
                }
                usableHeightPrevious = usableHeightNow
            }
        }

        private fun View.computeUsableHeight(): Int {
            val r = android.graphics.Rect()
            getWindowVisibleDisplayFrame(r)
            return r.bottom - r.top
        }

        companion object {
            fun assistActivity(activity: Activity) {
                AndroidBug5497Workaround(activity).assistActivity()
            }
        }
    }


    private fun setUpDrawer() {
        binding.drawerNavigationview.itemIconTintList = null
        val headerView =
            binding.drawerNavigationview.inflateHeaderView(R.layout.activity_main_nav_header)
        binding.drawer.setStatusBarBackgroundColor(Color.TRANSPARENT)
        binding.drawer.setScrimColor(getBackgroundColorSecondAsTint())
        binding.drawer.drawerElevation = ImageUtils.dp2px(this, 84f).toFloat()
        drawerAvatar = headerView.findViewById(R.id.avatar)
        drawerHeader = headerView.findViewById(R.id.drawer_header)
        drawerNickname = headerView.findViewById(R.id.nickname)
        drawerUsername = headerView.findViewById(R.id.username)
        binding.drawer.addDrawerListener(object : DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                //offset 偏移值
                val mContent = binding.drawer.getChildAt(0)
                val scale = 1 - slideOffset
                val rightScale = 0.8f + scale * 0.2f
                mContent.translationX = -drawerView.measuredWidth * slideOffset
                mContent.pivotX = mContent.measuredWidth.toFloat()
                mContent.pivotY = (mContent.measuredHeight shr 1.toFloat().toInt()).toFloat()
                mContent.invalidate()
                mContent.scaleX = rightScale
                mContent.scaleY = rightScale
            }

            override fun onDrawerOpened(drawerView: View) {
                // setUserViews(viewModel.localUser)
            }

            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        binding.drawerNavigationview.setNavigationItemSelectedListener { item: MenuItem ->
            var jumped = true
            when (item.itemId) {
                R.id.drawer_nav_ua -> {
                    UserAgreementDialog().show(supportFragmentManager, "ua")
                }
                R.id.drawer_nav_timetable_manager -> {
                    ActivityUtils.startTimetableManager(getThis())
                }
                R.id.drawer_nav_about -> {
                    ActivityUtils.startActivity(getThis(), ActivityAbout::class.java)
                }
                else -> jumped = false
            }
//            if (jumped) {
//                binding.drawer.closeDrawer(GravityCompat.START)
//            }
            jumped
        }
    }

    var checkedUpdate = false
    var lastCheckTs: Long = 0

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        viewModel.startRefreshUser()
        refreshTheme()
        refreshDrawerEasInfo()
        EASRepository.getInstance(application).observeEasToken().observe(this, easTokenObserver)
        maybeAutoReimportTimetable()
        try {
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    0
                ).longVersionCode
            } else {
                packageManager.getPackageInfo(
                    packageName,
                    0
                ).versionCode.toLong()
            }
            if (System.currentTimeMillis() - lastCheckTs > 5 * 60 * 1000) checkedUpdate = false
            if (!checkedUpdate) {
                if (LocalUserRepository.getInstance(this).getLoggedInUser().isValid()) {
                    checkedUpdate = true
                    lastCheckTs = System.currentTimeMillis()
                }
                viewModel.checkForUpdate(code)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun maybeAutoReimportTimetable() {
        val settings = EasSettingsRepository.getInstance(application)
        if (!settings.isAutoReimportEnabled()) return
        val token = EASRepository.getInstance(application).getEasToken()
        if (!token.isLogin()) return
        if (autoReimportAttempted) return
        val now = System.currentTimeMillis()
        val last = settings.getLastAutoReimportTs()
        if (now - last < autoReimportIntervalMs) return
        autoReimportAttempted = true
        val isUndergrad = token.stutype == com.limpu.hitax.data.model.eas.EASToken.TYPE.UNDERGRAD
        EASRepository.getInstance(application).startAutoImportCurrentTimetable(isUndergrad) { success ->
            if (success) {
                settings.setLastAutoReimportTs(System.currentTimeMillis())
            }
        }
    }


    override fun initViews() {
        setUpDrawer()
        //  binding.title.text = binding.navView.menu.getItem(0).title
        binding.pager.adapter = object : BaseTabAdapter(supportFragmentManager, 4) {
            override fun initItem(position: Int): Fragment {
                return when (position) {
                    0 -> FragmentTimeLine()
                    1 -> TimetableFragment()
                    2 -> AgentChatFragment()      // 助手
                    else -> NavigationFragment()    // 功能中心
                }
            }

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                //super.destroyItem(container, position, `object`)
            }
        }
        binding.pager.offscreenPageLimit = 4
        binding.pager.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                binding.navView.itemActiveIndex = position
                binding.agentLayout.visibility = GONE
                binding.timetableLayout.visibility = GONE
                binding.navigationLayout.visibility = GONE
                binding.todayLayout.visibility = GONE
                when (position) {
                    0 -> binding.todayLayout.visibility = VISIBLE
                    1 -> binding.timetableLayout.visibility = VISIBLE
                    2 -> binding.navigationLayout.visibility = VISIBLE
                    3 -> binding.agentLayout.visibility = VISIBLE
                }
//                val item = binding.navView.menu.getItem(position)
//                item.isChecked = true
//                binding.title.text = item.title
                //Objects.requireNonNull(getSupportActionBar()).setTitle(item.getTitle());
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
        binding.navView.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelect(pos: Int): Boolean {
                binding.navView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                binding.pager.currentItem = pos
                return true
            }

        }
//        binding.navView.setOnNavigationItemSelectedListener { item: MenuItem ->
//            when (item.itemId) {
//                R.id.navigation_timeline -> binding.pager.currentItem = 0
//                R.id.navigation_timetable -> binding.pager.currentItem = 1
//                R.id.navigation_navigation -> binding.pager.currentItem = 2
//            }
//            binding.navView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
//            true
//        }
        binding.drawerButton.setOnClickListener { binding.drawer.openDrawer(GravityCompat.END) }

        binding.timetableSetting.setOnClickListener {
            FragmentTimetablePanel().show(supportFragmentManager, "panel")
        }

        binding.agentChat.setOnClickListener {
            binding.pager.currentItem = 3
        }

        binding.addEvent.setOnClickListener {
            PopupAddEvent().show(supportFragmentManager, "add_event")
        }

        binding.switchTheme.setOnClickListener {
            ThemeTools.switchTheme(getThis())
            WidgetUtils.sendRefreshToAll(this)
        }
        viewModel.checkUpdateResult.observe(this) {
            if (it.state == DataState.STATE.SUCCESS) {
                it.data?.let { cr ->
                    if (cr.shouldUpdate) {
                        ActivityUtils.showUpdateNotification(cr, this)
                    }
                }
            }
        }
        viewModel.loggedInUserLiveData.observe(this) {
            LogUtils.e(it.toString())
            refreshDrawerEasInfo()
        }
    }


    private fun refreshDrawerEasInfo() {
        val localUser = LocalUserRepository.getInstance(applicationContext).getLoggedInUser()
        if (localUser.isValid()) {
            com.limpu.stupiduser.util.ImageUtils.loadAvatarInto(
                this,
                localUser.avatar,
                drawerAvatar!!
            )
            drawerUsername?.text = localUser.username
            drawerNickname?.text = localUser.nickname
            drawerHeader?.setOnClickListener {
                ActivityUtils.startProfileActivity(
                    getThis(),
                    localUser.id,
                    drawerAvatar
                )
            }
            return
        }

        val easToken = EASRepository.getInstance(application).getEasToken()
        if (easToken.isLogin()) {
            drawerUsername?.text = easToken.name?.ifBlank { easToken.stuId?.ifBlank { easToken.username } }
                ?: easToken.stuId?.ifBlank { easToken.username }
                ?: easToken.username
                ?: getString(R.string.eas_account_not_logged_in_title)
            drawerNickname?.text = buildString {
                val primary = easToken.stuId?.trim().orEmpty()
                val secondary = listOf(
                    easToken.school,
                    easToken.major,
                    easToken.grade,
                    easToken.className
                ).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                    .joinToString(" · ")
                append(primary)
                if (secondary.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(secondary)
                }
            }.ifBlank { easToken.username.orEmpty() }
        } else {
            drawerUsername?.setText(R.string.eas_account_not_logged_in_title)
            drawerNickname?.setText(R.string.eas_account_not_logged_in_subtitle)
        }
        drawerAvatar?.setImageResource(R.drawable.place_holder_avatar)
        drawerHeader?.setOnClickListener {
            ActivityUtils.showEasVerifyWindow<Activity>(
                getThis(),
                directTo = null,
                onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                    override fun onSuccess(window: PopUpLoginEAS) {
                        window.dismiss()
                    }

                    override fun onFailed(window: PopUpLoginEAS) {}
                }
            )
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        //super.onBackPressed();
        if (binding.drawer.isDrawerOpen(GravityCompat.END)) {
            binding.drawer.closeDrawer(GravityCompat.END)
            return
        }
        //返回桌面而非退出
        val intent = Intent(Intent.ACTION_MAIN)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.addCategory(Intent.CATEGORY_HOME)
        startActivity(intent)
    }

    override fun onStop() {
        EASRepository.getInstance(application).observeEasToken().removeObserver(easTokenObserver)
        super.onStop()
    }


    private fun refreshTheme() {
        when (ThemeTools.getThemeMode(this)) {
            ThemeTools.MODE.DARK -> binding.switchTheme.setImageResource(R.drawable.ic_moon2)
            ThemeTools.MODE.LIGHT -> binding.switchTheme.setImageResource(R.drawable.ic_sun)
            else -> binding.switchTheme.setImageResource(R.drawable.ic_moon_auto)
        }
    }

    override fun getViewModelClass(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setTitleText(string: String) {
        binding.timetableTitle.text = string
        binding.timetableNameCard.visibility = VISIBLE
    }

    override fun setTimetableName(String: String) {
        binding.timetableName.text = String
        binding.timetableNameCard.visibility = VISIBLE
    }

    override fun setSingleTitle(string: String) {
        binding.timetableTitle.text = string
        binding.timetableNameCard.visibility = GONE
    }

    override fun setTimelineTitleText(string: String) {
        binding.todayTitle.text = string
    }
}
