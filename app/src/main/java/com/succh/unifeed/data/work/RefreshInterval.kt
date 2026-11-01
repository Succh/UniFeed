package com.succh.unifeed.data.work

/**
 * 后台自动刷新间隔档位。
 * 15 分钟对齐 WorkManager 周期任务最小间隔，不可再缩短。
 */
enum class RefreshInterval(val minutes: Long) {
    MINUTES_15(15),
    MINUTES_30(30),
    HOUR_1(60),
    MANUAL(0);

    val isPeriodic: Boolean get() = this != MANUAL

    companion object {
        fun fromName(name: String?): RefreshInterval =
            entries.firstOrNull { it.name == name } ?: MANUAL

        /**
         * 存量偏好迁移：关闭或非正小时数 -> 手动；其余小时档（2/6/12/24）-> 1 小时。
         */
        fun fromLegacyHours(enabled: Boolean, hours: Long): RefreshInterval {
            if (!enabled || hours <= 0L) return MANUAL
            return HOUR_1
        }
    }
}
