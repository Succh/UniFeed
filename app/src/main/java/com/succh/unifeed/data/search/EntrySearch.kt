package com.succh.unifeed.data.search

/**
 * 文章全文搜索辅助：LIKE 转义、字段匹配。
 * 覆盖标题、摘要、正文（正文先去 HTML 标签再比）。
 */
object EntrySearch {

    private val HTML_TAG = Regex("<[^>]+>")

    /** 转义 LIKE 通配符，避免用户输入 % / _ 被当成模式 */
    fun escapeLike(query: String): String {
        return query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    fun normalizeQuery(raw: String): String = raw.trim()

    fun matches(title: String, summary: String?, content: String?, query: String): Boolean {
        val q = normalizeQuery(query)
        if (q.isEmpty()) return false
        if (title.contains(q, ignoreCase = true)) return true
        if (summary?.contains(q, ignoreCase = true) == true) return true
        return stripTags(content).contains(q, ignoreCase = true)
    }

    fun stripTags(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return html.replace(HTML_TAG, "").trim()
    }

    /**
     * 在纯文本中找出全部忽略大小写的命中区间，区间互不重叠。
     */
    fun findRanges(text: String, query: String): List<IntRange> {
        val q = normalizeQuery(query)
        if (q.isEmpty() || text.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        val qLen = q.length
        var start = 0
        while (start <= text.length - qLen) {
            val idx = text.indexOf(q, start, ignoreCase = true)
            if (idx < 0) break
            ranges.add(idx until (idx + qLen))
            start = idx + qLen
        }
        return ranges
    }

    /**
     * 结果列表摘要：标题未命中时，优先用摘要；摘要也未命中则从正文截一段带关键词的上下文。
     */
    fun resultSnippet(title: String, summary: String?, content: String?, query: String, maxLen: Int = 120): String {
        val q = normalizeQuery(query)
        val summaryText = summary.orEmpty()
        if (q.isEmpty()) return summaryText
        if (title.contains(q, ignoreCase = true)) return summaryText
        if (summaryText.contains(q, ignoreCase = true)) return summaryText
        val plain = stripTags(content)
        val idx = plain.indexOf(q, ignoreCase = true)
        if (idx < 0) return summaryText
        val start = (idx - 24).coerceAtLeast(0)
        val end = (idx + q.length + (maxLen - q.length - 24)).coerceAtMost(plain.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < plain.length) "…" else ""
        return prefix + plain.substring(start, end) + suffix
    }
}
