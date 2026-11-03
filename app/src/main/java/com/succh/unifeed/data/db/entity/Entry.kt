package com.succh.unifeed.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val guid: String,
    val title: String,
    val link: String? = null,
    val content: String? = null,
    val summary: String? = null,
    val author: String? = null,
    val publishedAt: Long = 0L,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    /** 稍后读，与收藏独立，可同时为 true */
    val isReadLater: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis(),
    /** 封面图 URL，解析自 enclosure / media / 正文首图 */
    val imageUrl: String? = null,
    /**
     * 阅读滚动进度，取值 0~1，表示已滚过的可滚动高度比例。
     * 用比例而非像素，避免字号或窗口变化后进度失真。
     */
    val lastReadPosition: Float = 0f,
    /** 最近一次写入阅读进度的时间戳（epoch millis） */
    val lastReadAt: Long = 0L,
    /** 关键字规则命中「隐藏」时为 true，列表不展示且不计入未读 */
    val isHidden: Boolean = false
)
