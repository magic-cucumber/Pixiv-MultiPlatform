package top.kagg886.pmf.database.dao

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

/**
 * Records the pagination outcome after a logical page was loaded.
 *
 * A page-size source leaves [url] null. A next-url source stores the cursor for the
 * following page in [url]. [endOfPaginationReached] distinguishes a missing record
 * (which has not been loaded yet) from a known end of the remote list.
 */
@Entity(tableName = "page_key_record", primaryKeys = ["id", "page"])
data class PageKeyRecord(
    val id: String,
    val page: Int,
    val url: String?,
    val endOfPaginationReached: Boolean,
) {
    init {
        require(page >= 1) { "page must be positive" }
    }
}

@Dao
interface PageKeyRecordDao {
    @Query("SELECT * FROM page_key_record WHERE id = :id AND page = :page")
    suspend fun find(id: String, page: Int): PageKeyRecord?

    @Query("SELECT * FROM page_key_record WHERE id = :id ORDER BY page DESC LIMIT 1")
    suspend fun findLatest(id: String): PageKeyRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PageKeyRecord)

    @Query("DELETE FROM page_key_record WHERE id = :id")
    suspend fun delete(id: String)
}
