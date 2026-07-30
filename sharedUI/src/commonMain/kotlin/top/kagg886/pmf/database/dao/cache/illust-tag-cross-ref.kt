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
    primaryKeys = ["currentUserId", "illustId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = IllustCache::class,
            parentColumns = ["currentUserId", "illustId"],
            childColumns = ["currentUserId", "illustId"],
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
    val currentUserId: Long,
    val illustId: Long,
    val tagId: String,
)

@Dao
interface IllustTagCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<IllustTagCrossRef>)

    @Query("SELECT * FROM illust_tag_cross_ref WHERE currentUserId = :currentUserId AND illustId = :illustId")
    suspend fun findByIllustId(currentUserId: Long, illustId: Long): List<IllustTagCrossRef>

    @Query("DELETE FROM illust_tag_cross_ref WHERE currentUserId = :currentUserId AND illustId = :illustId")
    suspend fun deleteByIllustId(currentUserId: Long, illustId: Long)
}
