package top.kagg886.pmf.database.account.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import top.kagg886.pmf.database.account.entity.IllustMetaPageCrossRef

@Dao
interface IllustMetaPageCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<IllustMetaPageCrossRef>)

    @Query("SELECT * FROM illust_meta_page_cross_ref WHERE illustId = :illustId ORDER BY sortIndex")
    suspend fun findByIllustId(illustId: Long): List<IllustMetaPageCrossRef>

    @Query("DELETE FROM illust_meta_page_cross_ref WHERE illustId = :illustId")
    suspend fun deleteByIllustId(illustId: Long)
}
