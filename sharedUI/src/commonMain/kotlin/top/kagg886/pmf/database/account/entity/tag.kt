package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import top.kagg886.pixko.Tag

@Entity(tableName = "tag_cache", indices = [Index(value = ["name"], unique = true)])
data class TagCache(
    @PrimaryKey val id: String,
    val name: String,
    val translatedName: String? = null,
) {
    companion object {
        fun fromBean(bean: Tag): TagCache = TagCache(
            id = "tag:${bean.name}",
            name = bean.name,
            translatedName = bean.translatedName,
        )
    }
}
