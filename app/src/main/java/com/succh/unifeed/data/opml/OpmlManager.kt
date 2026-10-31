package com.succh.unifeed.data.opml

import android.util.Xml
import com.succh.unifeed.data.db.entity.Feed
import com.succh.unifeed.data.db.entity.Folder
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class OpmlFeed(
    val title: String,
    val xmlUrl: String,
    val htmlUrl: String = "",
    val folderName: String? = null
)

object OpmlManager {

    fun export(feeds: List<Feed>, folders: List<Folder>): String {
        val folderMap = folders.associateBy { it.id }
        val grouped = feeds.groupBy { it.folderId }
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<opml version=\"2.0\">")
        sb.appendLine("  <head>")
        sb.appendLine("    <title>UniFeed OPML Export</title>")
        sb.appendLine("  </head>")
        sb.appendLine("  <body>")
        for ((folderId, feedList) in grouped) {
            if (folderId != null) {
                val folderName = folderMap[folderId]?.name ?: "Unnamed"
                sb.appendLine("    <outline text=\"${escape(folderName)}\">")
                for (feed in feedList) {
                    sb.appendLine("      " + feedOutline(feed))
                }
                sb.appendLine("    </outline>")
            } else {
                for (feed in feedList) {
                    sb.appendLine("    " + feedOutline(feed))
                }
            }
        }
        sb.appendLine("  </body>")
        sb.appendLine("</opml>")
        return sb.toString()
    }

    private fun feedOutline(feed: Feed): String {
        // 标准 OPML 订阅项：title 给人看，xmlUrl 是源地址
        val title = escape(feed.title.ifEmpty { feed.url })
        val xmlUrl = escape(feed.url)
        val htmlUrl = escape(feed.siteUrl ?: "")
        val htmlAttr = if (htmlUrl.isNotEmpty()) " htmlUrl=\"$htmlUrl\"" else ""
        return "<outline type=\"rss\" text=\"$title\" title=\"$title\" xmlUrl=\"$xmlUrl\"$htmlAttr/>"
    }

    private fun escape(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun parse(inputStream: InputStream): List<OpmlFeed> {
        val result = mutableListOf<OpmlFeed>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        val folderStack = mutableListOf<Pair<String, Int>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true)) {
                        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                        if (xmlUrl.isNullOrEmpty()) {
                            val folderTitle = parser.getAttributeValue(null, "text")
                                ?: parser.getAttributeValue(null, "title")
                                ?: ""
                            if (folderTitle.isNotEmpty()) {
                                folderStack.add(folderTitle to parser.depth)
                            }
                        } else {
                            val title = (parser.getAttributeValue(null, "title")
                                ?: parser.getAttributeValue(null, "text")
                                ?: xmlUrl).trim()
                            val htmlUrl = parser.getAttributeValue(null, "htmlUrl")
                                ?.takeIf { it.isNotEmpty() } ?: ""
                            val folderName = folderStack.lastOrNull()
                                ?.first?.takeIf { it.isNotEmpty() }
                            result.add(
                                OpmlFeed(
                                    title = title.ifEmpty { xmlUrl },
                                    xmlUrl = xmlUrl,
                                    htmlUrl = htmlUrl,
                                    folderName = folderName,
                                )
                            )
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true) &&
                        folderStack.isNotEmpty() &&
                        folderStack.last().second == parser.depth
                    ) {
                        folderStack.removeLast()
                    }
                }
            }
            event = parser.next()
        }
        return result
    }
}
