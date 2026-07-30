package top.kagg886.pmf.database.dao.cache

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Entity(
    tableName = "illust_tag_cross_ref",
    primaryKeys = ["illustId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = IllustCache::class,
            parentColumns = ["id"],
            childColumns = ["illustId"],
        ),
        ForeignKey(
            entity = TagCache::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
data class IllustTagCrossRef(
    val illustId: Long,
    val tagId: String,
)

@Dao
interface IllustTagCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<IllustTagCrossRef>)

    @Query("SELECT * FROM illust_tag_cross_ref WHERE illustId = :illustId")
    suspend fun findByIllustId(illustId: Long): List<IllustTagCrossRef>

    @Query("DELETE FROM illust_tag_cross_ref WHERE illustId = :illustId")
    suspend fun deleteByIllustId(illustId: Long)
}
