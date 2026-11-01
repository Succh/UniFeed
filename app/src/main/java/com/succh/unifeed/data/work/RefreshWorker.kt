package com.succh.unifeed.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.succh.unifeed.UniFeedApp
import com.succh.unifeed.ui.ReaderPrefs
import java.util.concurrent.TimeUnit

/**
 * 后台自动刷新 Worker：定期调用 repository.refreshAll() 抓取新文章。
 * 需要联网；失败时重试。
 */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as UniFeedApp
            app.repository.refreshAll()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "auto-refresh"

        /**
         * 按当前偏好重新调度：手动档取消周期任务；其余档用 REPLACE 覆盖旧间隔。
         * 仅 Wi-Fi 开启时约束为 UNMETERED，否则 CONNECTED。
         */
        fun reschedule(context: Context, prefs: ReaderPrefs) {
            schedule(context, prefs.refreshInterval, prefs.refreshWifiOnly)
        }

        fun schedule(
            context: Context,
            interval: RefreshInterval,
            wifiOnly: Boolean
        ) {
            if (!interval.isPeriodic) {
                cancel(context)
                return
            }
            val networkType = if (wifiOnly) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(
                interval.minutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
