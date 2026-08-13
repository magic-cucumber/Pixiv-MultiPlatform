@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.database.account.entity

import androidx.room3.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.kagg886.pixko.module.illust.Illust
import kotlin.time.ExperimentalTime

@Entity(
    tableName = "illust_cache",
    primaryKeys = ["illustId"],
    foreignKeys = [
        ForeignKey(entity = UserCache::class, parentColumns = ["userId"], childColumns = ["authorId"]),
        ForeignKey(entity = ImageUrlsCache::class, parentColumns = ["id"], childColumns = ["imageUrlsId"]),
    ],
    indices = [Index(value = ["authorId"]), Index(value = ["imageUrlsId"])],
)
data class IllustCache(
    val illustId: Long,
    val title: String,
    val caption: String,
    val type: String,
    val authorId: Long,
    val createTime: Long,
    val pageCount: Int,
    val width: Int,
    val height: Int,
    val sanityLevel: Int,
    val xRestrict: Int,
    val totalView: Int,
    val totalBookmarks: Int,
    val isBookmarked: Boolean,
    val illustAiType: Int,
    val imageUrlsId: String,
    val singlePageMetaJson: String? = null,
) {
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
    val illustId: Long,
    val title: String,
    val caption: String,
    val type: String,
    val authorId: Long,
    val createTime: Long,
    val pageCount: Int,
    val width: Int,
    val height: Int,
    val sanityLevel: Int,
    val xRestrict: Int,
    val totalView: Int,
    val totalBookmarks: Int,
    val isBookmarked: Boolean,
    val illustAiType: Int,
    val imageUrlsId: String,
    val singlePageMetaJson: String? = null,

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
) {
    @Ignore
    val isR18G: Boolean = xRestrict == 2 && sanityLevel >= 6

    /**
     * 是否为R18
     */@Ignore
    val isR18: Boolean = isR18G || xRestrict == 1 && sanityLevel >= 4
    @Ignore
    val isUgoira: Boolean = type == "ugoira"


    /**
     * 是否为AI
     */
    @Ignore
    val isAI: Boolean = illustAiType == 2

    @delegate:Ignore
    val contentImages: List<ImageUrlsCache> by lazy {
        if (pageCount > 1) {
            return@lazy metaPages.map { it.imageUrls }
        }
        return@lazy listOf(
            imageUrls.copy(
                original = singlePageMetaJson?.let {
                    Json.parseToJsonElement(it).jsonObject["original_image_url"]?.jsonPrimitive?.content
                }
            )
        )
    }

    /**
     * 插画若被限制，可调用此字段获取限制原因
     */
    @delegate:Ignore
    val limitLevel: Illust.LimitLevel by lazy {
        val token = "https://s.pximg.net/common/images/limit_"
        val level = imageUrls.content.takeIf { it.startsWith(token) }?.substring(token.length)
            ?: return@lazy Illust.LimitLevel.NONE

        when {
            level.startsWith("r18g_") -> Illust.LimitLevel.LIMIT_R18G
            level.startsWith("r18_") -> Illust.LimitLevel.LIMIT_R18
            level.startsWith("mypixiv_") -> Illust.LimitLevel.LIMIT_PRIVACY
            level.startsWith("sanity_level_") -> Illust.LimitLevel.LIMIT_R15
            level.startsWith("unviewable_") -> Illust.LimitLevel.LIMIT_UNKNOWN
            level.startsWith("unknown_") -> Illust.LimitLevel.LIMIT_UNKNOWN
            else -> Illust.LimitLevel.NONE
        }
    }
}
