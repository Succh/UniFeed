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

    @Test
    fun wrapWideTablesAddsHorizontalScrollContainer() {
        val html = ArticleHtmlBuilder.normalize(
            """<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>""",
            null
        )
        val doc = Jsoup.parseBodyFragment(html)
        assertEquals(1, doc.select("div.table-scroll").size)
        assertEquals(1, doc.select("div.table-scroll > table").size)
        assertEquals(2, doc.select("div.table-scroll th").size)
        assertEquals(2, doc.select("div.table-scroll td").size)
    }

    @Test
    fun wrapWideTablesSkipsNestedAndAlreadyWrapped() {
        val nested = ArticleHtmlBuilder.normalize(
            """<table><tr><td><table><tr><td>内层</td></tr></table></td></tr></table>""",
            null
        )
        val nestedDoc = Jsoup.parseBodyFragment(nested)
        assertEquals(1, nestedDoc.select("div.table-scroll").size)
        assertEquals(1, nestedDoc.select("div.table-scroll > table").size)

        val already = ArticleHtmlBuilder.normalize(
            """<div class="table-scroll"><table><tr><td>x</td></tr></table></div>""",
            null
        )
        val alreadyDoc = Jsoup.parseBodyFragment(already)
        assertEquals(1, alreadyDoc.select("div.table-scroll").size)
        assertEquals(0, alreadyDoc.select("div.table-scroll div.table-scroll").size)
    }
}
