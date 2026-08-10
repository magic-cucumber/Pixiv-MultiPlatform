package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "illust_flow",
    foreignKeys = [
        ForeignKey(
            entity = IllustCache::class,
            parentColumns = ["illustId"],
            childColumns = ["illustCacheId"],
        ),
    ],
    indices = [Index(value = ["tag"]), Index(value = ["illustCacheId"])],
)
data class IllustFlow(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tag: String,
    val illustCacheId: Long,
)
