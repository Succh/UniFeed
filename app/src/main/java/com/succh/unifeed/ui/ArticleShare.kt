package com.succh.unifeed.ui

/**
 * 文章系统分享：只拼标题与链接，真正弹出面板走 Android 系统 Intent。
 */
object ArticleShare {

    /**
     * 分享正文：标题与链接各占一行。
     * 链接缺失或空白时只发标题，避免把字面量 "null" 发给对方。
     */
    fun shareText(title: String, link: String?): String {
        val heading = title.trim()
        val url = link?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            heading.isNotEmpty() && url != null -> "$heading\n$url"
            url != null -> url
            else -> heading
        }
    }
}
