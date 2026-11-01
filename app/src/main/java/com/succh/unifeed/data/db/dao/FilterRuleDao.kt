package com.succh.unifeed.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.succh.unifeed.data.db.entity.FilterRule
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterRuleDao {
    @Query("SELECT * FROM filter_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<FilterRule>>

    @Query("SELECT * FROM filter_rules")
    suspend fun getAll(): List<FilterRule>

    @Insert
    suspend fun insert(rule: FilterRule): Long

    @Update
    suspend fun update(rule: FilterRule)

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM filter_rules WHERE feedId = :feedId")
    suspend fun deleteByFeed(feedId: Long)
}
