package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "illust_tag_cross_ref",
    primaryKeys = ["illustId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = IllustCache::class, parentColumns = ["illustId"], childColumns = ["illustId"]),
        ForeignKey(entity = TagCache::class, parentColumns = ["id"], childColumns = ["tagId"]),
    ],
    indices = [Index(value = ["tagId"])],
)
data class IllustTagCrossRef(val illustId: Long, val tagId: String)
