package top.kagg886.pmf.database.account.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import top.kagg886.pmf.database.account.entity.AuthorDisplayed
import top.kagg886.pmf.database.account.entity.AuthorFlow

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface AuthorFlowDao {
    @Query("DELETE FROM author_flow WHERE tag = :tag")
    suspend fun clean(tag: String)

    @Query(
        """
        SELECT user_cache.* FROM user_cache
        INNER JOIN author_flow ON author_flow.userCacheId = user_cache.userId
        WHERE author_flow.tag = :tag
        ORDER BY author_flow.id
        """,
    )
    @Transaction
    fun query(tag: String): PagingSource<Int, AuthorDisplayed>

    @Insert
    suspend fun insert(flow: List<AuthorFlow>)
}
