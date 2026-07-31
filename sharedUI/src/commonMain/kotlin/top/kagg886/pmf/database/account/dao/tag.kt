package top.kagg886.pmf.database.account.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import top.kagg886.pmf.database.account.entity.TagCache

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
