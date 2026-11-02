package com.succh.unifeed.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class Feed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val siteUrl: String? = null,
    val description: String? = null,
    val faviconUrl: String? = null,
    val folderId: Long? = null,
    val lastUpdated: Long = 0L,
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 上次刷新完成时间（成功或失败都会写入） */
    val lastRefreshTime: Long = 0L,
    /** 上次刷新失败原因；成功时为 null */
    val lastRefreshError: String? = null,
    /** 是否套用全局关键字规则；关闭后仅该源专属规则生效 */
    val applyGlobalFilter: Boolean = true
)
