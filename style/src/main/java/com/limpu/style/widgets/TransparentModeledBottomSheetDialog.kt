package com.limpu.style.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.limpu.style.R
import com.limpu.style.base.BaseActivity

/**
 * 透明背景的底部弹窗Fragment
 */
@Suppress("DEPRECATION")
abstract class TransparentModeledBottomSheetDialog<T : ViewModel, V : ViewBinding> :
    BottomSheetDialogFragment() {
    var binding: V? = null
    private var viewModelInit: Boolean = false
    protected lateinit var viewModel: T

    /**
     * 以下四个函数的作用和BaseActivity里的四个函数类似
     */
    protected abstract fun getViewModelClass(): Class<T>
    protected abstract fun initViews(view: View)

    fun getColorPrimary(): Int {
        val act = activity
        return if (act is BaseActivity<*, *>) {
            act.getColorPrimary()
        } else {
            val typedValue = android.util.TypedValue()
            act?.theme?.resolveAttribute(R.attr.colorPrimary, typedValue, true)
            typedValue.data
        }
    }

    fun getColorControlNormal(): Int {
        val act = activity
        return if (act is BaseActivity<*, *>) {
            act.getColorControlNormal()
        } else {
            val typedValue = android.util.TypedValue()
            act?.theme?.resolveAttribute(R.attr.colorControlNormal, typedValue, true)
            typedValue.data
        }
    }

    fun getTextColorSecondary(): Int {
        val act = activity
        return if (act is BaseActivity<*, *>) {
            act.getTextColorSecondary()
        } else {
            val typedValue = android.util.TypedValue()
            act?.theme?.resolveAttribute(R.attr.textColorSecondary, typedValue, true)
            typedValue.data
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val contextThemeWrapper =
            ContextThemeWrapper(context, R.style.AppTheme) // your app theme here
        val v = inflater.cloneInContext(contextThemeWrapper)
            .inflate(getLayoutId(), container, false)
        binding = initViewBinding(v)
        getViewModelClass().let {
            viewModel = if (it.superclass == AndroidViewModel::class.java) {
                ViewModelProvider(
                    this,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
                ).get(it)
            } else {
                ViewModelProvider(this, defaultViewModelProviderFactory).get(it)
            }
        }
        viewModelInit = true
        binding?.root?.let { initViews(it) }
        return binding?.root
    }

    protected abstract fun getLayoutId(): Int
    protected abstract fun initViewBinding(v: View): V
}