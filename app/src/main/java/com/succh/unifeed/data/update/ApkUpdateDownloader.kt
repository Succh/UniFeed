package com.succh.unifeed.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/** 单个下载源：按优先级列出，实际使用顺序由测速结果决定 */
enum class UpdateSource(val label: String) {
    // Gitee 国内直链最快，但 Release 未同步时是 404，测速阶段会自动淘汰
    GITEE("Gitee"),
    GITHUB("GitHub"),
    // ghproxy 类公开镜像做第三兜底；前两源都活着时基本用不到
    GH_PROXY("GitHub 镜像");

    fun apkUrl(tag: String): String {
        val githubDirect =
            "https://github.com/${UpdateChecker.GITHUB_REPO}/releases/download/$tag/${UpdateChecker.APK_ASSET_NAME}"
        return when (this) {
            GITEE -> "https://gitee.com/${UpdateChecker.GITEE_REPO}/releases/download/$tag/${UpdateChecker.APK_ASSET_NAME}"
            GITHUB -> githubDirect
            GH_PROXY -> "https://gh-proxy.com/$githubDirect"
        }
    }
}

/** 下载进度。totalBytes 未知时为 -1 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(
        val receivedBytes: Long,
        val totalBytes: Long,
        /** 实测速度，字节/秒 */
        val speedBytesPerSec: Long,
        /** 当前实际在用的下载源 */
        val source: UpdateSource
    ) : DownloadState()

    data class Completed(val apkFile: File) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

/**
 * APK 双源下载器：
 * 1. 对全部候选源并行测速（各拉 64KB 计时），404/超时的源直接淘汰——
 *    Gitee Release 尚未同步时会在这里被自然跳过，不会拖死下载；
 * 2. 活着的源按实测速度重排后逐个尝试完整下载；
 * 3. 已下载字节落在本地 .part 文件，换源时带 Range 断点续传，不重头来；
 * 4. 单源中途失速（>15s 无新字节）视为失败，自动切下一源接着下。
 */
class ApkUpdateDownloader(context: Context, client: OkHttpClient? = null) {

    private val http: OkHttpClient = (client ?: OkHttpClient()).newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val updateDir = File(context.cacheDir, "update")

    suspend fun download(tag: String, onState: (DownloadState) -> Unit) {
        withContext(Dispatchers.IO) {
            runDownload(tag, onState)
        }
    }

