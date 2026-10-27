package com.succh.unifeed.ui

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.succh.unifeed.data.db.entity.Entry
import com.succh.unifeed.data.rss.ReaderImageClient
import kotlin.math.abs
import kotlinx.coroutines.delay
import okhttp3.Response
import java.util.Collections

/** 进度写入最短间隔，避免滚动时每像素都打库 */
private const val PROGRESS_SAVE_MIN_INTERVAL_MS = 800L
/** 进度变化小于该比例时跳过写入 */
private const val PROGRESS_SAVE_MIN_DELTA = 0.03f
/** 超过该比例视为读完，下次打开从头开始 */
private const val READ_COMPLETE_THRESHOLD = 0.95f
/** 打开后停留多久才标记已读（非「打开即标记」模式） */
private const val MARK_READ_DWELL_MS = 2000L

/** 图片扩展名判定，WebView 每个子资源请求都会走一遍，故预编译 */
private val IMAGE_EXT = Regex(".*\\.(png|jpg|jpeg|gif|webp|svg|bmp|ico|avif)(\\?.*)?$", RegexOption.IGNORE_CASE)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(
    entry: Entry,
    prefs: ReaderPrefs,
    onBack: () -> Unit,
    onToggleStar: (Entry, Boolean) -> Unit,
    onOpenBrowser: (Entry) -> Unit,
    onShare: (Entry) -> Unit,
    feedTitle: String? = null,
    feedFaviconUrl: String? = null,
    contentIncomplete: Boolean = false,
    isLoadingFullContent: Boolean = false,
    onLoadFullContent: () -> Unit = {},
    listEntries: List<Entry> = emptyList(),
    restoreProgress: Boolean = true,
    onSwitchEntry: (Entry) -> Unit = {},
    onSaveProgress: (Long, Float) -> Unit = { _, _ -> },
    onMarkRead: (Entry) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    var showAaPanel by remember { mutableStateOf(false) }
    var starred by remember(entry.id, entry.isStarred) { mutableStateOf(entry.isStarred) }
    val snackbarHostState = remember { SnackbarHostState() }
    val currentIndex = listEntries.indexOfFirst { it.id == entry.id }
    val prevEntry = listEntries.getOrNull(currentIndex - 1)
    val nextEntry = listEntries.getOrNull(currentIndex + 1)
    val latestSave by rememberUpdatedState(onSaveProgress)
    val latestMarkRead by rememberUpdatedState(onMarkRead)
    val latestEntry by rememberUpdatedState(entry)
    val restoreRatio = remember(entry.id, restoreProgress) {
        if (!restoreProgress || entry.lastReadPosition > READ_COMPLETE_THRESHOLD) {
            0f
        } else {
            entry.lastReadPosition.coerceIn(0f, 1f)
        }
    }
    var currentProgress by remember(entry.id) { mutableFloatStateOf(restoreRatio) }
    val progressByEntry = remember { mutableMapOf<Long, Float>() }
    progressByEntry[entry.id] = currentProgress

    LaunchedEffect(entry.id, prefs.markReadOnOpen, entry.isRead) {
        if (entry.isRead) return@LaunchedEffect
        if (!prefs.markReadOnOpen) {
            delay(MARK_READ_DWELL_MS)
        }
        latestMarkRead(latestEntry)
    }

    DisposableEffect(entry.id) {
        val id = entry.id
        onDispose {
            latestSave(id, progressByEntry[id] ?: 0f)
        }
    }

    val articleHtml = remember(
        entry.content, entry.summary, entry.title, entry.imageUrl, feedTitle, feedFaviconUrl,
        prefs.fontSize, prefs.lineHeight, prefs.theme,
        prefs.showImages, prefs.serifFont, prefs.justifyText
    ) {
        ArticleHtmlBuilder.build(
            title = entry.title,
            author = entry.author,
            link = entry.link,
            publishedAt = entry.publishedAt,
            content = entry.content ?: entry.summary ?: "",
            prefs = prefs,
            feedTitle = feedTitle,
            feedFaviconUrl = feedFaviconUrl,
            coverImageUrl = entry.imageUrl
        )
    }
    val baseUrl = entry.link?.takeIf { it.startsWith("http") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        entry.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAaPanel = true }) {
                        Icon(Icons.Filled.FormatSize, contentDescription = "阅读设置", tint = colors.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        starred = !starred
                        onToggleStar(entry, starred)
                    }) {
                        Icon(
                            if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "收藏",
                            tint = if (starred) Color(0xFFFFB300) else colors.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onShare(entry) }) {
                        Icon(Icons.Filled.Share, contentDescription = "分享")
                    }
                    IconButton(onClick = { onOpenBrowser(entry) }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "浏览器打开")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { prevEntry?.let(onSwitchEntry) },
                        enabled = prevEntry != null
                    ) { Text("上一篇") }
                    TextButton(
                        onClick = { nextEntry?.let(onSwitchEntry) },
                        enabled = nextEntry != null
                    ) { Text("下一篇") }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            key(entry.id) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ProgressTrackingWebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.javaScriptEnabled = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.domStorageEnabled = true
                            settings.defaultTextEncodingName = "UTF-8"
                            settings.loadsImagesAutomatically = true
                            settings.blockNetworkImage = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            onProgress = { ratio ->
                                currentProgress = ratio
                                progressByEntry[entry.id] = ratio
                                maybeSaveProgress(entry.id, ratio, latestSave)
                            }
                            pendingRestoreRatio = restoreRatio
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val url = request?.url?.toString() ?: return null
                                    val isImage = url.matches(IMAGE_EXT)
                                        || url.contains("/img/") || url.contains("/image/")
                                        || url.contains("/images/") || url.contains("/pics/")
                                        || url.contains("mmbiz.") || url.contains("wxing.")
                                        || url.contains("sinaimg.") || url.contains("hdslb.")
                                        || url.contains("/wp-content/uploads/")
                                    if (!isImage) return null
                                    return interceptImage(ctx, url, baseUrl)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    (view as? ProgressTrackingWebView)?.restorePendingProgress()
                                }
                            }
                            loadDataWithBaseURL(baseUrl, articleHtml, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        if (webView.tag!= articleHtml) {
                            webView.tag = articleHtml
                            webView.pendingRestoreRatio = restoreRatio
                            webView.loadDataWithBaseURL(baseUrl, articleHtml, "text/html", "UTF-8", null)
                        }
                    }
                )
            }

            if (contentIncomplete) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingFullContent) {
                        CircularProgressIndicator()
                    } else {
                        Button(onClick = onLoadFullContent) {
                            Text("加载全文")
                        }
                    }
                }
            }
        }
    }

    if (showAaPanel) {
        AaSettingsPanel(
            prefs = prefs,
            onDismiss = { showAaPanel = false }
        )
    }
}

