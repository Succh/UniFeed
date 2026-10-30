package com.succh.unifeed.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.succh.unifeed.data.db.entity.SearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC")
    fun observeAll(): Flow<List<SearchHistory>>

    @Query("SELECT * FROM search_history WHERE query = :query LIMIT 1")
    suspend fun getByQuery(query: String): SearchHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SearchHistory): Long

    @Update
    suspend fun update(item: SearchHistory)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun deleteAll()

    /**
     * 超出保留条数的最旧记录 id。
     * 不用 LIMIT -1 OFFSET，避免 Room 解析负 LIMIT。
     */
    @Query(
        """
        SELECT id FROM search_history
        WHERE id NOT IN (
            SELECT id FROM search_history ORDER BY searchedAt DESC LIMIT :keep
        )
        """
    )
    suspend fun idsBeyond(keep: Int): List<Long>

    @Query("DELETE FROM search_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
