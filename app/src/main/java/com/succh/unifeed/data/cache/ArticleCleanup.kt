package com.succh.unifeed.data.cache

/**
 * 旧文章清理判定：只删已读、非收藏、非稍后读，且早于截止时间的文章。
 * 未读一律保留。时间优先用发布时间，缺失时用抓取时间。
 */
object ArticleCleanup {
    const val DAY_MS = 24L * 60L * 60L * 1000L

    fun cutoffMillis(retention: ArticleRetention, nowMillis: Long): Long? {
        val days = retention.days ?: return null
        return nowMillis - days * DAY_MS
    }

    fun isEligible(
        isRead: Boolean,
        isStarred: Boolean,
        isReadLater: Boolean,
        publishedAt: Long,
        fetchedAt: Long,
        cutoffMillis: Long
    ): Boolean {
        if (!isRead) return false
        if (isStarred) return false
        if (isReadLater) return false
        val ageAt = if (publishedAt > 0L) publishedAt else fetchedAt
        return ageAt < cutoffMillis
    }
}
