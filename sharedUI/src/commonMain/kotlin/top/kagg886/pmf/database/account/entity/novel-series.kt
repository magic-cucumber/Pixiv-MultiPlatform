package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import top.kagg886.pixko.module.novel.SeriesDetail
import top.kagg886.pixko.module.novel.SimpleSeries

@Entity(
    tableName = "novel_series_cache",
    foreignKeys = [
        ForeignKey(entity = UserCache::class, parentColumns = ["userId"], childColumns = ["userId"]),
    ],
    indices = [Index(value = ["userId"]), Index(value = ["tag"])],
)
data class NovelSeriesCache(
    @PrimaryKey val id: Long,
    val title: String,
    val caption: String?,
    val contentCount: Int?,
    val totalCharacterCount: Int?,
    val userId: Long,
    val tag: String = "novel:series:$id",
) {
    companion object {
        fun fromBean(bean: SimpleSeries, userId: Long): NovelSeriesCache {
            val id = requireNotNull(bean.id?.takeIf { it >= 0 }) { "SimpleSeries must have a valid id" }
            return NovelSeriesCache(
                id = id.toLong(),
                title = bean.title,
                caption = null,
                contentCount = null,
                totalCharacterCount = null,
                userId = userId,
            )
        }

        fun fromBean(bean: SeriesDetail): NovelSeriesCache = NovelSeriesCache(
            id = bean.id.toLong(),
            title = bean.title,
            caption = bean.caption,
            contentCount = bean.coutentCount,
            totalCharacterCount = bean.totalCharacterCount,
            userId = bean.user.id.toLong(),
        )
    }
}
