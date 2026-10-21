package com.dai.babyfeed

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader

/**
 * 主界面：一个全屏 WebView，加载 assets 里的 index.html。
 * 用 WebViewAssetLoader 把本地资产映射成 https 域名，
 * 这样 localStorage 才有正常 origin，记录不会丢。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var pageLoaded = false
    private var pendingJs: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(web)
        web.setBackgroundColor(getColor(R.color.bg))

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = false
        s.useWideViewPort = false
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.textZoom = 100
        s.mediaPlaybackRequiresUserGesture = true

        web.addJavascriptInterface(Bridge(this), "Android")

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                pageLoaded = true
                pendingJs?.let {
                    view.evaluateJavascript(it, null)
                    pendingJs = null
                }
            }
        }

        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** 小组件点击后带着 action 进来 */
    private fun handleIntent(intent: Intent?) {
        when (intent?.getStringExtra("action")) {
            "start_breast" -> runJs("openSide('breast')")
            "start_pump"   -> runJs("openSide('pump')")
            "end"          -> runJs("if(typeof active!=='undefined' && active){ endActive(); }")
            else -> {}
        }
    }

    private fun runJs(js: String) {
        if (!pageLoaded) {
            pendingJs = js
            return
        }
        web.post { web.evaluateJavascript(js, null) }
    }

    override fun onResume() {
        super.onResume()
        FeedWidget.updateAll(this)
    }

    override fun onPause() {
        super.onPause()
        FeedWidget.updateAll(this)
    }
}
