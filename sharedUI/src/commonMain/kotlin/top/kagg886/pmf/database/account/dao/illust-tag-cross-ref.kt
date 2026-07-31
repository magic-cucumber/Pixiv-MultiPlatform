package top.kagg886.pmf.database.account.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import top.kagg886.pmf.database.account.entity.IllustTagCrossRef

@Dao
interface IllustTagCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<IllustTagCrossRef>)

    @Query("SELECT * FROM illust_tag_cross_ref WHERE illustId = :illustId")
    suspend fun findByIllustId(illustId: Long): List<IllustTagCrossRef>

    @Query("DELETE FROM illust_tag_cross_ref WHERE illustId = :illustId")
    suspend fun deleteByIllustId(illustId: Long)
}
