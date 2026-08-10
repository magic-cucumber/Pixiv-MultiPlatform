package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Junction
import androidx.room3.Relation

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
)

data class IllustCacheDisplayed(
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
    val author: AuthorDisplayed,

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
)
