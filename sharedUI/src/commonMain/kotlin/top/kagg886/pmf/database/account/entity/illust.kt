@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.database.account.entity

import androidx.room3.*
import top.kagg886.pixko.module.illust.Illust
import kotlin.time.ExperimentalTime

sealed interface IllustCacheLike {
    val illustId: Long
    val title: String
    val caption: String
    val type: String
    val authorId: Long
    val createTime: Long
    val pageCount: Int
    val width: Int
    val height: Int
    val sanityLevel: Int
    val xRestrict: Int
    val totalView: Int
    val totalBookmarks: Int
    val isBookmarked: Boolean
    val illustAiType: Int
    val imageUrlsId: String
    val singlePageMetaJson: String?
}

@Entity(
    tableName = "illust_cache",
    primaryKeys = ["illustId"],
    foreignKeys = [
        ForeignKey(
            entity = UserCache::class,
            parentColumns = ["userId"],
            childColumns = ["authorId"],
        ),
        ForeignKey(
            entity = ImageUrlsCache::class,
            parentColumns = ["id"],
            childColumns = ["imageUrlsId"],
        ),
    ],
    indices = [
        Index(value = ["authorId"]),
        Index(value = ["imageUrlsId"]),
    ],
)
data class IllustCache(
    override val illustId: Long,
    override val title: String,
    override val caption: String,
    override val type: String,
    override val authorId: Long,
    override val createTime: Long,
    override val pageCount: Int,
    override val width: Int,
    override val height: Int,
    override val sanityLevel: Int,
    override val xRestrict: Int,
    override val totalView: Int,
    override val totalBookmarks: Int,
    override val isBookmarked: Boolean,
    override val illustAiType: Int,
    override val imageUrlsId: String,
    override val singlePageMetaJson: String? = null,
) : IllustCacheLike {
    companion object {
        @OptIn(ExperimentalTime::class)
        fun fromBean(bean: Illust): IllustCache = IllustCache(
            illustId = bean.id.toLong(),
            title = bean.title,
            caption = bean.caption,
            type = bean.type,
            authorId = bean.user.id.toLong(),
            createTime = bean.createTime.toEpochMilliseconds(),
            pageCount = bean.pageCount,
            width = bean.width,
            height = bean.height,
            sanityLevel = bean.sanityLevel,
            xRestrict = bean.xRestrict,
            totalView = bean.totalView,
            totalBookmarks = bean.totalBookmarks,
            isBookmarked = bean.isBookMarked,
            illustAiType = bean.illustAiType,
            imageUrlsId = "illust:${bean.id}:cover",
            singlePageMetaJson = bean.singlePageMeta?.toString(),
        )
    }
}

data class IllustCacheDisplayed(
    /** The database identity of the row in illust_flow, not the Pixiv illustration ID. */
    val flowId: Long,

    override val illustId: Long,
    override val title: String,
    override val caption: String,
    override val type: String,
    override val authorId: Long,
    override val createTime: Long,
    override val pageCount: Int,
    override val width: Int,
    override val height: Int,
    override val sanityLevel: Int,
    override val xRestrict: Int,
    override val totalView: Int,
    override val totalBookmarks: Int,
    override val isBookmarked: Boolean,
    override val illustAiType: Int,
    override val imageUrlsId: String,
    override val singlePageMetaJson: String? = null,

    @Relation(
        entity = UserCache::class,
        parentColumns = ["authorId"],
        entityColumns = ["userId"],
    )
    val author: UserDisplayedWithoutFlowed,

    @Relation(
        parentColumns = ["imageUrlsId"],
        entityColumns = ["id"],
    )
    val imageUrls: ImageUrlsCache,

    @Relation(
        entity = TagCache::class,
        parentColumns = ["illustId"],
        entityColumns = ["id"],
        associateBy = Junction(
            value = IllustTagCrossRef::class,
            parentColumns = ["illustId"],
            entityColumns = ["tagId"],
        ),
    )
    val tags: List<TagCache>,

    @Relation(
        entity = IllustMetaPageCrossRef::class,
        parentColumns = ["illustId"],
        entityColumns = ["illustId"],
    )
    val metaPages: List<IllustMetaPageDisplayed>,
) : IllustCacheLike {
    // 原有的 isR18G / isR18 / isUgoira / isAI /
    // contentImages / limitLevel 保持不变
}
