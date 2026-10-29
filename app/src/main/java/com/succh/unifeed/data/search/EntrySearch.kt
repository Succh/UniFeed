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
}
