package com.succh.unifeed.data.db.dao

import androidx.room.*
import com.succh.unifeed.data.db.entity.Entry
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<Entry>>

    /** 一次查出该源已有 guid，供刷新路径批量去重 */
    @Query("SELECT guid FROM entries WHERE feedId = :feedId")
    suspend fun getGuidsByFeed(feedId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<Entry>)

    @Update
    suspend fun update(entry: Entry)

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

    @Query("UPDATE entries SET lastReadPosition = :position, lastReadAt = :readAt WHERE id = :id")
    suspend fun updateReadProgress(id: Long, position: Float, readAt: Long)

    @Query("SELECT COUNT(*) FROM entries WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("DELETE FROM entries WHERE feedId = :feedId")
    suspend fun deleteByFeed(feedId: Long)
}
