package com.dai.babyfeed

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
    private lateinit var bridge: Bridge
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
        s.allowFileAccess = false
        s.allowContentAccess = false

        bridge = Bridge(this)
        web.addJavascriptInterface(bridge, "Android")

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            // 万一网页还是走了 <a download> / blob: 下载，这里兜住，不让它变成"复制文字"
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (url.startsWith("blob:") || url.startsWith("data:text/csv")) {
                    view.post { handleBlobDownload(url) }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageLoaded = true
                pendingJs?.let {
                    view.evaluateJavascript(it, null)
                    pendingJs = null
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Log.d(TAG, "JS: ${msg.message()} @${msg.lineNumber()}")
                return true
            }
        }

        CookieManager.getInstance().setAcceptCookie(true)
        web.loadUrl(HOME)
        handleIntent(intent)
    }

    /** blob: 下载兜底：从 JS 里读出文本，走原生保存 */
    private fun handleBlobDownload(url: String) {
        val js = """
            (function(){
              try{
                var xhr=new XMLHttpRequest();
                xhr.open('GET','$url',false);
                xhr.send(null);
                var t=xhr.responseText||'';
                Android.exportCsv(t,'喂奶记录_备用.csv',0);
              }catch(e){ Android.exportCsv('','导出失败:'+e,0); }
            })();
        """.trimIndent()
        web.evaluateJavascript(js, null)
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

    override fun onBackPressed() {
        // 页面里没有二级路由，按返回直接交给系统处理
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        private const val TAG = "BabyFeed"
        private const val HOME = "https://appassets.androidplatform.net/assets/index.html"
    }
}
