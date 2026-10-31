package com.succh.unifeed.ui

import com.succh.unifeed.data.search.EntrySearch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * 将 RSS 条目正文转成带样式的 HTML，供 ReaderScreen 的 WebView 渲染。
 * 新闻风排版：来源行 → 大标题 → 作者/时间 → 分隔线 → 封面图 → 正文。
 */
object ArticleHtmlBuilder {

    private data class Palette(
        val bg: String, val fg: String, val muted: String,
        val border: String, val codeBg: String, val link: String, val quote: String,
        val mark: String
    )

    private fun palette(theme: ReaderTheme): Palette = when (theme) {
        ReaderTheme.LIGHT -> Palette("#ffffff", "#1c1c1e", "#6e6e73", "#e5e5ea", "#f5f5f7", "#007aff", "#3a3a3c", "#ffe58a")
        ReaderTheme.SEPIA -> Palette("#faf6ef", "#3d3a34", "#8a8378", "#e2dccb", "#f0ead9", "#8b5e3c", "#5c564c", "#f3d27a")
        ReaderTheme.DARK -> Palette("#1c1c1e", "#e8e8ed", "#98989f", "#3a3a3c", "#2c2c2e", "#4da3ff", "#c7c7cc", "#8a6d1b")
    }

    private fun buildCss(
        theme: ReaderTheme,
        fontSize: Float,
        lineHeight: Float,
        showImages: Boolean,
        serif: Boolean,
        justify: Boolean
    ): String {
        val p = palette(theme)
        // 页面背景透明，让 Compose 玻璃光晕透过来；正文色仍走主题以保证对比度
        val pageBg = "transparent"
        val fontFamily = if (serif) {
            "Georgia, 'Times New Roman', 'Songti SC', SimSun, serif"
        } else {
            "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', sans-serif"
        }
        val textAlign = if (justify) "text-align: justify;" else "text-align: left;"
        val imgRule = if (showImages) {
            "img { max-width: 100%; width: 100%; height: auto; display: block; margin: 20px 0; border-radius: 12px; }"
        } else {
            "img { display: none !important; } .hero { display: none !important; }"
        }
        val bodyLh = lineHeight
        return """
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: $fontFamily; font-size: ${fontSize}px; line-height: $bodyLh; color: ${p.fg}; background: $pageBg; padding: 0 0 48px; word-wrap: break-word; overflow-x: hidden; $textAlign -webkit-text-size-adjust: 100%; }
.article { padding: 0 20px 8px; overflow-x: hidden; max-width: 100%; background: linear-gradient(to bottom, ${p.bg}00 0%, ${p.bg}cc 28px, ${p.bg}ee 64px, ${p.bg}f2 100%); }
$imgRule
a { color: ${p.link}; text-decoration: none; }
p { margin: 0 0 1.1em 0; text-indent: 0; line-height: $bodyLh; }
h1.headline { font-size: 1.75em; line-height: 1.28; font-weight: 800; letter-spacing: -0.02em; margin: 8px 0 12px 0; }
h1, h2, h3, h4 { margin: 24px 0 12px 0; line-height: 1.35; }
pre, code { font-family: Menlo, Consolas, monospace; font-size: 0.85em; background: ${p.codeBg}; border-radius: 6px; color: ${p.fg}; }
/* 长代码不换行、不截断，由外层 .code-scroll 横向滚动 */
.code-scroll { overflow-x: auto; -webkit-overflow-scrolling: touch; max-width: 100%; margin: 16px 0; border-radius: 6px; background: ${p.codeBg}; }
pre { padding: 16px; margin: 0; overflow: visible; white-space: pre; word-wrap: normal; overflow-wrap: normal; word-break: normal; background: transparent; }
pre code { display: block; padding: 0; background: transparent; white-space: pre; word-wrap: normal; overflow-wrap: normal; word-break: normal; }
code { padding: 2px 6px; }
blockquote { border-left: 4px solid ${p.link}; margin: 20px 0; padding: 4px 0 4px 16px; color: ${p.quote}; background: transparent; }
/* 宽表不挤列、不撑破正文，由 .table-scroll 横向滚动，表头与单元格同列宽 */
.table-scroll { overflow-x: auto; -webkit-overflow-scrolling: touch; max-width: 100%; margin: 16px 0; }
table { width: max-content; min-width: 100%; border-collapse: collapse; margin: 0; table-layout: auto; }
th, td { border: 1px solid ${p.border}; padding: 8px 12px; white-space: nowrap; vertical-align: top; }
ul, ol { margin: 12px 0 1.1em; padding-left: 24px; }
hr { border: none; border-top: 1px solid ${p.border}; margin: 20px 0; }
.source-row { display: flex; align-items: center; gap: 8px; font-size: 13px; color: ${p.muted}; margin-bottom: 4px; }
.source-row img.favicon { width: 18px; height: 18px; border-radius: 4px; margin: 0; }
.byline { font-size: 13px; color: ${p.muted}; margin: 0 0 4px 0; }
.hero { position: relative; width: 100%; height: min(38vw, 160px); margin: 0 0 -28px; overflow: hidden; pointer-events: none; }
.hero img { width: 100%; height: 100%; object-fit: cover; object-position: center; margin: 0; border-radius: 0; opacity: 0.38; display: block; }
.hero::after { content: ""; position: absolute; inset: 0; pointer-events: none; background: linear-gradient(to bottom, ${p.bg}22 0%, ${p.bg}66 48%, ${p.bg} 100%); }
.orig { display: block; margin: 32px auto 8px; padding: 10px 20px; text-align: center; border: 1px solid ${p.border}; border-radius: 20px; color: ${p.link}; font-size: 15px; }
mark.search-hit { background: ${p.mark}; color: inherit; padding: 0 2px; border-radius: 2px; }
""".trimIndent()
    }

