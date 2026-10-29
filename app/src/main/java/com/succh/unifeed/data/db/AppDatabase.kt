package com.succh.unifeed.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.succh.unifeed.data.db.dao.EntryDao
import com.succh.unifeed.data.db.dao.FeedDao
import com.succh.unifeed.data.db.dao.FolderDao
import com.succh.unifeed.data.db.dao.SearchHistoryDao
import com.succh.unifeed.data.db.entity.Entry
import com.succh.unifeed.data.db.entity.Feed
import com.succh.unifeed.data.db.entity.Folder
import com.succh.unifeed.data.db.entity.SearchHistory

@Database(
    entities = [Feed::class, Entry::class, Folder::class, SearchHistory::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun entryDao(): EntryDao
    abstract fun folderDao(): FolderDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        /** v1 → v2：文章封面图 + 订阅源刷新状态字段 */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN imageUrl TEXT")
                db.execSQL("ALTER TABLE feeds ADD COLUMN lastRefreshTime INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE feeds ADD COLUMN lastRefreshError TEXT")
            }
        }

        /** v2 → v3：阅读进度比例 + 最近阅读时间 */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN lastReadPosition REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE entries ADD COLUMN lastReadAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4：搜索历史表，query 唯一，便于同词刷新时间戳 */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS search_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        query TEXT NOT NULL,
                        searchedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_query ON search_history (query)"
                )
            }
        }
    }
}
