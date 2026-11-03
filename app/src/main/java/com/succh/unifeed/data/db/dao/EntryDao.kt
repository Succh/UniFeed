package com.succh.unifeed.data.db.dao

import androidx.room.*
import com.succh.unifeed.data.db.entity.Entry
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<Entry>>

    /** 列表展示用：被隐藏的文章不出现 */
    @Query("SELECT * FROM entries WHERE isHidden = 0 ORDER BY publishedAt DESC")
    fun observeVisible(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE feedId = :feedId")
    suspend fun getByFeed(feedId: Long): List<Entry>

    /** 一次查出该源已有 guid，供刷新路径批量去重 */
    @Query("SELECT guid FROM entries WHERE feedId = :feedId")
    suspend fun getGuidsByFeed(feedId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<Entry>)

    @Update
    suspend fun update(entry: Entry)

    @Update
    suspend fun updateAll(entries: List<Entry>)

    @Query("UPDATE entries SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE entries SET isRead = 0 WHERE id = :id")
    suspend fun markUnread(id: Long)

    @Query("UPDATE entries SET isRead = 1 WHERE feedId = :feedId AND isRead = 0")
    suspend fun markAllRead(feedId: Long)

    @Query("UPDATE entries SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllReadGlobal()

    @Query("UPDATE entries SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE entries SET isReadLater = :readLater WHERE id = :id")
    suspend fun setReadLater(id: Long, readLater: Boolean)

    @Query("UPDATE entries SET lastReadPosition = :position, lastReadAt = :readAt WHERE id = :id")
    suspend fun updateReadProgress(id: Long, position: Float, readAt: Long)

    @Query("SELECT COUNT(*) FROM entries WHERE isRead = 0 AND isHidden = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("DELETE FROM entries WHERE feedId = :feedId")
    suspend fun deleteByFeed(feedId: Long)

    /**
     * 全文搜索全部已订阅源文章：标题、摘要、正文均走 LIKE。
     * pattern 需已转义 % / _，由仓库层负责。
     */
    @Query(
        """
        SELECT * FROM entries
        WHERE isHidden = 0 AND (
            title LIKE '%' || :pattern || '%' ESCAPE '\'
            OR IFNULL(summary, '') LIKE '%' || :pattern || '%' ESCAPE '\'
            OR IFNULL(content, '') LIKE '%' || :pattern || '%' ESCAPE '\'
        )
        ORDER BY publishedAt DESC
        """
    )
    suspend fun searchAll(pattern: String): List<Entry>
}
