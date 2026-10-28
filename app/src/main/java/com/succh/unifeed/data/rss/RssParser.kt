package com.succh.unifeed.data.rss

import com.succh.unifeed.data.model.ParsedEntry
import com.succh.unifeed.data.model.ParsedFeed
import com.succh.unifeed.data.rss.FeedFetchException.Kind
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * 轻量级 RSS/Atom/RDF 解析器。
 * 抓取阶段处理编码、重定向、站点对 UA/Referer 的限制，以及残缺 feed。
 */
class RssParser(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()) {

    companion object {
        private const val MAX_ENTRIES_PER_FEED = 500
    private const val DESKTOP_UA = UserAgents.DESKTOP
    private const val MOBILE_UA = UserAgents.MOBILE
        private const val READER_UA = "UniFeed/1.0 (+https://github.com/Succh/UniFeed; RSS Reader)"
        private val CHARSET_META = Regex(
            """encoding\s*=\s*['\"]?\s*([A-Za-z0-9_\-]+)""",
            RegexOption.IGNORE_CASE
        )
    private val IMG_SRC = Regex(
        """<img[^>]+(?:src|data-src|data-original)=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    /** RSS pubDate / Atom published|updated 的常见形态，按精度从高到低尝试 */
    private val DATE_PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    }

    /**
     * 用户自定义 RSSHub 实例的域名，由 FeedRepository 在写入 customRsshubInstance 时同步。
     * 抓取层默认拿不到该配置，不注入的话自定义实例的失败会被分类成普通 HTTP。
     */
    @Volatile
    var rsshubExtraHost: String? = null

    /**
     * 抓取并解析 RSS/Atom 订阅源。
     * 同一 URL 会按桌面 UA → 移动 UA → 阅读器 UA 重试，并带上 Referer。
     */
    suspend fun fetch(url: String): ParsedFeed {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val normalized = FeedUrls.withScheme(url)
            var last: Exception? = null
            val attempts = listOf(
                Headers(DESKTOP_UA, refererOf(normalized)),
                Headers(MOBILE_UA, refererOf(normalized)),
                Headers(READER_UA, null)
            )
            for (h in attempts) {
                try {
                    return@withContext fetchOnce(normalized, h)
                } catch (e: FeedFetchException) {
                    last = e
                    // 仅 401/403/429 这类请求头问题换 UA/Referer 再试；4xx/5xx 本体不再空转
                    if (e.kind == Kind.HEADERS) continue
                    throw e
                } catch (e: Exception) {
                    last = e
                }
            }
            throw classify(last, normalized)
        }
    }

    /**
     * 抓取原始 HTML 页面内容（仅按需「加载全文」使用，刷新路径禁止调用）
     */
    suspend fun fetchHtml(url: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val request = Request.Builder()
        .url(FeedUrls.withScheme(url))
                .header("User-Agent", DESKTOP_UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw FeedFetchException(Kind.HTTP, "HTTP ${response.code}")
                }
                val bytes = response.body?.bytes() ?: throw FeedFetchException(Kind.EMPTY, "Empty body")
                decodeBody(bytes, response.header("Content-Type"))
            }
        }
    }

    private data class Headers(val ua: String, val referer: String?)

    private fun fetchOnce(url: String, headers: Headers): ParsedFeed {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", headers.ua)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, application/rdf+xml, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Cache-Control", "no-cache")
        if (!headers.referer.isNullOrBlank()) {
            builder.header("Referer", headers.referer)
        }
        try {
            client.newCall(builder.build()).execute().use { response ->
                val code = response.code
                val finalUrl = response.request.url.toString()
                if (code in 300..399) {
                    throw FeedFetchException(Kind.REDIRECT, "重定向未完成 HTTP $code")
                }
                if (code == 401 || code == 403 || code == 429) {
                    throw FeedFetchException(Kind.HEADERS, "HTTP $code，可能需要特定 User-Agent / Referer")
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                val xml = if (bytes.isEmpty()) "" else decodeBody(bytes, response.header("Content-Type"))
                val trimmed = stripBomAndPreface(xml)
                if (!response.isSuccessful) {
                    throw classifyHttp(code, url, trimmed)
                }
                if (bytes.isEmpty()) {
                    throw FeedFetchException(Kind.EMPTY, "Empty body")
                }
                if (looksLikeHtml(trimmed) || looksLikeRsshubErrorPage(trimmed)) {
                    throw classifyHtmlBody(url, trimmed)
                }
                return parse(trimmed, finalUrl.ifBlank { url })
            }
        } catch (e: FeedFetchException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            throw FeedFetchException(Kind.NETWORK, e.message ?: "域名解析失败")
        } catch (e: java.net.SocketTimeoutException) {
            throw FeedFetchException(Kind.NETWORK, "连接超时")
        } catch (e: javax.net.ssl.SSLException) {
            throw FeedFetchException(Kind.NETWORK, "TLS/SSL 失败: ${e.message}")
        } catch (e: java.io.IOException) {
            throw FeedFetchException(Kind.NETWORK, e.message ?: "IO 错误")
        }
    }

    /**
     * 从 XML 字符串解析订阅源，兼容 RSS 2.0 / Atom / RDF。
     */
    fun parse(xml: String, sourceUrl: String): ParsedFeed {
        val cleaned = stripBomAndPreface(xml)
        if (looksLikeHtml(cleaned)) {
            throw FeedFetchException(Kind.MALFORMED, "响应是 HTML 而非 RSS/Atom")
        }
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        try {
            parser.setInput(StringReaderLenient(cleaned))
        } catch (e: Exception) {
            throw FeedFetchException(Kind.ENCODING, e.message ?: "无法按声明编码读取")
        }

        val fetchedAt = System.currentTimeMillis()
        var channelTitle = ""
        var channelLink = ""
        var channelDesc = ""
        var isAtom = false
        val entries = mutableListOf<ParsedEntry>()

        var currentEntry: ParsedEntry? = null
        var currentText = StringBuilder()
        var inItem = false
        var inChannel = false
        var tagName = ""
        var enclosureImage: String? = null
        var mediaImage: String? = null
        var sawFeedRoot = false

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        tagName = local(parser.name)
                        currentText = StringBuilder()
                        if (tagName.equals("rss", true) || tagName.equals("rdf", true) ||
            tagName.equals("feed", true)
                        ) {
                            sawFeedRoot = true
                        }
                        if (tagName == "feed" && !isAtom && channelTitle.isEmpty()) {
                            isAtom = true
                        }
                        if (tagName == "item" || tagName == "entry") {
                            inItem = true
                            currentEntry = ParsedEntry(guid = "", title = "")
                            enclosureImage = null
                            mediaImage = null
                        }
                        if (tagName == "channel") inChannel = true

                        if (inItem && currentEntry != null) {
                            when (tagName) {
                                "enclosure" -> {
                                    val url = parser.getAttributeValue(null, "url")
                                        ?: parser.getAttributeValue(null, "href")
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    if (!url.isNullOrBlank() && isImageHint(type, url) && enclosureImage == null) {
                                        enclosureImage = url
                                    }
                                }
                                "content", "thumbnail" -> {
                                    val url = parser.getAttributeValue(null, "url")
                                        ?: parser.getAttributeValue(null, "href")
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    val medium = parser.getAttributeValue(null, "medium").orEmpty()
                                    if (!url.isNullOrBlank() &&
                                        (isImageHint(type, url) || medium.equals("image", true) || tagName == "thumbnail") &&
                                        mediaImage == null
                                    ) {
                                        mediaImage = url
                                    }
                                }
                                "link" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    val rel = parser.getAttributeValue(null, "rel").orEmpty()
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    if (!href.isNullOrBlank()) {
                                        if (rel == "enclosure" && isImageHint(type, href) && enclosureImage == null) {
                                            enclosureImage = href
                                        } else if (currentEntry!!.link == null && (rel.isEmpty() || rel == "alternate")) {
                                            currentEntry = currentEntry!!.copy(link = href)
                                        }
                                    }
                                }
                            }
                        } else if (!inItem && tagName == "link") {
                            val href = parser.getAttributeValue(null, "href")
                            val rel = parser.getAttributeValue(null, "rel").orEmpty()
                            if (!href.isNullOrBlank() && channelLink.isEmpty() && (rel.isEmpty() || rel == "alternate")) {
                                channelLink = href
                            }
                        }
                    }

                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                        currentText.append(parser.text ?: "")
                    }

                    XmlPullParser.END_TAG -> {
                        val text = currentText.toString().trim()
                        val tagname = local(parser.name)

                        if (inItem && currentEntry != null) {
                            when (tagname) {
                                "title" -> if (currentEntry!!.title.isEmpty()) currentEntry = currentEntry!!.copy(title = decodeEntities(text))
                                "link" -> if (currentEntry!!.link == null && text.isNotEmpty()) currentEntry = currentEntry!!.copy(link = text)
                                "guid", "id" -> if (currentEntry!!.guid.isEmpty()) currentEntry = currentEntry!!.copy(guid = text)
                                "description", "summary" -> {
                                    val decoded = decodeEntities(text)
                                    if (currentEntry!!.summary == null || decoded.length > currentEntry!!.summary!!.length) {
                                        currentEntry = currentEntry!!.copy(summary = decoded)
                                    }
                                    if (currentEntry!!.content == null || decoded.length > currentEntry!!.content!!.length) {
                                        currentEntry = currentEntry!!.copy(content = decoded)
                                    }
                                }
                                "content", "encoded" -> {
                                    val decoded = decodeEntities(text)
                                    if (decoded.isNotEmpty() && (currentEntry!!.content == null || decoded.length > currentEntry!!.content!!.length)) {
                                        currentEntry = currentEntry!!.copy(content = decoded)
                                    }
                                }
                                "pubDate", "published", "updated", "date" -> if (currentEntry!!.publishedAt == 0L) {
                                    currentEntry = currentEntry!!.copy(publishedAt = parseDate(text))
                                }
                                "author", "creator", "name" -> if (currentEntry!!.author == null && text.isNotEmpty()) {
                                    currentEntry = currentEntry!!.copy(author = decodeEntities(text))
                                }
                                "item", "entry" -> {
                                    if (currentEntry!!.guid.isEmpty()) {
                                        currentEntry = currentEntry!!.copy(guid = currentEntry!!.link ?: sourceUrl + "#" + currentEntry!!.title)
                                    }
            // 源没有时间时用本次抓取时间
            if (currentEntry!!.publishedAt == 0L) {
                currentEntry = currentEntry!!.copy(publishedAt = fetchedAt)
            }
                                    val cover = enclosureImage
                                        ?: mediaImage
                                        ?: firstImgInHtml(currentEntry!!.content)
                                        ?: firstImgInHtml(currentEntry!!.summary)
                                    entries.add(currentEntry!!.copy(imageUrl = cover))
                                    currentEntry = null
                                    inItem = false
                                }
                            }
                        } else {
                            when (tagname) {
                                "title" -> if ((inChannel || isAtom) && channelTitle.isEmpty()) channelTitle = decodeEntities(text)
                                "link" -> if ((inChannel || isAtom) && channelLink.isEmpty() && text.isNotEmpty()) channelLink = text
                                "description", "subtitle" -> if ((inChannel || isAtom) && channelDesc.isEmpty()) channelDesc = decodeEntities(text)
                                "channel" -> inChannel = false
                            }
                        }
                    }
                }
                eventType = try {
                    parser.next()
                } catch (e: XmlPullParserException) {
                    // 残缺 feed：尽力返回已经解析出的条目
                    if (entries.isNotEmpty()) break
                    throw FeedFetchException(Kind.MALFORMED, e.message ?: "XML 解析失败")
                }
            }
        } catch (e: FeedFetchException) {
            throw e
        } catch (e: XmlPullParserException) {
            if (entries.isEmpty()) {
                throw FeedFetchException(Kind.MALFORMED, e.message ?: "XML 解析失败")
            }
        }

        if (!sawFeedRoot && entries.isEmpty()) {
            throw FeedFetchException(Kind.MALFORMED, "未识别到 RSS/Atom 根节点")
        }

        return ParsedFeed(
            title = channelTitle.ifEmpty { sourceUrl },
            url = sourceUrl,
            siteUrl = channelLink.ifEmpty { null },
            description = channelDesc.ifEmpty { null },
            entries = entries.take(MAX_ENTRIES_PER_FEED)
        )
    }

    private fun local(name: String): String {
        val i = name.indexOf(':')
        return if (i >= 0) name.substring(i + 1) else name
    }

    private fun isImageHint(type: String, url: String): Boolean {
        if (type.startsWith("image/", ignoreCase = true)) return true
        val lower = url.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".avif") ||
            lower.contains(".jpg?") || lower.contains(".jpeg?") || lower.contains(".png?") ||
            lower.contains(".webp?")
    }

    private fun firstImgInHtml(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val src = IMG_SRC.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        return src.takeIf { it.startsWith("http") || it.startsWith("//") }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun parseDate(dateStr: String): Long {
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return 0L
        // 纯数字时间戳：秒或毫秒
        trimmed.toLongOrNull()?.let { v ->
            return if (v < 1_000_000_000_000L) v * 1000 else v
        }
        for (format in DATE_PATTERNS) {
            try {
                val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // 换下一个格式
            }
        }
        return 0L
    }

    private fun decodeBody(bytes: ByteArray, contentType: String?): String {
        val fromHeader = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() }
        val sniff = sniffCharset(bytes)
        val candidates = listOfNotNull(fromHeader, sniff, "UTF-8", "GBK", "GB2312", "GB18030", "ISO-8859-1")
            .distinctBy { it.lowercase() }
        var lastError: Exception? = null
        for (name in candidates) {
            try {
                val cs = charsetOrNull(name) ?: continue
                val text = String(bytes, cs)
                if (text.contains('\uFFFD') && name.equals("UTF-8", true) && candidates.any { it.startsWith("GB", true) }) {
                    continue
                }
                return text
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw FeedFetchException(Kind.ENCODING, lastError?.message ?: "无法识别编码")
    }

    private fun sniffCharset(bytes: ByteArray): String? {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return "UTF-8"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return "UTF-16LE"
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return "UTF-16BE"
        val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.ISO_8859_1)
        return CHARSET_META.find(head)?.groupValues?.getOrNull(1)
    }

    private fun charsetOrNull(name: String): Charset? = try {
        Charset.forName(name)
    } catch (_: Exception) {
        null
    }

    /** 去掉 BOM、XML 声明前的空白/脚本，避免部分站点输出残缺前言 */
    private fun stripBomAndPreface(xml: String): String {
        var s = xml.trimStart('\uFEFF', '\uFFFE', ' ', '\t', '\n', '\r')
        val idxRss = s.indexOf("<rss", ignoreCase = true)
        val idxFeed = s.indexOf("<feed", ignoreCase = true)
        val idxRdf = s.indexOf("<rdf:RDF", ignoreCase = true)
        val idxXml = s.indexOf("<?xml", ignoreCase = true)
        val starts = listOf(idxXml, idxRss, idxFeed, idxRdf).filter { it >= 0 }
        if (starts.isNotEmpty()) {
            val start = starts.minOrNull() ?: 0
            if (start > 0) s = s.substring(start)
        }
        return s
    }

    private fun looksLikeHtml(text: String): Boolean {
        val head = text.take(400).lowercase()
        val hasFeed = head.contains("<rss") || head.contains("<feed") || head.contains("<rdf")
        if (hasFeed) return false
        return head.contains("<!doctype html") || head.contains("<html")
    }

    /** RSSHub 失败页常见标题，即使 HTTP 200 也不是 feed */
    private fun looksLikeRsshubErrorPage(text: String): Boolean {
        val head = text.take(800)
        return head.contains("Welcome to RSSHub", ignoreCase = true) ||
            head.contains("RSSHub 路由", ignoreCase = true)
    }

    private fun isRsshubUrl(url: String): Boolean = RsshubEndpoints.isRsshubUrl(url, extraHost = rsshubExtraHost)

    private fun classifyHttp(code: Int, url: String, body: String): FeedFetchException {
        if (isRsshubUrl(url) || looksLikeRsshubErrorPage(body)) {
            val hint = when (code) {
                404 -> "路由不存在或参数不完整 HTTP 404"
                401, 403 -> "需要 Cookie / 令牌 HTTP $code"
                429 -> "触发限流 HTTP 429"
                502, 503, 504 -> "公共实例对该路由返回 HTTP $code（反爬或上游失败）"
                else -> "HTTP $code"
            }
            return FeedFetchException(Kind.RSSHUB, hint)
        }
        return FeedFetchException(Kind.HTTP, "HTTP $code")
    }

    private fun classifyHtmlBody(url: String, body: String): FeedFetchException {
        if (isRsshubUrl(url) || looksLikeRsshubErrorPage(body)) {
            return FeedFetchException(Kind.RSSHUB, "响应是 RSSHub 错误页而非订阅源")
        }
        return FeedFetchException(Kind.MALFORMED, "响应是 HTML 而非 RSS/Atom")
    }

    private fun decodeEntities(text: String): String {
        if (text.isEmpty() || !text.contains('&')) return text
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
    }


    private fun refererOf(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val port = when {
                uri.port < 0 -> ""
                else -> ":${uri.port}"
            }
            "${uri.scheme}://${uri.host}$port/"
        } catch (_: Exception) {
            url
        }
    }

    private fun classify(e: Exception?, url: String): FeedFetchException {
        if (e is FeedFetchException) return e
        val msg = e?.message ?: "未知错误"
        return FeedFetchException(Kind.NETWORK, "$url : $msg")
    }

    /**
     * XmlPullParser 对部分残缺声明会抛错，用 ByteArray 再喂一次更稳。
     */
    private fun StringReaderLenient(text: String): java.io.Reader {
        return InputStreamReader(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
    }
}
