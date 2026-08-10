package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Relation

@Entity(
    tableName = "user_cache",
    primaryKeys = ["userId"],
    foreignKeys = [ForeignKey(entity = ImageUrlsCache::class, parentColumns = ["id"], childColumns = ["profileImageUrlsId"])],
    indices = [Index(value = ["profileImageUrlsId"])],
)
data class UserCache(
    val userId: Long,
    val name: String,
    val account: String,
    val profileImageUrlsId: String,
    val isFollowed: Boolean? = null,
    val comment: String? = null,
)

data class AuthorDisplayed(
    val userId: Long,
    val name: String,
    val account: String,
    val profileImageUrlsId: String,
    val isFollowed: Boolean? = null,
    val comment: String? = null,

    @Relation(
        parentColumns = ["profileImageUrlsId"],
        entityColumns = ["id"],
    )
    val profileImageUrls: ImageUrlsCache,
)
