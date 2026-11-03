package com.succh.unifeed.desktop

/**
 * 角标展示数：关闭开关或未读为 0 时一律清角标。
 * 未读口径与 EntryDao 一致：isRead = 0 AND isHidden = 0。
 */
object UnreadBadgeCount {
    fun display(unread: Int, enabled: Boolean): Int {
        if (!enabled) return 0
        return unread.coerceAtLeast(0)
    }
}
