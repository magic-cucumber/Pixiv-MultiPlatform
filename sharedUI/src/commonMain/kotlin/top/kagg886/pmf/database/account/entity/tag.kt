package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "tag_cache", indices = [Index(value = ["name"], unique = true)])
data class TagCache(
    @PrimaryKey val id: String,
    val name: String,
    val translatedName: String? = null,
)
