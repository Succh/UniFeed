package com.succh.unifeed.data.rss

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 文章正文提取器
 * 基于 jsoup 的 Readability 风格正文抽取
 */
object ContentExtractor {

    /**
     * 从 HTML 中提取正文
     */
    fun extract(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            (findMainContent(doc) ?: doc.body())?.html() ?: html
        } catch (_: Exception) {
            html
        }
    }

    /**
     * 查找主内容区域
     */
    private fun findMainContent(doc: Document): Element? {
        // 1. 优先 article/main 标签
        val semantic = doc.selectFirst("article, main, [role=main]")
        if (semantic != null) {
            val score = scoreNode(semantic)
            if (score > 10) return cleanNode(semantic)
        }

        // 2. 常见类名/ID
        val selectors = listOf(
            "#content", "#main", "#article", "#post", ".article-content", ".post-content",
            ".entry-content", ".article-body", ".post-body", ".content-body", ".rich_media_content"
        )
        for (sel in selectors) {
            val el = doc.selectFirst(sel)
            if (el != null && el.text().length > 100) {
                return cleanNode(el)
            }
        }

        // 3. 正文密度评分
        var best: Element? = null
        var bestScore = 0.0
        for (p in doc.select("p")) {
            val parent = p.parent() ?: continue
            val score = scoreNode(parent)
            if (score > bestScore) {
                bestScore = score
                best = parent
            }
        }

        return if (best != null && bestScore > 20) cleanNode(best) else null
    }

    /**
     * 对节点打分（文本密度启发式）
     */
    private fun scoreNode(el: Element): Double {
        var score = 0.0
        val text = el.text()
        val textLen = text.length.toDouble()
        val commas = text.count { it == ',' || it == '，' || it == '。' || it == '.' }
        val pCount = el.select("p").size
        val linkLen = el.select("a").sumOf { it.text().length }.toDouble()

        score += Math.min(textLen / 100.0, 10.0)
        score += pCount * 1.5
        score += Math.min(commas / 5.0, 5.0)
        // 链接密度惩罚；正文为空时不做除法，避免 NaN 污染总分
        if (textLen > 0.0) score -= Math.min(linkLen / textLen * 2, 5.0)

        // 标签加分
        val id = el.id().lowercase()
        val cls = el.className().lowercase()
        if ("content" in id || "article" in id || "post" in id) score += 5
        if ("content" in cls || "article" in cls || "post" in cls || "entry" in cls) score += 5
        if ("comment" in id || "comment" in cls || "footer" in id || "nav" in id || "menu" in id) score -= 10
        if ("ad" in id || "ad" in cls || "promo" in id || "sponsor" in id) score -= 10

        return score
    }

    /**
     * 清理节点，去掉干扰元素
     */
    private fun cleanNode(el: Element): Element {
        // 移除脚本、样式、广告
        // iframe 留给阅读器做成音视频占位封面，这里只清脚本广告
        el.select("script, style, noscript, .ad, .ads, .advertisement, .banner, .promo, .comment, .comments, .footer, .share, .social, .related, #ad, #comments").remove()
        return el
    }
}
