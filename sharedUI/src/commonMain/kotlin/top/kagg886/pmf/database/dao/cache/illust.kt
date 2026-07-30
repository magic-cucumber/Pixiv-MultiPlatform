package top.kagg886.pmf.database.dao.cache

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert

@Entity(
    tableName = "illust_cache",
    foreignKeys = [
        ForeignKey(
            entity = UserCache::class,
            parentColumns = ["id"],
            childColumns = ["authorId"],
        ),
        ForeignKey(
            entity = ImageUrlsCache::class,
            parentColumns = ["id"],
            childColumns = ["imageUrlsId"],
        ),
    ],
    indices = [Index(value = ["authorId"]), Index(value = ["imageUrlsId"])],
)
data class IllustCache(
    @PrimaryKey
    val id: Long,
    val title: String,
    val caption: String,
    val type: String,
    val authorId: Long,
    val createTime: Long,
    val pageCount: Int,
    val width: Int,
    val height: Int,
    val sanityLevel: Int,
    val xRestrict: Int,
    val totalView: Int,
    val totalBookmarks: Int,
    val isBookmarked: Boolean,
    val illustAiType: Int,
    val imageUrlsId: String,
    val singlePageMetaJson: String? = null,
)

@Dao
interface IllustDao {
    @Upsert
    suspend fun upsert(item: IllustCache)

    @Upsert
    suspend fun upsert(items: List<IllustCache>)

    @Query("SELECT * FROM illust_cache WHERE id = :id")
    suspend fun find(id: Long): IllustCache?

    @Query("DELETE FROM illust_cache WHERE id = :id")
    suspend fun delete(id: Long)
}
