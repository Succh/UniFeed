package com.succh.unifeed.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import com.succh.unifeed.data.rss.RsshubEndpoints

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

    /** 后台自动刷新开关 */
    private val _autoRefreshEnabled = mutableStateOf(sp.getBoolean(KEY_AUTO_REFRESH_ENABLED, false))
    var autoRefreshEnabled: Boolean
        get() = _autoRefreshEnabled.value
        set(value) {
            _autoRefreshEnabled.value = value
            sp.edit().putBoolean(KEY_AUTO_REFRESH_ENABLED, value).apply()
        }

    /** 后台自动刷新间隔（小时） */
    private val _autoRefreshIntervalHours = mutableStateOf(sp.getLong(KEY_AUTO_REFRESH_INTERVAL_HOURS, 6L))
    var autoRefreshIntervalHours: Long
        get() = _autoRefreshIntervalHours.value
        set(value) {
            _autoRefreshIntervalHours.value = value
            sp.edit().putLong(KEY_AUTO_REFRESH_INTERVAL_HOURS, value).apply()
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

    private companion object {
        const val KEY_SIZE = "font_size"
        const val KEY_LH = "line_height"
        const val KEY_THEME = "reader_theme"
        const val KEY_IMAGES = "show_images"
        const val KEY_SERIF = "serif_font"
        const val KEY_JUSTIFY = "justify_text"
        const val KEY_LIST_SUMMARY = "list_summary"
        const val KEY_AUTO_READ = "auto_read"
        const val KEY_MARK_READ_ON_OPEN = "mark_read_on_open"
        const val KEY_RSSHUB_INSTANCE = "rsshub_instance"
        const val KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled"
        const val KEY_AUTO_REFRESH_INTERVAL_HOURS = "auto_refresh_interval_hours"
    }
}
