package top.kagg886.pmf.database.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import top.kagg886.pmf.database.dao.cache.IllustCache

/** A caller-defined ordered flow of references to cached illustrations. */
@Entity(
    tableName = "illust_flow",
    primaryKeys = ["id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = IllustCache::class,
            parentColumns = ["id"],
            childColumns = ["illustId"],
        ),
    ],
    indices = [Index(value = ["illustId"])],
)
data class IllustFlow(
    /** Caller-defined flow identity. */
    val id: String,
    /** Stable display order within [id]. */
    val position: Int,
    /** Account-isolated [IllustCache.id]. */
    val illustId: Long,
)

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface IllustFlowDao {
    @Query(
        "SELECT illust_cache.* FROM illust_flow " +
            "INNER JOIN illust_cache ON illust_flow.illustId = illust_cache.id " +
            "WHERE illust_flow.id = :id " +
            "ORDER BY illust_flow.position",
    )
    fun pagingSource(id: String): PagingSource<Int, IllustCache>

    @Query("SELECT COUNT(*) FROM illust_flow WHERE id = :id")
    suspend fun count(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<IllustFlow>)

    /** Deletes only flow references; cached illustrations remain available to other flows. */
    @Query("DELETE FROM illust_flow WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    suspend fun replace(id: String, items: List<IllustFlow>) {
        require(items.all { it.id == id }) { "All items must belong to flow $id" }
        delete(id)
        insert(items)
    }
}
