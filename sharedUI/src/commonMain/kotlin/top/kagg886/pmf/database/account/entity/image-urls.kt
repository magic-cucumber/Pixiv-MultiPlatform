package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey

@Entity(tableName = "image_urls_cache")
data class ImageUrlsCache(
    @PrimaryKey val id: String,
    val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val original: String? = null,
) {
    @get:Ignore
    val content
        get() = arrayOf(medium, squareMedium, large).firstNotNullOf { it }

    @get:Ignore
    val contentLarge
        get() = arrayOf(large, medium, squareMedium).firstNotNullOf { it }
}
