package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Junction
import androidx.room3.Relation
import top.kagg886.pixko.module.novel.Novel
import kotlin.time.ExperimentalTime

@Entity(
    tableName = "novel_cache",
    primaryKeys = ["novelId"],
    foreignKeys = [
        ForeignKey(entity = ImageUrlsCache::class, parentColumns = ["id"], childColumns = ["imageUrlsId"]),
        ForeignKey(entity = UserCache::class, parentColumns = ["userId"], childColumns = ["authorId"]),
        ForeignKey(entity = NovelSeriesCache::class, parentColumns = ["id"], childColumns = ["seriesId"]),
    ],
    indices = [Index(value = ["imageUrlsId"]), Index(value = ["authorId"]), Index(value = ["seriesId"])],
)
data class NovelCache(
    val novelId: Long,
    val title: String,
    val caption: String,
    val imageUrlsId: String,
    val authorId: Long,
    val createTime: Long,
    val textLength: Int,
    val seriesId: Long?,
    val isBookmarked: Boolean,
    val totalBookmarks: Int,
    val totalView: Int,
    val totalComments: Int,
    val isAI: Boolean,
    val isR18: Boolean,
    val isR18G: Boolean,
) {
    companion object {
        @OptIn(ExperimentalTime::class)
        fun fromBean(bean: Novel): NovelCache = NovelCache(
            novelId = bean.id.toLong(),
            title = bean.title,
            caption = bean.caption,
            imageUrlsId = "novel:${bean.id}:cover",
            authorId = bean.user.id.toLong(),
            createTime = bean.createDate.toEpochMilliseconds(),
            textLength = bean.textLength,
            seriesId = bean.series.id?.takeIf { it >= 0 }?.toLong(),
            isBookmarked = bean.isBookmarked,
            totalBookmarks = bean.totalBookmarks,
            totalView = bean.totalView,
            totalComments = bean.totalComments,
            isAI = bean.isAI,
            isR18 = bean.isR18,
            isR18G = bean.isR18G,
        )
    }
}

data class NovelCacheDisplayed(
    val novelId: Long,
    val title: String,
    val caption: String,
    val imageUrlsId: String,
    val authorId: Long,
    val createTime: Long,
    val textLength: Int,
    val seriesId: Long?,
    val isBookmarked: Boolean,
    val totalBookmarks: Int,
    val totalView: Int,
    val totalComments: Int,
    val isAI: Boolean,
    val isR18: Boolean,
    val isR18G: Boolean,

    @Relation(entity = UserCache::class, parentColumns = ["authorId"], entityColumns = ["userId"])
    val author: AuthorDisplayed,

    @Relation(parentColumns = ["imageUrlsId"], entityColumns = ["id"])
    val imageUrls: ImageUrlsCache,

    @Relation(
        entity = TagCache::class,
        parentColumns = ["novelId"],
        entityColumns = ["id"],
        associateBy = Junction(
            value = NovelTagCrossRef::class,
            parentColumns = ["novelId"],
            entityColumns = ["tagId"],
        ),
    )
    val tags: List<TagCache>,

    @Relation(parentColumns = ["seriesId"], entityColumns = ["id"])
    val series: NovelSeriesCache?,
)
