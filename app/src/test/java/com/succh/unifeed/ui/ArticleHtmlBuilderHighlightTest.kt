package com.succh.unifeed.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHtmlBuilderHighlightTest {

    @Test
    fun highlightPlainWrapsKeyword() {
        val html = ArticleHtmlBuilder.highlightPlain("Kotlin 协程入门", "协程")
        assertTrue(html.contains("<mark class='search-hit'>协程</mark>"))
    }

    @Test
    fun highlightHtmlSkipsAttributes() {
        val html = ArticleHtmlBuilder.highlightHtml(
            """<p>正文提到 Compose</p><img src="compose.png" alt="compose">""",
            "compose"
        )
        assertTrue(html.contains("<mark class=\"search-hit\">Compose</mark>") || html.contains("<mark class='search-hit'>Compose</mark>"))
        assertTrue(html.contains("src=\"compose.png\"") || html.contains("src=\"compose.png\""))
        assertFalse(html.contains("<mark class=\"search-hit\">compose.png"))
    }
}
