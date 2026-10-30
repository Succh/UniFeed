package com.succh.unifeed

import android.app.Application
import androidx.room.Room
import com.succh.unifeed.data.db.AppDatabase
import com.succh.unifeed.data.repository.FeedRepository
import com.succh.unifeed.data.work.RefreshWorker
import com.succh.unifeed.ui.ReaderPrefs

class UniFeedApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 启动时恢复后台自动刷新调度
        if (appPrefs.autoRefreshEnabled) {
            RefreshWorker.schedule(this, appPrefs.autoRefreshIntervalHours)
        }
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "unifeed.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4
            )
            .build()
    }

    /** 全局阅读偏好（含 RSSHub 实例配置） */
    val appPrefs: ReaderPrefs by lazy {
        ReaderPrefs(this)
    }

    val repository: FeedRepository by lazy {
        FeedRepository(database).apply {
            // 启动时同步已保存的自定义实例
            customRsshubInstance = appPrefs.rsshubInstance
        }
    }
}
