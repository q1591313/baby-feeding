package com.dai.babyfeed

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

/**
 * 桌面小组件（2x1）：
 * - 空闲：显示「亲喂 / 吸奶」两个快捷入口，点了打开 App 并弹出左右选择
 * - 计时中：显示类型和实时走秒的计时器（Chronometer 自己走，不耗电），右侧「结束」一键停止
 */
class FeedWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(ctx, mgr, id)
    }

    override fun onEnabled(ctx: Context) {
        updateAll(ctx)
    }

    companion object {

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, FeedWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) update(ctx, mgr, id)
        }

        private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val prefs = ctx.getSharedPreferences(Bridge.PREF, Context.MODE_PRIVATE)
            val raw = prefs.getString(Bridge.KEY_SESSION, null)
            val views = RemoteViews(ctx.packageName, R.layout.widget_feed)

            if (raw.isNullOrBlank()) {
                views.setViewVisibility(R.id.wg_running, View.GONE)
                views.setViewVisibility(R.id.wg_idle, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.wg_btn_breast, pi(ctx, "start_breast", id))
                views.setOnClickPendingIntent(R.id.wg_btn_pump, pi(ctx, "start_pump", id))
                views.setOnClickPendingIntent(R.id.wg_root, pi(ctx, "open", id))
            } else {
                val o = try { JSONObject(raw) } catch (e: Exception) { null }
                val type = o?.optString("type") ?: "breast"
                val side = o?.optString("side") ?: ""
                val start = o?.optLong("start", System.currentTimeMillis()) ?: System.currentTimeMillis()

                val typeText = when (type) {
                    "pump" -> "吸奶中"
                    "bottle" -> "瓶喂中"
                    else -> "亲喂中"
                }
                val sideText = when (side) {
                    "left" -> " · 左侧"
                    "right" -> " · 右侧"
                    "both" -> " · 双侧"
                    else -> ""
                }

                views.setViewVisibility(R.id.wg_idle, View.GONE)
                views.setViewVisibility(R.id.wg_running, View.VISIBLE)
                views.setTextViewText(R.id.wg_title, typeText + sideText)
                // Chronometer 基于开始时刻自己走时，无需每秒刷新
                val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - start)
                views.setChronometer(R.id.wg_chron, base, null, true)
                views.setOnClickPendingIntent(R.id.wg_end, pi(ctx, "end", id))
                views.setOnClickPendingIntent(R.id.wg_root, pi(ctx, "open", id))
            }
            mgr.updateAppWidget(id, views)
        }

        private fun pi(ctx: Context, action: String, id: Int): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                putExtra("action", action)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("bf://widget/$action/$id")
            }
            return PendingIntent.getActivity(
                ctx,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
