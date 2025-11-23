package top.kagg886.pmf.backend.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import top.kagg886.pmf.backend.database.converters.JsonObjectConverter
import top.kagg886.pmf.backend.database.converters.WatchLaterTypeConverter

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/22 21:05
 * ================================================
 */

@Dao
interface WatchLaterDao {
    @Insert
    suspend fun insert(item: WatchLaterItem): Long

    @Query("SELECT * FROM WatchLaterItem ORDER BY createTime DESC")
    fun source(): PagingSource<Int, WatchLaterItem>

    @Query("DELETE FROM WatchLaterItem WHERE createTime < :deadline")
    suspend fun cleanBefore(deadline: Long)

    @Query(
        """
    SELECT EXISTS(
        SELECT 1 FROM WatchLaterItem 
        WHERE type = :type AND payload = :payload
    )
    """,
    )
    suspend fun exists(type: WatchLaterType, payload: Long): Boolean

    @Query("DELETE FROM WatchLaterItem WHERE payload = :payload AND type = :type")
    suspend fun delete(type: WatchLaterType, payload: Long)
}

@Entity
@TypeConverters(WatchLaterTypeConverter::class, JsonObjectConverter::class)
data class WatchLaterItem(
    @PrimaryKey(autoGenerate = false)
    val id: Long? = null,
    val type: WatchLaterType,

    val payload: Long, // 随type的改变而改变
    val metadata: JsonObject,

    val createTime: Long = Clock.System.now().toEpochMilliseconds(),
)

@Serializable
enum class WatchLaterType {
    ILLUST,
    NOVEL,
    AUTHOR,
    SERIES,
}
