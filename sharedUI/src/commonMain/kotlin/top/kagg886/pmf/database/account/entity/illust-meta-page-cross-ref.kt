package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Relation

@Entity(
    tableName = "illust_meta_page_cross_ref",
    primaryKeys = ["illustId", "sortIndex"],
    foreignKeys = [
        ForeignKey(entity = IllustCache::class, parentColumns = ["illustId"], childColumns = ["illustId"]),
        ForeignKey(entity = ImageUrlsCache::class, parentColumns = ["id"], childColumns = ["imageUrlsId"]),
    ],
    indices = [Index(value = ["imageUrlsId"])],
)
data class IllustMetaPageCrossRef(val illustId: Long, val imageUrlsId: String, val sortIndex: Int)

data class IllustMetaPageDisplayed(
    val illustId: Long,
    val imageUrlsId: String,
    val sortIndex: Int,

    @Relation(
        parentColumns = ["imageUrlsId"],
        entityColumns = ["id"],
    )
    val imageUrls: ImageUrlsCache,
)
