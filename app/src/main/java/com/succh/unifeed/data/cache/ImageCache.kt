package com.succh.unifeed.data.cache

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import java.io.File

/**
 * Coil 图片磁盘缓存：按档位设上限、统计占用、一键清空。
 * 只动 Coil 缓存目录，不碰 Room 里的正文、收藏与稍后读。
 */
object ImageCache {
    const val DIR_NAME = "image_cache"

    fun directory(context: Context): File = File(context.applicationContext.cacheDir, DIR_NAME)

    fun createLoader(context: Context, limit: ImageCacheLimit): ImageLoader {
        val app = context.applicationContext
        return ImageLoader.Builder(app)
            .diskCache {
                val builder = DiskCache.Builder().directory(directory(app))
                val max = limit.maxBytes
                if (max != null) {
                    builder.maxSizeBytes(max)
                } else {
                    // 不限：用剩余磁盘空间，不另设业务上限
                    builder.maxSizePercent(1.0)
                }
                builder.build()
            }
            .build()
    }

    /** 切换上限后替换全局 ImageLoader，并关掉旧实例以免同一目录双开锁 */
    fun applyLimit(context: Context, limit: ImageCacheLimit) {
        val app = context.applicationContext
        val previous = Coil.imageLoader(app)
        val next = createLoader(app, limit)
        Coil.setImageLoader(next)
        if (previous !== next) {
            previous.shutdown()
        }
    }

    fun sizeBytes(context: Context): Long {
        val loader = Coil.imageLoader(context.applicationContext)
        val cached = loader.diskCache?.size
        if (cached != null && cached > 0L) return cached
        return directorySize(directory(context))
    }

    /** 清空磁盘与内存图片缓存，文章正文仍在数据库 */
    fun clear(context: Context) {
        val loader = Coil.imageLoader(context.applicationContext)
        loader.diskCache?.clear()
        loader.memoryCache?.clear()
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val file = stack.removeFirst()
            if (file.isDirectory) {
                file.listFiles()?.forEach { stack.add(it) }
            } else {
                total += file.length()
            }
        }
        return total
    }
}
