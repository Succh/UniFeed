package com.succh.unifeed.ui

import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.JavascriptInterface
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.succh.unifeed.data.rss.ReaderImageClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.max
import kotlin.math.roundToInt

/** 双指捏合的缩放上限 */
private const val LIGHTBOX_MAX_SCALE = 5f
/** 双击放大的目标倍数 */
private const val LIGHTBOX_DOUBLE_TAP_SCALE = 2.5f
/** 灯箱大图解码的最长边（按倍数取样，控制大图内存） */
private const val LIGHTBOX_DECODE_MAX_DIM = 1600

/**
 * JS 桥：正文图片点按后由 PAGE_JS 经 window.UniFeedLightbox 调用。
 * 回调发生在 WebView 的 JS 线程，调用方需自行切到主线程再改 Compose 状态。
 */
class LightboxBridge(private val onTap: (Int) -> Unit) {
    @JavascriptInterface
    fun onImageTap(index: Int) {
        onTap(index)
    }
}

/** 灯箱数据：与正文 data-lb-i 一一对应的图片列表 + 点按进入时的起始页 */
data class ReaderLightboxData(
    val urls: List<String>,
    val index: Int,
    val articleReferer: String? = null
)

/**
 * 解析 evaluateJavascript 返回的图片数组 JSON（PAGE_JS 的 __uniFeedLightboxImages）。
 * 非法输入一律返回空列表，不让坏数据进灯箱。
 */
fun parseLightboxUrls(json: String?): List<String> {
    if (json.isNullOrBlank() || json == "null") return emptyList()
    return try {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) add(s)
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * 阅读页图片灯箱：全屏浮层，双指缩放、双击放大/还原、左右滑动切换、张数指示。
 * 只是叠在阅读页上的 Dialog，不动 WebView 本体，关闭后滚动位置与朗读都不受影响。
 */
@Composable
fun ReaderLightbox(
    data: ReaderLightboxData,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val pagerState = rememberPagerState(
                initialPage = data.index.coerceIn(0, (data.urls.size - 1).coerceAtLeast(0))
            ) {
                data.urls.size
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                LightboxImagePage(
                    url = data.urls[page],
                    articleReferer = data.articleReferer,
                    onTapClose = onDismiss
                )
            }
            // 顶部浮层：关闭按钮 + 张数指示（多图才显示）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭灯箱",
                        tint = Color.White
                    )
                }
                if (data.urls.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${data.urls.size}",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .background(Color(0x66000000), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** 单页：加载 → 展示（可缩放）/ 失败占位（可重试），三态都不允许白屏死掉 */
@Composable
private fun LightboxImagePage(
    url: String,
    articleReferer: String?,
    onTapClose: () -> Unit
) {
    val context = LocalContext.current
    var retryKey by remember(url) { mutableStateOf(0) }
    // 三态加载：Loading / Done / Failed，失败明确提示而不是空白死屏
    val load by produceState<LightboxLoad>(LightboxLoad.Loading, url, retryKey) {
        value = LightboxLoad.Loading
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { decodeLightboxImage(context, url, articleReferer) }.getOrNull()
        }
        value = if (bitmap != null) LightboxLoad.Done(bitmap) else LightboxLoad.Failed
    }
    when (val state = load) {
        is LightboxLoad.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        is LightboxLoad.Failed -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(url, retryKey) {
                        detectTapGestures { retryKey++ }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("图片加载失败", color = Color.White, fontSize = 16.sp)
                    Text(
                        "点击重试",
                        color = Color(0xFFB0B0B0),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        is LightboxLoad.Done -> ZoomableBitmap(
            bitmap = state.bitmap,
            onTapClose = onTapClose
        )
    }
}

private sealed interface LightboxLoad {
    data object Loading : LightboxLoad
    data class Done(val bitmap: ImageBitmap) : LightboxLoad
    data object Failed : LightboxLoad
}

/** 可缩放的单张图：双指捏合 + 放大后拖动 + 双击放大/还原；未放大时水平拖动交给 Pager 翻页 */
@Composable
private fun ZoomableBitmap(
    bitmap: ImageBitmap,
    onTapClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { containerSize = it }
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "文章配图",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                // 双击/单击：未放大时单击关灯箱；双击在点击位置放大或还原
                .pointerInput(bitmap) {
                    detectTapGestures(
                        onTap = { if (scale <= 1.01f) onTapClose() },
                        onDoubleTap = { pos ->
                            val cRel = Offset(
                                pos.x - containerSize.width / 2f,
                                pos.y - containerSize.height / 2f
                            )
                            scope.launch {
                                val target = if (scale > 1.01f) 1f else LIGHTBOX_DOUBLE_TAP_SCALE
                                val s0 = scale
                                val t0 = offset
                                // 围绕点击点插值：s0→target 过程中保持点击处内容点不动
                                animate(initialValue = s0, targetValue = target, animationSpec = tween(200)) { v, _ ->
                                    val k = v / s0
                                    scale = v
                                    offset = if (v <= 1.01f) Offset.Zero else t0 * k + (1f - k) * cRel
                                }
                            }
                        }
                    )
                }
                // 双指捏合与放大后的拖动；单指未放大时不消费事件，让 Pager 接管翻页
                .pointerInput(bitmap) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var multiTouch = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 0) break
                            if (pressed >= 2) multiTouch = true
                            if (multiTouch) {
                                val zoomFactor = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val newScale = (scale * zoomFactor).coerceIn(1f, LIGHTBOX_MAX_SCALE)
                                val effective = newScale / scale
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val cRel = centroid - center
                                // 围绕质心缩放：保持质心下的内容点不动，再叠加双指整体位移
                                val next = offset * effective + (1f - effective) * cRel + pan
                                scale = newScale
                                offset = if (newScale <= 1.01f) Offset.Zero
                                else clampOffset(next, newScale, containerSize, bitmap)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (scale > 1.01f) {
                                // 已放大：单指拖动平移图片，消费掉避免 Pager 抢走
                                offset = clampOffset(
                                    offset + event.calculatePan(), scale, containerSize, bitmap
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    }
                }
        )
    }
}

