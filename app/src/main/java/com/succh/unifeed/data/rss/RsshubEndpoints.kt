package com.succh.unifeed.data.rss

/**
 * RSSHub 实例清单与域名识别的唯一来源。
 * 抓取错误分类、镜像竞速、订阅路径判定和设置页快捷选择共用同一份，
 * 避免镜像增删时在多个文件里各写一遍（历史上三份清单并不一致：
 * cups.moe 与 rssforever 的失败被误分类成普通 HTTP，而非「RSSHub 路由不可用」）。
 */
object RsshubEndpoints {

    /** 默认自建实例，优先级最高 */
    const val DEFAULT = "https://succh.zone.id"
    const val RSSFOREVER = "https://rsshub.rssforever.com"
    const val SLARKER = "https://hub.slarker.me"
    const val CUPS = "https://rsshub.cups.moe"
    const val OWO = "https://rss.owo.nz"
    const val OFFICIAL = "https://rsshub.app"

    /** 竞速候选，顺序即优先级 */
    val MIRRORS = listOf(DEFAULT, RSSFOREVER, SLARKER, CUPS, OWO, OFFICIAL)

    /** 路由文档 /api/namespace 的兜底实例 */
    val CATALOG_FALLBACKS = listOf(DEFAULT, RSSFOREVER, SLARKER, OFFICIAL)

    /** 只做域名识别、不参与竞速的实例：balancer 自身会再转发，重复竞速无意义 */
    private val RECOGNITION_ONLY_HOSTS = listOf("rsshub-balancer.virworks.moe")

    private val KNOWN_HOSTS: List<String> =
        (MIRRORS + CATALOG_FALLBACKS + RECOGNITION_ONLY_HOSTS)
            .mapNotNull(FeedUrls::hostOf)
            .distinct()

    /** 是否属于 RSSHub 实例；[extraHost] 传入用户自定义实例域名以扩大识别 */
    fun isRsshubUrl(url: String, extraHost: String? = null): Boolean {
        val host = FeedUrls.hostOf(url) ?: return false
        if (extraHost != null && host == extraHost.lowercase()) return true
        return host.contains("rsshub") || host in KNOWN_HOSTS
    }
}
