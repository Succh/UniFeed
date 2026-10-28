package com.succh.unifeed.data.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsshubEndpointsTest {

    @Test
    fun knownMirrorsAreRecognised() {
        RsshubEndpoints.MIRRORS.forEach { instance ->
            assertTrue("$instance 应被识别为 RSSHub", RsshubEndpoints.isRsshubUrl("$instance/zhihu/hot"))
        }
        RsshubEndpoints.CATALOG_FALLBACKS.forEach { instance ->
            assertTrue(RsshubEndpoints.isRsshubUrl("$instance/bilibili/popular/all"))
        }
    }

    @Test
    fun hostContainingRsshubIsRecognised() {
        assertTrue(RsshubEndpoints.isRsshubUrl("https://my-rsshub.example.com/zhihu/hot"))
    }

    @Test
    fun customInstanceNeedsExtraHost() {
        assertFalse(RsshubEndpoints.isRsshubUrl("https://hub.example.net/zhihu/hot"))
        assertTrue(RsshubEndpoints.isRsshubUrl("https://hub.example.net/zhihu/hot", extraHost = "hub.example.net"))
        assertTrue(RsshubEndpoints.isRsshubUrl("https://hub.example.net/zhihu/hot", extraHost = "HUB.Example.net"))
    }

    @Test
    fun ordinaryFeedsAreNotRsshub() {
        assertFalse(RsshubEndpoints.isRsshubUrl("https://sspai.com/feed"))
        assertFalse(RsshubEndpoints.isRsshubUrl("not a url"))
        assertFalse(RsshubEndpoints.isRsshubUrl(""))
    }
}
