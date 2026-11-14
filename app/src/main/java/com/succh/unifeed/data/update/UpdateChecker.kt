package com.succh.unifeed.data.update

import android.os.Build
import com.succh.unifeed.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 检查到的新版本信息 */
data class UpdateInfo(
    /** Release tag，如 v0.1.41 */
    val tag: String,
    /** 版本名（去掉 v 前缀），如 0.1.41 */
    val versionName: String,
    /** Release notes 原文 */
    val releaseNotes: String,
    /** Release 网页地址（浏览器备用通道落地页） */
    val releaseUrl: String
)

/** 更新检查结果：明确区分「已是最新」与「检查失败」，不静默失败 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

/**
 * 更新检查：主读 GitHub Releases latest 接口，国内接口不通时回落 Gitee 同名接口。
 * 两个接口都失败时返回 Failed，由调用方如实提示，不假装「已是最新」。
 */
class UpdateChecker(client: OkHttpClient? = null) {

    private val http: OkHttpClient = (client ?: OkHttpClient()).newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // 主源：GitHub Releases latest
        fetchLatest("https://api.github.com/repos/${GITHUB_REPO}/releases/latest")?.let {
            return@withContext judge(it)
        }
        errors += "GitHub 接口不可达"

        // 回落源：Gitee 同名 Release 接口（国内 GitHub API 被墙时有网也能查到）
        fetchLatest("https://gitee.com/api/v5/repos/${GITEE_REPO}/releases/latest")?.let {
            return@withContext judge(it)
        }
        errors += "Gitee 接口不可达"

        UpdateCheckResult.Failed("无法获取最新版本信息（${errors.joinToString("、")}），请检查网络后重试")
    }

    private fun judge(info: UpdateInfo): UpdateCheckResult {
        return if (isVersionNewer(info.versionName, BuildConfig.VERSION_NAME)) {
            UpdateCheckResult.UpdateAvailable(info)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    private fun fetchLatest(url: String): UpdateInfo? {
        return try {
            withTimeoutOrNull(15_000L) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "UniFeed/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})")
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withTimeoutOrNull null
                    val body = response.body?.string() ?: return@withTimeoutOrNull null
                    parseRelease(body)
                }
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun parseRelease(body: String): UpdateInfo? {
        return try {
            val obj = JSONObject(body)
            val tag = obj.optString("tag_name").orEmpty()
            if (tag.isBlank()) return null
            val notes = obj.optString("body").orEmpty()
            // 优先用接口返回的页面地址；Gitee 接口返回的就是 gitee 页面，浏览器都能打开
            val pageUrl = obj.optString("html_url")
                .takeIf { it.startsWith("http") }
                ?: "https://github.com/${GITHUB_REPO}/releases/tag/$tag"
            UpdateInfo(
                tag = tag,
                versionName = tag.removePrefix("v").removePrefix("V"),
                releaseNotes = notes,
                releaseUrl = pageUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val GITHUB_REPO = "Succh/UniFeed"
        const val GITEE_REPO = "succh/UniFeed"
        const val APK_ASSET_NAME = "app-debug.apk"

        /**
         * 语义化版本比较：按数字段逐位比，缺失段按 0 处理。
         * 忽略 v 前缀与 -beta 之类后缀；解析不出来的段按 0 兜底，绝不让比较抛异常。
         */
        fun isVersionNewer(remote: String, local: String): Boolean {
            val remoteSeg = parseVersion(remote)
            val localSeg = parseVersion(local)
            val size = maxOf(remoteSeg.size, localSeg.size)
            for (i in 0 until size) {
                val r = remoteSeg.getOrElse(i) { 0 }
                val l = localSeg.getOrElse(i) { 0 }
                if (r != l) return r > l
            }
            return false
        }

        private fun parseVersion(version: String): List<Int> {
            val cleaned = version.trim().removePrefix("v").removePrefix("V")
                .substringBefore('-')
            return cleaned.split('.').map { seg -> seg.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        }
    }
}
