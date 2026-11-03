package com.succh.unifeed.desktop

import android.content.Context
import com.succh.unifeed.UniFeedApp
import com.succh.unifeed.ui.ReaderPrefs
import me.leolin.shortcutbadger.ShortcutBadger

/**
 * 应用图标未读角标：走 ShortcutBadger 启动器广播，不发通知、不常驻通知。
 * 不支持角标的启动器上 apply/remove 会失败，必须吞掉异常，绝不崩溃。
 */
object UnreadBadge {
    fun sync(context: Context, unreadCount: Int) {
        // 每次从 SharedPreferences 读开关，避免设置页与 Application 各持一份内存状态
        val enabled = ReaderPrefs.unreadBadgeEnabled(context)
        val display = UnreadBadgeCount.display(unreadCount, enabled)
        applySilently(context, display)
    }

    suspend fun syncFromDb(context: Context) {
        val app = context.applicationContext as? UniFeedApp ?: return
        sync(app, app.repository.getUnreadCount())
    }

    private fun applySilently(context: Context, count: Int) {
        val appContext = context.applicationContext
        runCatching {
            if (count > 0) {
                ShortcutBadger.applyCount(appContext, count)
            } else {
                ShortcutBadger.removeCount(appContext)
            }
        }
    }
}
