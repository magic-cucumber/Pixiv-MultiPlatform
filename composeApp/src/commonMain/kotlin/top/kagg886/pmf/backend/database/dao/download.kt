package top.kagg886.pmf.backend.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Update
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.novel.Novel
import top.kagg886.pmf.backend.database.converters.IllustConverter

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(item: DownloadItem): Long

    @Query("SELECT * FROM DownloadItem WHERE id = :id")
    suspend fun find(id: Long): DownloadItem?

    @Update
    suspend fun update(item: DownloadItem)

    @Query(
        """
        SELECT * FROM DownloadItem
        WHERE 
            (:type IS NULL OR meta = :type)
            AND (
                LOWER(title) LIKE '%' || LOWER(:keyWord) || '%'
                OR (:searchInData AND LOWER(data) LIKE '%' || LOWER(:keyWord) || '%')
            )
        ORDER BY createTime DESC
    """,
    )
    fun query(
        keyWord: String = "",
        searchInData: Boolean = false,
        type: DownloadItemType? = null,
    ): PagingSource<Int, DownloadItem>

    // val data = database.downloadDAO().allSuspend()
    //            for (i in data) {
    //                if (!i.success) {
    //                    database.downloadDAO().update(i.copy(success = false, progress = -1f))
    //                }
    //            }

    @Query(
        """
        UPDATE DownloadItem
        SET progress = -1
        WHERE success = 0
    """,
    )
    suspend fun reset()
}

enum class DownloadItemType {
    ILLUST,
    NOVEL,
}

@Entity
@TypeConverters(IllustConverter::class)
data class DownloadItem(
    @PrimaryKey(autoGenerate = false)
    val id: Long,
    val title: String,
    val meta: DownloadItemType,
    val data: String,
    val success: Boolean,
    val progress: Float = -1f,
    val createTime: Long = Clock.System.now().toEpochMilliseconds(),
)

fun DownloadItem(
    id: Long,
    illust: Illust,
    success: Boolean,
    progress: Float = -1f,
    createTime: Long = Clock.System.now().toEpochMilliseconds(),
) = DownloadItem(id, illust.title, DownloadItemType.ILLUST, Json.encodeToString(illust), success, progress, createTime)

fun DownloadItem(
    id: Long,
    novel: Novel,
    success: Boolean,
    progress: Float = -1f,
    createTime: Long = Clock.System.now().toEpochMilliseconds(),
) = DownloadItem(id, novel.title, DownloadItemType.NOVEL, Json.encodeToString(novel), success, progress, createTime)

val DownloadItem.illust: Illust
    get() {
        check(meta == DownloadItemType.ILLUST) { "Not an illust" }
        return Json.decodeFromString<Illust>(data)
    }
val DownloadItem.novel: Novel
    get() {
        check(meta == DownloadItemType.NOVEL) { "Not a novel" }
        return Json.decodeFromString<Novel>(data)
    }
