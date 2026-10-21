package com.dai.babyfeed

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * JS 与原生之间的桥。
 * - onSessionChanged：网页里开始/结束计时时，把状态写给小组件读的 SharedPreferences
 * - exportCsv：把 CSV 写进系统「下载」目录，可选直接拉起分享（微信）
 *
 * 注意：所有对外方法都必须标 @JavascriptInterface，否则新版 WebView 不会暴露给 JS。
 */
class Bridge(private val ctx: Context) {

    @JavascriptInterface
    fun onSessionChanged(json: String) {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (json.isBlank() || json == "null") {
            prefs.edit().clear().apply()
        } else {
            prefs.edit().putString(KEY_SESSION, json).apply()
        }
        FeedWidget.updateAll(ctx)
    }

    @JavascriptInterface
    fun exportCsv(content: String, filename: String, wantShare: Int) {
        val uri = saveToDownloads(content, filename)
        if (uri == null) {
            toast("导出失败，请重试")
            return
        }
        if (wantShare == 1) {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                ctx.startActivity(Intent.createChooser(share, "分享喂奶记录"))
            } catch (e: Exception) {
                toast("已保存到「下载」目录")
            }
        } else {
            toast("已保存到「下载」目录：$filename")
        }
    }

    /** 供 MainActivity 的下载监听复用 */
    fun saveFromDownload(content: String, filename: String): Boolean {
        return saveToDownloads(content, filename) != null
    }

    private fun saveToDownloads(content: String, filename: String): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            null
        }
    }

    /** 必须切回主线程才能弹 Toast / 分享 */
    private fun toast(msg: String) {
        val act = ctx as? MainActivity
        if (act != null) {
            act.runOnUiThread { Toast.makeText(act, msg, Toast.LENGTH_LONG).show() }
        }
    }

    companion object {
        const val PREF = "bf_widget"
        const val KEY_SESSION = "session"
    }
}
