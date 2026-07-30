package top.kagg886.pmf.database.dao.cache

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Entity(
    tableName = "illust_meta_page_cross_ref",
    primaryKeys = ["illustId", "sortIndex"],
    foreignKeys = [
        ForeignKey(
            entity = IllustCache::class,
            parentColumns = ["id"],
            childColumns = ["illustId"],
        ),
        ForeignKey(
            entity = ImageUrlsCache::class,
            parentColumns = ["id"],
            childColumns = ["imageUrlsId"],
        ),
    ],
    indices = [Index(value = ["imageUrlsId"])],
)
data class IllustMetaPageCrossRef(
    val illustId: Long,
    val imageUrlsId: String,
    val sortIndex: Int,
)

@Dao
interface IllustMetaPageCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<IllustMetaPageCrossRef>)

    @Query(
        "SELECT * FROM illust_meta_page_cross_ref " +
            "WHERE illustId = :illustId ORDER BY sortIndex",
    )
    suspend fun findByIllustId(illustId: Long): List<IllustMetaPageCrossRef>

    @Query("DELETE FROM illust_meta_page_cross_ref WHERE illustId = :illustId")
    suspend fun deleteByIllustId(illustId: Long)
}
