package com.succh.unifeed.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import com.succh.unifeed.data.cache.ArticleRetention
import com.succh.unifeed.data.cache.ImageCacheLimit
import com.succh.unifeed.data.rss.RsshubEndpoints
import com.succh.unifeed.data.work.RefreshInterval

enum class ReaderTheme { LIGHT, SEPIA, DARK }

class ReaderPrefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("unifeed_prefs", Context.MODE_PRIVATE)

    private val _fontSize = mutableFloatStateOf(sp.getFloat(KEY_SIZE, 17f))
    var fontSize: Float
        get() = _fontSize.floatValue
        set(value) {
            val v = value.coerceIn(13f, 24f)
            _fontSize.floatValue = v
            sp.edit().putFloat(KEY_SIZE, v).apply()
        }

    private val _lineHeight = mutableFloatStateOf(sp.getFloat(KEY_LH, 1.7f))
    var lineHeight: Float
        get() = _lineHeight.floatValue
        set(value) {
            val v = value.coerceIn(1.2f, 2.2f)
            _lineHeight.floatValue = v
            sp.edit().putFloat(KEY_LH, v).apply()
        }

    private val _theme = mutableStateOf(loadTheme())
    var theme: ReaderTheme
        get() = _theme.value
        set(value) {
            _theme.value = value
            sp.edit().putString(KEY_THEME, value.name).apply()
        }

    private val _showImages = mutableStateOf(sp.getBoolean(KEY_IMAGES, true))
    var showImages: Boolean
        get() = _showImages.value
        set(value) {
            _showImages.value = value
            sp.edit().putBoolean(KEY_IMAGES, value).apply()
        }

    private val _serifFont = mutableStateOf(sp.getBoolean(KEY_SERIF, false))
    var serifFont: Boolean
        get() = _serifFont.value
        set(value) {
            _serifFont.value = value
            sp.edit().putBoolean(KEY_SERIF, value).apply()
        }

    private val _justifyText = mutableStateOf(sp.getBoolean(KEY_JUSTIFY, false))
    var justifyText: Boolean
        get() = _justifyText.value
        set(value) {
            _justifyText.value = value
            sp.edit().putBoolean(KEY_JUSTIFY, value).apply()
        }

    private val _listShowSummary = mutableStateOf(sp.getBoolean(KEY_LIST_SUMMARY, false))
    var listShowSummary: Boolean
        get() = _listShowSummary.value
        set(value) {
            _listShowSummary.value = value
            sp.edit().putBoolean(KEY_LIST_SUMMARY, value).apply()
        }

    /**
     * 打开即标记已读。默认关闭，走阅读器停留 2 秒后再标已读。
     * 旧键 auto_read 仅在新键尚未写入时作为初始值。
     */
    private val _markReadOnOpen = mutableStateOf(loadMarkReadOnOpen())
    var markReadOnOpen: Boolean
        get() = _markReadOnOpen.value
        set(value) {
            _markReadOnOpen.value = value
            sp.edit().putBoolean(KEY_MARK_READ_ON_OPEN, value).apply()
        }

    /** 自定义 RSSHub 实例地址（null 表示使用内置默认实例策略） */
    private val _rsshubInstance = mutableStateOf(sp.getString(KEY_RSSHUB_INSTANCE, null))
    var rsshubInstance: String?
        get() = _rsshubInstance.value
        set(value) {
            val v = value?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            _rsshubInstance.value = v
            sp.edit().putString(KEY_RSSHUB_INSTANCE, v).apply()
        }

    /** 当前生效的 RSSHub 实例：用户自定义 > 默认自建实例 */
    val effectiveRsshubInstance: String
        get() = rsshubInstance ?: RsshubEndpoints.DEFAULT

    /** 后台自动刷新开关；手动档时视为关闭周期任务 */
    private val _autoRefreshEnabled = mutableStateOf(loadAutoRefreshEnabled())
    var autoRefreshEnabled: Boolean
        get() = _autoRefreshEnabled.value
        set(value) {
            _autoRefreshEnabled.value = value
            sp.edit().putBoolean(KEY_AUTO_REFRESH_ENABLED, value).apply()
            if (!value) {
                refreshInterval = RefreshInterval.MANUAL
            } else if (refreshInterval == RefreshInterval.MANUAL) {
                refreshInterval = RefreshInterval.HOUR_1
            }
        }

    /** 后台自动刷新间隔档位（含手动） */
    private val _refreshInterval = mutableStateOf(loadRefreshInterval())
    var refreshInterval: RefreshInterval
        get() = _refreshInterval.value
        set(value) {
            _refreshInterval.value = value
            sp.edit().putString(KEY_REFRESH_INTERVAL, value.name).apply()
            val enabled = value.isPeriodic
            if (_autoRefreshEnabled.value != enabled) {
                _autoRefreshEnabled.value = enabled
                sp.edit().putBoolean(KEY_AUTO_REFRESH_ENABLED, enabled).apply()
            }
        }

    /** 仅 Wi-Fi 时后台刷新：开启后任务约束为非计费网络 */
    private val _refreshWifiOnly = mutableStateOf(sp.getBoolean(KEY_REFRESH_WIFI_ONLY, false))
    var refreshWifiOnly: Boolean
        get() = _refreshWifiOnly.value
        set(value) {
            _refreshWifiOnly.value = value
            sp.edit().putBoolean(KEY_REFRESH_WIFI_ONLY, value).apply()
        }

    /** 图片磁盘缓存上限；默认 500MB */
    private val _imageCacheLimit = mutableStateOf(loadImageCacheLimit())
    var imageCacheLimit: ImageCacheLimit
        get() = _imageCacheLimit.value
        set(value) {
            _imageCacheLimit.value = value
            sp.edit().putString(KEY_IMAGE_CACHE_LIMIT, value.name).apply()
        }

    /** 旧文章保留时长；默认不清理 */
    private val _articleRetention = mutableStateOf(loadArticleRetention())
    var articleRetention: ArticleRetention
        get() = _articleRetention.value
        set(value) {
            _articleRetention.value = value
            sp.edit().putString(KEY_ARTICLE_RETENTION, value.name).apply()
        }

    /** 桌面图标未读角标；默认开启，不支持的启动器上静默无效果 */
    private val _unreadBadgeEnabled = mutableStateOf(sp.getBoolean(KEY_UNREAD_BADGE, true))
    var unreadBadgeEnabled: Boolean
        get() = _unreadBadgeEnabled.value
        set(value) {
            _unreadBadgeEnabled.value = value
            sp.edit().putBoolean(KEY_UNREAD_BADGE, value).apply()
        }

    private fun loadTheme(): ReaderTheme {
        val name = sp.getString(KEY_THEME, ReaderTheme.LIGHT.name)
        return ReaderTheme.entries.firstOrNull { it.name == name } ?: ReaderTheme.LIGHT
    }

    private fun loadMarkReadOnOpen(): Boolean {
        if (sp.contains(KEY_MARK_READ_ON_OPEN)) {
            return sp.getBoolean(KEY_MARK_READ_ON_OPEN, false)
        }
        return sp.getBoolean(KEY_AUTO_READ, false)
    }

    /**
     * 优先读新档位键；没有则用旧开关 + 小时数平滑迁移，并写回新键。
     */
    private fun loadRefreshInterval(): RefreshInterval {
        val stored = sp.getString(KEY_REFRESH_INTERVAL, null)
        if (stored != null) {
            return RefreshInterval.fromName(stored)
        }
        val enabled = sp.getBoolean(KEY_AUTO_REFRESH_ENABLED, false)
        val hours = sp.getLong(KEY_AUTO_REFRESH_INTERVAL_HOURS, 6L)
        val migrated = RefreshInterval.fromLegacyHours(enabled, hours)
        sp.edit().putString(KEY_REFRESH_INTERVAL, migrated.name).apply()
        return migrated
    }

    private fun loadAutoRefreshEnabled(): Boolean {
        return loadRefreshInterval().isPeriodic
    }

    private fun loadImageCacheLimit(): ImageCacheLimit {
        return ImageCacheLimit.fromName(sp.getString(KEY_IMAGE_CACHE_LIMIT, null))
    }

    private fun loadArticleRetention(): ArticleRetention {
        return ArticleRetention.fromName(sp.getString(KEY_ARTICLE_RETENTION, null))
    }

    companion object {
        private const val PREFS_NAME = "unifeed_prefs"
        private const val KEY_SIZE = "font_size"
        private const val KEY_LH = "line_height"
        private const val KEY_THEME = "reader_theme"
        private const val KEY_IMAGES = "show_images"
        private const val KEY_SERIF = "serif_font"
        private const val KEY_JUSTIFY = "justify_text"
        private const val KEY_LIST_SUMMARY = "list_summary"
        private const val KEY_AUTO_READ = "auto_read"
        private const val KEY_MARK_READ_ON_OPEN = "mark_read_on_open"
        private const val KEY_RSSHUB_INSTANCE = "rsshub_instance"
        private const val KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled"
        private const val KEY_AUTO_REFRESH_INTERVAL_HOURS = "auto_refresh_interval_hours"
        private const val KEY_REFRESH_INTERVAL = "refresh_interval"
        private const val KEY_REFRESH_WIFI_ONLY = "refresh_wifi_only"
        private const val KEY_IMAGE_CACHE_LIMIT = "image_cache_limit"
        private const val KEY_ARTICLE_RETENTION = "article_retention"
        private const val KEY_UNREAD_BADGE = "unread_badge_enabled"

        /** 直接读 SP，供角标同步在 IO 线程使用，不新建 Compose 状态 */
        fun unreadBadgeEnabled(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_UNREAD_BADGE, true)
        }
    }
}
