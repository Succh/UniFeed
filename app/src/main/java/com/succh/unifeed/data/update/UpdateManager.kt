package com.succh.unifeed.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.succh.unifeed.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 更新模块对 UI 暴露的完整状态 */
data class UpdateUiState(
    /** 检查中 */
    val isChecking: Boolean = false,
    /** 检查结果（UpToDate/Failed 也要展示，不能静默） */
    val checkResult: UpdateCheckResult? = null,
    val download: DownloadState = DownloadState.Idle,
    /** 当前待更新的版本信息（有值才弹更新弹窗/显示下载入口） */
    val pendingUpdate: UpdateInfo? = null
)

/**
 * 应用内更新通道管理器：检查、双源下载（含通知栏进度）、调起安装器、浏览器备用入口。
 * 单例挂在 Application 上，启动静默检查与设置页手动检查共用同一份状态。
 */
class UpdateManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checker = UpdateChecker()
    private val downloader by lazy { ApkUpdateDownloader(context) }

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null
    private var downloadTag: String? = null

    /** 手动检查更新（设置页入口）。结果无论成败都写入状态供界面如实展示 */
    fun checkNow() {
        if (_state.value.isChecking) return
        _state.value = _state.value.copy(isChecking = true)
        scope.launch {
            val result = try {
                checker.check()
            } catch (e: Exception) {
                UpdateCheckResult.Failed("检查更新失败：${e.message ?: "网络异常"}")
            }
            val pending = (result as? UpdateCheckResult.UpdateAvailable)?.info
            // 已是最新或检查失败时清掉旧的待更新信息，避免残留弹窗
            _state.value = _state.value.copy(
                isChecking = false,
                checkResult = result,
                pendingUpdate = pending
            )
            markLastCheckTime()
        }
    }

    /**
     * 启动静默检查：有网且距上次检查超过一天才查；有更新才置状态由界面弹窗，
     * 无网/已是最新/失败一律不打扰。
     */
    fun silentCheckOnStartup() {
        if (System.currentTimeMillis() - readLastCheckTime() < SILENT_CHECK_INTERVAL_MS) return
        if (!isNetworkAvailable()) return
        scope.launch {
            when (val result = checker.check()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _state.value = _state.value.copy(checkResult = result, pendingUpdate = result.info)
                    markLastCheckTime()
                }
                else -> markLastCheckTime()
            }
        }
    }

    /** 开始/恢复 APP 内下载（后台协程 + 通知栏真实进度 + 断点续传）。force=true 时清掉旧文件重下 */
    fun startDownload(info: UpdateInfo, force: Boolean = false) {
        if (downloadJob?.isActive == true) return
        if (force) {
            apkFile(info.tag).delete()
            File(context.cacheDir, "update/${info.tag}.apk.part").delete()
        } else if (_state.value.download is DownloadState.Completed && apkFile(info.tag).exists()) {
            // 已下完，直接装
            installApk()
            return
        }
        ensureNotificationChannel()
        downloadTag = info.tag
        _state.value = _state.value.copy(download = DownloadState.Idle)
        downloadJob = scope.launch {
            downloader.download(info.tag) { newState ->
                _state.value = _state.value.copy(download = newState)
                when (newState) {
                    is DownloadState.Downloading -> notifyProgress(info, newState)
                    is DownloadState.Completed -> notifyInstallReady(info)
                    is DownloadState.Failed -> notifyFailure(info, newState.message)
                    DownloadState.Idle -> Unit
                }
            }
        }
    }

    /** 停止下载，.part 保留，下次点更新从断点续传 */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        cancelProgressNotification()
        _state.value = _state.value.copy(download = DownloadState.Idle)
    }

    /** 调起系统安装器；未授予「安装未知应用」权限时先跳授权页 */
    fun installApk() {
        val info = _state.value.pendingUpdate ?: return
        val file = (_state.value.download as? DownloadState.Completed)?.apkFile ?: apkFile(info.tag)
        if (!file.exists()) return
        // Android 8+ 需要先拿到「安装未知应用」授权；没有就跳到本应用的授权页，回来再点一次安装
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        context.startActivity(buildInstallIntent(info).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 备用通道：系统浏览器打开 Release 下载页，由用户在浏览器里下载、安装 */
    fun openReleasePageInBrowser() {
        val url = _state.value.pendingUpdate?.releaseUrl
            ?: "https://github.com/${UpdateChecker.GITHUB_REPO}/releases/latest"
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ---------- 通知 ----------

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "应用更新",
                NotificationManager.IMPORTANCE_LOW // 进度通知低优先级，不响铃不打扰
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun notifyProgress(info: UpdateInfo, state: DownloadState.Downloading) {
        val percent = if (state.totalBytes > 0) {
            (state.receivedBytes * 100 / state.totalBytes).toInt().coerceIn(0, 100)
        } else null
        val text = buildString {
            append("正在从${state.source.label}下载 v${info.versionName}")
            if (percent != null) append("  $percent%")
            append("  ${formatBytes(state.receivedBytes)}")
            if (state.totalBytes > 0) append(" / ${formatBytes(state.totalBytes)}")
            append("  ${formatBytes(state.speedBytesPerSec)}/s")
        }
        val notification = progressBuilder(info)
            .setContentText(text)
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .build()
        notifySafely(NOTIFY_ID_PROGRESS, notification)
    }

    private fun notifyInstallReady(info: UpdateInfo) {
        val installIntent = PendingIntent.getActivity(
            context,
            0,
            buildInstallIntent(info),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = progressBuilder(info)
            .setContentTitle("UniFeed v${info.versionName} 下载完成")
            .setContentText("点击安装")
            .setContentIntent(installIntent)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notifySafely(NOTIFY_ID_PROGRESS, notification)
    }

    private fun notifyFailure(info: UpdateInfo, message: String) {
        val notification = progressBuilder(info)
            .setContentTitle("UniFeed 更新下载失败")
            .setContentText(message)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notifySafely(NOTIFY_ID_PROGRESS, notification)
    }

    private fun cancelProgressNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFY_ID_PROGRESS)
    }

    private fun progressBuilder(info: UpdateInfo): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在下载 UniFeed v${info.versionName}")
            .setOnlyAlertOnce(true)
            .setSilent(true)

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // 用户未授予通知权限：不影响下载本身，应用内进度照常
        }
    }

    /** 构建「点开即装 APK」的 Intent，通知与手动安装复用 */
    private fun buildInstallIntent(info: UpdateInfo): Intent {
        val file = apkFile(info.tag)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun apkFile(tag: String): File = File(File(context.cacheDir, "update"), "$tag.apk")

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000 -> "%.1fMB".format(bytes / 1_000_000f)
        bytes >= 1_000 -> "%.0fKB".format(bytes / 1_000f)
        else -> "${bytes}B"
    }

    // ---------- 杂项 ----------

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun readLastCheckTime(): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK, 0L)

    private fun markLastCheckTime() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /** 通知权限是否已授予（Android 13+ 下载进度通知需要） */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    companion object {
        private const val CHANNEL_ID = "update"
        private const val NOTIFY_ID_PROGRESS = 2001
        private const val PREFS = "update_prefs"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val SILENT_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: UpdateManager? = null

        fun get(context: Context): UpdateManager =
            instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also { instance = it }
            }
    }
}
