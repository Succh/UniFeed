package com.succh.unifeed.desktop

import com.succh.unifeed.ui.theme.formatRelativeTimeZh

/** 小部件文案：空标题/空来源给占位，时间走相对时间。 */
object UnreadWidgetContent {
    const val MAX_ITEMS = 5

    fun displayTitle(title: String): String = title.trim().ifEmpty { "无标题" }

    fun displayFeed(feedTitle: String): String = feedTitle.trim().ifEmpty { "未知来源" }

    fun displayTime(publishedAt: Long, now: Long = System.currentTimeMillis()): String =
        formatRelativeTimeZh(publishedAt, now)
}
