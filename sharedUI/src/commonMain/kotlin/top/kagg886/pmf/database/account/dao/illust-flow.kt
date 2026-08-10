package top.kagg886.pmf.database.account.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import top.kagg886.pmf.database.account.entity.IllustCache
import top.kagg886.pmf.database.account.entity.IllustFlow

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/10 13:47
 * ================================================
 */

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface IllustFlowDao {
    @Query("DELETE FROM illust_flow WHERE tag = :tag")
    suspend fun clean(tag: String)

    @Query(
        """
        SELECT illust_cache.* FROM illust_cache
        INNER JOIN illust_flow ON illust_flow.illustCacheId = illust_cache.illustId
        WHERE illust_flow.tag = :tag
        ORDER BY illust_flow.id
        """,
    )
    fun query(tag: String): PagingSource<Int, IllustCache>

    @Insert
    suspend fun insert(flow: List<IllustFlow>)
}
