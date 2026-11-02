package com.succh.unifeed.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 关键字屏蔽规则。
 * feedId 为空表示全局规则（默认对所有源生效）；非空则只对该订阅源生效。
 */
@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    /** [ACTION_MARK_READ] 或 [ACTION_HIDE] */
    val action: String,
    val feedId: Long? = null
) {
    companion object {
        const val ACTION_MARK_READ = "MARK_READ"
        const val ACTION_HIDE = "HIDE"
    }
}
