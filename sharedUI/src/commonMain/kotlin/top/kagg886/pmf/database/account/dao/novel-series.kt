package top.kagg886.pmf.database.account.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import top.kagg886.pmf.database.account.entity.NovelSeriesCache

@Dao
interface NovelSeriesDao {
    @Upsert
    suspend fun upsert(item: NovelSeriesCache)

    @Upsert
    suspend fun upsert(items: List<NovelSeriesCache>)

    @Query("SELECT * FROM novel_series_cache WHERE id = :id")
    suspend fun find(id: Long): NovelSeriesCache?

    @Query("DELETE FROM novel_series_cache WHERE id = :id")
    suspend fun delete(id: Long)
}
