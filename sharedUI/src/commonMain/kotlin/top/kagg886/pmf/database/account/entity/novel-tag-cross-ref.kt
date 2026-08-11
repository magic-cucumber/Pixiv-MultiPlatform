package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "novel_tag_cross_ref",
    primaryKeys = ["novelId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = NovelCache::class, parentColumns = ["novelId"], childColumns = ["novelId"]),
        ForeignKey(entity = TagCache::class, parentColumns = ["id"], childColumns = ["tagId"]),
    ],
    indices = [Index(value = ["tagId"])],
)
data class NovelTagCrossRef(val novelId: Long, val tagId: String)
