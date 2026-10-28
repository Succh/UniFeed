package com.succh.unifeed.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedUrlsTest {

    @Test
    fun withSchemeOnlyAddsHttpsWhenMissing() {
        assertEquals("https://sspai.com/feed", FeedUrls.withScheme("sspai.com/feed"))
        assertEquals("http://example.com/feed", FeedUrls.withScheme("http://example.com/feed"))
        assertEquals("https://example.com/feed", FeedUrls.withScheme("  https://example.com/feed  "))
    }

    @Test
    fun httpsifyFixesProtocolRelative() {
        assertEquals("https://cdn.example.com/a.png", FeedUrls.httpsify("//cdn.example.com/a.png"))
        assertEquals("https://cdn.example.com/a.png", FeedUrls.httpsify("https://cdn.example.com/a.png"))
    }

    @Test
    fun hostOfParsesWithoutSchemeAndLowercases() {
        assertEquals("rsshub.app", FeedUrls.hostOf("https://rsshub.app/zhihu/hot"))
        assertEquals("example.com", FeedUrls.hostOf("example.com/feed"))
        assertEquals("example.com", FeedUrls.hostOf("https://EXAMPLE.com:8443/feed"))
        assertEquals("example.com", FeedUrls.hostOf("http://example.com:8080/feed"))
        assertNull(FeedUrls.hostOf("not a url"))
    }

    @Test
    fun pathOfReturnsEmptyWhenMissing() {
        assertEquals("/zhihu/hot", FeedUrls.pathOf("https://rsshub.app/zhihu/hot"))
        assertEquals("", FeedUrls.pathOf("https://rsshub.app"))
        assertEquals("", FeedUrls.pathOf("not a url"))
    }
}
