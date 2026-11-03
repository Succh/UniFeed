package com.succh.unifeed.data.cache

/**
 * 图片磁盘缓存上限档位。
 * UNLIMITED 表示不设业务上限，由磁盘空间约束。
 */
enum class ImageCacheLimit(val maxBytes: Long?) {
    MB_100(100L * 1024L * 1024L),
    MB_500(500L * 1024L * 1024L),
    GB_1(1024L * 1024L * 1024L),
    UNLIMITED(null);

    companion object {
        fun fromName(name: String?): ImageCacheLimit =
            entries.firstOrNull { it.name == name } ?: MB_500
    }
}
