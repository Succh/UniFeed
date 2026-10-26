package com.succh.unifeed.data.rss

/**
 * 订阅源抓取/解析失败，带分类标签写入 [com.succh.unifeed.data.db.entity.Feed.lastRefreshError]。
 * 修不好的源不会被静默删除，UI 直接展示该分类。
 */
class FeedFetchException(
    val kind: Kind,
    detail: String
) : Exception("[${kind.label}] $detail") {

    enum class Kind(val label: String) {
        NETWORK("网络错误"),
        HTTP("HTTP 错误"),
        REDIRECT("重定向异常"),
        ENCODING("编码问题"),
        HEADERS("需要特定请求头"),
        MALFORMED("非标准/残缺 feed"),
        EMPTY("无文章"),
        RSSHUB("RSSHub 路由不可用")
    }
}