/** 按适配后的图片尺寸限制平移范围，避免放大后把图拖出屏幕 */
private fun clampOffset(
    offset: Offset,
    scale: Float,
    containerSize: IntSize,
    bitmap: ImageBitmap
): Offset {
    if (containerSize.width == 0 || containerSize.height == 0) return offset
    val fit = minOf(
        containerSize.width.toFloat() / bitmap.width,
        containerSize.height.toFloat() / bitmap.height
    )
    val maxX = max(0f, (bitmap.width * fit * scale - containerSize.width) / 2f)
    val maxY = max(0f, (bitmap.height * fit * scale - containerSize.height) / 2f)
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}

/**
 * 拉取并解码灯箱大图：复用 ReaderImageClient（与 WebView 同一条管线、同一磁盘缓存），
 * 先按域名 Referer 请求，失败再用文章页 Referer 重试；data: 内嵌图直接 base64 解码。
 */
private fun decodeLightboxImage(
    context: android.content.Context,
    url: String,
    articleReferer: String?
): ImageBitmap? {
    val bytes: ByteArray = if (url.startsWith("data:")) {
        if (!url.contains("base64,")) return null
        Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT)
    } else {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val client = ReaderImageClient.get(context)
        val first = runCatching {
            client.newCall(ReaderImageClient.imageRequest(url, ReaderImageClient.mappedReferer(url))).execute()
        }.getOrNull()
        val firstBytes = first?.takeIf { it.isSuccessful }?.body?.use { it.bytes() }
        if (firstBytes != null) {
            firstBytes
        } else {
            first?.close()
            val retryReferer = articleReferer?.takeIf { it.startsWith("http") } ?: return null
            runCatching {
                client.newCall(ReaderImageClient.imageRequest(url, retryReferer)).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull() ?: return null
        }
    }
    return decodeSampled(bytes, LIGHTBOX_DECODE_MAX_DIM)?.asImageBitmap()
}

/** 先读尺寸再按倍数取样解码，超长边大图不致撑爆内存 */
private fun decodeSampled(bytes: ByteArray, maxDim: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val maxSide = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (maxSide / (sample * 2) >= maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    // 解码后仍超上限的（极端长边），按比例二次缩到上限
    val longest = max(bitmap.width, bitmap.height)
    if (longest > maxDim * 2) {
        val ratio = maxDim * 2f / longest
        val scaled = android.graphics.Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
    return bitmap
}
