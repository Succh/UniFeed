package com.succh.unifeed.data.rss

import java.net.URI

/**
 * URL 归一化工具：抓取、订阅发现、镜像拼接、图片防盗链共用同一套解析。
 * 此前 java.net.URI 与 android.net.Uri 混用（后者宽容、前者严格，缺协议时还会把
 * `example.com` 的 host 解析成 null），统一到这里以保证边界行为一致，并可在 JVM 单测直接覆盖。
 */
object FeedUrls {

    /** 缺协议时补 https */
    fun withScheme(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"
    }

    /** 协议相对地址（//cdn.example/a.png）补上 https */
    fun httpsify(url: String): String = if (url.startsWith("//")) "https:$url" else url

    /** 小写主机名，解析不出来返回 null */
    fun hostOf(url: String): String? =
        runCatching { URI(withScheme(url)).host }.getOrNull()?.lowercase()?.takeIf { it.isNotEmpty() }

    /** 路径部分（含前导斜杠），解析不出来返回空串 */
    fun pathOf(url: String): String =
        runCatching { URI(withScheme(url)).path }.getOrNull().orEmpty()
}
