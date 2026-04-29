package com.limpu.hitax.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.limpu.style.base.BaseActivity

abstract class HiltBaseFragment<V : ViewBinding> : Fragment() {

    protected var binding: V? = null

    protected abstract fun initViewBinding(): V
    protected abstract fun initViews(view: View)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = initViewBinding()
        binding?.let { initViews(it.root) }
        return binding?.root
    }

    fun getColorPrimary(): Int {
        val act = activity ?: return 0
        return if (act is BaseActivity<*, *>) {
            act.getColorPrimary()
        } else {
            val typedValue = android.util.TypedValue()
            act.theme.resolveAttribute(com.limpu.style.R.attr.colorPrimary, typedValue, true)
            typedValue.data
        }
    }

    fun getColorPrimaryDisabled(): Int {
        val act = activity ?: return 0
        return if (act is BaseActivity<*, *>) {
            act.getColorPrimaryDisabled()
        } else {
            val typedValue = android.util.TypedValue()
            act.theme.resolveAttribute(com.limpu.style.R.attr.colorPrimaryDisabled, typedValue, true)
            typedValue.data
        }
    }

    fun getTextColorSecondary(): Int {
        val act = activity ?: return 0
        return if (act is BaseActivity<*, *>) {
            act.getTextColorSecondary()
        } else {
            val typedValue = android.util.TypedValue()
            act.theme.resolveAttribute(com.limpu.style.R.attr.textColorSecondary, typedValue, true)
            typedValue.data
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
