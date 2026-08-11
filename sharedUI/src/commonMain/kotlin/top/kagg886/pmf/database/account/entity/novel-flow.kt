package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "novel_flow",
    foreignKeys = [
        ForeignKey(entity = NovelCache::class, parentColumns = ["novelId"], childColumns = ["novelCacheId"]),
    ],
    indices = [Index(value = ["tag"]), Index(value = ["novelCacheId"])],
)
data class NovelFlow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tag: String,
    val novelCacheId: Long,
)
