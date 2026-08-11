package top.kagg886.pmf.database.account.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import top.kagg886.pmf.database.account.entity.NovelCache

@Dao
interface NovelDao {
    @Upsert
    suspend fun upsert(item: NovelCache)

    @Upsert
    suspend fun upsert(items: List<NovelCache>)

    @Query("SELECT * FROM novel_cache WHERE novelId = :novelId")
    suspend fun find(novelId: Long): NovelCache?

    @Query("DELETE FROM novel_cache WHERE novelId = :novelId")
    suspend fun deleteByNovelId(novelId: Long)
}
