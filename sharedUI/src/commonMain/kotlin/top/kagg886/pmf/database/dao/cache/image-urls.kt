package top.kagg886.pmf.database.dao.cache

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert

@Entity(tableName = "image_urls_cache")
data class ImageUrlsCache(
    @PrimaryKey
    val id: String,
    val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val original: String? = null,
)

@Dao
interface ImageUrlsDao {
    @Upsert
    suspend fun upsert(item: ImageUrlsCache)

    @Query("SELECT * FROM image_urls_cache WHERE id = :id")
    suspend fun find(id: String): ImageUrlsCache?

    @Query("DELETE FROM image_urls_cache WHERE id = :id")
    suspend fun delete(id: String)
}
