package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Relation
import top.kagg886.pixko.User

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
) {
    companion object {
        fun fromBean(bean: User): UserCache = UserCache(
            userId = bean.id.toLong(),
            name = bean.name,
            account = bean.account,
            profileImageUrlsId = "user:${bean.id}:profile",
            isFollowed = bean.isFollowed,
            comment = bean.comment,
        )
    }
}

data class UserDisplayed(
    /** The database identity of the row in author_flow; zero for nested author relations. */
    val flowId: Long = 0L,
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


data class UserDisplayedWithoutFlowed(
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
