package com.succh.unifeed.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntrySearchTest {

    @Test
    fun escapeLikeEscapesWildcards() {
        assertEquals("100\\%", EntrySearch.escapeLike("100%"))
        assertEquals("a\\_b", EntrySearch.escapeLike("a_b"))
        assertEquals("\\\\path", EntrySearch.escapeLike("\\path"))
    }

    @Test
    fun matchesTitleSummaryAndContent() {
        assertTrue(EntrySearch.matches("Kotlin 协程", null, null, "协程"))
        assertTrue(EntrySearch.matches("标题", "摘要里有 Room", null, "room"))
        assertTrue(EntrySearch.matches("标题", null, "<p>正文提到 <b>Compose</b></p>", "compose"))
        assertFalse(EntrySearch.matches("标题", "摘要", "<p>正文</p>", "找不到"))
        assertFalse(EntrySearch.matches("标题", "摘要", "正文", "  "))
    }

    @Test
    fun stripTagsRemovesHtml() {
        assertEquals("Hello World", EntrySearch.stripTags("<p>Hello <b>World</b></p>"))
    }

    @Test
    fun findRangesIgnoresCaseAndFindsAll() {
        val ranges = EntrySearch.findRanges("Room 与 room 还有 ROOM", "room")
        assertEquals(3, ranges.size)
        assertEquals(0, ranges[0].first)
        assertEquals(8, ranges[1].first)
        assertEquals(16, ranges[2].first)
    }

    @Test
    fun resultSnippetFallsBackToContent() {
        val snippet = EntrySearch.resultSnippet(
            title = "标题",
            summary = "无关摘要",
            content = "<p>前面一些字 Compose 很适合写 UI</p>",
            query = "compose"
        )
        assertTrue(snippet.contains("Compose", ignoreCase = true))
    }
}
