package com.succh.unifeed.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleSpeechTextTest {

    @Test
    fun htmlToPlainStripsTagsAndDecodesEntities() {
        val plain = ArticleSpeechText.htmlToPlain(
            "<p>你好&nbsp;<b>世界</b> &amp; Kotlin</p>"
        )
        assertEquals("你好 世界 & Kotlin", plain)
        assertFalse(plain.contains('<'))
        assertFalse(plain.contains("nbsp"))
    }

    @Test
    fun htmlToPlainDropsScriptAndStyle() {
        val plain = ArticleSpeechText.htmlToPlain(
            "<p>正文</p><script>alert('x')</script><style>p{color:red}</style>"
        )
        assertEquals("正文", plain)
        assertFalse(plain.contains("alert"))
        assertFalse(plain.contains("color"))
    }

    @Test
    fun htmlToPlainBlankIsEmpty() {
        assertEquals("", ArticleSpeechText.htmlToPlain("  "))
        assertEquals("", ArticleSpeechText.htmlToPlain("<p>   </p>"))
    }

    @Test
    fun speakableChunksTitleAndBody() {
        val chunks = ArticleSpeechText.speakableChunks(
            title = "标题",
            html = "<p>第一段</p><p>第二段</p>"
        )
        assertEquals(listOf("标题", "第一段", "第二段"), chunks)
    }

    @Test
    fun speakableChunksBodyEmptyUsesTitle() {
        assertEquals(listOf("只有标题"), ArticleSpeechText.speakableChunks("只有标题", "<p></p>"))
    }

    @Test
    fun speakableChunksBothEmpty() {
        assertTrue(ArticleSpeechText.speakableChunks("  ", " <div></div> ").isEmpty())
    }

    @Test
    fun splitUtterancesKeepsShortParagraphs() {
        val chunks = ArticleSpeechText.splitUtterances("甲段\n\n乙段")
        assertEquals(listOf("甲段", "乙段"), chunks)
    }

    @Test
    fun splitUtterancesSplitsLongBySentence() {
        val chunks = ArticleSpeechText.splitUtterances(
            "你好。世界！继续。",
            maxLen = 6
        )
        assertEquals(listOf("你好。", "世界！", "继续。"), chunks)
    }

    @Test
    fun splitUtterancesHardSplitsOversizeSentence() {
        val long = "a".repeat(50)
        val chunks = ArticleSpeechText.splitUtterances(long, maxLen = 20)
        assertTrue(chunks.size >= 3)
        assertTrue(chunks.all { it.length <= 20 })
        assertEquals(long, chunks.joinToString(""))
    }
}
