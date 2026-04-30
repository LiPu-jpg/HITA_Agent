package com.limpu.style.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.limpu.style.R
import com.limpu.style.base.BaseActivity

/**
 * 透明背景的底部弹窗Fragment
 */
@Suppress("DEPRECATION")
abstract class TransparentBottomSheetDialog<V:ViewBinding> : BottomSheetDialogFragment() {
    lateinit var binding:V

    private var cachedColorPrimary: Int? = null
    private var cachedColorControlNormal: Int? = null
    private var cachedTextColorSecondary: Int? = null

    fun getColorPrimary(): Int {
        cachedColorPrimary?.let { return it }
        val act = activity ?: return 0
        val result = when (act) {
            is BaseActivity<*, *> -> act.getColorPrimary()
            else -> {
                val typedValue = android.util.TypedValue()
                act.theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
                typedValue.data
            }
        }
        cachedColorPrimary = result
        return result
    }

    fun getColorControlNormal(): Int {
        cachedColorControlNormal?.let { return it }
        val act = activity ?: return 0
        val result = when (act) {
            is BaseActivity<*, *> -> act.getColorControlNormal()
            else -> {
                val typedValue = android.util.TypedValue()
                act.theme.resolveAttribute(R.attr.colorControlNormal, typedValue, true)
                typedValue.data
            }
        }
        cachedColorControlNormal = result
        return result
    }

    fun getTextColorSecondary(): Int {
        cachedTextColorSecondary?.let { return it }
        val act = activity ?: return 0
        val result = when (act) {
            is BaseActivity<*, *> -> act.getTextColorSecondary()
            else -> {
                val typedValue = android.util.TypedValue()
                act.theme.resolveAttribute(R.attr.textColorSecondary, typedValue, true)
                typedValue.data
            }
        }
        cachedTextColorSecondary = result
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialogTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val contextThemeWrapper = ContextThemeWrapper(context,R.style.AppTheme)
        val v = inflater.cloneInContext(contextThemeWrapper)
            .inflate(getLayoutId(), container, false)
        binding = initViewBinding(v)
        initViews(binding.root)
        return binding.root
    }

    protected abstract fun getLayoutId():Int
    protected abstract fun initViewBinding(v:View):V
    protected abstract fun initViews(v: View)
}