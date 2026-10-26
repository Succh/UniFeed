package com.succh.unifeed.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserTest {

    private val parser = RssParser()

    @Test
    fun parseRss20() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>少数派</title>
                <link>https://sspai.com</link>
                <description>desc</description>
                <item>
                  <title>文章一</title>
                  <link>https://sspai.com/a</link>
                  <guid>g1</guid>
                  <description>摘要</description>
                  <pubDate>Sat, 24 Oct 2026 00:00:00 +0000</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val feed = parser.parse(xml, "https://sspai.com/feed")
        assertEquals("少数派", feed.title)
        assertEquals(1, feed.entries.size)
        assertEquals("文章一", feed.entries[0].title)
        assertEquals("https://sspai.com/a", feed.entries[0].link)
    }

    @Test
    fun parseAtom() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>阮一峰的网络日志</title>
              <link rel="alternate" href="https://www.ruanyifeng.com/blog/"/>
              <entry>
                <title>周刊</title>
                <id>id-1</id>
                <link href="https://www.ruanyifeng.com/blog/x"/>
                <updated>2026-10-24T00:00:00Z</updated>
                <summary>hello</summary>
              </entry>
            </feed>
        """.trimIndent()
        val feed = parser.parse(xml, "https://www.ruanyifeng.com/blog/atom.xml")
        assertEquals("阮一峰的网络日志", feed.title)
        assertEquals(1, feed.entries.size)
        assertEquals("https://www.ruanyifeng.com/blog/x", feed.entries[0].link)
    }

    @Test
    fun parseRdf() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns="http://purl.org/rss/1.0/">
              <channel>
                <title>Steam</title>
                <link>https://store.steampowered.com/</link>
              </channel>
              <item>
                <title>新闻</title>
                <link>https://store.steampowered.com/news/1</link>
                <description>d</description>
              </item>
            </rdf:RDF>
        """.trimIndent()
        val feed = parser.parse(xml, "https://store.steampowered.com/feeds/news.xml")
        assertEquals("Steam", feed.title)
        assertEquals(1, feed.entries.size)
    }

    @Test
    fun parseMalformedStillReturnsItems() {
        val xml = """
            <rss version="2.0"><channel><title>残缺</title>
            <item><title>A</title><link>https://a.example/1</link><guid>1</guid></item>
            <item><title>B
        """.trimIndent()
        val feed = parser.parse(xml, "https://example.com/feed")
        assertTrue(feed.entries.isNotEmpty())
        assertEquals("A", feed.entries[0].title)
    }

    @Test(expected = FeedFetchException::class)
    fun parseHtmlThrowsMalformed() {
        try {
            parser.parse("<!DOCTYPE html><html><head><title>x</title></head></html>", "https://x")
        } catch (e: FeedFetchException) {
            assertEquals(FeedFetchException.Kind.MALFORMED, e.kind)
            throw e
        }
    }

    @Test
    fun stripBomAndPreface() {
        val xml = "garbage before <?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>T</title></channel></rss>"
        val feed = parser.parse(xml, "https://x")
        assertEquals("T", feed.title)
    }

    @Test
    fun parseAtomLinkRelAlternate() {
        val xml = """
            <?xml version="1.0"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>NYT</title>
              <link rel="self" href="https://example.com/atom.xml"/>
              <link rel="alternate" href="https://example.com/"/>
              <entry>
                <title>A</title>
                <id>id-a</id>
                <link rel="alternate" href="https://example.com/a"/>
              </entry>
            </feed>
        """.trimIndent()
        val feed = parser.parse(xml, "https://example.com/atom.xml")
        assertEquals("https://example.com/", feed.siteUrl)
        assertEquals("https://example.com/a", feed.entries[0].link)
    }

    @Test
    fun kindLabelsAreChinese() {
        assertEquals("编码问题", FeedFetchException.Kind.ENCODING.label)
        assertEquals("需要特定请求头", FeedFetchException.Kind.HEADERS.label)
        assertEquals("非标准/残缺 feed", FeedFetchException.Kind.MALFORMED.label)
        assertEquals("RSSHub 路由不可用", FeedFetchException.Kind.RSSHUB.label)
    }
}
