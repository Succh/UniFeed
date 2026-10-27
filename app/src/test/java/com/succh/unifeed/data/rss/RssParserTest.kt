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

    private fun rssWith(pubDate: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>T</title>
          <item><title>A</title><link>https://a.example/1</link><guid>g1</guid>
            <pubDate>$pubDate</pubDate>
          </item>
        </channel></rss>
    """.trimIndent()

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    /** RFC 822 形式，与解析器第一条格式一致 */
    private fun rfc822(millis: Long): String =
        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(millis))

    @Test
    fun rssPubDateIsAdopted() {
        val expected = utcMillis(2026, java.util.Calendar.OCTOBER, 24, 8, 30)
        val entry = parser.parse(rssWith(rfc822(expected)), "https://a.example/feed").entries[0]
        assertEquals(expected, entry.publishedAt)
    }

    @Test
    fun epochSecondsAndMillisAreAdopted() {
        val seconds = parser.parse(rssWith("1792800000"), "https://a.example/feed").entries[0]
        assertEquals(1792800000L * 1000L, seconds.publishedAt)
        val millis = parser.parse(rssWith("1792800000123"), "https://a.example/feed").entries[0]
        assertEquals(1792800000123L, millis.publishedAt)
    }

    @Test
    fun unparsableDateFallsBackToFetchTime() {
        val before = System.currentTimeMillis()
        val entry = parser.parse(rssWith("不是时间"), "https://a.example/feed").entries[0]
        val after = System.currentTimeMillis()
        assertTrue("非法时间应退回抓取时间", entry.publishedAt in before..after)
    }

    @Test
    fun missingDateFallsBackToFetchTime() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>T</title>
              <item><title>A</title><link>https://a.example/1</link><guid>g1</guid></item>
            </channel></rss>
        """.trimIndent()
        val before = System.currentTimeMillis()
        val entry = parser.parse(xml, "https://a.example/feed").entries[0]
        val after = System.currentTimeMillis()
        assertTrue(entry.publishedAt in before..after)
    }

    @Test
    fun atomPublishedAndUpdatedAreAdopted() {
        val expected = utcMillis(2026, java.util.Calendar.OCTOBER, 24, 8, 30)
        val iso = java.time.Instant.ofEpochMilli(expected).toString().removeSuffix(".0")
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>T</title>
              <entry><title>A</title><id>id-1</id>
                <link href="https://a.example/1"/>
                <published>$iso</published>
              </entry>
            </feed>
        """.trimIndent()
        val entry = parser.parse(xml, "https://a.example/atom.xml").entries[0]
        assertEquals(expected, entry.publishedAt)

        val updatedXml = xml.replace("<published>", "<updated>").replace("</published>", "</updated>")
        val updatedEntry = parser.parse(updatedXml, "https://a.example/atom.xml").entries[0]
        assertEquals(expected, updatedEntry.publishedAt)
    }
}
