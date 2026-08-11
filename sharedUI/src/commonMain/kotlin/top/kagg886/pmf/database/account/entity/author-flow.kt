package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "author_flow",
    foreignKeys = [
        ForeignKey(entity = UserCache::class, parentColumns = ["userId"], childColumns = ["userCacheId"]),
    ],
    indices = [Index(value = ["tag"]), Index(value = ["userCacheId"])],
)
data class AuthorFlow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tag: String,
    val userCacheId: Long,
)
