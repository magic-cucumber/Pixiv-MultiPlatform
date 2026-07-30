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
    primaryKeys = ["currentUserId", "identifier", "position"],
    foreignKeys = [
        ForeignKey(
            entity = IllustCache::class,
            parentColumns = ["currentUserId", "illustId"],
            childColumns = ["currentUserId", "illustId"],
        ),
    ],
    indices = [Index(value = ["currentUserId", "illustId"])],
)
data class IllustFlow(
    /** Account whose personalized illustration cache this flow references. */
    val currentUserId: Long,
    /** Caller-defined flow identity within [currentUserId]. */
    val identifier: String,
    /** Stable display order within [identifier]. */
    val position: Int,
    /** Account-isolated [IllustCache.illustId]. */
    val illustId: Long,
)

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface IllustFlowDao {
    @Query(
        "SELECT illust_cache.* FROM illust_flow " +
            "INNER JOIN illust_cache ON " +
            "illust_flow.currentUserId = illust_cache.currentUserId AND " +
            "illust_flow.illustId = illust_cache.illustId " +
            "WHERE illust_flow.currentUserId = :currentUserId AND illust_flow.identifier = :identifier " +
            "ORDER BY illust_flow.position",
    )
    fun pagingSource(currentUserId: Long, identifier: String): PagingSource<Int, IllustCache>

    @Query("SELECT COUNT(*) FROM illust_flow WHERE currentUserId = :currentUserId AND identifier = :identifier")
    suspend fun count(currentUserId: Long, identifier: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<IllustFlow>)

    /** Deletes only flow references; cached illustrations remain available to other flows. */
    @Query("DELETE FROM illust_flow WHERE currentUserId = :currentUserId AND identifier = :identifier")
    suspend fun delete(currentUserId: Long, identifier: String)

    @Transaction
    suspend fun replace(currentUserId: Long, identifier: String, items: List<IllustFlow>) {
        require(items.all { it.currentUserId == currentUserId && it.identifier == identifier }) {
            "All items must belong to $currentUserId:$identifier"
        }
        delete(currentUserId, identifier)
        insert(items)
    }
}
