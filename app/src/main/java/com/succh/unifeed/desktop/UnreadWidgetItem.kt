package com.succh.unifeed.desktop

/**
 * 桌面小部件一行：最新未读文章的标题、来源与时间。
 * 字段名与 Room 查询别名对齐。
 */
data class UnreadWidgetItem(
    val id: Long,
    val title: String,
    val feedTitle: String,
    val publishedAt: Long
)
