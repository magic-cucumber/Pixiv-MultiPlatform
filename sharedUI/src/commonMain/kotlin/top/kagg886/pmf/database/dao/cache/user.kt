package top.kagg886.pmf.database.dao.cache

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Query
import androidx.room3.Upsert

@Entity(
    tableName = "user_cache",
    primaryKeys = ["currentUserId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = ImageUrlsCache::class,
            parentColumns = ["id"],
            childColumns = ["profileImageUrlsId"],
        ),
    ],
    indices = [Index(value = ["profileImageUrlsId"])],
)
data class UserCache(
    /** Account whose personalized user state this cache row contains. */
    val currentUserId: Long,
    val userId: Long,
    val name: String,
    val account: String,
    val profileImageUrlsId: String,
    val isFollowed: Boolean? = null,
    val comment: String? = null,
)

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(item: UserCache)

    @Query("SELECT * FROM user_cache WHERE currentUserId = :currentUserId AND userId = :userId")
    suspend fun find(currentUserId: Long, userId: Long): UserCache?

    @Query("DELETE FROM user_cache WHERE currentUserId = :currentUserId AND userId = :userId")
    suspend fun delete(currentUserId: Long, userId: Long)
}
