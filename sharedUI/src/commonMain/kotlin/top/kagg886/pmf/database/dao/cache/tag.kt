package top.kagg886.pmf.database.dao.cache

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert

@Entity(
    tableName = "tag_cache",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagCache(
    @PrimaryKey
    val id: String,
    val name: String,
    val translatedName: String? = null,
)

@Dao
interface TagDao {
    @Upsert
    suspend fun upsert(item: TagCache)

    @Query("SELECT * FROM tag_cache WHERE id = :id")
    suspend fun find(id: String): TagCache?

    @Query("SELECT * FROM tag_cache WHERE name = :name")
    suspend fun findByName(name: String): TagCache?

    @Query("DELETE FROM tag_cache WHERE id = :id")
    suspend fun delete(id: String)
}
