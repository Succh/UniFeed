package com.succh.unifeed.data.cache

/**
 * 旧文章自动清理保留时长。
 * NEVER 表示不按时间删除任何文章。
 */
enum class ArticleRetention(val days: Int?) {
    DAYS_30(30),
    DAYS_90(90),
    DAYS_180(180),
    NEVER(null);

    companion object {
        fun fromName(name: String?): ArticleRetention =
            entries.firstOrNull { it.name == name } ?: NEVER
    }
}
