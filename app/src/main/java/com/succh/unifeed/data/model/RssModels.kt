package com.succh.unifeed.data.model

data class ParsedFeed(
    val title: String,
    val url: String,
    val siteUrl: String? = null,
    val description: String? = null,
    val entries: List<ParsedEntry>
)

data class ParsedEntry(
    val guid: String,
    val title: String,
    val link: String? = null,
    val content: String? = null,
    val summary: String? = null,
    val author: String? = null,
    /** 0 表示源里没给出或解析失败，由抓取层兜底为抓取时间 */
    val publishedAt: Long = 0L,
    val imageUrl: String? = null
)

/** 订阅前预览：只展示、不入库 */
data class FeedPreview(
    val title: String,
    val url: String,
    val siteUrl: String?,
    val description: String?,
    val faviconUrl: String,
    val recentTitles: List<String>,
    val parsed: ParsedFeed
)