private var lastSavedEntryId: Long = -1L
private var lastSavedPosition: Float = -1f
private var lastSavedAtElapsed: Long = 0L

/** 时间与比例双重节流，滚动时不每像素写库 */
private fun maybeSaveProgress(
    entryId: Long,
    ratio: Float,
    save: (Long, Float) -> Unit
) {
    val now = SystemClock.elapsedRealtime()
    val sameEntry = entryId == lastSavedEntryId
    if (sameEntry &&
        now - lastSavedAtElapsed < PROGRESS_SAVE_MIN_INTERVAL_MS &&
        abs(ratio - lastSavedPosition) < PROGRESS_SAVE_MIN_DELTA
    ) {
        return
    }
    lastSavedEntryId = entryId
    lastSavedPosition = ratio
    lastSavedAtElapsed = now
    save(entryId, ratio)
}

/**
 * 用 0~1 比例记录滚动位置：scrollY / (contentHeight * scale - viewport)。
 * 比例不受字号、窗口高度变化影响。
 */
private class ProgressTrackingWebView(context: android.content.Context): WebView(context) {
    var onProgress: ((Float) -> Unit)? = null
    var pendingRestoreRatio: Float = 0f
    private var restoring = false

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (restoring) return
        onProgress?.invoke(currentRatio())
    }

    fun currentRatio(): Float {
        val range = (contentHeight * scale - height).coerceAtLeast(1f)
        return (scrollY / range).coerceIn(0f, 1f)
    }

    fun restorePendingProgress() {
        val ratio = pendingRestoreRatio
        if (ratio <= 0f) {
            scrollTo(0, 0)
            return
        }
        post {
            val range = (contentHeight * scale - height).coerceAtLeast(1f)
            restoring = true
            scrollTo(0, (ratio * range).toInt())
            restoring = false
        }
    }
}

/**
 * 用带磁盘缓存的 OkHttp 拉图：先按域名映射 Referer，失败再带文章页 Referer 重试。
 * 未命中映射时不加 Referer 头（不是设空字符串）。
 */
private fun interceptImage(
    context: android.content.Context,
    imageUrl: String,
    articleReferer: String?
): WebResourceResponse? {
    val client = ReaderImageClient.get(context)
    val mapped = ReaderImageClient.mappedReferer(imageUrl)
    val first = runCatching {
        client.newCall(ReaderImageClient.imageRequest(imageUrl, mapped)).execute()
    }.getOrNull()
    val firstOk = first != null && first.isSuccessful && (first.body?.contentLength() ?: 1L) != 0L
    val response = if (firstOk) {
        first
    } else {
        first?.close()
        val retryReferer = articleReferer?.takeIf { it.startsWith("http") }
        if (retryReferer.isNullOrBlank()) {
            null
        } else {
            runCatching {
                client.newCall(ReaderImageClient.imageRequest(imageUrl, retryReferer)).execute()
            }.getOrNull()
        }
    } ?: return null

    return try {
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        toWebResourceResponse(response)
    } catch (_: Exception) {
        response.close()
        null
    }
}

private fun toWebResourceResponse(response: Response): WebResourceResponse {
    val body = response.body ?: throw IllegalStateException("empty body")
    val mediaType = body.contentType()
    val mime = mediaType?.let { "${it.type}/${it.subtype}" } ?: response.header("Content-Type")?.substringBefore(";")?.trim()
    if (mime.isNullOrBlank()) {
        response.close()
        throw IllegalStateException("missing content-type")
    }
    val encoding = mediaType?.charset()?.name()
    val headers = HashMap<String, String>()
    response.headers.names().forEach { name ->
        if (name.equals("Content-Encoding", true) ||
            name.equals("Content-Length", true) ||
            name.equals("Transfer-Encoding", true)
        ) return@forEach
        val value = response.header(name)
        if (value != null) headers[name] = value
    }
    return WebResourceResponse(
        mime,
        encoding,
        response.code,
        response.message.ifBlank { "OK" },
        Collections.unmodifiableMap(headers),
        body.byteStream()
    )
}
