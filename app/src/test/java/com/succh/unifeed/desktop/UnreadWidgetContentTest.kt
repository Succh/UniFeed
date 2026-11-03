package com.succh.unifeed.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadWidgetContentTest {

    @Test
    fun blankTitleFallsBack() {
        assertEquals("无标题", UnreadWidgetContent.displayTitle("  "))
        assertEquals("今日热点", UnreadWidgetContent.displayTitle(" 今日热点 "))
    }

    @Test
    fun blankFeedFallsBack() {
        assertEquals("未知来源", UnreadWidgetContent.displayFeed(""))
        assertEquals("36氪", UnreadWidgetContent.displayFeed("36氪"))
    }

    @Test
    fun relativeTimeForRecentArticle() {
        val now = 1_700_000_000_000L
        assertEquals("刚刚", UnreadWidgetContent.displayTime(now - 10_000L, now))
        assertEquals("2小时前", UnreadWidgetContent.displayTime(now - 2 * 60 * 60 * 1000L, now))
        assertEquals("", UnreadWidgetContent.displayTime(0L, now))
    }
}
