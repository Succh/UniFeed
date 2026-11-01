package com.succh.unifeed.data.filter

import com.succh.unifeed.data.db.entity.FilterRule

/** 标题命中后的处理动作 */
enum class FilterAction {
    /** 入库或回扫时标为已读 */
    MARK_READ,
    /** 从列表隐藏，且不计入未读 */
    HIDE
}

/**
 * 纯函数匹配器：按标题子串（忽略大小写）套用关键字规则。
 * 隐藏优先于自动标已读。
 */
object ContentFilter {

    fun parseAction(raw: String): FilterAction? = when (raw) {
        FilterRule.ACTION_HIDE -> FilterAction.HIDE
        FilterRule.ACTION_MARK_READ -> FilterAction.MARK_READ
        else -> null
    }

    /**
     * 取出对某源生效的规则：专属规则始终计入；全局规则受 [applyGlobal] 开关控制。
     */
    fun rulesForFeed(
        feedId: Long,
        applyGlobal: Boolean,
        allRules: List<FilterRule>
    ): List<FilterRule> {
        return allRules.filter { rule ->
            if (rule.keyword.isBlank()) return@filter false
            when {
                rule.feedId == feedId -> true
                rule.feedId == null && applyGlobal -> true
                else -> false
            }
        }
    }

    /**
     * 返回标题命中的最强动作；未命中返回 null。
     * 多条同时命中时隐藏优先。
     */
    fun match(title: String, rules: List<FilterRule>): FilterAction? {
        if (title.isEmpty() || rules.isEmpty()) return null
        var markRead = false
        for (rule in rules) {
            val keyword = rule.keyword.trim()
            if (keyword.isEmpty()) continue
            if (!title.contains(keyword, ignoreCase = true)) continue
            when (parseAction(rule.action)) {
                FilterAction.HIDE -> return FilterAction.HIDE
                FilterAction.MARK_READ -> markRead = true
                null -> Unit
            }
        }
        return if (markRead) FilterAction.MARK_READ else null
    }
}