    fun build(
        title: String,
        author: String?,
        link: String?,
        publishedAt: Long,
        content: String,
        prefs: ReaderPrefs,
        feedTitle: String? = null,
        feedFaviconUrl: String? = null,
        coverImageUrl: String? = null,
        highlightQuery: String? = null
    ): String {
        val keyword = EntrySearch.normalizeQuery(highlightQuery.orEmpty())
        val sourceRow = buildString {
            append("<div class='source-row'>")
            if (!feedFaviconUrl.isNullOrBlank()) {
                append("<img class='favicon' src='${escapeAttr(feedFaviconUrl)}' alt=''>")
            }
            if (!feedTitle.isNullOrBlank()) append("<span>${escape(feedTitle)}</span>")
            if (publishedAt > 0L) {
                if (!feedTitle.isNullOrBlank()) append("<span>·</span>")
                append("<span>${escape(formatFullTime(publishedAt))}</span>")
            }
            append("</div>")
        }
        val byline = buildString {
            val parts = mutableListOf<String>()
            if (!author.isNullOrBlank()) parts.add(escape(author))
            if (publishedAt > 0L) parts.add(escape(formatFullTime(publishedAt)))
            if (parts.isNotEmpty()) {
                append("<div class='byline'>${parts.joinToString(" · ")}</div>")
            }
        }
        // 顶部条幅横幅：半透明融入背景，渐隐到正文，不占用阅读区
        val hero = if (!coverImageUrl.isNullOrBlank() && prefs.showImages) {
            "<div class='hero'><img src='${escapeAttr(coverImageUrl)}' alt='' referrerpolicy='no-referrer'></div>"
        } else ""
        val body = highlightHtml(normalize(content, link), keyword)
        val orig = if (!link.isNullOrBlank()) {
            "<a class='orig' href='${escapeAttr(link)}'>阅读原文 ↗</a>"
        } else ""
        return """<!DOCTYPE html>
<html lang='zh'><head>
<meta charset='UTF-8'>
<meta name='viewport' content='width=device-width, initial-scale=1.0'>
<meta name='referrer' content='no-referrer'>
<style>${buildCss(prefs.theme, prefs.fontSize, prefs.lineHeight, prefs.showImages, prefs.serifFont, prefs.justifyText)}</style>
</head><body>
$hero
<div class='article'>
$sourceRow
<h1 class='headline'>${highlightPlain(title, keyword)}</h1>
$byline
<hr>
$body
$orig
</div>
</body></html>"""
    }

    /** 纯文本关键词包一层 mark，供已转义的标题使用 */
    internal fun highlightPlain(text: String, query: String): String {
        val escaped = escape(text)
        if (query.isEmpty()) return escaped
        if (EntrySearch.findRanges(text, query).isEmpty()) return escaped
        // 标题已转义，按原文字符区间再包 mark 会错位；直接对转义后文本做忽略大小写替换
        val pattern = Pattern.compile(Pattern.quote(escape(query)), Pattern.CASE_INSENSITIVE)
        return pattern.matcher(escaped).replaceAll("<mark class='search-hit'>\$0</mark>")
    }

    /**
     * 只在文本节点里高亮，避开标签属性，避免破坏 HTML。
     */
    internal fun highlightHtml(html: String, query: String): String {
        if (query.isEmpty() || html.isBlank()) return html
        return try {
            val doc = Jsoup.parseBodyFragment(html)
            val skip = setOf("script", "style", "code", "pre", "mark")
            walkHighlight(doc.body(), query, skip)
            doc.body().html()
        } catch (_: Exception) {
            html
        }
    }

    private fun walkHighlight(element: Element, query: String, skip: Set<String>) {
        if (element.tagName().lowercase() in skip) return
        val children = element.childNodes().toList()
        children.forEach { child ->
            when (child) {
                is TextNode -> highlightTextNode(child, query, skip)
                is Element -> walkHighlight(child, query, skip)
            }
        }
    }

