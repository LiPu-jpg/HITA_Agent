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
        return (activity as BaseActivity<*, *>).getColorPrimary()
    }

    fun getColorPrimaryDisabled(): Int {
        return (activity as BaseActivity<*, *>).getColorPrimaryDisabled()
    }

    fun getTextColorSecondary(): Int {
        return (activity as BaseActivity<*, *>).getTextColorSecondary()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
