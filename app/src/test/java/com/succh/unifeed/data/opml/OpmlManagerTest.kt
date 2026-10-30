package com.succh.unifeed.data.opml

import com.succh.unifeed.data.db.entity.Feed
import com.succh.unifeed.data.db.entity.Folder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlManagerTest {

    @Test
    fun exportIncludesTitleAndXmlUrl() {
        val xml = OpmlManager.export(
            listOf(Feed(id = 1, title = "少数派", url = "https://sspai.com/feed")),
            emptyList()
        )
        assertTrue(xml.contains("title=\"少数派\""))
        assertTrue(xml.contains("xmlUrl=\"https://sspai.com/feed\""))
        assertTrue(xml.contains("type=\"rss\""))
    }

    @Test
    fun exportEscapesSpecialCharsInTitleAndUrl() {
        val xml = OpmlManager.export(
            listOf(Feed(id = 1, title = "A & B", url = "https://ex.com/feed?a=1&b=2")),
            emptyList()
        )
        assertTrue(xml.contains("title=\"A &amp; B\""))
        assertTrue(xml.contains("xmlUrl=\"https://ex.com/feed?a=1&amp;b=2\""))
        assertFalse(xml.contains("title=\"A & B\""))
    }

    @Test
    fun exportNestsFeedsUnderFolder() {
        val xml = OpmlManager.export(
            listOf(
                Feed(id = 1, title = "源", url = "https://ex.com/rss", folderId = 9)
            ),
            listOf(Folder(id = 9, name = "科技"))
        )
        assertTrue(xml.contains("<outline text=\"科技\">"))
        assertTrue(xml.contains("xmlUrl=\"https://ex.com/rss\""))
    }
}