    private suspend fun runDownload(tag: String, onState: (DownloadState) -> Unit) {
        updateDir.mkdirs()
        val partFile = File(updateDir, "$tag.apk.part")
        val doneFile = File(updateDir, "$tag.apk")
        // 之前下完的同一版本直接复用
        if (doneFile.exists() && doneFile.length() > 0) {
            onState(DownloadState.Completed(doneFile))
            return
        }

        val candidates = probeSources(tag)
        if (candidates.isEmpty()) {
            onState(
                DownloadState.Failed("所有下载源均不可用（Gitee/GitHub/镜像都无法访问），请检查网络，或在浏览器中下载")
            )
            return
        }

        var lastError: String? = null
        for (source in candidates) {
            try {
                // runInterruptible：协程被取消时中断阻塞读，OkHttp 流会立刻抛 InterruptedIOException
                runInterruptible { downloadFrom(source, tag, partFile, doneFile, onState) }
                onState(DownloadState.Completed(doneFile))
                return
            } catch (e: Exception) {
                // 用户主动停止（协程被取消）不算换源失败，直接上抛
                currentCoroutineContext().ensureActive()
                // 换源续传：.part 文件保留，下个源从已有字节接着下
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        onState(DownloadState.Failed("各下载源均下载失败，最后错误：$lastError"))
    }

    /** 并行测速：返回按实测速度排序的可用源 */
    private suspend fun probeSources(tag: String): List<UpdateSource> = coroutineScope {
        val results = UpdateSource.entries.map { source ->
            async {
                val start = System.nanoTime()
                val ok = try {
                    val request = Request.Builder()
                        .url(source.apkUrl(tag))
                        .header("Range", "bytes=0-${PROBE_RANGE - 1}")
                        .get()
                        .build()
                    http.newCall(request).execute().use { response ->
                        // 206=支持断点续传，200=整包也可用；404/403 视为该源不可用
                        (response.code == 206 || response.code == 200) &&
                            response.body?.byteStream()?.readNBytes(PROBE_RANGE)?.isNotEmpty() == true
                    }
                } catch (_: Exception) {
                    false
                }
                if (ok) ProbeResult(source, System.nanoTime() - start) else null
            }
        }
        results.awaitAll().filterNotNull()
            .sortedBy { it.elapsedNanos }
            .map { it.source }
    }

    private fun downloadFrom(
        source: UpdateSource,
        tag: String,
        partFile: File,
        doneFile: File,
        onState: (DownloadState) -> Unit
    ) {
        val alreadyHave = if (partFile.exists()) partFile.length() else 0L
        val builder = Request.Builder().url(source.apkUrl(tag)).get()
        if (alreadyHave > 0) {
            // 断点续传：从 .part 已有字节处继续
            builder.header("Range", "bytes=$alreadyHave-")
        }
        http.newCall(builder.build()).execute().use { response ->
            val append = response.code == 206 && alreadyHave > 0
            if (!append) {
                // 200 整包响应（源不支持 Range 或忽略断点），重头来
                if (alreadyHave > 0) partFile.delete()
                if (!partFile.exists()) partFile.parentFile?.mkdirs()
                partFile.createNewFile()
            }
            val totalBytes = parseTotalBytes(response, alreadyHave, append)
            val input = response.body?.byteStream() ?: throw IOException("${source.label} 响应无内容")
            FileOutputStream(partFile, append).use { output ->
                copyWithProgress(input, output, if (append) alreadyHave else 0L, totalBytes, source, onState)
            }
            if (!partFile.renameTo(doneFile)) {
                partFile.copyTo(doneFile, overwrite = true)
                partFile.delete()
            }
        }
    }

    private fun parseTotalBytes(response: Response, alreadyHave: Long, append: Boolean): Long {
        // 206 的 Content-Range 形如 "bytes 1000-200000/200001"，末段是总长
        val contentRange = response.header("Content-Range")
        if (contentRange != null && contentRange.contains("/")) {
            contentRange.substringAfterLast('/').toLongOrNull()?.let { return it }
        }
        return response.header("Content-Length")?.toLongOrNull()
            ?.plus(if (append) alreadyHave else 0L)
            ?: -1L
    }

    /** 带进度与失速检测的拷贝：长时间无新字节即抛异常触发换源 */
    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        baseBytes: Long,
        totalBytes: Long,
        source: UpdateSource,
        onState: (DownloadState) -> Unit
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var received = baseBytes
        var lastReportBytes = received
        var lastReportTime = System.currentTimeMillis()
        var lastProgressTime = lastReportTime

        while (true) {
            if (Thread.currentThread().isInterrupted) {
                // 协程取消会中断 IO 线程，以 InterruptedIOException 退出，保留 .part 供续传
                throw InterruptedIOException("下载已停止")
            }
            val read = input.read(buffer)
            if (read == -1) break
            if (read > 0) {
                output.write(buffer, 0, read)
                received += read
                lastProgressTime = System.currentTimeMillis()
            }
            val now = System.currentTimeMillis()
            // 节流上报：至少 100KB 或 500ms 才发一次进度
            if (now - lastReportTime >= 500 || received - lastReportBytes >= 100_000) {
                val elapsed = (now - lastReportTime).coerceAtLeast(1)
                val speed = (received - lastReportBytes) * 1000 / elapsed
                onState(DownloadState.Downloading(received, totalBytes, speed, source))
                lastReportBytes = received
                lastReportTime = now
            }
            // 失速检测：15s 读不到新字节就切源
            if (now - lastProgressTime > STALL_TIMEOUT_MS) {
                throw IOException("${source.label} 下载失速（超过 15 秒无进度）")
            }
        }
        output.flush()
    }

    private data class ProbeResult(val source: UpdateSource, val elapsedNanos: Long)

    companion object {
        private const val PROBE_RANGE = 64 * 1024
        private const val BUFFER_SIZE = 32 * 1024
        private const val STALL_TIMEOUT_MS = 15_000L
    }
}
