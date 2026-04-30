package com.limpu.style.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding
import com.limpu.style.R
import com.limpu.style.base.BaseActivity


/**
 * 透明背景的底部弹窗Fragment
 */
@Suppress("DEPRECATION")
abstract class TransparentDialog<V:ViewBinding> : DialogFragment() {
    lateinit var binding:V
    fun getColorPrimary(): Int {
        val act = activity ?: return 0
        return if (act is BaseActivity<*, *>) {
            act.getColorPrimary()
        } else {
            val typedValue = android.util.TypedValue()
            act.theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
            typedValue.data
        }
    }

    fun getColorControlNormal(): Int {
        val act = activity ?: return 0
        return if (act is BaseActivity<*, *>) {
            act.getColorControlNormal()
        } else {
            val typedValue = android.util.TypedValue()
            act.theme.resolveAttribute(R.attr.colorControlNormal, typedValue, true)
            typedValue.data
        }
    }

    fun getTextColorSecondary(): Int {
        val act = activity ?: return 0
        return if (act is BaseActivity<*, *>) {
            act.getTextColorSecondary()
        } else {
            val typedValue = android.util.TypedValue()
            act.theme.resolveAttribute(R.attr.textColorSecondary, typedValue, true)
            typedValue.data
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        setStyle(STYLE_NO_TITLE, R.style.TransparentDialogTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val contextThemeWrapper = ContextThemeWrapper(context,R.style.AppTheme)
        val v = inflater.cloneInContext(contextThemeWrapper)
            .inflate(getLayoutId(), container, false)
        binding = initViewBinding(v)
        initViews(binding.root)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window
        val attributes: WindowManager.LayoutParams? = window?.attributes
        attributes?.width = WindowManager.LayoutParams.MATCH_PARENT //满屏
        window?.attributes = attributes;
    }

    protected abstract fun getLayoutId():Int
    protected abstract fun initViewBinding(v:View):V
    protected abstract fun initViews(v: View)
}