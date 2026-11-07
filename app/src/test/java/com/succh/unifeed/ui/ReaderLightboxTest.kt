package com.succh.unifeed.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLightboxTest {

    @Test
    fun parseLightboxUrlsReadsJsonArray() {
        val json = """["https://a.example/1.jpg","https://a.example/2.webp"]"""
        assertEquals(
            listOf("https://a.example/1.jpg", "https://a.example/2.webp"),
            parseLightboxUrls(json)
        )
    }

    @Test
    fun parseLightboxUrlsSkipsBlankEntries() {
        val json = """["https://a.example/1.jpg","","  "]"""
        assertEquals(listOf("https://a.example/1.jpg"), parseLightboxUrls(json))
    }

    @Test
    fun parseLightboxUrlsToleratesNullAndGarbage() {
        assertTrue(parseLightboxUrls(null).isEmpty())
        assertTrue(parseLightboxUrls("").isEmpty())
        assertTrue(parseLightboxUrls("null").isEmpty())
        assertTrue(parseLightboxUrls("not-json").isEmpty())
        assertTrue(parseLightboxUrls("""{"url":"x"}""").isEmpty())
    }
}
