package top.kagg886.pmf.database.account.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import top.kagg886.pmf.database.account.entity.NovelCacheDisplayed
import top.kagg886.pmf.database.account.entity.NovelFlow

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface NovelFlowDao {
    @Query("DELETE FROM novel_flow WHERE tag = :tag")
    suspend fun clean(tag: String)

    @Query(
        """
        SELECT novel_cache.*, novel_flow.id AS flowId FROM novel_cache
        INNER JOIN novel_flow ON novel_flow.novelCacheId = novel_cache.novelId
        WHERE novel_flow.tag = :tag
        ORDER BY novel_flow.id
        """,
    )
    @Transaction
    fun query(tag: String): PagingSource<Int, NovelCacheDisplayed>

    @Insert
    suspend fun insert(flow: List<NovelFlow>)
}
