@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(ExperimentalTime::class)

package top.kagg886.pmf.backend.database.dao

import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.kagg886.pixko.ImageUrls
import top.kagg886.pixko.Tag
import top.kagg886.pixko.User
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.ImageUrlsWrapper

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2026/7/6 21:16
 * ================================================
 */

@Dao
interface IllustGalleryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: IllustGalleryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentImages(items: List<IllustGalleryContentImages>)

    @Query("DELETE FROM IllustGalleryContentImages WHERE illustId = :illustId")
    suspend fun deleteContentImages(illustId: Int)

    @Query("SELECT * FROM IllustGalleryEntity WHERE id = :id")
    suspend fun findEntity(id: Int): IllustGalleryEntity?

    @Query("SELECT * FROM IllustGalleryContentImages WHERE illustId = :id ORDER BY sortId")
    suspend fun findContentImages(id: Int): List<IllustGalleryContentImages>

    @Transaction
    suspend fun insert(illust: Illust) {
        val (entity, images) = illust.toEntity()
        insert(entity)
        deleteContentImages(entity.id)
        insertContentImages(images)
    }

    @Transaction
    suspend fun find(id: Int): Illust? {
        val entity = findEntity(id) ?: return null
        return (entity to findContentImages(id)).toIllust()
    }
}

@Entity
@ColumnTypeConverters(IllustGalleryConverter::class)
data class IllustGalleryEntity(
    @PrimaryKey(autoGenerate = false)
    val id: Int,
    val title: String,
    val caption: String,
    val type: String,
    val user: User,
    val tags: List<Tag>,
    val createTime: Instant,
    val pageCount: Int,
    val width: Int,
    val height: Int,
    val sanityLevel: Int,
    val xRestrict: Int,
    val totalView: Int,
    val totalBookmarks: Int,
    val isBookMarked: Boolean,
    val illustAiType: Int,
    val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val original: String? = null,
) {
    @get:Ignore
    val imageUrls: ImageUrls
        get() = ImageUrls(
            squareMedium = squareMedium,
            medium = medium,
            large = large,
            original = original,
        )
}

@Entity(
    indices = [
        Index(value = ["illustId", "sortId"], unique = true),
    ],
)
data class IllustGalleryContentImages(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    val illustId: Int,
    val sortId: Int,
    val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val original: String? = null,
) {
    @get:Ignore
    val imageUrls: ImageUrls
        get() = ImageUrls(
            squareMedium = squareMedium,
            medium = medium,
            large = large,
            original = original,
        )
}

class IllustGalleryConverter {
    @ColumnTypeConverter
    fun stringToUser(value: String): User = Json.decodeFromString(value)

    @ColumnTypeConverter
    fun userToString(value: User): String = Json.encodeToString(value)

    @ColumnTypeConverter
    fun stringToTags(value: String): List<Tag> = Json.decodeFromString(ListSerializer(Tag.serializer()), value)

    @ColumnTypeConverter
    fun tagsToString(value: List<Tag>): String = Json.encodeToString(ListSerializer(Tag.serializer()), value)

    @ColumnTypeConverter
    fun longToInstant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

    @ColumnTypeConverter
    fun instantToLong(value: Instant): Long = value.toEpochMilliseconds()
}

fun Pair<IllustGalleryEntity, List<IllustGalleryContentImages>>.toIllust(): Illust {
    val (entity, images) = this
    val sortedImages = images.sortedBy(IllustGalleryContentImages::sortId)
    return Illust(
        id = entity.id,
        title = entity.title,
        caption = entity.caption,
        type = entity.type,
        user = entity.user,
        tags = entity.tags,
        createTime = entity.createTime,
        pageCount = entity.pageCount,
        width = entity.width,
        height = entity.height,
        sanityLevel = entity.sanityLevel,
        xRestrict = entity.xRestrict,
        totalView = entity.totalView,
        totalBookmarks = entity.totalBookmarks,
        isBookMarked = entity.isBookMarked,
        illustAiType = entity.illustAiType,
        imageUrls = entity.imageUrls,
        _metaPages = if (entity.pageCount > 1) {
            sortedImages.map { ImageUrlsWrapper(it.imageUrls) }
        } else {
            emptyList()
        },
        singlePageMeta = if (entity.pageCount > 1) {
            buildJsonObject { }
        } else {
            sortedImages.firstOrNull()?.original?.let { original ->
                buildJsonObject {
                    put("original_image_url", original)
                }
            }
        },
    )
}

fun Illust.toEntity(): Pair<IllustGalleryEntity, List<IllustGalleryContentImages>> {
    val entity = IllustGalleryEntity(
        id = id,
        title = title,
        caption = caption,
        type = type,
        user = user,
        tags = tags,
        createTime = createTime,
        pageCount = pageCount,
        width = width,
        height = height,
        sanityLevel = sanityLevel,
        xRestrict = xRestrict,
        totalView = totalView,
        totalBookmarks = totalBookmarks,
        isBookMarked = isBookMarked,
        illustAiType = illustAiType,
        squareMedium = imageUrls.squareMedium,
        medium = imageUrls.medium,
        large = imageUrls.large,
        original = imageUrls.original,
    )
    val images = contentImages.mapIndexed { index, imageUrls ->
        IllustGalleryContentImages(
            illustId = id,
            sortId = index,
            squareMedium = imageUrls.squareMedium,
            medium = imageUrls.medium,
            large = imageUrls.large,
            original = imageUrls.original,
        )
    }
    return entity to images
}
