package com.limpu.hitax.ui.base

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.viewbinding.ViewBinding
import com.limpu.style.R
import com.limpu.style.ThemeTools

@Suppress("DEPRECATION")
abstract class HiltBaseActivity<V : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: V

    protected abstract fun initViewBinding(): V
    protected abstract fun initViews()

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = when (ThemeTools.getThemeMode(this)) {
            ThemeTools.MODE.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeTools.MODE.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)
        setWindowParams(true, null, false)
        binding = initViewBinding()
        setContentView(binding.root)
        initViews()
    }

    protected fun setWindowParams(statusBar: Boolean, darkColor: Boolean? = null, navi: Boolean) {
        val dc = darkColor ?: isLightColor(getBackgroundColorBottom())
        window.decorView.systemUiVisibility =
            if (dc) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else View.SYSTEM_UI_FLAG_VISIBLE
        if (statusBar) window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        if (navi) window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    }

    protected open fun setTranslucentStatusBar() {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                )
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = Color.TRANSPARENT
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = Color.TRANSPARENT
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    )
                }
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            }
        }
    }

    protected fun setToolbarActionBack(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun isLightColor(@ColorInt color: Int): Boolean {
        return ColorUtils.calculateLuminance(color) >= 0.5
    }

    fun getColorPrimary(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
        return typedValue.data
    }

    fun getTextColorSecondary(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.textColorSecondary, typedValue, true)
        return typedValue.data
    }

    fun getColorPrimaryDisabled(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.colorPrimaryDisabled, typedValue, true)
        return typedValue.data
    }

    fun getColorControlNormal(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.colorControlNormal, typedValue, true)
        return typedValue.data
    }

    fun getBackgroundColorBottom(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.backgroundColorBottom, typedValue, true)
        return typedValue.data
    }

    fun getBackgroundColorSecondAsTint(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.backgroundColorSecondAsTint, typedValue, true)
        return typedValue.data
    }
}
