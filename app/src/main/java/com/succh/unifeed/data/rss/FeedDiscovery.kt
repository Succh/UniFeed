package com.succh.unifeed.data.rss

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * 发现到的候选订阅源
 */
data class DiscoveredFeed(
    val title: String,
    val feedUrl: String,
    val siteUrl: String
)

/**
 * 订阅源发现器：输入一个网站地址，自动探测它的 RSS/Atom 订阅源。
 *
 * 策略：
 *  1. 抓取页面 HTML，扫描 <link rel="alternate"> 中 rss/atom 类型的地址；
 *  2. 若未发现，再探测常见路径（/feed、/rss.xml、/atom.xml 等），命中即返回。
 */
class FeedDiscovery(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    suspend fun discover(input: String): List<DiscoveredFeed> = withContext(Dispatchers.IO) {
        val base = parseBase(input) ?: return@withContext emptyList()
        val (origin, pageUrl) = base
        val result = LinkedHashMap<String, DiscoveredFeed>()
        try {
            val html = fetch(pageUrl)
            val doc = Jsoup.parse(html, pageUrl)
            val siteTitle = doc.title().trim().ifEmpty { hostOf(origin) }

            // 1. 扫描 <link rel="alternate" type="application/rss+xml|atom+xml">
            doc.select("link").forEach { link ->
                val rels = link.attr("rel").lowercase().split(Regex("\\s+"))
                if ("alternate" in rels) {
                    val type = link.attr("type").lowercase()
                    val href = link.absUrl("href")
                    val looksFeed = type.contains("rss") || type.contains("atom") ||
                        (type.isEmpty() && (href.endsWith(".xml") || href.endsWith(".rss") || href.contains("/feed")))
                    if (looksFeed && href.startsWith("http")) {
                        val title = link.attr("title").trim().ifEmpty { siteTitle }
                        result[href] = DiscoveredFeed(title, href, origin)
                    }
                }
            }

            // 2. 兜底：常见路径探测
            if (result.isEmpty()) {
                for (path in COMMON_PATHS) {
                    val candidate = origin + path
                    if (looksLikeFeed(candidate)) {
                        result[candidate] = DiscoveredFeed(siteTitle, candidate, origin)
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // 网络/解析失败时返回空列表
        }
        result.values.toList()
    }

    private fun fetch(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Empty body")
        }
    }

    /** 快速判断某个 URL 是否返回 feed 内容 */
    private fun looksLikeFeed(candidate: String): Boolean {
        return try {
            val req = Request.Builder().url(candidate).header("User-Agent", UA).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val head = (resp.body?.string() ?: "").take(800).lowercase()
                head.contains("<rss") || head.contains("<feed") || head.contains("<rdf")
            }
        } catch (_: Exception) {
            false
        }
    }

    /** 规范化输入，返回 (origin, 页面完整地址) */
    private fun parseBase(raw: String): Pair<String, String>? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return try {
            val uri = java.net.URI(s)
            val host = uri.host?.lowercase() ?: return null
            val scheme = uri.scheme?.lowercase() ?: "https"
            val port = when {
                uri.port < 0 -> ""
                scheme == "http" && uri.port == 80 -> ""
                scheme == "https" && uri.port == 443 -> ""
                else -> ":${uri.port}"
            }
            val origin = "$scheme://$host$port"
            val path = uri.path?.trimEnd('/') ?: ""
            origin to (origin + path)
        } catch (_: Exception) {
            null
        }
    }

    private fun hostOf(origin: String): String =
        origin.removePrefix("https://").removePrefix("http://").substringBefore(':').substringBefore('/')

    private companion object {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val COMMON_PATHS = listOf("/feed", "/rss", "/rss.xml", "/atom.xml", "/feed.xml", "/index.xml")
    }
}
