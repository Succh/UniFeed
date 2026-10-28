package com.succh.unifeed.data.rss

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 阅读器图片专用 OkHttpClient：磁盘缓存 + 连接池 + 失败重试。
 * 构造思路与 RssParser 一致，额外挂 Cache。
 */
object ReaderImageClient {

    @Volatile
    private var client: OkHttpClient? = null

    fun get(context: Context): OkHttpClient {
        return client ?: synchronized(this) {
            client ?: build(context.applicationContext).also { client = it }
        }
    }

    private fun build(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "reader_images")
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .cache(Cache(cacheDir, 50L * 1024 * 1024))
            .build()
    }

    /**
     * 按图片域名映射 Referer；未命中返回 null，调用方不得设置空 Referer 头。
     */
    fun mappedReferer(imageUrl: String): String? {
        val host = FeedUrls.hostOf(imageUrl) ?: return null
        return when {
            host.contains("sinaimg.cn") -> "https://weibo.com"
            host.contains("mmbiz.qpic.cn") || host.contains("wximg") -> "https://mp.weixin.qq.com"
            host.contains("zhimg.com") -> "https://www.zhihu.com"
            host.contains("hdslb.com") -> "https://www.bilibili.com"
            else -> null
        }
    }

    fun imageRequest(url: String, referer: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgents.MOBILE)
            .header("Accept", "image/avif,image/webp,image/png,image/*,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
        if (!referer.isNullOrBlank()) {
            builder.header("Referer", referer)
        }
        return builder.build()
    }
}
