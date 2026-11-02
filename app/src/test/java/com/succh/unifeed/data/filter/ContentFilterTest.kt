package com.succh.unifeed.data.filter

import com.succh.unifeed.data.db.entity.FilterRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterTest {

    @Test
    fun hideTakesPrecedenceOverMarkRead() {
        val rules = listOf(
            rule("广告", FilterRule.ACTION_MARK_READ),
            rule("广告", FilterRule.ACTION_HIDE)
        )
        assertEquals(FilterAction.HIDE, ContentFilter.match("今日广告投放", rules))
    }

    @Test
    fun markReadWhenOnlyMarkReadMatches() {
        val rules = listOf(rule("招聘", FilterRule.ACTION_MARK_READ))
        assertEquals(FilterAction.MARK_READ, ContentFilter.match("春季招聘启动", rules))
    }

    @Test
    fun matchIgnoresCase() {
        val rules = listOf(rule("Ad", FilterRule.ACTION_HIDE))
        assertEquals(FilterAction.HIDE, ContentFilter.match("sponsored AD inside", rules))
    }

    @Test
    fun noMatchReturnsNull() {
        val rules = listOf(rule("广告", FilterRule.ACTION_HIDE))
        assertNull(ContentFilter.match("今日要闻", rules))
        assertNull(ContentFilter.match("今日广告", emptyList()))
        assertNull(ContentFilter.match("", listOf(rule("广告", FilterRule.ACTION_HIDE))))
    }

    @Test
    fun blankKeywordIsIgnored() {
        val rules = listOf(rule("  ", FilterRule.ACTION_HIDE), rule("促销", FilterRule.ACTION_MARK_READ))
        assertEquals(FilterAction.MARK_READ, ContentFilter.match("大促销开始", rules))
    }

    @Test
    fun rulesForFeedIncludesGlobalWhenEnabled() {
        val global = rule("广告", FilterRule.ACTION_HIDE)
        val other = rule("招聘", FilterRule.ACTION_MARK_READ, feedId = 2L)
        val mine = rule("促销", FilterRule.ACTION_HIDE, feedId = 1L)
        val applicable = ContentFilter.rulesForFeed(1L, applyGlobal = true, listOf(global, other, mine))
        assertEquals(2, applicable.size)
        assertTrue(applicable.contains(global))
        assertTrue(applicable.contains(mine))
    }

    @Test
    fun rulesForFeedSkipsGlobalWhenDisabled() {
        val global = rule("广告", FilterRule.ACTION_HIDE)
        val mine = rule("促销", FilterRule.ACTION_HIDE, feedId = 1L)
        val applicable = ContentFilter.rulesForFeed(1L, applyGlobal = false, listOf(global, mine))
        assertEquals(listOf(mine), applicable)
    }

    private fun rule(keyword: String, action: String, feedId: Long? = null) =
        FilterRule(keyword = keyword, action = action, feedId = feedId)
}
