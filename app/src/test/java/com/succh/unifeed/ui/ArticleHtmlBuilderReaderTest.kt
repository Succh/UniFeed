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

    @Test
    fun wrapVideoUsesPosterAndDefersSrc() {
        val html = ArticleHtmlBuilder.normalize(
            """<video src="/a.mp4" poster="/p.jpg"></video>""",
            "https://example.com/post"
        )
        val doc = Jsoup.parseBodyFragment(html)
        val ph = doc.selectFirst("div.media-ph")
        requireNotNull(ph)
        assertEquals("video", ph.attr("data-kind"))
        assertEquals(1, ph.select(".media-play").size)
        val video = ph.selectFirst("video")
        requireNotNull(video)
        assertTrue(video.attr("src").isBlank())
        assertEquals("https://example.com/a.mp4", video.attr("data-src"))
        assertEquals("https://example.com/p.jpg", video.attr("poster"))
        assertTrue(ph.selectFirst(".media-poster")!!.attr("style").contains("https://example.com/p.jpg"))
    }

    @Test
    fun wrapAudioShowsPlaceholderWithoutBlankGap() {
        val html = ArticleHtmlBuilder.normalize(
            """<audio><source src="https://cdn.example.com/a.mp3" type="audio/mpeg"></audio>""",
            null
        )
        val doc = Jsoup.parseBodyFragment(html)
        val ph = doc.selectFirst("div.media-ph")
        requireNotNull(ph)
        assertEquals("audio", ph.attr("data-kind"))
        assertEquals(1, ph.select(".media-play").size)
        assertEquals("音频", ph.selectFirst(".media-caption")!!.text())
        val source = ph.selectFirst("audio source")
        requireNotNull(source)
        assertTrue(source.attr("src").isBlank())
        assertEquals("https://cdn.example.com/a.mp3", source.attr("data-src"))
    }

    @Test
    fun wrapYoutubeIframeBecomesClickToLoadPoster() {
        val html = ArticleHtmlBuilder.normalize(
            """<iframe src="https://www.youtube.com/embed/dQw4w9wgGcQ"></iframe>""",
            null
        )
        val doc = Jsoup.parseBodyFragment(html)
        assertEquals(0, doc.select("iframe").size)
        val ph = doc.selectFirst("div.media-ph")
        requireNotNull(ph)
        assertEquals("iframe", ph.attr("data-kind"))
        assertEquals("https://www.youtube.com/embed/dQw4w9wgGcQ", ph.attr("data-src"))
        assertEquals(1, ph.select(".media-play").size)
        val poster = ArticleHtmlBuilder.iframePosterUrl("https://www.youtube.com/embed/dQw4w9wgGcQ")
        assertEquals("https://img.youtube.com/vi/dQw4w9wgGcQ/hqdefault.jpg", poster)
        assertTrue(ph.selectFirst(".media-poster")!!.attr("style").contains(poster!!))
    }

    @Test
    fun footnotesWireRefAndBackLink() {
        val html = ArticleHtmlBuilder.normalize(
            """
            <p>正文<sup><a href="#fn1">1</a></sup></p>
            <ol class="footnotes"><li id="fn1">注释内容</li></ol>
            """.trimIndent(),
            "https://example.com/post"
        )
        val doc = Jsoup.parseBodyFragment(html)
        val ref = doc.selectFirst("a.fn-ref")
        requireNotNull(ref)
        assertEquals("#fn1", ref.attr("href"))
        assertTrue(ref.id().isNotBlank())
        val def = doc.getElementById("fn1")
        requireNotNull(def)
        assertTrue(def.hasClass("fn-def"))
        val back = def.selectFirst("a.fn-back")
        requireNotNull(back)
        assertEquals("#${ref.id()}", back.attr("href"))
        assertEquals("↩", back.text())
    }

    @Test
    fun footnotesKeepExistingBackrefAndCollapseAbsoluteHash() {
        val html = ArticleHtmlBuilder.normalize(
            """
            <p>见<sup><a href="https://example.com/post#fn:1">1</a></sup></p>
            <div id="fn:1">说明 <a class="footnote-backref" href="#fnref1">↩</a></div>
            """.trimIndent(),
            "https://example.com/post"
        )
        val doc = Jsoup.parseBodyFragment(html)
        val ref = doc.selectFirst("a.fn-ref")
        requireNotNull(ref)
        assertEquals("#fn:1", ref.attr("href"))
        val back = doc.selectFirst("a.fn-back")
        requireNotNull(back)
        assertEquals("#${ref.id()}", back.attr("href"))
        assertEquals(1, doc.select("a.fn-back").size)
    }

    @Test
    fun ordinaryInPageHashIsNotTreatedAsFootnote() {
        val html = ArticleHtmlBuilder.normalize(
            """<p><a href="#intro">目录</a></p><h2 id="intro">引言</h2>""",
            null
        )
        val doc = Jsoup.parseBodyFragment(html)
        assertEquals(0, doc.select("a.fn-ref").size)
        assertEquals(0, doc.select("a.fn-back").size)
        assertEquals("#intro", doc.selectFirst("a[href=#intro]")!!.attr("href"))
    }
}
