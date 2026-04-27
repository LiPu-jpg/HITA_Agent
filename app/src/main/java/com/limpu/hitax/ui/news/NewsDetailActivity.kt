package com.limpu.hitax.ui.news

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.webkit.*
import com.limpu.hitax.databinding.ActivityNewsDetailBinding
import com.limpu.style.base.BaseActivity

@Suppress("DEPRECATION")
class NewsDetailActivity : BaseActivity<NewsViewModel, ActivityNewsDetailBinding>() {
    override fun initViewBinding(): ActivityNewsDetailBinding {
        return ActivityNewsDetailBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    override fun getViewModelClass(): Class<NewsViewModel> {
        return NewsViewModel::class.java
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
