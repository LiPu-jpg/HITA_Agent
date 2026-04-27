package com.limpu.hitax.ui.news

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.webkit.*
import androidx.activity.viewModels
import com.limpu.hitax.databinding.ActivityNewsDetailBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NewsDetailActivity : HiltBaseActivity<ActivityNewsDetailBinding>() {

    protected val viewModel: NewsViewModel by viewModels()

    override fun initViewBinding(): ActivityNewsDetailBinding {
        return ActivityNewsDetailBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun initViews() {
        binding.webview.webChromeClient = WebChromeClient()
        binding.webview.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        binding.webview.settings.loadWithOverviewMode = true
        binding.webview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
            }
        }
        binding.collapse.setExpandedTitleColor(Color.TRANSPARENT)
        binding.collapse.setCollapsedTitleTextColor(Color.TRANSPARENT)
        viewModel.metaData.observe(this) {
            it.data?.let { m ->
                if (m["time"] != null) binding.detailTime.text = Html.fromHtml(m["time"].toString())
                binding.webview.loadUrl("http://www.hitsz.edu.cn" + intent.getStringExtra("link"))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.detailTitle.text = intent.getStringExtra("title")
        viewModel.refresh(intent.getStringExtra("link") ?: "")
    }
}
