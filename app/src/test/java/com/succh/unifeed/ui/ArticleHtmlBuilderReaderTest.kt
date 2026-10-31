package com.succh.unifeed.ui

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHtmlBuilderReaderTest {

    @Test
    fun wrapCodeBlocksAddsHorizontalScrollContainer() {
        val html = ArticleHtmlBuilder.normalize(
            """<p>说明</p><pre><code>fun veryLongIdentifierThatShouldNotWrap() = 1</code></pre>""",
            "https://example.com/a"
        )
        val doc = Jsoup.parseBodyFragment(html)
        val wrap = doc.selectFirst("div.code-scroll")
        requireNotNull(wrap)
        assertEquals(1, wrap.select("> pre").size)
        assertEquals(1, doc.select("div.code-scroll").size)
        assertFalse(wrap.parent()?.tagName().equals("pre", ignoreCase = true))
    }

    @Test
    fun wrapCodeBlocksDoesNotNestTwice() {
        val html = ArticleHtmlBuilder.normalize(
            """<div class="code-scroll"><pre>already</pre></div>""",
            null
        )
        val doc = Jsoup.parseBodyFragment(html)
        assertEquals(1, doc.select("div.code-scroll").size)
        assertEquals(1, doc.select("div.code-scroll > pre").size)
        assertEquals(0, doc.select("div.code-scroll div.code-scroll").size)
    }
}
