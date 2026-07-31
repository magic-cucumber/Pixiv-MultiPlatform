package top.kagg886.pmf.database.account.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import top.kagg886.pmf.database.account.entity.IllustCache

@Dao
interface IllustDao {
    @Upsert
    suspend fun upsert(item: IllustCache)

    @Upsert
    suspend fun upsert(items: List<IllustCache>)

    @Query("SELECT * FROM illust_cache WHERE illustId = :illustId")
    suspend fun find(illustId: Long): IllustCache?

    @Query("DELETE FROM illust_cache WHERE illustId = :illustId")
    suspend fun delete(illustId: Long)
}