    private fun highlightTextNode(node: TextNode, query: String, skip: Set<String>) {
        val parent = node.parent() as? Element ?: return
        if (parent.tagName().lowercase() in skip) return
        val text = node.wholeText
        val ranges = EntrySearch.findRanges(text, query)
        if (ranges.isEmpty()) return
        val fragment = StringBuilder()
        var cursor = 0
        ranges.forEach { range ->
            if (range.first > cursor) fragment.append(escapeHtmlKeep(text.substring(cursor, range.first)))
            fragment.append("<mark class='search-hit'>")
            fragment.append(escapeHtmlKeep(text.substring(range.first, range.last + 1)))
            fragment.append("</mark>")
            cursor = range.last + 1
        }
        if (cursor < text.length) fragment.append(escapeHtmlKeep(text.substring(cursor)))
        node.after(fragment.toString())
        node.remove()
    }

    private fun escapeHtmlKeep(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    /**
     * 正文 HTML 归一化：去脚本/样式，图片/链接相对地址补全为绝对地址。
     */
    internal fun normalize(html: String, baseUrl: String?): String {
        if (html.isBlank()) return "<p>（无正文内容）</p>"
        return try {
            val doc: Document
            if (html.trimStart().startsWith("<")) {
                doc = Jsoup.parse(html)
            } else {
                return "<p>${escape(html)}</p>"
            }
            doc.select("script,style,ins,iframe,noscript,form,button,input").remove()

            // 1) 图片：懒加载属性补全 → src；各种相对地址 → 绝对地址；加 referrerpolicy 防防盗链
            doc.select("img").forEach { img ->
                if (img.attr("src").isBlank()) {
                    for (k in listOf("data-src", "data-original", "data-lazy-src", "data-url", "data-actualsrc", "data-lazy", "data-echo", "data-thumb", "data-srcset", "data-ks-lazyload")) {
                        val v = img.attr(k)
                        if (v.isNotBlank()) {
                            img.attr("src", v.substringBefore(" ").substringBefore(","))
                            break
                        }
                    }
                }
                var src = img.attr("src")
                src = absolutize(src, baseUrl)
                if (src.isNotBlank()) img.attr("src", src)
                img.removeAttr("srcset")
                img.removeAttr("data-src")
                img.removeAttr("data-original")
                img.removeAttr("loading")
                img.attr("referrerpolicy", "no-referrer")
            }

            // 2) 链接：相对地址 → 绝对地址
            doc.select("a[href]").forEach { a ->
                val href = absolutize(a.attr("href"), baseUrl)
                if (href.isNotBlank()) a.attr("href", href)
            }

            // 3) 代码块：包一层横向滚动容器，长行保持原样不换行、不截断
            wrapCodeBlocks(doc)
            // 4) 宽表格：包一层横向滚动容器，列宽跟内容走，表头与单元格对齐
            wrapWideTables(doc)

            doc.body().html()
        } catch (_: Exception) {
            "<p>${escape(html)}</p>"
        }
    }

    /** 独立 pre 外包 .code-scroll，避免长代码把正文撑破或被挤换行 */
    private fun wrapCodeBlocks(doc: Document) {
        doc.select("pre").forEach { pre ->
            if (pre.parent()?.hasClass("code-scroll") == true) return@forEach
            val wrap = Element("div")
            wrap.addClass("code-scroll")
            pre.replaceWith(wrap)
            wrap.appendChild(pre)
        }
    }

    /** 顶层 table 外包 .table-scroll，宽表横向滑动，不把正文撑破 */
    private fun wrapWideTables(doc: Document) {
        doc.select("table").forEach { table ->
            if (table.parent()?.hasClass("table-scroll") == true) return@forEach
            if (table.parents().any { it.tagName().equals("table", ignoreCase = true) }) return@forEach
            val wrap = Element("div")
            wrap.addClass("table-scroll")
            table.replaceWith(wrap)
            wrap.appendChild(table)
        }
    }

    private fun absolutize(url: String, baseUrl: String?): String {
        if (url.isBlank()) return ""
        if (url.startsWith("//")) return "https:$url"
        val hasScheme = url.startsWith("http://") || url.startsWith("https://") ||
            url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("about:") ||
            url.startsWith("file:") || url.startsWith("ftp:") || url.startsWith("mailto:") ||
            url.startsWith("tel:") || url.startsWith("javascript:") || url.startsWith("#")
        if (hasScheme) return url
        if (baseUrl.isNullOrBlank()) return url
        return resolve(baseUrl, url)
    }

    private fun resolve(baseUrl: String, path: String): String {
        return try {
            val base = URI(baseUrl)
            val resolved = base.resolve(path)
            resolved.toString()
        } catch (_: Exception) {
            path
        }
    }

    private fun escape(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&#34;")
            .replace("'", "&#39;")
            .replace("\n", "<br>")
    }

    private fun escapeAttr(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&#34;")
            .replace("'", "&#39;")
    }

    private fun formatFullTime(timestamp: Long): String {
        if (timestamp == 0L) return ""
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        } catch (_: Exception) {
            ""
        }
    }
}
