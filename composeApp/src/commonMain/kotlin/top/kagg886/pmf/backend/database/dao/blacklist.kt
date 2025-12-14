package top.kagg886.pmf.backend.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import top.kagg886.pixko.User
import top.kagg886.pmf.backend.database.converters.UserConverter

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/12/11 09:31
 * ================================================
 */

@Dao
interface BlackListDao {
    @Query("SELECT EXISTS(SELECT 1 FROM BlackListItem WHERE type = :type AND payload = :payload)")
    suspend fun matchRules(type: BlackListType, payload: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: BlackListItem): Long

    @Query("DELETE FROM BlackListItem WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM BlackListItem WHERE type = :type")
    fun query(type: BlackListType): PagingSource<Int, BlackListItem>
}

@Entity(
    indices = [
        Index(value = ["type", "payload"], unique = true),
    ],
)
@TypeConverters(UserConverter::class)
data class BlackListItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    val type: BlackListType,
    val payload: String, // 为AUTHOR_ID时，payload可以转换为数字，为TAG_NAME时，payload为标签名
    val meta: User? = null, // 为AUTHOR_ID时，meta为用户信息的缓存(不能保证type为AUTHOR_ID时，这里一定有值)
    val createTime: Long = Clock.System.now().toEpochMilliseconds(),
)

val BlackListItem.name
    get() = when (type) {
        BlackListType.TAG_NAME -> payload
        else -> error("can't cast payload to name, because type is:$type")
    }

val BlackListItem.illustOrNovelId
    get() = when (type) {
        BlackListType.AUTHOR_ID -> payload.toLong()
        else -> error("can't cast payload to illust/novel id, because type is:$type")
    }

fun BlackListItem(payload: User) = BlackListItem(
    type = BlackListType.AUTHOR_ID,
    payload = payload.id.toString(),
    meta = payload,
)

fun BlackListItem(payload: String) = BlackListItem(
    type = BlackListType.TAG_NAME,
    payload = payload,
)

@Serializable
enum class BlackListType {
    TAG_NAME,
    AUTHOR_ID,
}
