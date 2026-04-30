package com.limpu.hitax.ui.resource

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.activity.viewModels
import com.limpu.hitax.databinding.ActivityInternalWebBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InternalWebActivity : HiltBaseActivity<ActivityInternalWebBinding>() {
    protected val viewModel: InternalWebViewModel by viewModels()

    override fun initViewBinding(): ActivityInternalWebBinding =
        ActivityInternalWebBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun initViews() {
        binding.toolbar.title = intent.getStringExtra("title") ?: ""
        binding.webview.webChromeClient = WebChromeClient()
        binding.webview.webViewClient = WebViewClient()
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.settings.loadWithOverviewMode = true
        binding.webview.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        binding.webview.loadUrl(intent.getStringExtra("url") ?: "about:blank")
    }
}

class InternalWebViewModel : androidx.lifecycle.ViewModel()