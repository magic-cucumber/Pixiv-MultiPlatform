package top.kagg886.pmf.database.account.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import top.kagg886.pmf.database.account.entity.NovelTagCrossRef

@Dao
interface NovelTagCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<NovelTagCrossRef>)

    @Query("SELECT * FROM novel_tag_cross_ref WHERE novelId = :novelId")
    suspend fun findByNovelId(novelId: Long): List<NovelTagCrossRef>

    @Query("DELETE FROM novel_tag_cross_ref WHERE novelId = :novelId")
    suspend fun deleteByNovelId(novelId: Long)
}
