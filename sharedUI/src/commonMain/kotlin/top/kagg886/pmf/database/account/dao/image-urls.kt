package top.kagg886.pmf.database.account.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import top.kagg886.pmf.database.account.entity.ImageUrlsCache

@Dao
interface ImageUrlsDao {
    @Upsert
    suspend fun upsert(item: ImageUrlsCache)

    @Query("SELECT * FROM image_urls_cache WHERE id = :id")
    suspend fun find(id: String): ImageUrlsCache?

    @Query("DELETE FROM image_urls_cache WHERE id = :id")
    suspend fun delete(id: String)
}
