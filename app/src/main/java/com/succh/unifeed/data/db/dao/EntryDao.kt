package com.succh.unifeed.data.db.dao

import androidx.room.*
import com.succh.unifeed.data.db.entity.Entry
import com.succh.unifeed.desktop.UnreadWidgetItem
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<Entry>>

    /** 列表展示用：被隐藏的文章不出现 */
    @Query("SELECT * FROM entries WHERE isHidden = 0 ORDER BY publishedAt DESC")
    fun observeVisible(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Entry?

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

    @Query("SELECT COUNT(*) FROM entries WHERE isRead = 0 AND isHidden = 0")
    suspend fun getUnreadCount(): Int

    /** 桌面小部件：最新未读，被隐藏的不计入，与未读角标同一口径 */
    @Query(
        """
        SELECT e.id AS id, e.title AS title, IFNULL(f.title, '') AS feedTitle, e.publishedAt AS publishedAt
        FROM entries AS e
        LEFT JOIN feeds AS f ON f.id = e.feedId
        WHERE e.isRead = 0 AND e.isHidden = 0
        ORDER BY e.publishedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getLatestUnreadForWidget(limit: Int): List<UnreadWidgetItem>

    @Query("DELETE FROM entries WHERE feedId = :feedId")
    suspend fun deleteByFeed(feedId: Long)

    /**
     * 删除截止时间之前、已读且未收藏/未稍后读的文章。
     * 发布时间缺失时用抓取时间；未读一律不删。
     */
    @Query(
        """
        DELETE FROM entries
        WHERE isRead = 1
          AND isStarred = 0
          AND isReadLater = 0
          AND CASE WHEN publishedAt > 0 THEN publishedAt ELSE fetchedAt END < :cutoffMillis
        """
    )
    suspend fun deleteStaleRead(cutoffMillis: Long): Int

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
