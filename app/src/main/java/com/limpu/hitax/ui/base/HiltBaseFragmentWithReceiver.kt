package com.limpu.hitax.ui.base

import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.viewbinding.ViewBinding

abstract class HiltBaseFragmentWithReceiver<V : ViewBinding> : HiltBaseFragment<V>() {
    abstract var receiver: BroadcastReceiver
    abstract fun getIntentFilter(): IntentFilter
    override fun onStart() {
        super.onStart()
        requireContext().registerReceiver(receiver, getIntentFilter())
    }
    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(receiver)
    }
}
