package com.succh.unifeed.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleShareTest {

    @Test
    fun shareTextJoinsTitleAndLink() {
        assertEquals(
            "协程入门\nhttps://example.com/a",
            ArticleShare.shareText("协程入门", "https://example.com/a")
        )
    }

    @Test
    fun shareTextOmitsNullLink() {
        assertEquals("仅标题", ArticleShare.shareText("仅标题", null))
    }

    @Test
    fun shareTextOmitsBlankLink() {
        assertEquals("仅标题", ArticleShare.shareText(" 仅标题 ", "  "))
    }

    @Test
    fun shareTextFallsBackToLinkWhenTitleBlank() {
        assertEquals("https://example.com/a", ArticleShare.shareText("  ", "https://example.com/a"))
    }
}
